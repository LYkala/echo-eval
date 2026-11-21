/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.impl;

import com.huawei.aisfsdkv2.component.eventbus.Event;
import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.EventHandleStrategyEnum;
import com.huawei.aisfsdkv2.component.eventbus.EventSubscriber;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.util.CollectionUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线管理器实现
 *
 * @author l00504561
 * @since 2026-02-06
 */
public class EventBusManagerImpl implements EventBusManager {

    /**
     * 订阅信息管理数据结构
     */
    private final Map<String, List<EventSubscriber>> subscriberMap = CollectionUtil.newHashMap();

    private static class Holder {
        private static final EventBusManagerImpl INSTANCE = new EventBusManagerImpl();
    }

    /**
     * 获取单例实例（供接口调用）
     *
     * @return EventBusManagerImpl 实例
     */
    public static EventBusManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    private EventBusManagerImpl() {
    }

    @Override
    public void subscribeEvent(EventSubscriber subscriber, Event event) {
        String eventType = event.getEventType();
        if (!subscriberMap.containsKey(eventType)) {
            subscriberMap.put(eventType, new CopyOnWriteArrayList<>());
        }
        List<EventSubscriber> eventSubscriberList = subscriberMap.get(eventType);
        if (eventSubscriberList != null) {
            eventSubscriberList.add(subscriber);
            eventSubscriberList.sort(Comparator.comparingInt(EventSubscriber::order));
        }
    }

    @Override
    public void unsubscribeEvent(EventSubscriber subscriber) {
        for (List<EventSubscriber> eventSubscriberList : subscriberMap.values()) {
            eventSubscriberList.remove(subscriber);
        }
    }

    @Override
    public void publish(Event event, EventHandleStrategyEnum handleStrategyEnum) {
        String eventType = event.getEventType();
        List<EventSubscriber> eventSubscriberList = subscriberMap.get(eventType);
        if (eventSubscriberList == null) {
            return;
        }

        switch (handleStrategyEnum) {
            case SYNC:
                handleSync(eventSubscriberList, event);
                break;
            case ASYNC_SINGLE_THREAD:
                handleAsyncSingleThread(eventSubscriberList, event);
                break;
            case ASYNC_PARALLEL:
                handleAsyncParallel(eventSubscriberList, event);
                break;
            default:
                // 默认同步处理
                handleSync(eventSubscriberList, event);
                break;
        }
    }

    /**
     * 同步串行处理
     */
    private void handleSync(List<EventSubscriber> subscribers, Event event) {
        for (EventSubscriber subscriber : subscribers) {
            subscriber.handler(event);
        }
    }

    /**
     * 异步单线程处理
     */
    private void handleAsyncSingleThread(List<EventSubscriber> subscribers, Event event) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            for (EventSubscriber subscriber : subscribers) {
                subscriber.handler(event);
            }
        });
    }

    /**
     * 异步并行处理
     */
    private void handleAsyncParallel(List<EventSubscriber> subscribers, Event event) {
        for (EventSubscriber subscriber : subscribers) {
            ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
                subscriber.handler(event);
            });
        }
    }
}
