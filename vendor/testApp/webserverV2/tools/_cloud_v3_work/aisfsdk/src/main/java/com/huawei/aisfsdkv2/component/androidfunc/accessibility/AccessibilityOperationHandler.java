/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility;

import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍操作事件处理器接口
 *
 * <p>实现此接口以接收无障碍事件回调。</p>
 *
 * @since 2026-04-09
 */
public interface AccessibilityOperationHandler {

    /**
     * 处理无障碍事件
     *
     * @param event 无障碍事件
     */
    void onAccessibilityEvent(AccessibilityEvent event);
}
