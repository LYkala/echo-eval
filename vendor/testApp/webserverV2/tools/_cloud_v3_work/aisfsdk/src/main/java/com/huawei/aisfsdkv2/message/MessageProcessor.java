/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message;

import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.message.impl.MessageProcessorImpl;

/**
 * 消息处理器接口
 *
 * <p>提供消息发送、Handler 管理和消息处理控制能力。</p>
 */
public interface MessageProcessor {

    /**
     * 获取单例实例
     *
     * @return MessageProcessor 实例
     */
    static MessageProcessor getInstance() {
        return MessageProcessorImpl.getInstance();
    }

    /**
     * 初始化消息处理器
     */
    default void init() {
    }

    /**
     * 注册消息处理器
     *
     * @param handler 消息处理器
     */
    void registerHandler(MessageHandler handler);

    /**
     * 发送消息
     *
     * <p>支持以下类型：
     * <ul>
     *   <li>MsgInfo - SDK消息对象（自动处理类型映射和编码）</li>
     *   <li>String - JSON字符串（直接发送）</li>
     *   <li>其他对象 - 使用toString()序列化</li>
     * </ul>
     *
     * @param message 消息对象（MsgInfo、String或其他类型）
     * @return 是否发送成功
     */
    boolean send(Object message);

    /**
     * 立即发送消息（同步）
     *
     * <p>直接通过底层连接发送消息，不经过内部队列。
     * <strong>警告：此方法会阻塞调用线程，直到发送完成或失败。
     * 仅用于高优先级消息（如 Logout），普通消息请使用 {@link #send(Object)}。</strong></p>
     *
     * @param message 消息对象（会调用 toString() 获取 JSON）
     * @return 是否发送成功
     */
    boolean sendImmediately(Object message);

    /**
     * 暂停消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。调用 {@link #resume()} 恢复处理。</p>
     */
    void pause();

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    void resume();

    /**
     * 是否暂停
     *
     * @return true 暂停中，false 正常处理
     */
    boolean isPaused();

    /**
     * 获取消息队列长度
     *
     * @return 队列中待处理消息数量
     */
    int getQueueSize();
}
