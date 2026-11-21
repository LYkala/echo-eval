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
import com.huawei.aisfsdkv2.message.handler.impl.helper.CropPosition;
import com.huawei.aisfsdkv2.message.handler.impl.helper.ImageHelper;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.message.model.gui.SwipeReqParam;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;

import java.util.concurrent.TimeUnit;

/**
 * 滑动消息子处理器
 *
 * <p>处理 GUI 滑动操作命令，支持截图对比防抖机制。该处理器实现了以下核心功能：</p>
 *
 * <p><b>核心功能：</b>
 * <ol>
 *   <li><b>截图对比防抖</b>：通过 SSIM 相似度算法对比前后的截图，避免无效滑动</li>
 *   <li><b>滑动操作</b>：在屏幕上执行滑动操作</li>
 *   <li><b>延迟等待</b>：滑动后可配置延迟时间等待动画完成</li>
 *   <li><b>屏幕稳定等待</b>：截图前等待屏幕内容稳定</li>
 *   <li><b>截图上传</b>：滑动后自动截图并上传到服务器</li>
 * </ol>
 * </p>
 *
 * @author AI Assistant
 * @since 2026-04-04
 * @see GuiSubMsgHandler
 * @see SwipeReqParam
 */
public class SwipeMsgSubHandler implements GuiSubMsgHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("SwipeMsgSubHandler");

    /** 最大重截图次数 */
    private static final int MAX_RE_SCREENSHOT_TIMES = 2;

    private int reScreenshotCount = 0;

    protected final ScreenshotManager screenshotManager;

    public SwipeMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 获取支持的命令类型
     *
     * @return {@link GuiCmdType#SWIPE}
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.SWIPE;
    }

    /**
     * 处理滑动消息
     *
     * @param msgInfo GUI 消息信息
     * @return 处理结果
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        SwipeReqParam params = (SwipeReqParam) msgInfo.getPayload().getParams();
        if (params == null) {
            LOGGER.error("SwipeMsgSubHandler: params is null");
            return GuiHandleResult.failure("Params is null");
        }
        int startX = params.getStartX();
        int startY = params.getStartY();
        int endX = params.getEndX();
        int endY = params.getEndY();
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            LOGGER.error("SwipeMsgSubHandler: invalid coordinates start[{}, {}] end[{}, {}]", startX, startY, endX, endY);
            return GuiHandleResult.failure("Invalid coordinates");
        }
        AccessibilityManager accessibilityManager = getAccessibilityManager();
        if (accessibilityManager == null) {
            LOGGER.error("SwipeMsgSubHandler: accessibilityManager is null, accessibility service not connected");
            return GuiHandleResult.failure("Accessibility service not connected");
        }
        int duration = params.getDuration();
        LOGGER.info("SwipeMsgSubHandler: start processing swipe from [{}, {}] to [{}, {}] duration={}ms", startX, startY, endX, endY, duration);
        boolean isComparePass = isScreenshotComparePass(params);
        if (isComparePass) {
            boolean success = accessibilityManager.swipe(startX, startY, endX, endY, duration);
            if (!success) {
                LOGGER.error("SwipeMsgSubHandler: swipe failed");
                return GuiHandleResult.failure("Swipe operation failed");
            }
            LOGGER.info("SwipeMsgSubHandler: swipe success");
        } else {
            LOGGER.info("SwipeMsgSubHandler: skip swipe due to screenshot compare not pass");
        }
        if (!params.isNeedScreenShot()) {
            LOGGER.info("SwipeMsgSubHandler: no need screenshot, return success");
            return GuiHandleResult.success();
        }
        if (isComparePass && params.getSleepTime() > 0) {
            LOGGER.info("SwipeMsgSubHandler: sleep {}ms after swipe", params.getSleepTime());
            try {
                TimeUnit.MILLISECONDS.sleep(params.getSleepTime());
            } catch (InterruptedException e) {
                LOGGER.warn("SwipeMsgSubHandler: sleep interrupted");
                Thread.currentThread().interrupt();
            }
        }
        boolean isReScreenShot = !isComparePass;
        return sleepCaptureAndUpload(msgInfo, params, isReScreenShot, accessibilityManager);
    }

    /**
     * 截图对比防抖
     */
    private boolean isScreenshotComparePass(SwipeReqParam params) {
        if (!params.isNeedScreenShot()) {
            LOGGER.info("isScreenshotComparePass: no need screenshot, skip compare");
            return true;
        }
        Bitmap lastScreenshot = screenshotManager.getLastScreenshot();
        if (lastScreenshot == null) {
            LOGGER.info("isScreenshotComparePass: no last screenshot, skip compare");
            return true;
        }
        Bitmap screenShotNow = screenshotManager.takeValidScreenshot();
        if (screenShotNow == null) {
            LOGGER.warn("isScreenshotComparePass: failed to take current screenshot, skip compare");
            return true;
        }
        try {
            CropPosition position = new CropPosition(params.getStartX(), params.getStartY(), 5, 100);
            boolean isSimilar = ImageHelper.isActionRoiStableDynamic(lastScreenshot, screenShotNow, 0.8f, position);
            if (!isSimilar) {
                reScreenshotCount++;
                if (reScreenshotCount >= MAX_RE_SCREENSHOT_TIMES) {
                    LOGGER.warn("isScreenshotComparePass: reScreenshotCount reached max {}, force pass", MAX_RE_SCREENSHOT_TIMES);
                    reScreenshotCount = 0;
                    return true;
                }
                LOGGER.info("isScreenshotComparePass: screenshot not similar, reScreenshotCount={}", reScreenshotCount);
                return false;
            } else {
                LOGGER.info("isScreenshotComparePass: screenshot similar, pass");
                reScreenshotCount = 0;
                return true;
            }
        } finally {
            if (lastScreenshot != null && !lastScreenshot.isRecycled()) {
                lastScreenshot.recycle();
            }
        }
    }

    /**
     * 等待截图并上传
     */
    private GuiHandleResult sleepCaptureAndUpload(GuiMsgInfo msgInfo, SwipeReqParam params, boolean isReScreenShot, AccessibilityManager accessibilityManager) {
        LOGGER.info("sleepCaptureAndUpload: start screenshot process");
        ImageHelper.waitForScreenStable(accessibilityManager, params.getMaxScreenshotTime());
        ScreenshotResult screenshotResult = screenshotManager.takeValidScreenshotWithResult();
        if (screenshotResult == null) {
            LOGGER.error("sleepCaptureAndUpload: failed to capture screenshot");
            return GuiHandleResult.failure("Failed to capture screenshot");
        }
        Bitmap screenshot = screenshotResult.getBitmap();
        try {
            byte[] imageData = ImageHelper.compressToJpeg(screenshot);
            if (imageData == null) {
                LOGGER.error("sleepCaptureAndUpload: failed to compress screenshot");
                return GuiHandleResult.failure("Failed to compress screenshot");
            }
            String fileUrl = ImageHelper.uploadScreenshot(imageData, msgInfo.getHeader().getTaskId());
            if (fileUrl == null) {
                LOGGER.error("sleepCaptureAndUpload: failed to upload screenshot");
                return GuiHandleResult.failure("Failed to upload screenshot");
            }
            LOGGER.info("sleepCaptureAndUpload: screenshot uploaded successfully, url={}", fileUrl);
            sendUploadScreenshotResponse(msgInfo, fileUrl, isReScreenShot, screenshotResult);
            return GuiHandleResult.success();
        } finally {
            if (screenshot != null && !screenshot.isRecycled()) {
                screenshot.recycle();
            }
        }
    }

    /**
     * 发送截图上传成功响应
     */
    private void sendUploadScreenshotResponse(GuiMsgInfo msgInfo, String fileUrl, boolean isReScreenShot, ScreenshotResult screenshotResult) {
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
        respParam.setResolutionW(screenshotResult.getBitmap().getWidth());
        respParam.setResolutionH(screenshotResult.getBitmap().getHeight());
        respParam.setReScreenShot(isReScreenShot);
        respParam.setChangeCnt(0);
        respParam.setForegroundAppPackageName(screenshotResult.getForegroundAppPackageName());
        respMsg.getPayload().setParams(respParam);
        MessageProcessor.getInstance().send(respMsg);
        LOGGER.debug("sendUploadScreenshotResponse: upload screenshot response sent");
    }
}
