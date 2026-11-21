/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app;

import com.huawei.aisfsdkv2.component.androidfunc.app.model.AppInfo;
import com.huawei.aisfsdkv2.component.androidfunc.app.model.LaunchAppOptions;

import java.util.List;

/**
 * 应用管理器
 *
 * <p>提供应用生命周期管理能力</p>
 */
public interface AppManager {

    /**
     * 检查应用是否已安装
     *
     * @param packageName 应用包名
     * @return true 已安装，false 未安装或检查失败
     */
    boolean isAppInstalled(String packageName);

    /**
     * 获取已安装应用列表
     *
     * @param includeSystemApps 是否包含系统应用
     * @return 应用信息列表
     */
    List<AppInfo> getInstalledApps(boolean includeSystemApps);

    /**
     * 启动应用
     * <p>使用默认启动标志</p>
     *
     * @param packageName 应用包名
     * @return true 启动成功
     */
    boolean launchApp(String packageName);

    /**
     * 启动应用（带启动标志）
     *
     * <p>支持两种启动方式：</p>
     * <ul>
     *   <li><b>标准启动</b>：仅包名，如 "com.example.app"</li>
     *   <li><b>显式启动</b>：包名/Activity，如 "com.example.app/.MainActivity"</li>
     * </ul>
     *
     * <p>启动标志映射关系：</p>
     * <table border="1">
     *   <tr><th>传入值</th><th>实际使用的 Intent Flag</th></tr>
     *   <tr><td>0</td><td>FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK</td></tr>
     *   <tr><td>1</td><td>FLAG_ACTIVITY_NEW_TASK</td></tr>
     *   <tr><td>2</td><td>FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK</td></tr>
     *   <tr><td>3</td><td>FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP</td></tr>
     *   <tr><td>其他</td><td>透传原值</td></tr>
     * </table>
     *
     * @param packageName 应用包名或 packageName/ActivityName 格式
     * @param launchFlags 启动标志（参见上述映射关系）
     * @return true 启动成功
     */
    boolean launchApp(String packageName, int launchFlags);

    /**
     * 启动应用（使用选项对象）
     *
     * <p>通过 {@link LaunchAppOptions} 封装所有参数，代码更清晰、易于扩展。</p>
     *
     * <h3>使用示例</h3>
     * <pre>
     * LaunchAppOptions options = new LaunchAppOptions("com.example.app")
     *     .setLaunchFlags(1)
     *     .setMaxLaunchRetry(3);
     * boolean success = appManager.launchApp(options);
     * </pre>
     *
     * @param options 启动应用选项，不能为空
     * @return true 启动并就绪成功，false 失败
     * @throws IllegalArgumentException options 为 null 时抛出
     * @see LaunchAppOptions
     */
    boolean launchApp(LaunchAppOptions options);

    /**
     * 通过 URL 打开外部应用或网页
     *
     * <p>使用系统 Intent 打开指定的 URL，支持：</p>
     * <ul>
     *   <li>Deep Link：如 "app://example.com/path"，会跳转到对应应用</li>
     *   <li>网页链接：如 "https://www.example.com"，会打开浏览器</li>
     *   <li>系统链接：如 "tel:123456"，会打开拨号界面</li>
     * </ul>
     *
     * @param url 要打开的 URL 字符串
     * @return true 打开成功，false 打开失败（URL 为空或没有应用可以处理）
     */
    boolean openUrl(String url);

    /**
     * 获取当前应用包名
     *
     * <p>获取当前宿主应用的包名，首次获取后缓存，支持高频调用。</p>
     *
     * @return 当前应用包名，获取失败返回空字符串
     */
    String getCurrentAppPkgName();
}
