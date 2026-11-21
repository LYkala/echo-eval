/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus;

/**
 * 事件
 *
 * @author l00504561
 * @since 2026-02-07
 */
public interface Event {
    /**
     * 获取事件类型
     *
     * @return 事件类型
     */
    String getEventType();
}
