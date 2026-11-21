/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.impl;

import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl;
import com.huawei.aisfsdkv2.component.androidfunc.app.AppManager;
import com.huawei.aisfsdkv2.component.androidfunc.app.impl.AppManagerImpl;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManager;
import com.huawei.aisfsdkv2.component.androidfunc.screenshot.ScreenshotManagerImpl;

/**
 * Android 功能管理器实现类
 *
 * <p>提供无障碍操作、应用管理等系统级能力的统一入口。</p>
 *
 * @since 2026-04-09
 */
public class AndroidFuncManagerImpl implements AndroidFuncManager {

    private static final AndroidFuncManagerImpl INSTANCE = new AndroidFuncManagerImpl();

    private AppManager appManager;

    /**
     * 私有构造方法
     *
     * <p>初始化 AppManager 实例。</p>
     */
    private AndroidFuncManagerImpl() {
        this.appManager = new AppManagerImpl();
    }

    /**
     * 获取单例实例
     *
     * @return AndroidFuncManagerImpl 单例
     */
    public static AndroidFuncManagerImpl getInstance() {
        return INSTANCE;
    }

    /**
     * 获取应用管理器
     *
     * @return 应用管理器实例
     */
    @Override
    public AppManager getAppManager() {
        return appManager;
    }

    /**
     * 获取无障碍管理器
     *
     * <p>无障碍管理器通过 AccessibilityService 单例获取，
     * 需要用户开启无障碍服务权限后才能正常使用。</p>
     *
     * @return 无障碍管理器实例，服务未启动时返回 null
     */
    @Override
    public AccessibilityManager getAccessibilityManager() {
        return AccessibilityManagerImpl.getInstance();
    }

    /**
     * 获取截图管理器
     *
     * <p>截图管理器提供统一的截图能力，优先使用无障碍服务截图，
     * 失败时回退到虚拟屏截图。</p>
     *
     * @return 截图管理器实例
     */
    @Override
    public ScreenshotManager getScreenshotManager() {
        return ScreenshotManagerImpl.getInstance();
    }
}
