/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import android.graphics.Bitmap;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.handler.impl.helper.ImageHelper;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.message.model.gui.SystemKeyReqParam;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;

/**
 * 系统按键消息子处理器基类
 *
 * <p>提供系统按键操作的通用功能，包括截图上传能力</p>
 */
public abstract class AbstractSystemKeyMsgSubHandler implements GuiSubMsgHandler {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass().getSimpleName());

    protected final ScreenshotManager screenshotManager;

    /**
     * 构造方法
     */
    public AbstractSystemKeyMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 处理系统按键消息
     *
     * @param msgInfo GUI 消息信息
     * @param operation 操作名称（用于日志）
     * @param action 实际执行的操作
     * @return 处理结果
     */
    protected GuiHandleResult handleSystemKey(GuiMsgInfo msgInfo, String operation, SystemKeyAction action) {
        SystemKeyReqParam params = (SystemKeyReqParam) msgInfo.getPayload().getParams();
        if (params == null) {
            params = new SystemKeyReqParam();
        }
        LOGGER.info("{}: start processing", operation);
        boolean success = action.execute();
        if (!success) {
            LOGGER.error("{}: operation failed", operation);
            return GuiHandleResult.failure(operation + " failed");
        }
        LOGGER.info("{}: operation success", operation);
        if (!params.isNeedScreenShot()) {
            LOGGER.info("{}: no need screenshot, return success", operation);
            return GuiHandleResult.success();
        }
        return sleepCaptureAndUpload(msgInfo, params, operation);
    }

    /**
     * 等待截图并上传
     */
    private GuiHandleResult sleepCaptureAndUpload(GuiMsgInfo msgInfo, SystemKeyReqParam params, String operation) {
        LOGGER.info("sleepCaptureAndUpload: start screenshot process for {}", operation);
        ImageHelper.waitForScreenStable(getAccessibilityManager(), params.getMaxScreenshotTime());
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
            sendUploadScreenshotResponse(msgInfo, fileUrl, screenshotResult);
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
    private void sendUploadScreenshotResponse(GuiMsgInfo msgInfo, String fileUrl, ScreenshotResult screenshotResult) {
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
        respParam.setReScreenShot(false);
        respParam.setChangeCnt(0);
        respParam.setForegroundAppPackageName(screenshotResult.getForegroundAppPackageName());
        respMsg.getPayload().setParams(respParam);
        MessageProcessor.getInstance().send(respMsg);
        LOGGER.debug("sendUploadScreenshotResponse: upload screenshot response sent");
    }

    /**
     * 系统按键操作接口
     */
    protected interface SystemKeyAction {
        boolean execute();
    }
}
