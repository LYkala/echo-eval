/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 会话回调接口
 *
 * <p>用于接收会话相关通知。</p>
 */
public interface SessionCallback {
    /**
     * 接收会话相关通知
     *
     * @param errorCode    错误码，0表示成功/正常
     * @param errorMessage 错误信息，错误码为0时可为空
     * @param data         响应数据，包含用户输出类型、文本和流式完成标志
     */
    void onReceived(int errorCode, String errorMessage, SessionResponseData data);

    /**
     * 获取消息发送前处理器
     *
     * <p>在消息发送前允许用户自定义处理逻辑，如添加自定义字段等。</p>
     *
     * @return 处理器，默认返回 null
     */
    default SendPreHandler getSendPreHandler() {
        return null;
    }
}
