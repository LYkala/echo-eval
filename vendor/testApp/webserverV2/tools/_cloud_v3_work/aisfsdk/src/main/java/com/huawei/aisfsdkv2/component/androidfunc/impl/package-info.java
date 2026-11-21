/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * Android 功能组件实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager} 的实现类，
 * 统一管理各 Android 功能模块的实例创建和生命周期。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.androidfunc.impl.AndroidFuncManagerImpl}：Android 功能管理器实现</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>使用单例模式管理各功能模块</li>
 *   <li>延迟初始化，按需创建模块实例</li>
 *   <li>统一管理模块间的依赖关系</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@code AndroidFuncManager} 接口访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.androidfunc.impl;
