/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.location;

import com.huawei.aisfsdkv2.component.location.impl.LocationReporterImpl;

/**
 * 位置上报器接口
 * 负责获取设备位置并定时上报到服务器
 * <p>获取实例后需调用init()启动定时上报任务</p>
 */
public interface LocationReporter {

    /**
     * 获取位置上报器实例（单例）
     *
     * @return LocationReporter 实例
     */
    static LocationReporter getInstance() {
        return LocationReporterImpl.getInstance();
    }

    /**
     * 初始化（启动定时上报任务和连接事件订阅）
     */
    void init();

    /**
     * 启动位置上报（定时任务+事件订阅）
     *
     * <p>启动定时上报任务和连接事件订阅。已运行时重复调用不会重复启动。
     * 停止后可通过此方法重新启动。</p>
     */
    void start();

    /**
     * 停止位置上报（取消定时任务+取消事件订阅）
     *
     * <p>彻底停止定时上报任务和连接事件订阅，释放线程池资源。
     * 停止后可通过 {@link #start()} 重新启动。</p>
     */
    void stop();
}
