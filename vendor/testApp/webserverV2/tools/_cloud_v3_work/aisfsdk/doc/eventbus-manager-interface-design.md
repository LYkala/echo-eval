# EventBusManager 接口设计方案

## 背景

当前 `EventBusManager` 是一个具体的实现类，包含事件订阅和发布的完整逻辑。为了：
1. **解耦**：接口与实现分离，便于测试和替换实现
2. **可测试性**：可以 mock 接口进行单元测试
3. **扩展性**：未来可能需要不同的事件总线实现（如使用第三方库）

## 设计方案

### 目录结构变更

```
component/eventbus/
├── EventBusManager.java          # 接口（公共 API）
├── Event.java                    # 事件基类（已存在）
├── EventSubscriber.java          # 订阅者接口（已存在）
├── EventHandleStrategyEnum.java  # 处理策略枚举（已存在）
└── impl/
    └── EventBusManagerImpl.java  # 实现
```

### 1. EventBusManager 接口

```java
package com.huawei.aisfsdkv2.component.eventbus;

import com.huawei.aisfsdkv2.component.eventbus.impl.EventBusManagerImpl;

/**
 * 事件总线管理器接口
 * 提供事件的订阅和发布功能
 */
public interface EventBusManager {

    /**
     * 获取事件总线管理器实例（单例）
     *
     * @return EventBusManager 实例
     */
    static EventBusManager getInstance() {
        return EventBusManagerImpl.getInstance();
    }

    /**
     * 订阅事件
     *
     * @param subscriber 订阅者
     * @param event      事件（用于获取事件类型）
     */
    void subscribeEvent(EventSubscriber subscriber, Event event);

    /**
     * 发布事件
     *
     * @param event                  事件
     * @param handleStrategyEnum     处理策略（同步/异步单线程/异步并行）
     */
    void publish(Event event, EventHandleStrategyEnum handleStrategyEnum);
}
```

### 2. EventBusManagerImpl 实现类

```java
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
 */
public class EventBusManagerImpl implements EventBusManager {

    private final Map<String, List<EventSubscriber>> subscriberMap = CollectionUtil.newHashMap();

    private static class Holder {
        private static final EventBusManagerImpl INSTANCE = new EventBusManagerImpl();
    }

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
        List<EventSubscriber> subscriberList = subscriberMap.get(eventType);
        if (subscriberList != null) {
            subscriberList.add(subscriber);
            subscriberList.sort(Comparator.comparingInt(EventSubscriber::order));
        }
    }

    @Override
    public void publish(Event event, EventHandleStrategyEnum handleStrategyEnum) {
        String eventType = event.getEventType();
        List<EventSubscriber> subscriberList = subscriberMap.get(eventType);
        if (subscriberList == null) {
            return;
        }

        switch (handleStrategyEnum) {
            case SYNC:
                handleSync(subscriberList, event);
                break;
            case ASYNC_SINGLE_THREAD:
                handleAsyncSingleThread(subscriberList, event);
                break;
            case ASYNC_PARALLEL:
                handleAsyncParallel(subscriberList, event);
                break;
        }
    }

    private void handleSync(List<EventSubscriber> subscribers, Event event) {
        for (EventSubscriber subscriber : subscribers) {
            subscriber.handler(event);
        }
    }

    private void handleAsyncSingleThread(List<EventSubscriber> subscribers, Event event) {
        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            for (EventSubscriber subscriber : subscribers) {
                subscriber.handler(event);
            }
        });
    }

    private void handleAsyncParallel(List<EventSubscriber> subscribers, Event event) {
        for (EventSubscriber subscriber : subscribers) {
            ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
                subscriber.handler(event);
            });
        }
    }
}
```

### 3. 使用方式（不变）

```java
// 订阅事件
EventBusManager.getInstance().subscribeEvent(
    new EventSubscriber() {
        @Override
        public void handler(Event event) {
            // 处理事件
        }
        
        @Override
        public int order() {
            return 0;
        }
    },
    new Event("EVENT_TYPE")
);

// 发布事件（同步）
EventBusManager.getInstance().publish(
    new Event("EVENT_TYPE"),
    EventHandleStrategyEnum.SYNC
);

// 发布事件（异步单线程）
EventBusManager.getInstance().publish(
    new Event("EVENT_TYPE"),
    EventHandleStrategyEnum.ASYNC_SINGLE_THREAD
);

// 发布事件（异步并行）
EventBusManager.getInstance().publish(
    new Event("EVENT_TYPE"),
    EventHandleStrategyEnum.ASYNC_PARALLEL
);
```

## 变更分析

### 优点

1. **接口与实现分离**：便于单元测试和 mock
2. **使用方无感知**：`EventBusManager.getInstance()` 调用方式不变
3. **未来可扩展**：可以更换实现（如使用 RxJava、LiveData 等）

### 影响范围

| 文件 | 变更 |
|------|------|
| `EventBusManager.java` | 变为接口，提取公共方法 |
| 新增 `EventBusManagerImpl.java` | 移入 `impl` 包，实现接口 |
| 使用方 | 无需修改，调用方式不变 |

## 相关类位置

以下类保持在原位置，作为公共 API：
- `Event.java`
- `EventSubscriber.java`
- `EventHandleStrategyEnum.java`

## 实施步骤

1. **创建 `component/eventbus/impl` 目录**
2. **创建 `EventBusManager` 接口**
   - 提取 `subscribeEvent` 和 `publish` 方法
   - 添加静态 `getInstance()` 方法
3. **创建 `EventBusManagerImpl`**
   - 移入 `impl` 包
   - 实现 `EventBusManager` 接口
   - 单例 `getInstance()` 改为 public
4. **删除原 `EventBusManager` 类**（或替换为接口）
5. **编译测试**

## 确认问题

1. **是否添加取消订阅方法 `unsubscribeEvent`？**（当前实现无此方法）
2. **是否需要日志记录？**（当前实现无日志）
3. **确认后开始实现？**
