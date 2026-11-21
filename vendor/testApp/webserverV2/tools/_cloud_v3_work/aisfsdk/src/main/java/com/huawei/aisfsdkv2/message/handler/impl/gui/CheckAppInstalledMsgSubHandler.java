/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import com.huawei.aisfsdkv2.util.StringUtil;
import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.app.AppManager;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.AppInstallStatusRespParam;
import com.huawei.aisfsdkv2.message.model.gui.CheckAppInstalledReqParam;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.util.TimeUtil;

/**
 * 检查应用安装状态消息子处理器
 *
 * <p>处理检查应用是否安装的命令</p>
 */
public class CheckAppInstalledMsgSubHandler implements GuiSubMsgHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CheckAppInstalledMsgSubHandler");
    private final AppManager appManager;

    /**
     * 构造函数
     *
     * <p>初始化应用管理器实例</p>
     */
    public CheckAppInstalledMsgSubHandler() {
        this.appManager = AndroidFuncManager.getInstance().getAppManager();
    }

    /**
     * 获取支持的命令类型
     *
     * @return CHECK_APP_INSTALLED命令类型
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.CHECK_APP_INSTALLED;
    }

    /**
     * 处理检查应用安装状态消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>检查应用是否安装</li>
     *   <li>发送响应消息</li>
     * </ol>
     *
     * @param msgInfo GUI消息信息
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        LOGGER.info("CheckAppInstalledMsgSubHandler: start processing check app installed request");
        boolean isInstalled = checkAppInstallation(msgInfo);
        sendResponse(msgInfo, isInstalled);
        return GuiHandleResult.success();
    }

    /**
     * 检查应用安装状态
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>从消息中获取参数</li>
     *   <li>校验包名是否为空</li>
     *   <li>调用AppManager检查应用是否安装</li>
     * </ol>
     *
     * @param msgInfo GUI消息信息
     * @return 应用是否安装，true=已安装，false=未安装或检查失败
     */
    private boolean checkAppInstallation(GuiMsgInfo msgInfo) {
        CheckAppInstalledReqParam params = (CheckAppInstalledReqParam) msgInfo.getPayload().getParams();
        if (params == null) {
            LOGGER.warn("CheckAppInstalledMsgSubHandler: request params is null");
            return false;
        }
        String packageName = params.getPackageName();
        LOGGER.debug("CheckAppInstalledMsgSubHandler: received package name: {}", packageName);
        if (StringUtil.isNullOrEmpty(packageName)) {
            LOGGER.warn("CheckAppInstalledMsgSubHandler: package name is null or empty");
            return false;
        }
        LOGGER.info("CheckAppInstalledMsgSubHandler: checking installation status for package: {}", packageName);
        boolean isInstalled = appManager.isAppInstalled(packageName);
        LOGGER.info("CheckAppInstalledMsgSubHandler: package {} installation status: {}", packageName, isInstalled);
        return isInstalled;
    }

    /**
     * 发送应用安装状态响应
     *
     * @param msgInfo GUI消息信息
     * @param isInstalled 应用是否安装
     */
    private void sendResponse(GuiMsgInfo msgInfo, boolean isInstalled) {
        GuiMsgInfo guiRespMsgInfo = new GuiMsgInfo();
        HeaderMsgInfo respHeader = buildResponseHeader(msgInfo);
        guiRespMsgInfo.setHeader(respHeader);
        GuiData respData = new GuiData();
        respData.setMessageType("DataExchange");
        guiRespMsgInfo.setData(respData);
        guiRespMsgInfo.getPayload().setCmdType(GuiCmdType.APP_INSTALL_STATUS);
        AppInstallStatusRespParam appInstallStatusRespParam = new AppInstallStatusRespParam();
        if (isInstalled) {
            appInstallStatusRespParam.setIsInstall(AppInstallStatusRespParam.INSTALLED_VALUE);
        } else {
            appInstallStatusRespParam.setIsInstall(AppInstallStatusRespParam.NOT_INSTALLED_VALUE);
        }
        guiRespMsgInfo.getPayload().setParams(appInstallStatusRespParam);
        MessageProcessor.getInstance().send(guiRespMsgInfo);
    }

    /**
     * 构建响应Header
     *
     * @param msgInfo GUI消息信息
     * @return 响应Header
     */
    private HeaderMsgInfo buildResponseHeader(GuiMsgInfo msgInfo) {
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
        return respHeader;
    }
}
