/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import android.graphics.Bitmap;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.ClickResult;
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
import com.huawei.aisfsdkv2.message.model.gui.ClickReqParam;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;

import java.util.List;

/**
 * 点击消息子处理器
 *
 * <p>处理 GUI 点击操作命令，支持截图对比防抖机制。该处理器实现了以下核心功能：</p>
 *
 * <p><b>核心功能：</b>
 * <ol>
 *   <li><b>截图对比防抖</b>：通过 SSIM 相似度算法对比点击前后的截图，避免无效点击</li>
 *   <li><b>ROI 区域裁剪</b>：在点击位置周围裁剪 5x100 像素区域进行对比</li>
 *   <li><b>重试机制</b>：截图对比失败时最多重试 2 次，超过则强制通过</li>
 *   <li><b>屏幕稳定等待</b>：截图前等待屏幕内容稳定（连续 10 次检查无变化）</li>
 *   <li><b>截图上传</b>：点击后自动截图并上传到 ACM 服务器</li>
 * </ol>
 * </p>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * ClickMsgSubHandler handler = new ClickMsgSubHandler();
 * GuiHandleResult result = handler.handle(msgInfo);
 * </pre>
 * </p>
 *
 * <p><b>依赖组件：</b>
 * <ul>
 *   <li>{@link AccessibilityManager} - 提供点击和截图功能</li>
 *   <li>{@link ImageHelper} - 提供 SSIM 对比和上传功能</li>
 *   <li>{@link CropPosition} - ROI 裁剪参数</li>
 * </ul>
 * </p>
 *
 * @author AI Assistant
 * @version 1.0
 * @since 2026-04-03
 * @see GuiSubMsgHandler
 * @see ClickReqParam
 */
public class ClickMsgSubHandler implements GuiSubMsgHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClickMsgSubHandler");

    /** 最大重截图次数，截图对比不通过时最多重试 2 次 */
    private static final int MAX_RE_SCREENSHOT_TIMES = 2;

    /** 重截图计数器，记录当前重试次数 */
    private int reScreenshotCount = 0;

    protected final ScreenshotManager screenshotManager;

    public ClickMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 获取支持的命令类型
     *
     * @return {@link GuiCmdType#CLICK} 点击命令类型
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.CLICK;
    }

    /**
     * 处理点击消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>参数校验（坐标有效性）</li>
     *   <li>截图对比防抖（SSIM 相似度计算）</li>
     *   <li>条件执行点击（对比通过才点击）</li>
     *   <li>判断是否需要截图上传</li>
     *   <li>等待屏幕稳定并截图上传</li>
     * </ol>
     *
     * @param msgInfo GUI 消息信息，包含点击坐标和参数
     * @return 处理结果，成功返回 {@link GuiHandleResult#success()}，
     *         失败返回 {@link GuiHandleResult#failure(String)}
     * @throws ClassCastException 如果消息参数不是 {@link ClickReqParam} 类型
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        ClickReqParam params = (ClickReqParam) msgInfo.getPayload().getParams();
        if (params == null) {
            LOGGER.error("ClickMsgSubHandler: params is null");
            return GuiHandleResult.failure("Params is null");
        }
        int x = params.getStartX();
        int y = params.getStartY();
        if (x < 0 || y < 0) {
            LOGGER.error("ClickMsgSubHandler: invalid coordinates [{}, {}]", x, y);
            return GuiHandleResult.failure("Invalid coordinates");
        }
        AccessibilityManager accessibilityManager = getAccessibilityManager();
        if (accessibilityManager == null) {
            LOGGER.error("ClickMsgSubHandler: accessibilityManager is null, accessibility service not connected");
            return GuiHandleResult.failure("Accessibility service not connected");
        }
        LOGGER.info("ClickMsgSubHandler: start processing click at [{}, {}]", x, y);
        boolean isComparePass = isScreenshotComparePass(params);
        boolean isBlackAction = false;
        List<String> matchedKeywords = null;
        if (isComparePass) {
            ClickResult clickResult = accessibilityManager.click(x, y, params.getControlBlackList());
            matchedKeywords = clickResult.getMatchedKeywords();
            if (clickResult.isIntercepted()) {
                LOGGER.error("ClickMsgSubHandler: click intercepted by blacklist at [{}, {}]", x, y);
                isBlackAction = true;
            } else if (!clickResult.isSuccess()) {
                LOGGER.error("ClickMsgSubHandler: click failed at [{}, {}]", x, y);
            } else {
                LOGGER.info("ClickMsgSubHandler: click success at [{}, {}]", x, y);
            }
        } else {
            LOGGER.info("ClickMsgSubHandler: skip click due to screenshot compare not pass");
        }
        if (!params.isNeedScreenShot()) {
            if (isBlackAction) {
                return GuiHandleResult.failure("Click intercepted by blacklist");
            }
            LOGGER.info("ClickMsgSubHandler: no need screenshot, return success");
            return GuiHandleResult.success();
        }
        boolean isReScreenShot = !isComparePass;
        return sleepCaptureAndUpload(msgInfo, params, isReScreenShot, isBlackAction, matchedKeywords, accessibilityManager);
    }

    /**
     * 截图对比防抖
     *
     * @param params 点击参数
     * @return true 表示对比通过（可以执行点击）
     */
    private boolean isScreenshotComparePass(ClickReqParam params) {
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
     *
     * @param msgInfo          消息信息
     * @param params           点击参数
     * @param isReScreenShot   是否为重新截图
     * @param isBlackAction    是否被黑名单拦截
     * @param accessibilityManager 无障碍管理器
     * @return 处理结果
     */
    private GuiHandleResult sleepCaptureAndUpload(GuiMsgInfo msgInfo, ClickReqParam params, boolean isReScreenShot, boolean isBlackAction, List<String> matchedKeywords, AccessibilityManager accessibilityManager) {
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
            sendUploadScreenshotResponse(msgInfo, fileUrl, isReScreenShot, isBlackAction, matchedKeywords, screenshotResult);
            return GuiHandleResult.success();
        } finally {
            if (screenshot != null && !screenshot.isRecycled()) {
                screenshot.recycle();
            }
        }
    }

    /**
     * 发送截图上传成功响应
     *
     * @param msgInfo          原始消息
     * @param fileUrl          文件URL
     * @param isReScreenShot   是否为重新截图
     * @param isBlackAction    是否被黑名单拦截
     * @param screenshotResult 截图结果（含位图和前台包名）
     */
    private void sendUploadScreenshotResponse(GuiMsgInfo msgInfo, String fileUrl, boolean isReScreenShot, boolean isBlackAction, List<String> matchedKeywords, ScreenshotResult screenshotResult) {
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
        respParam.setMatchedKeywords(matchedKeywords);
        respParam.setChangeCnt(0);
        respParam.setForegroundAppPackageName(screenshotResult.getForegroundAppPackageName());
        respParam.setBlackAction(isBlackAction);
        respMsg.getPayload().setParams(respParam);
        MessageProcessor.getInstance().send(respMsg);
        LOGGER.debug("sendUploadScreenshotResponse: upload screenshot response sent");
    }
}
