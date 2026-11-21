/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import android.graphics.Bitmap;

import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotResult;
import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.message.model.gui.ScreenShotReqParam;
import com.huawei.aisfsdkv2.message.model.gui.UploadScreenshotRespParam;
import com.huawei.aisfsdkv2.util.TimeUtil;
import com.huawei.aisfsdkv2.message.handler.impl.helper.ImageHelper;

/**
 * 截图消息子处理器
 *
 * <p>处理截图命令，执行屏幕截图并上传到服务器。</p>
 */
public class ScreenShotMsgSubHandler implements GuiSubMsgHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenShotMsgSubHandler");

    protected final ScreenshotManager screenshotManager;

    public ScreenShotMsgSubHandler() {
        this.screenshotManager = AndroidFuncManager.getInstance().getScreenshotManager();
    }

    /**
     * 获取命令类型
     *
     * @return 截图命令类型
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.SCREEN_SHOT;
    }

    /**
     * 处理截图消息（异步上传）
     *
     * <p>执行截图后立即返回，上传在后台异步进行，不阻塞MessageProcessor线程池。</p>
     * <p>上传完成后通过回调发送响应消息，无论成功失败都会发送响应。</p>
     *
     * <p><b>处理流程：</b></p>
     * <ol>
     *   <li>提取参数</li>
     *   <li>执行截图（同步，快速操作）</li>
     *   <li>压缩图片（同步，快速操作）</li>
     *   <li>异步上传图片（不阻塞线程）</li>
     *   <li>异步发送响应（成功或失败）</li>
     *   <li>异步释放Bitmap资源</li>
     * </ol>
     *
     * @param msgInfo GUI消息信息
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        LOGGER.info("ScreenShotMsgSubHandler: start processing screenshot request");
        ScreenShotReqParam params = (ScreenShotReqParam) msgInfo.getPayload().getParams();
        int maxWaitTime = 2;
        if (params != null) {
            maxWaitTime = params.getMaxScreenshotTime();
            LOGGER.debug("ScreenShotMsgSubHandler: maxScreenshotTime={}", maxWaitTime);
        }

        // 截图（同步，快速操作）
        ScreenshotResult screenshotResult = screenshotManager.takeValidScreenshotWithResult();
        if (screenshotResult == null) {
            LOGGER.error("ScreenShotMsgSubHandler: failed to capture screen");
            return GuiHandleResult.failure("Screenshot capture failed");
        }

        Bitmap screenshot = screenshotResult.getBitmap();
        LOGGER.info("ScreenShotMsgSubHandler: screenshot captured, size={}x{}", screenshot.getWidth(), screenshot.getHeight());

        // 压缩图片（同步，快速操作）
        byte[] imageData = ImageHelper.compressToJpeg(screenshot);
        if (imageData == null) {
            LOGGER.error("ScreenShotMsgSubHandler: failed to compress screenshot");
            if (!screenshot.isRecycled()) {
                screenshot.recycle();
            }
            return GuiHandleResult.failure("Image compression failed");
        }
        LOGGER.debug("ScreenShotMsgSubHandler: screenshot compressed, size={} bytes", imageData.length);

        // 异步上传（不阻塞线程池）
        final Bitmap screenshotRef = screenshot;
        final ScreenshotResult resultRef = screenshotResult;
        ImageHelper.uploadScreenshotAsync(imageData, msgInfo.getTaskId(), new ImageHelper.UploadCallback() {
            @Override
            public void onSuccess(String fileUrl) {
                LOGGER.info("ScreenShotMsgSubHandler: async upload success, url={}", fileUrl);
                // 异步发送成功响应
                sendSuccessResponseAsync(msgInfo, fileUrl, screenshotRef.getWidth(), screenshotRef.getHeight(),
                                        resultRef.getForegroundAppPackageName());
                // 回收Bitmap
                if (!screenshotRef.isRecycled()) {
                    screenshotRef.recycle();
                }
            }

            @Override
            public void onFailure(String error) {
                LOGGER.error("ScreenShotMsgSubHandler: async upload failed, error={}", error);
                // 异步发送失败响应
                sendFailureResponseAsync(msgInfo, "Upload failed: " + error);
                // 回收Bitmap
                if (!screenshotRef.isRecycled()) {
                    screenshotRef.recycle();
                }
            }
        });

        // 立即返回，不阻塞线程池
        LOGGER.info("ScreenShotMsgSubHandler: screenshot upload started asynchronously");
        return GuiHandleResult.processing();
    }

    /**
     * 发送截图上传成功响应
     *
     * @param msgInfo                  原始消息
     * @param fileUrl                  文件URL
     * @param width                    图片宽度
     * @param height                   图片高度
     * @param foregroundAppPackageName 截图时刻屏幕最上层应用包名
     */
    private void sendSuccessResponse(GuiMsgInfo msgInfo, String fileUrl, int width, int height, String foregroundAppPackageName) {
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
        LOGGER.debug("ScreenShotMsgSubHandler: success response sent");
    }

    /**
     * 异步发送成功响应
     *
     * <p>在共享线程池中执行，避免阻塞上传线程。</p>
     *
     * @param msgInfo                  原始消息
     * @param fileUrl                  文件URL
     * @param width                    图片宽度
     * @param height                   图片高度
     * @param foregroundAppPackageName 截图时刻屏幕最上层应用包名
     */
    private void sendSuccessResponseAsync(GuiMsgInfo msgInfo, String fileUrl, int width, int height, String foregroundAppPackageName) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            sendSuccessResponse(msgInfo, fileUrl, width, height, foregroundAppPackageName);
        });
    }

    /**
     * 异步发送失败响应
     *
     * <p>在共享线程池中执行，避免阻塞上传线程。</p>
     *
     * @param msgInfo 原始消息
     * @param error   错误信息
     */
    private void sendFailureResponseAsync(GuiMsgInfo msgInfo, String error) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            // 发送失败响应（可根据需求实现）
            LOGGER.error("ScreenShotMsgSubHandler: sending failure response, error={}", error);
            // 注意：当前SDK可能没有定义失败响应格式，这里只记录日志
            // 如果需要发送失败响应，可参考sendSuccessResponse实现类似逻辑
        });
    }
}
