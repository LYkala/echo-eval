/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus;

/**
 * 异步执行策略枚举
 *
 * @author l00504561
 * @since 2026-02-07
 */
public enum EventHandleStrategyEnum {
    /**
     * 串行处理
     */
    SYNC,

    /**
     * 单独线程异步串行执行
     */
    ASYNC_SINGLE_THREAD,

    /**
     * 异步并行执行
     */
    ASYNC_PARALLEL
}
