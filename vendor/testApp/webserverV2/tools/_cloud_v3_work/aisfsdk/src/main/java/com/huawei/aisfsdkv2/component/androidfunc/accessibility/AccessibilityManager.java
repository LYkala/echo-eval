/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility;

import android.graphics.Bitmap;
import java.util.List;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.ClickResult;

/**
 * 无障碍管理器
 *
 * <p>基于 Android AccessibilityService 实现自动化操作</p>
 *
 * <p><b>使用前提:</b> 用户必须开启无障碍服务权限</p>
 */
public interface AccessibilityManager {

    /**
     * 检查无障碍服务是否已启用
     *
     * @return true 已启用
     */
    boolean isAccessibilityEnabled();

    /**
     * 获取当前屏幕最上层应用的包名
     *
     * @return 最上层应用包名，获取失败返回 null
     */
    String getForegroundPackageName();

    /**
     * 执行点击操作
     *
     * @param x X坐标（像素）
     * @param y Y坐标（像素）
     * @param controlBlackList 控件黑名单列表，可为null
     * @return 点击操作结果，包含执行状态和拦截信息
     */
    ClickResult click(int x, int y, List<String> controlBlackList);

    /**
     * 执行长按操作
     *
     * @param x        X坐标
     * @param y        Y坐标
     * @param duration 长按时长（毫秒），默认 1000ms
     * @return true 长按成功
     */
    boolean longClick(int x, int y, int duration);

    /**
     * 执行滑动操作
     *
     * @param startX   起始 X 坐标
     * @param startY   起始 Y 坐标
     * @param endX     结束 X 坐标
     * @param endY     结束 Y 坐标
     * @param duration 滑动时长（毫秒），默认 300ms
     * @return true 滑动成功
     */
    boolean swipe(int startX, int startY, int endX, int endY, int duration);

    /**
     * 输入文本
     * <p>点击指定位置后查找 EditText 节点并输入文本</p>
     *
     * @param x    点击位置的 X 坐标
     * @param y    点击位置的 Y 坐标
     * @param text 要输入的文本
     * @return true 输入成功
     */
    boolean inputText(int x, int y, String text);

    /**
     * 回到桌面
     *
     * @return true 操作成功
     */
    boolean goHome();

    /**
     * 返回键
     *
     * @return true 操作成功
     */
    boolean goBack();

    /**
     * 执行确认/回车操作
     *
     * @return true 操作成功
     */
    boolean goEnter();

    /**
     * 自动执行回车操作
     * <p>查找当前有焦点的输入框并执行回车/确认操作</p>
     *
     * @return true 操作成功
     */
    boolean autoPerformEnter();

    /**
     * 判断当前屏幕是否为目标应用
     * <p>比较当前屏幕的包名与目标包名</p>
     *
     * @param targetPackageName 目标应用包名
     * @return true 当前屏幕是目标应用
     */
    boolean isTargetAppOnScreen(String targetPackageName);

    /**
     * 单次截图
     * <p>执行一次截图操作，不带重试和过滤逻辑</p>
     *
     * @return Bitmap 截图位图，失败返回 null
     */
    Bitmap takeScreenshot();

    /**
     * 获取屏幕内容变更计数器
     *
     * <p>每当屏幕内容发生以下类型变化时，计数器自动+1：
     * <ul>
     *   <li>窗口状态变化 (TYPE_WINDOW_STATE_CHANGED)</li>
     *   <li>文本选择变化 (TYPE_VIEW_TEXT_SELECTION_CHANGED)</li>
     *   <li>Windows变化 (TYPE_WINDOWS_CHANGED)</li>
     * </ul>
     *
     * @return 当前变更次数
     */
    long getScreenGeneratorNum();

    /**
     * 注册无障碍事件处理器
     *
     * <p>注册后，处理器将接收无障碍事件通知</p>
     *
     * @param handler 无障碍事件处理器
     */
    void registerAccessibilityHandler(AccessibilityOperationHandler handler);

    /**
     * 注销无障碍事件处理器
     *
     * @param handler 无障碍事件处理器
     */
    void unregisterAccessibilityHandler(AccessibilityOperationHandler handler);
}
