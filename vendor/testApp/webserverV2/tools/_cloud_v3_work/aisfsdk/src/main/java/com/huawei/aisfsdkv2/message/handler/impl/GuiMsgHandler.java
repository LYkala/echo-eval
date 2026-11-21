/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.LaunchAppMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.ScreenShotMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.ClickMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.SwipeMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.LongClickMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.InputTextMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.CompletedMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.HomeMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.BackMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.EnterMsgSubHandler;
import com.huawei.aisfsdkv2.message.handler.impl.gui.CheckAppInstalledMsgSubHandler;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.HeaderMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.ExceptionRespParam;
import com.huawei.aisfsdkv2.message.model.gui.GuiData;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;
import com.huawei.aisfsdkv2.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI消息处理器
 *
 * <p>负责接收和分发GUI消息到具体的子处理器。
 * 采用策略模式，每个GUI命令类型对应一个 {@link GuiSubMsgHandler} 实现。</p>
 *
 * <p><b>处理流程：</b></p>
 * <ol>
 *   <li>验证消息是否为GUI类型</li>
 *   <li>解析JSON为GuiMsgInfo对象</li>
 *   <li>获取命令类型并校验</li>
 *   <li>查找对应的子处理器</li>
 *   <li>执行子处理器处理消息</li>
 * </ol>
 *
 * <p><b>线程安全：</b>此类非线程安全，应在单线程环境使用或由外部同步。</p>
 */
public class GuiMsgHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("GuiMsgHandler");

    /** JSON字段名：Header */
    private static final String FIELD_HEADER = "Header";
    /** JSON字段名：Payload */
    private static final String FIELD_PAYLOAD = "Payload";
    /** JSON字段名：Function */
    private static final String FIELD_FUNCTION = "Function";
    /** GUI功能标识 */
    private static final String FUNCTION_GUI = "GUI";

    /** 子处理器映射表: Key=命令类型, Value=对应处理器 */
    private final Map<GuiCmdType, GuiSubMsgHandler> subHandlers = new HashMap<>();

    /**
     * 构造函数
     *
     * <p>初始化时自动注册所有GUI子处理器。</p>
     */
    public GuiMsgHandler() {
        registerHandler(new LaunchAppMsgSubHandler());
        registerHandler(new ScreenShotMsgSubHandler());
        registerHandler(new ClickMsgSubHandler());
        registerHandler(new SwipeMsgSubHandler());
        registerHandler(new LongClickMsgSubHandler());
        registerHandler(new InputTextMsgSubHandler());
        registerHandler(new CompletedMsgSubHandler());
        registerHandler(new HomeMsgSubHandler());
        registerHandler(new BackMsgSubHandler());
        registerHandler(new EnterMsgSubHandler());
        registerHandler(new CheckAppInstalledMsgSubHandler());
        LOGGER.info("GuiMsgHandler initialized with {} handlers", subHandlers.size());
    }

    /**
     * 注册子处理器
     *
     * @param handler 子处理器实例
     */
    private void registerHandler(GuiSubMsgHandler handler) {
        subHandlers.put(handler.getCmdType(), handler);
        LOGGER.info("GuiMsgHandler: registered handler for cmdType={}, handler={}",
                handler.getCmdType(), handler.getHandlerName());
    }

    /**
     * 获取处理器名称
     *
     * @return 处理器名称 "GuiMsgHandler"
     */
    @Override
    public String getName() {
        return "GuiMsgHandler";
    }

    /**
     * 获取处理器优先级
     *
     * @return 优先级 10（高优先级，GUI 交互需要快速响应）
     */
    @Override
    public int getPriority() {
        return 10;
    }

    /**
     * 处理接收到的JSON消息
     *
     * <p>主入口方法，负责消息的完整处理流程：</p>
     * <ol>
     *   <li>验证消息是否为GUI类型</li>
     *   <li>解析JSON为GuiMsgInfo对象</li>
     *   <li>获取并校验命令类型</li>
     *   <li>从注册表中查找对应的子处理器</li>
     *   <li>执行子处理器处理消息</li>
     * </ol>
     *
     * <p>任何步骤失败都会记录日志并提前返回，不会抛出异常。</p>
     *
     * @param json 接收到的JSON格式消息对象
     * @return true 表示消息已消费；false 表示不处理，继续传递
     */
    @Override
    public boolean handleReceived(JSONObject json) {
        if (!isGuiMessage(json)) {
            return false;
        }
        GuiMsgInfo msgInfo;
        try {
            msgInfo = GuiMsgInfo.fromJson(json);
        } catch (JSONException e) {
            LOGGER.error("GuiMsgHandler: failed to parse GUI message, error: {}", e.getMessage());
            return true;
        }
        GuiCmdType cmdType = msgInfo.getCmdType();
        if (cmdType == GuiCmdType.UNKNOWN) {
            LOGGER.warn("GuiMsgHandler: unknown command type");
            return true;
        }
        LOGGER.info("GuiMsgHandler: processing message, cmdType={}, taskId={}",
                cmdType, msgInfo.getTaskId());
        try {
            com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                    .onCommandReceived(cmdType);
        } catch (Throwable ignored) {
            // latency tracking must not break GUI
        }
        GuiSubMsgHandler handler = subHandlers.get(cmdType);
        if (handler == null) {
            LOGGER.warn("GuiMsgHandler: no handler registered for cmdType={}", cmdType);
            return true;
        }
        try {
            GuiHandleResult result = handler.handle(msgInfo);
            if (result.isResult()) {
                LOGGER.info("GuiMsgHandler: message processed successfully, cmdType={}", cmdType);
            } else {
                LOGGER.warn("GuiMsgHandler: message processing failed, cmdType={}, code={}, description={}",
                        cmdType, result.getCode(), result.getDescription());
                sendExceptionResponse(msgInfo, result.getCode(), result.getDescription());
            }
        } catch (Exception e) {
            LOGGER.error("GuiMsgHandler: error processing message, cmdType={}, error: {}",
                    cmdType, e.getMessage(), e);
            sendExceptionResponse(msgInfo, GuiHandleResult.DEFAULT_CODE, e.getMessage());
        }
        return true;
    }

    /**
     * 判断JSON消息是否为GUI类型
     *
     * <p>判断条件：</p>
     * <ol>
     *   <li>必须包含Header字段</li>
     *   <li>必须包含Payload字段</li>
     *   <li>Payload.Function必须等于"GUI"</li>
     * </ol>
     *
     * @param json 消息JSON对象
     * @return true if 是GUI消息
     */
    private boolean isGuiMessage(JSONObject json) {
        JSONObject header = json.optJSONObject(FIELD_HEADER);
        if (header == null) {
            return false;
        }
        JSONObject payload = json.optJSONObject(FIELD_PAYLOAD);
        if (payload == null) {
            return false;
        }
        String function = payload.optString(FIELD_FUNCTION);
        return FUNCTION_GUI.equals(function);
    }

    /**
     * 发送异常响应消息
     *
     * <p>当GUI消息处理失败或发生异常时，发送异常响应给服务端</p>
     *
     * @param msgInfo 原始消息
     * @param errorCode 错误码
     * @param errorDesc 错误描述
     */
    private void sendExceptionResponse(GuiMsgInfo msgInfo, int errorCode, String errorDesc) {
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
        respData.setMessageType("DataExchange");
        respMsg.setData(respData);
        respMsg.getPayload().setCmdType(GuiCmdType.EXCEPTION);

        ExceptionRespParam respParam = new ExceptionRespParam();
        respParam.setErrorCode(errorCode);
        respParam.setErrorDesc(errorDesc);

        respMsg.getPayload().setParams(respParam);
        MessageProcessor.getInstance().send(respMsg);
        LOGGER.info("GuiMsgHandler: exception response sent, errorCode={}, errorDesc={}", errorCode, errorDesc);
    }
}
