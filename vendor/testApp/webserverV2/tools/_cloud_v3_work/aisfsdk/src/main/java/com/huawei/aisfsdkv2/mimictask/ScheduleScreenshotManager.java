/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

/**
 * 截图管理器接口
 *
 * <p>负责管理定时截图任务的启动和停止。</p>
 *
 * @since 2026-04-09
 */
public interface ScheduleScreenshotManager {

    /**
     * 获取单例实例
     *
     * @return ScheduleScreenshotManager 实例
     */
    static ScheduleScreenshotManager getInstance() {
        return ScheduleScreenshotManagerImpl.getInstance();
    }

    /**
     * 启动定时截图任务
     */
    void start();

    /**
     * 停止定时截图任务
     */
    void stop();

    /**
     * 查找操作前的最优图片
     *
     * <p>从截图列表中查找 captureTime 小于等于 eventTime 的最新一张图片</p>
     *
     * @param eventTime 事件时间
     * @return 操作前的截图记录，未找到返回 null
     */
    ScheduleScreenshotRecord findPreActionImage(long eventTime);
}
