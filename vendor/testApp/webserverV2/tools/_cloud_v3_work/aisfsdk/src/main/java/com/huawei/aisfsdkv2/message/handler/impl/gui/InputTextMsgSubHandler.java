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
import com.huawei.aisfsdkv2.message.handler.impl.helper.ImageHelper;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.message.model.gui.InputTextReqParam;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;

/**
 * 输入文本消息子处理器
 *
 * <p>处理 GUI 文本输入操作命令，支持截图上传。该处理器实现了以下核心功能：</p>
 *
 * <p><b>核心功能：</b>
 * <ol>
 *   <li><b>文本输入</b>：向当前焦点的输入框输入文本</li>
 *   <li><b>自动搜索</b>：可选自动执行回车操作触发搜索</li>
 *   <li><b>屏幕稳定等待</b>：截图前等待屏幕内容稳定</li>
 *   <li><b>截图上传</b>：输入后自动截图并上传到 ACM 服务器</li>
 * </ol>
 * </p>
 *
 * @author AI Assistant
 * @version 1.0
 * @since 2026-04-04
 * @see GuiSubMsgHandler
 * @see InputTextReqParam
 */
public class InputTextMsgSubHandler implements GuiSubMsgHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("InputTextMsgSubHandler");

    protected final ScreenshotManager screenshotManager;

    public InputTextMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 获取支持的命令类型
     *
     * @return {@link GuiCmdType#INPUT_TEXT} 输入文本命令类型
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.INPUT_TEXT;
    }

    /**
     * 处理输入文本消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>参数校验（文本非空）</li>
     *   <li>执行文本输入</li>
     *   <li>如果 isNeedSearch 为 true，执行回车操作</li>
     *   <li>判断是否需要截图上传</li>
     *   <li>等待屏幕稳定并截图上传</li>
     * </ol>
     *
     * @param msgInfo GUI 消息信息，包含输入文本和参数
     * @return 处理结果，成功返回 {@link GuiHandleResult#success()}，
     *         失败返回 {@link GuiHandleResult#failure(String)}
     * @throws ClassCastException 如果消息参数不是 {@link InputTextReqParam} 类型
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        InputTextReqParam params = (InputTextReqParam) msgInfo.getPayload().getParams();
        if (params == null) {
            LOGGER.error("InputTextMsgSubHandler: params is null");
            return GuiHandleResult.failure("Params is null");
        }
        String text = params.getInputText();
        if (text == null || text.isEmpty()) {
            LOGGER.error("InputTextMsgSubHandler: input text is empty");
            return GuiHandleResult.failure("Input text is empty");
        }
        int x = params.getStartX();
        int y = params.getStartY();
        AccessibilityManager accessibilityManager = getAccessibilityManager();
        if (accessibilityManager == null) {
            LOGGER.error("InputTextMsgSubHandler: accessibilityManager is null, accessibility service not connected");
            return GuiHandleResult.failure("Accessibility service not connected");
        }
        LOGGER.info("InputTextMsgSubHandler: start input text '{}' at [{}, {}]", text, x, y);
        boolean inputSuccess = accessibilityManager.inputText(x, y, text);
        if (!inputSuccess) {
            LOGGER.error("InputTextMsgSubHandler: input text failed at [{}, {}]", x, y);
            return GuiHandleResult.failure("Input text failed");
        }
        LOGGER.info("InputTextMsgSubHandler: input text success");
        if (params.isNeedSearch()) {
            LOGGER.info("InputTextMsgSubHandler: auto perform enter for search");
            boolean enterSuccess = accessibilityManager.autoPerformEnter();
            if (!enterSuccess) {
                LOGGER.warn("InputTextMsgSubHandler: auto perform enter failed, continue");
            }
        }
        if (!params.isNeedScreenShot()) {
            LOGGER.info("InputTextMsgSubHandler: no need screenshot, return success");
            return GuiHandleResult.success();
        }
        return sleepCaptureAndUpload(msgInfo, params, accessibilityManager);
    }

    /**
     * 等待截图并上传
     *
     * @param msgInfo 消息信息
     * @param params 输入文本参数
     * @param accessibilityManager 无障碍管理器
     * @return 处理结果
     */
    private GuiHandleResult sleepCaptureAndUpload(GuiMsgInfo msgInfo, InputTextReqParam params, AccessibilityManager accessibilityManager) {
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
            sendUploadScreenshotResponse(msgInfo, fileUrl, screenshot.getWidth(), screenshot.getHeight(), screenshotResult.getForegroundAppPackageName());
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
     * @param msgInfo 原始消息
     * @param fileUrl 文件URL
     * @param width 图片宽度
     * @param height 图片高度
     */
    private void sendUploadScreenshotResponse(GuiMsgInfo msgInfo, String fileUrl, int width, int height, String foregroundAppPackageName) {
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
        LOGGER.debug("sendUploadScreenshotResponse: upload screenshot response sent");
    }
}
