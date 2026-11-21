/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc;

import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.app.AppManager;
import com.huawei.aisfsdkv2.component.androidfunc.impl.AndroidFuncManagerImpl;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;

/**
 * Android 功能管理器
 * 提供无障碍操作、应用管理等系统级能力
 *
 * <p><b>使用示例:</b></p>
 * <pre>
 * // 获取管理器实例
 * AndroidFuncManager manager = AndroidFuncManager.getInstance();
 *
 * // 获取应用管理器
 * AppManager appMgr = manager.getAppManager();
 * </pre>
 */
public interface AndroidFuncManager {

    /**
     * 获取管理器实例（单例）
     *
     * @return AndroidFuncManager 单例
     */
    static AndroidFuncManager getInstance() {
        return AndroidFuncManagerImpl.getInstance();
    }

    /**
     * 获取应用管理器
     *
     * @return 应用管理器实例
     */
    AppManager getAppManager();

    /**
     * 获取无障碍管理器
     *
     * @return 无障碍管理器实例
     */
    AccessibilityManager getAccessibilityManager();

    /**
     * 获取截图管理器
     *
     * @return 截图管理器实例
     */
    ScreenshotManager getScreenshotManager();
}
