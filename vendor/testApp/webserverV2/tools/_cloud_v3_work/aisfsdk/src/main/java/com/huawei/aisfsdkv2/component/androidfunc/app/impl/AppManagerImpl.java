/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app.impl;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.BaseApi;
import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.app.AppManager;
import com.huawei.aisfsdkv2.component.androidfunc.app.model.AppInfo;
import com.huawei.aisfsdkv2.component.androidfunc.app.model.LaunchAppOptions;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用管理器实现类
 *
 * <p>提供应用生命周期管理能力，包括：</p>
 * <ol>
 *   <li>检查应用安装状态</li>
 *   <li>获取已安装应用列表</li>
 *   <li>启动应用</li>
 * </ol>
 */
public class AppManagerImpl implements AppManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AppManagerImpl");

    private AccessibilityManager accessibilityManager;
    private volatile String currentAppPkgNameCache;

    /**
     * 应用启动标志白名单
     * Key: 包名, Value: 启动标志映射值 (0/1/2/3)
     */
    private static final Map<String, Integer> DEFAULT_LAUNCH_APP_STRATEGY_MAP = new HashMap<>();

    static {
        DEFAULT_LAUNCH_APP_STRATEGY_MAP.put("com.chinaunicom.bol.cloudapp", 3);
    }

    /**
     * 获取 AccessibilityManager 实例（延迟初始化）
     *
     * <p>避免在构造函数中初始化导致的循环依赖问题</p>
     *
     * @return AccessibilityManager 实例
     */
    private AccessibilityManager getAccessibilityManager() {
        if (accessibilityManager == null) {
            accessibilityManager = AndroidFuncManager.getInstance().getAccessibilityManager();
        }
        return accessibilityManager;
    }

    /**
     * 获取应用上下文
     *
     * @return 应用上下文
     * @throws IllegalStateException 如果 AISFSDKClient 未初始化
     */
    private Context getContext() {
        BaseApi baseApi = AISFSDKClient.getBaseApi();
        if (baseApi == null) {
            throw new IllegalStateException("AISFSDKClient not initialized");
        }
        return baseApi.getAppContext();
    }

    /**
     * 获取包管理器
     *
     * @return 包管理器
     */
    private PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    /**
     * 获取当前应用包名
     *
     * <p>获取当前宿主应用的包名，首次获取后缓存，支持高频调用。</p>
     *
     * @return 当前应用包名，获取失败返回空字符串
     */
    @Override
    public String getCurrentAppPkgName() {
        if (currentAppPkgNameCache == null) {
            try {
                currentAppPkgNameCache = getContext().getPackageName();
            } catch (Exception e) {
                LOGGER.error("getCurrentAppPkgName: failed, error={}", e.getMessage());
                return "";
            }
        }
        return currentAppPkgNameCache;
    }

    /**
     * 检查应用是否已安装
     *
     * <p>通过PackageManager查询应用安装状态。</p>
     *
     * @param packageName 应用包名
     * @return true 已安装，false 未安装或检查失败
     */
    @Override
    public boolean isAppInstalled(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            LOGGER.warn("isAppInstalled: packageName is null or empty");
            return false;
        }
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            LOGGER.info("isAppInstalled: package [{}] is installed", packageName);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            LOGGER.info("isAppInstalled: package [{}] is not installed", packageName);
            return false;
        } catch (Exception e) {
            LOGGER.error("isAppInstalled: failed to check package [{}], error: {}", packageName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取已安装应用列表
     *
     * <p>遍历所有已安装应用，构建应用信息列表。可选择是否包含系统应用。</p>
     *
     * @param includeSystemApps true 包含系统应用，false 仅包含用户应用
     * @return 应用信息列表，查询失败返回空列表
     */
    @Override
    public List<AppInfo> getInstalledApps(boolean includeSystemApps) {
        List<AppInfo> appList = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo packageInfo : packages) {
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                if (!includeSystemApps && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                String appName = pm.getApplicationLabel(appInfo).toString();
                appList.add(new AppInfo(
                    packageInfo.packageName,
                    appName,
                    packageInfo.versionName,
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                ));
            }
        } catch (Exception e) {
            LOGGER.error("getInstalledApps: failed to get installed apps, error: {}", e.getMessage());
        }
        return appList;
    }

    /**
     * 启动应用
     *
     * <p>使用默认启动标志启动应用（CLEAR_TASK | NEW_TASK）。</p>
     *
     * @param packageName 应用包名
     * @return true 启动成功，false 启动失败或应用未安装
     */
    @Override
    public boolean launchApp(String packageName) {
        return launchApp(packageName, Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * 启动应用（带自定义启动标志）
     *
     * <p>支持两种启动方式：
     * <ul>
     *   <li><b>标准启动</b>：仅包名，使用 {@link #launchAppStandard(String, int)}</li>
     *   <li><b>显式启动</b>：包名/Activity，使用 {@link #launchAppExplicit(String, int)}</li>
     * </ul>
     *
     * <p>白名单机制：特定应用使用指定的启动标志
     *
     * <p>启动标志映射关系：
     * <ul>
     *   <li>0 -> FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK</li>
     *   <li>1 -> FLAG_ACTIVITY_NEW_TASK</li>
     *   <li>2 -> FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_MULTIPLE_TASK</li>
     *   <li>3 -> FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP</li>
     *   <li>其他 -> 透传原值</li>
     * </ul>
     *
     * @param packageName 应用包名或 packageName/ActivityName 格式
     * @param launchFlags Intent启动标志
     * @return true 启动成功，false 启动失败或应用未安装
     */
    @Override
    public boolean launchApp(String packageName, int launchFlags) {
        LaunchAppOptions options = new LaunchAppOptions(packageName)
                .setLaunchFlags(launchFlags);
        return launchApp(options);
    }

    /**
     * 启动应用（使用选项对象）
     *
     * <p>通过 {@link LaunchAppOptions} 封装所有参数，启动应用并等待应用显示在屏幕上。</p>
     *
     * <h3>流程</h3>
     * <ol>
     *   <li>检查用户授权（首次拉起需弹窗确认）</li>
     *   <li>尝试拉起应用（最多 options.maxLaunchRetry 次）</li>
     *   <li>拉起成功后，检查应用是否已在屏幕上</li>
     *   <li>如未就绪，等待 options.readyCheckIntervalMs 后重试检查（最多 options.maxReadyCheckRetry 次）</li>
     *   <li>就绪检查成功返回 true，否则返回 false</li>
     * </ol>
     *
     * @param options 启动应用选项，不能为空
     * @return true 启动并就绪成功，false 失败
     * @throws IllegalArgumentException options 为 null 时抛出
     * @see LaunchAppOptions
     */
    @Override
    public boolean launchApp(LaunchAppOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("LaunchAppOptions cannot be null");
        }
        String packageName = options.getAppPkgName();
        int launchFlags = options.getLaunchFlags();
        int maxLaunchRetry = options.getMaxLaunchRetry();
        int maxReadyCheckRetry = options.getMaxReadyCheckRetry();
        long readyCheckIntervalMs = options.getReadyCheckIntervalMs() > 0
                ? options.getReadyCheckIntervalMs() : 1000L;
        LOGGER.info("launchApp: entering, options={}", options);
        if (!LaunchAppConfirmManager.getInstance().isAuthorized(packageName)) {
            String appName = options.getAppName();
            if (!LaunchAppConfirmManager.getInstance().confirmAndAuthorize(packageName, appName)) {
                LOGGER.warn("launchApp: user denied launch for {}", packageName);
                return false;
            }
        }
        Integer whitelistedFlags = DEFAULT_LAUNCH_APP_STRATEGY_MAP.get(packageName);
        if (whitelistedFlags != null) {
            launchFlags = whitelistedFlags;
        }
        boolean launched = false;
        for (int i = 0; i < maxLaunchRetry; i++) {
            LOGGER.info("launchApp: launch attempt {} for {}", i + 1, packageName);
            boolean result = !packageName.contains("/")
                    ? launchAppStandard(packageName, launchFlags)
                    : launchAppExplicit(packageName, launchFlags);
            if (result) {
                LOGGER.info("launchApp: launch attempt {} success", i + 1);
                launched = true;
                break;
            }
            LOGGER.warn("launchApp: launch attempt {} failed", i + 1);
            if (i < maxLaunchRetry - 1) {
                ThreadUtil.sleepNoExcept(readyCheckIntervalMs);
            }
        }
        if (!launched) {
            LOGGER.error("launchApp: launch {} failed after {} attempts", packageName, maxLaunchRetry);
            return false;
        }
        return waitForLaunchAppReady(packageName, maxReadyCheckRetry, readyCheckIntervalMs);
    }

    /**
     * 等待应用就绪（对齐 DeviceSDK AAR 行为并加强）
     *
     * <ol>
     *   <li>无障碍未启用：固定等待后假定就绪（DeviceSDK 原逻辑）</li>
     *   <li>无障碍已启用：轮询 isTargetAppOnScreen</li>
     *   <li>轮询仍失败：再固定等待一次后假定就绪（小米上树常晚于 Displayed，
     *       硬失败会导致 Launch 101，而后续 CLICK 时树往往已切到目标包）</li>
     * </ol>
     *
     * @param packageName 应用包名
     * @param maxRetryCount 最大重试次数
     * @param intervalMs 检查间隔（毫秒）
     * @return true 应用已就绪或按 DeviceSDK 策略假定就绪
     */
    private boolean waitForLaunchAppReady(String packageName, int maxRetryCount, long intervalMs) {
        long waitMs = intervalMs > 0 ? intervalMs : 1000L;
        AccessibilityManager a11y = getAccessibilityManager();
        boolean a11yEnabled = a11y != null && a11y.isAccessibilityEnabled();
        if (!a11yEnabled) {
            // DeviceSDK.rar AAR：accessibility disabled, fixed wait then assume ready
            LOGGER.info("waitForLaunchAppReady: accessibility disabled, fixed wait then assume ready for {}",
                    packageName);
            ThreadUtil.sleepNoExcept(waitMs);
            return true;
        }

        LOGGER.info("waitForLaunchAppReady: waiting for app {} to be ready, max retry: {}",
                packageName, maxRetryCount);
        for (int i = 0; i < maxRetryCount; i++) {
            boolean isOnScreen = a11y.isTargetAppOnScreen(packageName);
            if (isOnScreen) {
                LOGGER.info("waitForLaunchAppReady: app {} is ready after {} retries", packageName, i + 1);
                return true;
            }
            LOGGER.debug("waitForLaunchAppReady: app {} not ready, retry {}/{}", packageName, i + 1, maxRetryCount);
            if (i < maxRetryCount - 1) {
                ThreadUtil.sleepNoExcept(waitMs);
            }
        }
        // DeviceSDK 精神：不因 a11y 误判硬失败；固定再等一轮后假定就绪，让后续点击有机会执行
        LOGGER.warn("waitForLaunchAppReady: a11y on-screen check timed out for {}, "
                + "fixed wait then assume ready (DeviceSDK-style)", packageName);
        ThreadUtil.sleepNoExcept(waitMs);
        return true;
    }

    /**
     * 标准启动应用（通过包名）
     *
     * @param packageName 应用包名
     * @param launchFlags 启动标志
     * @return true 启动成功
     */
    private boolean launchAppStandard(String packageName, int launchFlags) {
        LOGGER.info("launchAppStandard: entering, packageName={}, launchFlags={}", packageName, launchFlags);
        if (!isAppInstalled(packageName)) {
            LOGGER.warn("launchAppStandard: package [{}] is not installed", packageName);
            return false;
        }
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                LOGGER.warn("launchAppStandard: no launch intent for package [{}]", packageName);
                return false;
            }
            intent.addFlags(convertLaunchFlag(launchFlags));
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            intent.putExtra("isExist", true);
            getContext().startActivity(intent);
            LOGGER.info("launchAppStandard: started package [{}]", packageName);
            return true;
        } catch (Exception e) {
            LOGGER.error("launchAppStandard: failed to start package [{}], error: {}", packageName, e.getMessage());
            return launchAppViaShell(packageName);
        }
    }

    /**
     * 通过 am start 强制把目标 App 拉到前台（绕过小米对 startActivity 后台拉不起前台的限制）
     */
    private boolean launchAppViaShell(String packageName) {
        try {
            // 0x10008000 = NEW_TASK|CLEAR_TASK（小米 am 不支持 --activity-* 长选项）
            String cmd = "am start -f 0x10008000 -a android.intent.action.MAIN "
                    + "-c android.intent.category.LAUNCHER -p " + packageName;
            LOGGER.info("launchAppViaShell: exec {}", cmd);
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int code = process.waitFor();
            LOGGER.info("launchAppViaShell: exitCode={}", code);
            return code == 0;
        } catch (Exception e) {
            LOGGER.error("launchAppViaShell: failed for {}, error: {}", packageName, e.getMessage());
            return false;
        }
    }

    /**
     * 显式启动应用（通过包名/Activity）
     *
     * @param packageActivity 格式：packageName/ActivityName，如 "com.example.app/.MainActivity"
     * @param launchFlags     启动标志
     * @return true 启动成功
     */
    private boolean launchAppExplicit(String packageActivity, int launchFlags) {
        String[] parts = packageActivity.split("/");
        if (parts.length != 2) {
            LOGGER.warn("launchAppExplicit: invalid format [{}], expected packageName/ActivityName", packageActivity);
            return false;
        }

        String packageName = parts[0];
        String activityName = parts[1];

        if (!isAppInstalled(packageName)) {
            LOGGER.warn("launchAppExplicit: package [{}] is not installed", packageName);
            return false;
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(packageName, activityName));
            intent.addFlags(convertLaunchFlag(launchFlags));
            getContext().startActivity(intent);
            LOGGER.info("launchAppExplicit: started activity [{}/{}]", packageName, activityName);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            LOGGER.error("launchAppExplicit: activity not found [{}/{}]", packageName, activityName);
            return false;
        } catch (Exception e) {
            LOGGER.error("launchAppExplicit: failed to start activity [{}/{}], error: {}", packageName, activityName, e.getMessage());
            return false;
        }
    }

    /**
     * 转换启动标志为 Intent Flag
     *
     * @param flag 输入值 (0/1/2/3 或其他)
     * @return 对应的 Intent Flag
     */
    private int convertLaunchFlag(int flag) {
        int launchFlag;
        switch (flag) {
            case 0:
                launchFlag = Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK;
                break;
            case 1:
                launchFlag = Intent.FLAG_ACTIVITY_NEW_TASK;
                break;
            case 2:
                launchFlag = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
                break;
            case 3:
                launchFlag = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP;
                break;
            default:
                launchFlag = flag;
                break;
        }
        return launchFlag;
    }

    /**
     * 通过 URL 打开外部应用或网页
     *
     * <p>使用系统 Intent 打开指定的 URL，支持 Deep Link、网页链接、系统链接等多种类型</p>
     *
     * @param url 要打开的 URL 字符串
     * @return true 打开成功，false 打开失败（URL 为空或没有应用可以处理）
     */
    @Override
    public boolean openUrl(String url) {
        if (url == null || url.isEmpty()) {
            LOGGER.warn("openUrl: url is null or empty");
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getContext().startActivity(intent);
            LOGGER.info("openUrl: opened url [{}]", url);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            LOGGER.error("openUrl: no app can handle this url [{}]", url);
            return false;
        } catch (Exception e) {
            LOGGER.error("openUrl: failed to open url [{}], error: {}", url, e.getMessage());
            return false;
        }
    }

}
