/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.impl;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.component.trace.TraceManager;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;
import com.huawei.aisfsdkv2.login.connection.msghandle.MsgHandler;
import com.huawei.aisfsdkv2.login.connection.msghandle.SendResultCodeEnum;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.message.handler.impl.GuiMsgHandler;
import com.huawei.aisfsdkv2.message.handler.impl.SessionMsgHandler;
import com.huawei.aisfsdkv2.message.handler.impl.LogUploadMsgHandler;
import com.huawei.aisfsdkv2.message.handler.impl.TaskStatusReportMsgHandler;
import com.huawei.aisfsdkv2.message.model.MessageWrapper;
import com.huawei.aisfsdkv2.mimictask.MimicTaskMsgHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 消息处理器实现类
 *
 * <p>单例模式，负责消息编解码和广播分发。</p>
 *
 * <p>设计特点：
 * <ul>
 *   <li>自动注册所有消息处理器（GUI、位置、会话、文件）</li>
 *   <li>队列模式：消息先入队，再由处理线程串行处理</li>
 *   <li>广播模式：一条消息分发给所有Handler</li>
 *   <li>异常隔离：单个Handler失败不影响其他Handler</li>
 *   <li>可控处理：支持暂停/恢复消息处理</li>
 * </ul>
 */
public class MessageProcessorImpl implements MessageProcessor, MsgHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MessageProcessorImpl");
    private static final MessageProcessorImpl INSTANCE = new MessageProcessorImpl();
    private final List<MessageHandler> handlers = new CopyOnWriteArrayList<>();

    /** 消息接收队列 */
    private final BlockingQueue<MessageWrapper> receiveQueue = new LinkedBlockingQueue<>();
    /** 暂停锁，用于强一致性控制 */
    private final Object pauseLock = new Object();
    /** 暂停标志 */
    private volatile boolean paused = false;
    /** 运行标志 */
    private volatile boolean running = true;
    /** 队列拉取超时时间（毫秒） */
    private static final int POLL_TIMEOUT_MS = 100;

    /**
     * 私有构造方法，初始化时注册所有消息处理器并启动处理线程
     */
    private MessageProcessorImpl() {
        ConnectionManager.getInstance().addMsgHandler(this);
        registerHandler(new GuiMsgHandler());
        registerHandler(new SessionMsgHandler());
        registerHandler(new TaskStatusReportMsgHandler());
        registerHandler(new LogUploadMsgHandler());
        registerHandler(new MimicTaskMsgHandler());
        ThreadPoolManager.getInstance().executeIndependentThreadPool(this::runProcessor);
        LOGGER.info("MessageProcessor initialized with {} handlers", handlers.size());
    }

    /**
     * 获取单例实例
     *
     * @return MessageProcessor 实例
     */
    public static MessageProcessorImpl getInstance() {
        return INSTANCE;
    }

    /**
     * 消息处理线程主循环
     */
    private void runProcessor() {
        LOGGER.info("MessageProcessor thread started");
        while (running) {
            try {
                // 暂停检查（强一致性）
                synchronized (pauseLock) {
                    while (paused) {
                        pauseLock.wait();
                    }
                }
                MessageWrapper wrapper = receiveQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (wrapper != null) {
                    processMessage(wrapper);
                }
            } catch (InterruptedException e) {
                LOGGER.info("MessageProcessor thread interrupted");
                break;
            }
        }
        LOGGER.info("MessageProcessor thread stopped");
    }

    /**
     * 处理单条消息
     *
     * <p>按优先级顺序分发给处理器，消费即停止。</p>
     *
     * @param wrapper 消息包装对象
     */
    private void processMessage(MessageWrapper wrapper) {
        long waitTimeMs = System.currentTimeMillis() - wrapper.enqueueTimeMs;
        LOGGER.info("processMessage: queueWaitTime={}ms, message={}", waitTimeMs, wrapper.message);
        JSONObject json;
        try {
            json = new JSONObject(wrapper.message);
        } catch (JSONException e) {
            LOGGER.error("processMessage: parse json failed, message={}", wrapper.message, e);
            return;
        }
        JSONObject header = json.optJSONObject("Header");
        String taskId = (header != null) ? header.optString("TaskID", "") : "";
        TraceManager.getInstance().setCurrentTaskId(taskId);
        for (MessageHandler handler : handlers) {
            try {
                if (handler.handleReceived(json)) {
                    LOGGER.debug("processMessage: consumed by {}", handler.getName());
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("processMessage: {} failed", handler.getName(), e);
            }
        }
        LOGGER.warn("processMessage: no handler consumed the message");
    }

    /**
     * 注册消息处理器
     *
     * <p>注册后自动按优先级排序，数值越小优先级越高。</p>
     *
     * @param handler 消息处理器
     */
    @Override
    public void registerHandler(MessageHandler handler) {
        if (handler == null) {
            return;
        }
        handlers.add(handler);
        handlers.sort(Comparator.comparingInt(MessageHandler::getPriority));
        LOGGER.info("MessageProcessor.registerHandler: {} registered, priority={}, total={}",
                handler.getName(), handler.getPriority(), handlers.size());
    }

    /**
     * 发送消息
     *
     * @param message 消息对象
     * @return 是否发送成功
     */
    @Override
    public boolean send(Object message) {
        if (message == null) {
            LOGGER.error("MessageProcessor.send: message is null");
            return false;
        }
        try {
            if (message instanceof com.huawei.aisfsdkv2.message.model.GuiMsgInfo) {
                com.huawei.aisfsdkv2.message.model.GuiMsgInfo guiMsg =
                        (com.huawei.aisfsdkv2.message.model.GuiMsgInfo) message;
                if (guiMsg.getCmdType()
                        == com.huawei.aisfsdkv2.message.model.GuiCmdType.UPLOAD_SCREEN_SHOT) {
                    com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                            .onScreenshotUploaded();
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        String json = message.toString();
        ConnectionManager.getInstance().send(json);
        return true;
    }

    /**
     * 接收消息回调
     *
     * <p>ConnectionManager回调入口，消息入队后立即返回，
     * 由处理线程异步处理。</p>
     *
     * @param code 发送结果状态码
     * @param error 错误信息
     * @param message JSON格式的消息内容
     */
    @Override
    public void onReceiveMsg(SendResultCodeEnum code, String error, String message) {
        LOGGER.debug("MessageProcessor.onReceiveMsg: code={}, error={}, message={}", code, error, message);
        if (!code.isSuccess()) {
            LOGGER.debug("MessageProcessor.onReceiveMsg: ignore failed message, code={}", code);
            return;
        }
        boolean success = receiveQueue.offer(new MessageWrapper(message));
        if (!success) {
            LOGGER.error("MessageProcessor.onReceiveMsg: failed to queue message, queueSize={}", receiveQueue.size());
            return;
        }
        LOGGER.debug("MessageProcessor.onReceiveMsg: message queued, queueSize={}", receiveQueue.size());
    }

    /**
     * 消息预处理过滤器
     *
     * @param message 原始消息
     * @return 处理后的消息
     */
    @Override
    public String beforeFilter(String message) {
        return message;
    }

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
     */
    @Override
    public String getName() {
        return "MessageProcessor";
    }

    /**
     * 立即发送消息（同步）
     *
     * @param message 消息对象
     * @return 是否发送成功
     */
    @Override
    public boolean sendImmediately(Object message) {
        if (message == null) {
            LOGGER.error("MessageProcessor.sendImmediately: message is null");
            return false;
        }
        String json = message.toString();
        LOGGER.info("MessageProcessor.sendImmediately: sending message");
        boolean sent = ConnectionManager.getInstance().sendImmediately(json);
        if (sent) {
            LOGGER.info("MessageProcessor.sendImmediately: message sent successfully");
        } else {
            LOGGER.error("MessageProcessor.sendImmediately: failed to send message");
        }
        return sent;
    }

    /**
     * 暂停消息处理
     *
     * <p>调用后，消息处理线程暂停处理队列中的消息，
     * 新消息继续入队缓存。</p>
     */
    @Override
    public void pause() {
        synchronized (pauseLock) {
            paused = true;
            LOGGER.info("MessageProcessor paused, queueSize={}", receiveQueue.size());
        }
    }

    /**
     * 恢复消息处理
     *
     * <p>恢复后，处理线程继续处理队列中缓存的消息。</p>
     */
    @Override
    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
            LOGGER.info("MessageProcessor resumed, queueSize={}", receiveQueue.size());
        }
    }

    /**
     * 是否暂停
     *
     * @return true 暂停中，false 正常处理
     */
    @Override
    public boolean isPaused() {
        synchronized (pauseLock) {
            return paused;
        }
    }

    /**
     * 获取消息队列长度
     *
     * @return 队列中待处理消息数量
     */
    @Override
    public int getQueueSize() {
        return receiveQueue.size();
    }
}
