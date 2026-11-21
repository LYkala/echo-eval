/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.location;

/**
 * 位置接口
 *
 * @since 2026-04-16
 */
public interface LocationApi {

    /**
     * 删除用户位置信息
     *
     * @return 操作结果，包含是否成功及描述信息
     */
    LocationOprResult deleteUserLocationInfo();

    /**
     * 查询地理位置上报是否启用
     *
     * @return true 启用（默认），false 禁用
     */
    boolean getReportLocationEnable();

    /**
     * 更新地理位置上报配置
     *
     * @param enable true 启用，false 禁用
     */
    void updateReportLocationEnable(boolean enable);

    /**
     * 停止地理位置上报定时任务
     *
     * <p>彻底停止定时上报任务和连接事件订阅，释放线程池资源。
     * 与 {@link #updateReportLocationEnable(boolean)} 的区别：
     * updateReportLocationEnable(false) 仅跳过上报，定时任务仍在运行；
     * stopLocationReport() 完全取消定时任务，不占用线程池资源。</p>
     *
     * <p>停止后可通过 {@link #startLocationReport()} 重新启动。</p>
     *
     * @since 2026-07-02
     */
    void stopLocationReport();

    /**
     * 启动地理位置上报定时任务
     *
     * <p>重新启动定时上报任务和连接事件订阅。
     * 已运行时重复调用不会重复启动。</p>
     *
     * @since 2026-07-02
     */
    void startLocationReport();
}
