/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import android.graphics.Bitmap;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.component.androidfunc.app.model.LaunchAppOptions;
import com.huawei.aisfsdkv2.message.model.gui.LaunchAppReqParam;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;
import com.huawei.aisfsdkv2.message.handler.impl.helper.ImageHelper;

/**
 * 拉起APP消息子处理器
 *
 * <p>处理拉起APP命令</p>
 */
public class LaunchAppMsgSubHandler implements GuiSubMsgHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("LaunchAppMsgSubHandler");

    /** 默认拉起最大重试次数 */
    private static final int DEFAULT_LAUNCH_MAX_RETRY = 2;
    /** 默认就绪检查间隔（毫秒） */
    private static final long DEFAULT_READY_CHECK_INTERVAL_MS = 1000L;
    /** 默认截图等待时间（秒） */
    private static final int DEFAULT_SCREENSHOT_WAIT_TIME = 2;

    protected final ScreenshotManager screenshotManager;

    public LaunchAppMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 获取支持的命令类型
     *
     * @return LAUNCH_APP命令类型
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.LAUNCH_APP;
    }

    /**
     * 处理拉起APP消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>参数校验</li>
     *   <li>回到桌面</li>
     *   <li>启动应用</li>
     *   <li>等待应用主页面就绪</li>
     *   <li>截图并上传</li>
     * </ol>
     *
     * @param msgInfo GUI消息信息
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        LOGGER.info("LaunchAppMsgSubHandler: start processing launch app request");
        LaunchAppReqParam params = (LaunchAppReqParam) msgInfo.getPayload().getParams();
        LOGGER.info("LaunchAppMsgSubHandler: params: {}", params);
        if (params == null) {
            LOGGER.warn("LaunchAppMsgSubHandler: request params is null");
            return GuiHandleResult.failure("Request params is null");
        }
        String appPkgName = params.getAppPkgName();
        int launchFlag = params.getLaunchFlag();
        if (appPkgName == null || appPkgName.isEmpty()) {
            LOGGER.warn("LaunchAppMsgSubHandler: app name is null or empty");
            return GuiHandleResult.failure("App name is null or empty");
        }
        String appShowName = params.getAppName();
        // 与云侧 MaxScreenshotTime 对齐（常见为 8）；超时后 AppManager 会 DeviceSDK 式假定就绪
        int readyRetry = params.getScreenShotMaxTime() > 0
                ? params.getScreenShotMaxTime() : DEFAULT_SCREENSHOT_WAIT_TIME;
        LaunchAppOptions options = new LaunchAppOptions(appPkgName)
                .setAppName(appShowName)
                .setLaunchFlags(launchFlag)
                .setMaxLaunchRetry(DEFAULT_LAUNCH_MAX_RETRY)
                .setMaxReadyCheckRetry(readyRetry)
                .setReadyCheckIntervalMs(DEFAULT_READY_CHECK_INTERVAL_MS);
        LOGGER.info("LaunchAppMsgSubHandler: readyRetry={} (screenShotMaxTime={})",
                readyRetry, params.getScreenShotMaxTime());
        boolean launchSuccess = AndroidFuncManager.getInstance().getAppManager().launchApp(options);
        if (!launchSuccess) {
            LOGGER.error("LaunchAppMsgSubHandler: launch app {} failed", appPkgName);
            return GuiHandleResult.failure("Launch app failed");
        }
        LOGGER.info("LaunchAppMsgSubHandler: app {} launched and ready", appPkgName);
        waitPastLaunchSplash(getAccessibilityManager(), Math.max(readyRetry, 8));
        boolean screenshotSuccess = screenshotHandle(msgInfo, readyRetry);
        LOGGER.info("LaunchAppMsgSubHandler: launch app {} completed, screenshotSuccess={}", appPkgName, screenshotSuccess);
        if (!screenshotSuccess) {
            return GuiHandleResult.failure("Screenshot failed");
        }
        return GuiHandleResult.success();
    }

    /**
     * 闪屏会先稳住，再过几秒才切到主页。忽略拉起后 1.5 秒内的窗口抖动，
     * 等到下一次画面变化并稳住 1 秒再截图；超时则用当前画面。
     */
    private void waitPastLaunchSplash(AccessibilityManager accessibilityManager, int maxWaitSec) {
        if (accessibilityManager == null || !accessibilityManager.isAccessibilityEnabled()) {
            return;
        }
        int waitSec = maxWaitSec > 0 ? maxWaitSec : 8;
        long startMs = System.currentTimeMillis();
        long ignoreUntilMs = startMs + 1500L;
        long deadlineMs = startMs + waitSec * 1000L;
        long lastGen = accessibilityManager.getScreenGeneratorNum();
        long lastLateChangeMs = 0L;
        LOGGER.info("LaunchAppMsgSubHandler: wait past splash, maxWait={}s", waitSec);
        while (System.currentTimeMillis() < deadlineMs) {
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            long currentGen = accessibilityManager.getScreenGeneratorNum();
            if (currentGen != lastGen) {
                lastGen = currentGen;
                if (now >= ignoreUntilMs) {
                    lastLateChangeMs = now;
                }
            }
            if (lastLateChangeMs > 0L && now - lastLateChangeMs >= 1000L) {
                LOGGER.info("LaunchAppMsgSubHandler: page changed after splash, waited {}ms",
                        now - startMs);
                return;
            }
        }
        LOGGER.info("LaunchAppMsgSubHandler: splash wait timed out after {}s", waitSec);
    }

    /**
     * 处理截图
     *
     * <p>启动应用成功后截图并上传。</p>
     *
     * @param msgInfo GUI 消息信息
     * @return true 截图成功，false 失败
     */
    private boolean screenshotHandle(GuiMsgInfo msgInfo, int screenShotMaxTime) {
        AccessibilityManager accessibilityManager = getAccessibilityManager();
        LOGGER.info("LaunchAppMsgSubHandler: start screenshot process");
        if (accessibilityManager == null || !accessibilityManager.isAccessibilityEnabled()) {
            // DeviceSDK.rar：accessibility disabled, fixed wait for screen stable
            long waitSec = screenShotMaxTime > 0 ? screenShotMaxTime : DEFAULT_SCREENSHOT_WAIT_TIME;
            LOGGER.info("LaunchAppMsgSubHandler: accessibility disabled, fixed wait for screen stable ({}s)",
                    waitSec);
            try {
                Thread.sleep(waitSec * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            ImageHelper.waitForScreenStable(accessibilityManager, DEFAULT_SCREENSHOT_WAIT_TIME);
        }
        ScreenshotResult screenshotResult = screenshotManager.takeValidScreenshotWithResult();
        if (screenshotResult == null) {
            LOGGER.error("LaunchAppMsgSubHandler: failed to capture screen");
            return false;
        }
        Bitmap screenshot = screenshotResult.getBitmap();
        LOGGER.info("LaunchAppMsgSubHandler: screenshot captured, size={}x{}", screenshot.getWidth(), screenshot.getHeight());
        byte[] imageData = ImageHelper.compressToJpeg(screenshot);
        if (imageData == null) {
            LOGGER.error("LaunchAppMsgSubHandler: failed to compress screenshot");
            if (!screenshot.isRecycled()) {
                screenshot.recycle();
            }
            return false;
        }
        LOGGER.debug("LaunchAppMsgSubHandler: screenshot compressed, size={} bytes", imageData.length);
        String fileUrl = ImageHelper.uploadScreenshot(imageData, msgInfo.getTaskId());
        boolean success = fileUrl != null;
        if (!success) {
            LOGGER.error("LaunchAppMsgSubHandler: failed to upload screenshot after {} retries", ImageHelper.UPLOAD_RETRY_TIMES);
        } else {
            LOGGER.info("LaunchAppMsgSubHandler: screenshot uploaded successfully, url={}", fileUrl);
            sendUploadScreenshotMsg(msgInfo, fileUrl, screenshot.getWidth(), screenshot.getHeight(), screenshotResult.getForegroundAppPackageName());
        }
        if (!screenshot.isRecycled()) {
            screenshot.recycle();
        }
        return success;
    }

    /**
     * 发送截图上传成功响应
     *
     * @param msgInfo 原始消息
     * @param fileUrl 文件URL
     * @param width 图片宽度
     * @param height 图片高度
     */
    private void sendUploadScreenshotMsg(GuiMsgInfo msgInfo, String fileUrl, int width, int height, String foregroundAppPackageName) {
        GuiMsgInfo respMsg = new GuiMsgInfo();
        HeaderMsgInfo reqHeader = msgInfo.getHeader();
        HeaderMsgInfo respHeader = new HeaderMsgInfo();
        if (reqHeader != null) {
            respHeader.setClientSessionId(reqHeader.getClientSessionId());
            respHeader.setUserName(reqHeader.getUserName());
            respHeader.setDeviceId(reqHeader.getDeviceId());
            respHeader.setDeviceCategory(reqHeader.getDeviceCategory());
            respHeader.setClientExField(reqHeader.getClientExField());
            respHeader.setUuid(reqHeader.getUuid());
            respHeader.setTaskId(reqHeader.getTaskId());
        }
        respHeader.setMessageType("DataExchange");
        respHeader.setSender(AISFSDKClient.getBaseApi().getSdkRoleName());
        respHeader.setReceiver("SystemAgent");
        respHeader.setEventTimeStamp(TimeUtil.getEventTimestamp());
        DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
        if (deviceInfo != null) {
            respHeader.setDeviceCategoryStr(deviceInfo.getDeviceType().getName());
            respHeader.setDeviceUUID(deviceInfo.getDeviceUuid());
        }
        if (AISFSDKClient.getLoginApi() != null && AISFSDKClient.getLoginApi().getLoginInfo() != null) {
            respHeader.setIdentifyId(AISFSDKClient.getLoginApi().getLoginInfo().getIdentifyId());
        }
        respMsg.setHeader(respHeader);
        GuiData respData = new GuiData();
        respData.setMessageType("UserInput");
        respData.getExtParam().put("UserInputObject", fileUrl);
        respData.getExtParam().put("ImplicitInputFlag", true);
        respMsg.setData(respData);
        respMsg.getPayload().setCmdType(GuiCmdType.UPLOAD_SCREEN_SHOT);
        UploadScreenshotRespParam respParam = new UploadScreenshotRespParam();
        respParam.setResolutionW(width);
        respParam.setResolutionH(height);
        respParam.setReScreenShot(false);
        respParam.setChangeCnt(0);
        respParam.setForegroundAppPackageName(foregroundAppPackageName);
        respMsg.getPayload().setParams(respParam);
        MessageProcessor.getInstance().send(respMsg);
        LOGGER.debug("LaunchAppMsgSubHandler: upload screenshot response sent");
    }
}