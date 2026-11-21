/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 无障碍事件信息快照
 *
 * <p>用于保存AccessibilityEvent的关键信息，避免事件对象被回收。</p>
 *
 * @since 2026-04-10
 */
public class AccessibilityEventInfo {

    /** 事件类型 */
    private final int eventType;

    /** 事件时间 */
    private final long eventTime;

    /** 包名 */
    private final CharSequence packageName;

    /** 文本 */
    private final CharSequence text;

    /** 节点信息（复制的副本） */
    private final AccessibilityNodeInfo source;

    /** 滚动X坐标 */
    private final int scrollX;

    /** 滚动Y坐标 */
    private final int scrollY;

    /** 最大滚动X坐标 */
    private final int maxScrollX;

    /** 最大滚动Y坐标 */
    private final int maxScrollY;

    /**
     * 从AccessibilityEvent创建事件信息快照
     *
     * @param event 无障碍事件
     * @return 事件信息对象
     */
    public static AccessibilityEventInfo fromEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        long eventTime = event.getEventTime();
        CharSequence packageName = event.getPackageName();
        CharSequence text = event.getText() != null && !event.getText().isEmpty() ? event.getText().get(0) : null;
        AccessibilityNodeInfo source = event.getSource();
        AccessibilityNodeInfo sourceCopy = source != null ? AccessibilityNodeInfo.obtain(source) : null;
        int scrollX = event.getScrollX();
        int scrollY = event.getScrollY();
        int maxScrollX = event.getMaxScrollX();
        int maxScrollY = event.getMaxScrollY();
        return new AccessibilityEventInfo(eventType, eventTime, packageName, text, sourceCopy, scrollX, scrollY, maxScrollX, maxScrollY);
    }

    /**
     * 构造函数
     *
     * @param eventType   事件类型
     * @param eventTime   事件时间
     * @param packageName 包名
     * @param text        文本
     * @param source      节点信息
     * @param scrollX     滚动X坐标
     * @param scrollY     滚动Y坐标
     * @param maxScrollX  最大滚动X坐标
     * @param maxScrollY  最大滚动Y坐标
     */
    AccessibilityEventInfo(int eventType, long eventTime, CharSequence packageName, CharSequence text,
                           AccessibilityNodeInfo source, int scrollX, int scrollY, int maxScrollX, int maxScrollY) {
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.packageName = packageName;
        this.text = text;
        this.source = source;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.maxScrollX = maxScrollX;
        this.maxScrollY = maxScrollY;
    }

    /**
     * 获取事件类型
     *
     * @return 事件类型
     */
    public int getEventType() {
        return eventType;
    }

    /**
     * 获取事件时间
     *
     * @return 事件时间
     */
    public long getEventTime() {
        return eventTime;
    }

    /**
     * 获取包名
     *
     * @return 包名
     */
    public CharSequence getPackageName() {
        return packageName;
    }

    /**
     * 获取文本
     *
     * @return 文本
     */
    public CharSequence getText() {
        return text;
    }

    /**
     * 获取节点信息
     *
     * @return 节点信息
     */
    public AccessibilityNodeInfo getSource() {
        return source;
    }

    /**
     * 获取滚动X坐标
     *
     * @return 滚动X坐标
     */
    public int getScrollX() {
        return scrollX;
    }

    /**
     * 获取滚动Y坐标
     *
     * @return 滚动Y坐标
     */
    public int getScrollY() {
        return scrollY;
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
     * 获取最大滚动Y坐标
     *
     * @return 最大滚动Y坐标
     */
    public int getMaxScrollY() {
        return maxScrollY;
    }
}
