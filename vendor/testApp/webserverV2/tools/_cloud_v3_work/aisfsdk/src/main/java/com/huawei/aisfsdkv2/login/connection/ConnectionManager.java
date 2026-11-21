/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.login.connection;

import com.huawei.aisfsdkv2.login.connection.connhandle.ConnectionParam;
import com.huawei.aisfsdkv2.login.connection.impl.ConnectionManagerImpl;
import com.huawei.aisfsdkv2.login.connection.msghandle.MsgHandler;

/**
 * 连接管理器接口
 * 管理 WebSocket 连接的建立、断开和重连
 */
public interface ConnectionManager {

    /**
     * 获取连接管理器实例（单例）
     *
     * @return ConnectionManager 实例
     */
    static ConnectionManager getInstance() {
        return ConnectionManagerImpl.getInstance();
    }

    /**
     * 建立 WebSocket 连接
     *
     * @param param 连接参数（包含 URL、认证信息、回调等）
     */
    void connect(ConnectionParam param);

    /**
     * 断开 WebSocket 连接
     */
    void disconnect();

    /**
     * 重新连接
     * <p>使用上次保存的参数重新建立连接
     */
    void reconnect();

    /**
     * 是否已连接
     *
     * @return true 已连接，false 未连接
     */
    boolean isConnected();

    /**
     * 是否正在连接中
     *
     * @return true 正在连接中，false 未在连接
     */
    boolean isConnecting();

    /**
     * 发送字符串消息（异步、无阻塞、无返回值）
     *
     * <p>消息立即进入内部队列，方法立即返回。发送结果通过 MsgHandler 异步处理。
     *
     * @param message 要发送的字符串消息，若为 null 或空则被忽略
     */
    void send(String message);

    /**
     * 添加消息处理器
     *
     * <p>支持添加多个 Handler，按添加顺序形成处理链。
     * beforeFilter 按顺序执行，onResult 按逆序回调。
     *
     * @param handler 消息处理器，不能为 null
     */
    void addMsgHandler(MsgHandler handler);

    /**
     * 立即发送消息（同步）
     *
     * <p>不经过内部队列，直接通过 WebSocket 发送。
     * <strong>警告：此方法会阻塞调用线程，直到发送完成或失败。
     * 仅用于高优先级消息（如 Logout），普通消息请使用 {@link #send(String)}。</strong></p>
     *
     * @param message 要发送的字符串消息
     * @return 是否发送成功
     */
    boolean sendImmediately(String message);
}
