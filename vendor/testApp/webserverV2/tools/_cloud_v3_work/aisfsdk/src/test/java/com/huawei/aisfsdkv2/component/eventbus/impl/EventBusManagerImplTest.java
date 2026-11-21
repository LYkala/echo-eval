/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.impl;

import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.component.eventbus.Event;
import com.huawei.aisfsdkv2.component.eventbus.EventHandleStrategyEnum;
import com.huawei.aisfsdkv2.component.eventbus.EventSubscriber;
import com.huawei.aisfsdkv2.component.eventbus.event.LoginStatusEvent;
import org.junit.Before;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * EventBusManagerImpl 单元测试
 */
public class EventBusManagerImplTest {

    private EventBusManagerImpl eventBus;

    @Before
    public void setUp() {
        eventBus = EventBusManagerImpl.getInstance();
    }

    @Test
    public void testGetInstance_NotNull() {
        assertNotNull(EventBusManagerImpl.getInstance());
    }

    @Test
    public void testGetInstance_SameInstance() {
        assertSame(EventBusManagerImpl.getInstance(), EventBusManagerImpl.getInstance());
    }

    @Test
    public void testSubscribeEvent_AndPublish_Sync() {
        AtomicInteger counter = new AtomicInteger(0);
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        EventSubscriber subscriber = e -> counter.incrementAndGet();
        eventBus.subscribeEvent(subscriber, event);
        eventBus.publish(event, EventHandleStrategyEnum.SYNC);
        assertEquals(1, counter.get());
    }

    @Test
    public void testPublish_NoSubscriber_DoesNotThrow() {
        Event customEvent = () -> "CUSTOM_EVENT";
        eventBus.publish(customEvent, EventHandleStrategyEnum.SYNC);
    }

    @Test
    public void testSubscribeEvent_MultipleSubscribers_Sync() {
        AtomicInteger counter = new AtomicInteger(0);
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.IDLE);
        EventSubscriber subscriber1 = e -> counter.incrementAndGet();
        EventSubscriber subscriber2 = e -> counter.incrementAndGet();
        EventSubscriber subscriber3 = e -> counter.incrementAndGet();
        eventBus.subscribeEvent(subscriber1, event);
        eventBus.subscribeEvent(subscriber2, event);
        eventBus.subscribeEvent(subscriber3, event);
        eventBus.publish(event, EventHandleStrategyEnum.SYNC);
        assertEquals(3, counter.get());
    }

    @Test
    public void testSubscribeEvent_SubscriberOrder() {
        StringBuilder sb = new StringBuilder();
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        EventSubscriber lowPriority = new EventSubscriber() {
            @Override
            public int order() {
                return 100;
            }
            @Override
            public void handler(Event event) {
                sb.append("low");
            }
        };
        EventSubscriber highPriority = new EventSubscriber() {
            @Override
            public int order() {
                return 1;
            }
            @Override
            public void handler(Event event) {
                sb.append("high");
            }
        };
        eventBus.subscribeEvent(lowPriority, event);
        eventBus.subscribeEvent(highPriority, event);
        eventBus.publish(event, EventHandleStrategyEnum.SYNC);
        assertEquals("highlow", sb.toString());
    }

    @Test
    public void testPublish_AsyncParallel() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        EventSubscriber subscriber1 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        EventSubscriber subscriber2 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        EventSubscriber subscriber3 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        eventBus.subscribeEvent(subscriber1, event);
        eventBus.subscribeEvent(subscriber2, event);
        eventBus.subscribeEvent(subscriber3, event);
        eventBus.publish(event, EventHandleStrategyEnum.ASYNC_PARALLEL);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, counter.get());
    }

    @Test
    public void testPublish_AsyncSingleThread() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.CONNECTED);
        EventSubscriber subscriber1 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        EventSubscriber subscriber2 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        EventSubscriber subscriber3 = e -> {
            counter.incrementAndGet();
            latch.countDown();
        };
        eventBus.subscribeEvent(subscriber1, event);
        eventBus.subscribeEvent(subscriber2, event);
        eventBus.subscribeEvent(subscriber3, event);
        eventBus.publish(event, EventHandleStrategyEnum.ASYNC_SINGLE_THREAD);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, counter.get());
    }

    @Test
    public void testPublish_EventDataCorrect() {
        AtomicReference<LoginStatus> capturedStatus = new AtomicReference<>();
        LoginStatusEvent event = new LoginStatusEvent(LoginStatus.AUTHING);
        EventSubscriber subscriber = e -> {
            if (e instanceof LoginStatusEvent) {
                capturedStatus.set(((LoginStatusEvent) e).getStatus());
            }
        };
        eventBus.subscribeEvent(subscriber, event);
        eventBus.publish(event, EventHandleStrategyEnum.SYNC);
        assertEquals(LoginStatus.AUTHING, capturedStatus.get());
    }
}
