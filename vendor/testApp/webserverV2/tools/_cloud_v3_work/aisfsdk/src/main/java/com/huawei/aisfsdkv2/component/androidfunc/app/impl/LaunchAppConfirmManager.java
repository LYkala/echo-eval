/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.app.impl;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.FileUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 拉起应用授权管理器
 *
 * <p>管理应用拉起的用户授权状态，首次拉起应用时弹窗提示用户确认，
 * 用户同意后记录授权信息，后续拉起同一应用不再弹窗。</p>
 *
 * <h3>授权流程</h3>
 * <ol>
 *   <li>读取本地配置文件检查是否已授权</li>
 *   <li>未授权则弹窗等待用户操作（超时1分钟）</li>
 *   <li>用户同意后写入配置文件</li>
 * </ol>
 *
 * @since 2026-04-19
 */
public class LaunchAppConfirmManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("LaunchAppConfirmManager");

    /** 弹窗超时时间（毫秒） */
    private static final long CONFIRM_TIMEOUT_MS = 60_000L;

    /** 授权配置目录名 */
    private static final String CONFIG_DIR_NAME = "launchapp";

    /** 授权配置文件名 */
    private static final String CONFIG_FILE_NAME = "launchapp-config.json";

    /** JSON中已授权应用列表字段名 */
    private static final String FIELD_AUTHORIZED_APPS = "AuthorizedApps";

    /** 配置文件读写锁 */
    private final Object configLock = new Object();

    /** 配置文件绝对路径（懒加载，双重检查锁定） */
    private volatile String configFilePath;

    /** 无人值守：跳过拉起 App 确认弹窗并自动授权 */
    private volatile boolean unattendedMode = false;

    private LaunchAppConfirmManager() {
    }

    /**
     * 获取单例实例
     *
     * @return LaunchAppConfirmManager 实例
     */
    public static LaunchAppConfirmManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final LaunchAppConfirmManager INSTANCE = new LaunchAppConfirmManager();
    }

    /**
     * 设置无人值守模式。开启后 confirmAndAuthorize 不再弹窗，直接授权。
     *
     * @param enabled true 无人值守
     */
    public void setUnattendedMode(boolean enabled) {
        this.unattendedMode = enabled;
        LOGGER.info("setUnattendedMode: {}", enabled);
    }

    /**
     * @return 当前是否无人值守
     */
    public boolean isUnattendedMode() {
        return unattendedMode;
    }

    /**
     * 检查应用是否已授权
     *
     * <p>每次从配置文件读取，避免内存缓存不一致。</p>
     *
     * @param packageName 应用包名
     * @return true 已授权，false 未授权
     */
    public boolean isAuthorized(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        Set<String> authorizedApps = loadConfig();
        return authorizedApps.contains(packageName);
    }

    /**
     * 弹窗确认并授权
     *
     * <p>在UI线程弹出确认对话框，等待用户点击同意或取消。
     * 超时时间1分钟，超时视为取消。无人值守模式下直接授权。</p>
     *
     * @param packageName 应用包名
     * @param appName 应用显示名称（可为空，为空时自动查询）
     * @return true 用户同意并授权成功，false 用户取消或超时
     */
    public boolean confirmAndAuthorize(String packageName, String appName) {
        if (packageName == null || packageName.isEmpty()) {
            LOGGER.warn("confirmAndAuthorize: packageName is null or empty");
            return false;
        }
        if (unattendedMode) {
            LOGGER.info("confirmAndAuthorize: unattended auto-allow for {}", packageName);
            authorizeApp(packageName);
            return true;
        }
        LOGGER.info("confirmAndAuthorize: showing confirm dialog for {}", packageName);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean confirmed = new AtomicBoolean(false);
        Handler uiHandler = new Handler(Looper.getMainLooper());
        uiHandler.post(() -> showDialog(packageName, appName, latch, confirmed));
        try {
            boolean awaited = latch.await(CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!awaited) {
                LOGGER.warn("confirmAndAuthorize: confirm dialog timed out for {}", packageName);
                uiHandler.post(() -> dismissDialogIfExists(packageName));
                return false;
            }
        } catch (InterruptedException e) {
            LOGGER.error("confirmAndAuthorize: interrupted while waiting for confirm, pkg={}", packageName);
            Thread.currentThread().interrupt();
            return false;
        }
        if (confirmed.get()) {
            authorizeApp(packageName);
            return true;
        }
        LOGGER.info("confirmAndAuthorize: user cancelled for {}", packageName);
        return false;
    }

    /** 当前显示的对话框引用，用于超时关闭 */
    private volatile AlertDialog currentDialog;

    /**
     * 显示确认对话框
     */
    private void showDialog(String packageName, String appName, CountDownLatch latch, AtomicBoolean confirmed) {
        AlertDialog dialog = null;
        try {
            Context context = AISFSDKClient.getBaseApi().getAppContext();
            if (context == null) {
                LOGGER.error("showDialog: app context is null");
                return;
            }
            String displayName = appName;
            if (displayName == null || displayName.isEmpty()) {
                displayName = getAppNameByPackageName(packageName);
            }
            if (displayName == null) {
                displayName = packageName;
            }
            dialog = new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle("应用拉起确认")
                    .setMessage("即将拉起应用：" + displayName + "，是否允许？")
                    .setPositiveButton("允许", (dialog2, which) -> {
                        LOGGER.info("showDialog: user confirmed for {}", packageName);
                        confirmed.set(true);
                        latch.countDown();
                    })
                    .setNegativeButton("取消", (dialog1, which) -> {
                        LOGGER.info("showDialog: user cancelled for {}", packageName);
                        latch.countDown();
                    })
                    .setCancelable(false)
                    .create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.setCanceledOnTouchOutside(false);
            currentDialog = dialog;
            dialog.show();
            LOGGER.info("showDialog: dialog shown for {}", packageName);
        } catch (Exception e) {
            LOGGER.error("showDialog: failed to show dialog, error={}", e.getMessage());
        } finally {
            if (dialog == null || !dialog.isShowing()) {
                latch.countDown();
            }
        }
    }

    /**
     * 根据包名获取应用名称
     *
     * <p>使用 PackageManager 直接查询，避免遍历所有应用。</p>
     *
     * @param packageName 应用包名
     * @return 应用名称，未找到返回 null
     */
    private String getAppNameByPackageName(String packageName) {
        try {
            Context context = AISFSDKClient.getBaseApi().getAppContext();
            if (context == null) {
                LOGGER.warn("getAppNameByPackageName: app context is null");
                return null;
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            LOGGER.warn("getAppNameByPackageName: app not found, pkg={}", packageName);
            return null;
        } catch (Exception e) {
            LOGGER.error("getAppNameByPackageName: failed to get app name, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 关闭当前对话框（超时场景）
     */
    private void dismissDialogIfExists(String packageName) {
        try {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
                LOGGER.info("dismissDialogIfExists: dialog dismissed for {}", packageName);
            }
        } catch (Exception e) {
            LOGGER.error("dismissDialogIfExists: failed to dismiss dialog, error={}", e.getMessage());
        } finally {
            currentDialog = null;
        }
    }

    /**
     * 授权应用并持久化
     *
     * @param packageName 应用包名
     */
    private void authorizeApp(String packageName) {
        saveConfig(packageName);
        LOGGER.info("authorizeApp: authorized and saved for {}", packageName);
    }

    /**
     * 加载配置文件
     *
     * @return 已授权应用包名集合
     */
    private Set<String> loadConfig() {
        Set<String> authorizedApps = new HashSet<>();
        synchronized (configLock) {
            String filePath = getConfigFilePath();
            if (filePath == null || filePath.isEmpty()) {
                LOGGER.warn("loadConfig: config file path is empty");
                return authorizedApps;
            }
            File file = new File(filePath);
            if (!file.exists()) {
                LOGGER.info("loadConfig: config file not exists, skip loading");
                return authorizedApps;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int readLen = fis.read(data);
                if (readLen <= 0) {
                    LOGGER.warn("loadConfig: config file is empty");
                    return authorizedApps;
                }
                String jsonStr = new String(data, 0, readLen, StandardCharsets.UTF_8);
                JSONObject jsonObj = new JSONObject(jsonStr);
                JSONArray apps = jsonObj.optJSONArray(FIELD_AUTHORIZED_APPS);
                if (apps != null) {
                    for (int i = 0; i < apps.length(); i++) {
                        String pkg = apps.optString(i, "");
                        if (!pkg.isEmpty()) {
                            authorizedApps.add(pkg);
                        }
                    }
                }
                LOGGER.info("loadConfig: loaded {} authorized apps", apps != null ? apps.length() : 0);
            } catch (Exception e) {
                LOGGER.error("loadConfig: failed to load config, error={}", e.getMessage());
            }
        }
        return authorizedApps;
    }

    /**
     * 保存授权应用到配置文件
     *
     * @param packageName 新授权的应用包名
     */
    private void saveConfig(String packageName) {
        synchronized (configLock) {
            String filePath = getConfigFilePath();
            if (filePath == null || filePath.isEmpty()) {
                LOGGER.warn("saveConfig: config file path is empty");
                return;
            }
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                FileUtil.mkdirs(parentDir);
            }
            try {
                Set<String> authorizedApps = loadConfig();
                authorizedApps.add(packageName);
                JSONObject jsonObj = new JSONObject();
                JSONArray apps = new JSONArray();
                for (String pkg : authorizedApps) {
                    apps.put(pkg);
                }
                jsonObj.put(FIELD_AUTHORIZED_APPS, apps);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(jsonObj.toString().getBytes(StandardCharsets.UTF_8));
                }
                LOGGER.info("saveConfig: saved {} authorized apps", apps.length());
            } catch (Exception e) {
                LOGGER.error("saveConfig: failed to save config, error={}", e.getMessage());
            }
        }
    }

    /**
     * 获取配置文件绝对路径（双重检查锁定）
     *
     * @return 配置文件路径
     */
    private String getConfigFilePath() {
        if (configFilePath == null) {
            synchronized (configLock) {
                if (configFilePath == null) {
                    KeyFileInfo keyFileInfo = AISFSDKClient.getBaseApi().getKeyFileInfo();
                    if (keyFileInfo == null) {
                        LOGGER.warn("getConfigFilePath: KeyFileInfo is null");
                        return "";
                    }
                    String sdkDataDir = keyFileInfo.getSdkDataDir();
                    if (sdkDataDir == null || sdkDataDir.isEmpty()) {
                        LOGGER.warn("getConfigFilePath: sdkDataDir is empty");
                        return "";
                    }
                    File configDir = new File(sdkDataDir, CONFIG_DIR_NAME);
                    File configFile = new File(configDir, CONFIG_FILE_NAME);
                    try {
                        configFilePath = configFile.getCanonicalPath();
                    } catch (IOException e) {
                        LOGGER.error("getConfigFilePath: failed to get canonical path, error={}", e.getMessage());
                        return "";
                    }
                }
            }
        }
        return configFilePath;
    }
}
