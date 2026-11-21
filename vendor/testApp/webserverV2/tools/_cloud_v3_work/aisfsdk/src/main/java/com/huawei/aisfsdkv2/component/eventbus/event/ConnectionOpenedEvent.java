/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.eventbus.event;

import com.huawei.aisfsdkv2.component.eventbus.Event;

/**
 * 连接打开事件
 * 
 * <p>当 WebSocket 连接成功建立时触发，用于通知订阅者执行连接成功后的业务逻辑。</p>
 * 
 * <p>典型使用场景：
 * <ul>
 *   <li>位置信息上报</li>
 *   <li>状态同步</li>
 *   <li>心跳机制启动</li>
 * </ul>
 * 
 * @since 2025-03-23
 */
public class ConnectionOpenedEvent implements Event {
    
    /** 事件类型常量 */
    public static final String EVENT_TYPE = "CONNECTION_OPENED";
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
}