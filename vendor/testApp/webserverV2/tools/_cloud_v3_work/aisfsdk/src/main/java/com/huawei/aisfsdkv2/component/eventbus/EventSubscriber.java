/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus;

/**
 * 事件订阅
 */
public interface EventSubscriber {
    /**
     * 处理优先级
     *
     * @return 优先级，值越小越优先处理，该方法默认实现，用户可不关注
     */
    default int order() {
        return 10000;
    }

    /**
     * 处理事件
     *
     * @param event 事件
     */
    void handler(Event event);
}
