/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;

import org.json.JSONObject;

/**
 * 日志上传消息处理器
 *
 * <p>处理服务端下发的日志上传指令。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>检查 Header.MessageType 是否为 "LifecycleManagement"</li>
 *   <li>检查 Data.MessageType 是否为 "UploadLogFile"</li>
 *   <li>调用 {@link com.huawei.aisfsdkv2.api.log.LogApi#uploadLogTrigger()} 触发日志上传</li>
 * </ol>
 */
public class LogUploadMsgHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("LogUploadMsgHandler");

    private static final String JSON_KEY_HEADER = "Header";
    private static final String JSON_KEY_DATA = "Data";
    private static final String JSON_KEY_MESSAGE_TYPE = "MessageType";

    private static final String MESSAGE_TYPE_LIFECYCLE_MANAGEMENT = "LifecycleManagement";
    private static final String MESSAGE_TYPE_UPLOAD_LOG_FILE = "UploadLogFile";

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    @Override
    public String getName() {
        return "LogUploadMsgHandler";
    }

    /**
     * 获取处理器优先级
     *
     * @return 优先级 40
     */
    @Override
    public int getPriority() {
        return 40;
    }

    /**
     * 处理接收到的消息
     *
     * <p>仅处理满足以下条件的消息：</p>
     * <ul>
     *   <li>Header.MessageType = "LifecycleManagement"</li>
     *   <li>Data.MessageType = "UploadLogFile"</li>
     * </ul>
     *
     * @param json JSON消息对象
     * @return true 表示消息已消费；false 表示不处理，继续传递
     */
    @Override
    public boolean handleReceived(JSONObject json) {
        if (json == null) {
            return false;
        }
        JSONObject header = json.optJSONObject(JSON_KEY_HEADER);
        if (header == null) {
            return false;
        }
        if (!MESSAGE_TYPE_LIFECYCLE_MANAGEMENT.equals(header.optString(JSON_KEY_MESSAGE_TYPE))) {
            return false;
        }
        JSONObject data = json.optJSONObject(JSON_KEY_DATA);
        if (data == null) {
            return false;
        }
        if (!MESSAGE_TYPE_UPLOAD_LOG_FILE.equals(data.optString(JSON_KEY_MESSAGE_TYPE))) {
            return false;
        }
        LOGGER.info("LogUploadMsgHandler.handleReceived: UploadLogFile command received, triggering log upload");
        AISFSDKClient.getLogApi().uploadLogTrigger();
        return true;
    }
}
