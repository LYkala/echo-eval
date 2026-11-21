/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection.msghandle;

/**
 * 消息处理器接口
 *
 * <p>用户可实现此接口，在消息处理的两个阶段插入自定义逻辑：
 * 1. beforeFilter: 入队前过滤/转换
 * 2. onResult: 发送结果通知
 *
 * <p>支持注册多个 Handler，形成处理链。
 */
public interface MsgHandler {

    /**
     * 获取处理器名称
     *
     * <p>用于日志记录和调试，建议返回可读的业务名称。
     * 默认实现返回简单类名，实现类可覆盖以提供更友好的名称。
     *
     * @return 处理器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 消息入队前过滤/处理
     *
     * <p>按 Handler 注册顺序链式调用。
     * 任一 Handler 返回 null，消息被丢弃，后续 Handler 不再处理。
     *
     * <p>常见用途：
     * - 消息校验（返回 null 表示丢弃）
     * - 消息转换（如对象序列化为 JSON）
     * - 消息增强（如添加时间戳、序号等）
     * - 压缩、加密
     *
     * @param message 原始消息（可能已被前置 Handler 处理）
     * @return 处理后的消息，返回 null 表示丢弃该消息
     */
    default String beforeFilter(String message) {
        return message;
    }

    /**
     * 接收服务端消息回调
     *
     * <p>当从 WebSocket 接收到服务端推送的消息时触发。
     * 按 Handler 注册逆序回调（后注册的先回调）。
     *
     * <p>常见用途：
     * - 消息解析
     * - 业务处理
     * - 日志记录
     * - 消息转发
     *
     * @param code    结果码枚举（通常 SUCCESS 表示正常接收）
     * @param error   错误描述（正常时为 null）
     * @param message 接收到的消息内容
     */
    default void onReceiveMsg(SendResultCodeEnum code, String error, String message) {
    }
}
