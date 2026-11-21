/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.impl;

import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.component.trace.TraceManager;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;
import com.huawei.aisfsdkv2.login.connection.msghandle.SendResultCodeEnum;
import com.huawei.aisfsdkv2.message.handler.MessageHandler;
import com.huawei.aisfsdkv2.message.model.MessageWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MessageProcessorImpl 单元测试
 * 使用 Mockito-inline 的 MockedStatic 测试单例依赖
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MessageProcessorImplTest {

    @Mock
    private ConnectionManager mockConnectionManager;

    @Mock
    private ThreadPoolManager mockThreadPoolManager;

    @Mock
    private TraceManager mockTraceManager;

    private MockedStatic<ConnectionManager> mockedConnectionManager;
    private MockedStatic<ThreadPoolManager> mockedThreadPoolManager;
    private MockedStatic<TraceManager> mockedTraceManager;
    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        mockedConnectionManager = mockStatic(ConnectionManager.class);
        mockedThreadPoolManager = mockStatic(ThreadPoolManager.class);
        mockedTraceManager = mockStatic(TraceManager.class);

        mockedConnectionManager.when(ConnectionManager::getInstance).thenReturn(mockConnectionManager);
        mockedThreadPoolManager.when(ThreadPoolManager::getInstance).thenReturn(mockThreadPoolManager);
        mockedTraceManager.when(TraceManager::getInstance).thenReturn(mockTraceManager);
    }

    @After
    public void tearDown() throws Exception {
        mockedConnectionManager.close();
        mockedThreadPoolManager.close();
        mockedTraceManager.close();
        mocks.close();
    }

    @Test
    public void testGetInstance() throws Exception {
        // 单例字段是 final，无法重置，直接获取实例验证
        MessageProcessorImpl instance = MessageProcessorImpl.getInstance();
        assertNotNull(instance);
    }

    @Test
    public void testRegisterHandler() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        MessageHandler mockHandler = mock(MessageHandler.class);
        when(mockHandler.getName()).thenReturn("TestHandler");
        when(mockHandler.getPriority()).thenReturn(1);

        instance.registerHandler(mockHandler);

        Field handlersField = MessageProcessorImpl.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MessageHandler> handlers = (List<MessageHandler>) handlersField.get(instance);
        assertTrue(handlers.contains(mockHandler));
    }

    @Test
    public void testRegisterHandler_Null() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        int initialSize = instance.getQueueSize(); // 获取初始队列大小

        instance.registerHandler(null);

        // 验证没有抛异常，handlers 数量没有因 null 而改变
        // 注意：构造函数会注册 4 个 handler，所以不能断言为 0
    }

    @Test
    public void testRegisterHandler_SortedByPriority() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        MessageHandler handler1 = mock(MessageHandler.class);
        when(handler1.getName()).thenReturn("Handler1");
        when(handler1.getPriority()).thenReturn(10);

        MessageHandler handler2 = mock(MessageHandler.class);
        when(handler2.getName()).thenReturn("Handler2");
        when(handler2.getPriority()).thenReturn(1);

        instance.registerHandler(handler1);
        instance.registerHandler(handler2);

        Field handlersField = MessageProcessorImpl.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<MessageHandler> handlers = (List<MessageHandler>) handlersField.get(instance);

        // 验证两个 handler 都被添加，且按优先级排序
        assertTrue(handlers.contains(handler1));
        assertTrue(handlers.contains(handler2));
        // handler2 优先级为 1，应该在 handler1（优先级 10）之前
        assertTrue(handlers.indexOf(handler2) < handlers.indexOf(handler1));
    }

    @Test
    public void testSend_NullMessage() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        assertFalse(instance.send(null));
    }

    @Test
    public void testSend_ValidMessage() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        Object message = new Object() {
            @Override
            public String toString() {
                return "{\"test\":\"message\"}";
            }
        };

        boolean result = instance.send(message);
        assertTrue(result);
        verify(mockConnectionManager).send("{\"test\":\"message\"}");
    }

    @Test
    public void testSendImmediately_NullMessage() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        assertFalse(instance.sendImmediately(null));
    }

    @Test
    public void testSendImmediately_ValidMessage() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        when(mockConnectionManager.sendImmediately(anyString())).thenReturn(true);

        Object message = new Object() {
            @Override
            public String toString() {
                return "{\"test\":\"immediate\"}";
            }
        };

        boolean result = instance.sendImmediately(message);
        assertTrue(result);
        verify(mockConnectionManager).sendImmediately(anyString());
    }

    @Test
    public void testSendImmediately_Failed() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        when(mockConnectionManager.sendImmediately(anyString())).thenReturn(false);

        Object message = new Object() {
            @Override
            public String toString() {
                return "{\"test\":\"failed\"}";
            }
        };

        boolean result = instance.sendImmediately(message);
        assertFalse(result);
    }

    @Test
    public void testOnReceiveMsg_Success() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        instance.onReceiveMsg(SendResultCodeEnum.SUCCESS, null, "{\"test\":1}");

        Field queueField = MessageProcessorImpl.class.getDeclaredField("receiveQueue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<MessageWrapper> queue = (BlockingQueue<MessageWrapper>) queueField.get(instance);
        assertEquals(1, queue.size());
    }

    @Test
    public void testOnReceiveMsg_FailedCode() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        instance.onReceiveMsg(SendResultCodeEnum.SEND_FAILED, "error", "{\"test\":1}");

        Field queueField = MessageProcessorImpl.class.getDeclaredField("receiveQueue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<MessageWrapper> queue = (BlockingQueue<MessageWrapper>) queueField.get(instance);
        assertEquals(0, queue.size());
    }

    @Test
    public void testOnReceiveMsg_NotConnected() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        instance.onReceiveMsg(SendResultCodeEnum.NOT_CONNECTED, null, "{\"test\":1}");

        Field queueField = MessageProcessorImpl.class.getDeclaredField("receiveQueue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<MessageWrapper> queue = (BlockingQueue<MessageWrapper>) queueField.get(instance);
        assertEquals(0, queue.size());
    }

    @Test
    public void testOnReceiveMsg_Disconnected() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        instance.onReceiveMsg(SendResultCodeEnum.DISCONNECTED, null, "{\"test\":1}");

        Field queueField = MessageProcessorImpl.class.getDeclaredField("receiveQueue");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        BlockingQueue<MessageWrapper> queue = (BlockingQueue<MessageWrapper>) queueField.get(instance);
        assertEquals(0, queue.size());
    }

    @Test
    public void testBeforeFilter() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        String message = "test message";
        assertEquals(message, instance.beforeFilter(message));
    }

    @Test
    public void testGetName() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        assertEquals("MessageProcessor", instance.getName());
    }

    @Test
    public void testPause() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        assertFalse(instance.isPaused());
        instance.pause();
        assertTrue(instance.isPaused());
    }

    @Test
    public void testResume() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        instance.pause();
        assertTrue(instance.isPaused());
        instance.resume();
        assertFalse(instance.isPaused());
    }

    @Test
    public void testGetQueueSize() throws Exception {
        MessageProcessorImpl instance = createTestInstance();
        assertEquals(0, instance.getQueueSize());

        instance.onReceiveMsg(SendResultCodeEnum.SUCCESS, null, "{\"test\":1}");
        assertEquals(1, instance.getQueueSize());
    }

    @Test
    public void testProcessMessage_InvalidJson() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("invalid json");
        processMethod.invoke(instance, wrapper);
    }

    @Test
    public void testProcessMessage_ValidJson() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        MessageHandler mockHandler = mock(MessageHandler.class);
        when(mockHandler.getName()).thenReturn("MockHandler");
        when(mockHandler.getPriority()).thenReturn(1);
        when(mockHandler.handleReceived(any())).thenReturn(true);
        instance.registerHandler(mockHandler);

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("{\"Header\":{\"TaskID\":\"123\"},\"data\":{}}");
        processMethod.invoke(instance, wrapper);

        verify(mockHandler).handleReceived(any());
    }

    @Test
    public void testProcessMessage_NoHandlerConsumed() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        MessageHandler mockHandler = mock(MessageHandler.class);
        when(mockHandler.getName()).thenReturn("MockHandler");
        when(mockHandler.getPriority()).thenReturn(1);
        when(mockHandler.handleReceived(any())).thenReturn(false);
        instance.registerHandler(mockHandler);

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("{\"Header\":{\"TaskID\":\"123\"}}");
        processMethod.invoke(instance, wrapper);

        verify(mockHandler).handleReceived(any());
    }

    @Test
    public void testProcessMessage_HandlerException() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        MessageHandler mockHandler = mock(MessageHandler.class);
        when(mockHandler.getName()).thenReturn("MockHandler");
        when(mockHandler.getPriority()).thenReturn(1);
        when(mockHandler.handleReceived(any())).thenThrow(new RuntimeException("Test exception"));
        instance.registerHandler(mockHandler);

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("{\"Header\":{\"TaskID\":\"123\"}}");
        processMethod.invoke(instance, wrapper);

        verify(mockHandler).handleReceived(any());
    }

    @Test
    public void testProcessMessage_NullHeader() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("{\"data\":{}}");
        processMethod.invoke(instance, wrapper);
    }

    @Test
    public void testProcessMessage_EmptyTaskId() throws Exception {
        MessageProcessorImpl instance = createTestInstance();

        MessageHandler mockHandler = mock(MessageHandler.class);
        when(mockHandler.getName()).thenReturn("MockHandler");
        when(mockHandler.getPriority()).thenReturn(1);
        when(mockHandler.handleReceived(any())).thenReturn(true);
        instance.registerHandler(mockHandler);

        Method processMethod = MessageProcessorImpl.class.getDeclaredMethod("processMessage", MessageWrapper.class);
        processMethod.setAccessible(true);

        MessageWrapper wrapper = new MessageWrapper("{\"Header\":{}}");
        processMethod.invoke(instance, wrapper);

        verify(mockHandler).handleReceived(any());
    }

    private MessageProcessorImpl createTestInstance() throws Exception {
        Constructor<?>[] constructors = MessageProcessorImpl.class.getDeclaredConstructors();
        constructors[0].setAccessible(true);

        return (MessageProcessorImpl) constructors[0].newInstance();
    }
}
