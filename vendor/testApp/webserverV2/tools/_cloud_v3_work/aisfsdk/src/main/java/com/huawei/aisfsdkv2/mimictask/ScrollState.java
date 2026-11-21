/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

/**
 * 滚动状态
 *
 * <p>记录滚动事件的中间状态，用于判断滚动方向。</p>
 *
 * @since 2026-04-10
 */
public class ScrollState {

    private int lastScrollX = 0;

    private int lastScrollY = 0;

    private int maxScrollX = 0;

    private int maxScrollY = 0;

    private long lastEventTime = 0;

    /**
     * 获取上次滚动X坐标
     *
     * @return 上次滚动X坐标
     */
    public int getLastScrollX() {
        return lastScrollX;
    }

    /**
     * 设置上次滚动X坐标
     *
     * @param lastScrollX 上次滚动X坐标
     */
    public void setLastScrollX(int lastScrollX) {
        this.lastScrollX = lastScrollX;
    }

    /**
     * 获取上次滚动Y坐标
     *
     * @return 上次滚动Y坐标
     */
    public int getLastScrollY() {
        return lastScrollY;
    }

    /**
     * 设置上次滚动Y坐标
     *
     * @param lastScrollY 上次滚动Y坐标
     */
    public void setLastScrollY(int lastScrollY) {
        this.lastScrollY = lastScrollY;
    }

    /**
     * 获取最大滚动X坐标
     *
     * @return 最大滚动X坐标
     */
    public int getMaxScrollX() {
        return maxScrollX;
    }

    /**
     * 设置最大滚动X坐标
     *
     * @param maxScrollX 最大滚动X坐标
     */
    public void setMaxScrollX(int maxScrollX) {
        this.maxScrollX = maxScrollX;
    }

    /**
     * 获取最大滚动Y坐标
     *
     * @return 最大滚动Y坐标
     */
    public int getMaxScrollY() {
        return maxScrollY;
    }

    /**
     * 设置最大滚动Y坐标
     *
     * @param maxScrollY 最大滚动Y坐标
     */
    public void setMaxScrollY(int maxScrollY) {
        this.maxScrollY = maxScrollY;
    }

    /**
     * 获取上次事件时间
     *
     * @return 上次事件时间
     */
    public long getLastEventTime() {
        return lastEventTime;
    }

    /**
     * 设置上次事件时间
     *
     * @param lastEventTime 上次事件时间
     */
    public void setLastEventTime(long lastEventTime) {
        this.lastEventTime = lastEventTime;
    }
}
