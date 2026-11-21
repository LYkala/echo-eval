/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.screenshot;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.component.androidfunc.permission.PermissionRequestActivity;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.ExceptionUtil;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

/**
 * 媒体投影截屏服务
 *
 * <p>使用MediaProjection API实现屏幕截图功能的前台服务。</p>
 *
 * <p>该服务通过MediaProjection获取屏幕内容，并使用ImageReader捕获屏幕图像。</p>
 *
 * <h3>功能特性</h3>
 * <ol>
 *   <li>请求并管理MediaProjection权限</li>
 *   <li>通过ImageReader捕获屏幕内容</li>
 *   <li>作为前台服务运行，确保后台稳定性</li>
 *   <li>支持单例模式访问</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式一：自动请求权限并截屏
 * Bitmap screenshot = MediaScreenshotService.captureScreen();
 *
 * // 方式二：手动控制生命周期
 * MediaScreenshotService.start();  // 启动服务并请求权限
 * Bitmap screenshot = MediaScreenshotService.captureScreen();  // 截屏
 * MediaScreenshotService.stop();   // 停止服务
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ol>
 *   <li>需要用户授权MediaProjection权限</li>
 *   <li>Android Q及以上需要FOREGROUND_SERVICE_MEDIA_PROJECTION权限</li>
 *   <li>截屏功能依赖于服务的正确初始化</li>
 * </ol>
 *
 * @author l00504561
 * @since 2026-04-10
 */
public class MediaScreenshotService extends Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("MediaScreenshotService");
    private static MediaScreenshotService instance;

    /**
     * 服务就绪回调接口
     *
     * <p>用于通知服务启动完成，解决 Netty 异步事件循环导致的时序问题。</p>
     *
     * @since 2026-06-01
     */
    public interface ServiceReadyCallback {
        /**
         * 服务就绪回调
         *
         * @param service 服务实例
         */
        void onServiceReady(MediaScreenshotService service);

        /**
         * 服务启动失败回调
         *
         * @param error 错误信息
         */
        void onServiceFailed(String error);
    }

    /** 服务就绪回调列表（线程安全） */
    private static final List<ServiceReadyCallback> serviceReadyCallbacks = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean hasCapturePermission = new AtomicBoolean(false);
    private static final String CHANNEL_ID = "MediaScreenshotServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "aisfsdk_screenshot";
    private static final String KEY_PERMISSION_GRANTED = "screenshot_permission_granted";
    private static final String KEY_RESULT_CODE = "screenshot_result_code";
    private static final String KEY_RESULT_DATA = "screenshot_result_data";

    static {
        restorePermissionFromStorage();
    }
    private MediaProjection mMediaProjection;
    private WindowManager WINDOW_MANAGER = null;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplayImageReader;
    private int mResultCode;
    private Intent mResultData;
    private boolean isRunning = false;
    private final MediaProjection.Callback MEDIA_PROJECTION_CALLBACK = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            LOGGER.error("MediaProjection is stop");
            mMediaProjection = null;
            isRunning = false;
        }
    };

    /**
     * 获取服务实例
     *
     * @return 服务实例，服务未启动时返回null
     */
    public static MediaScreenshotService getInstance() {
        return instance;
    }

    /**
     * 是否已保存 MediaProjection 录屏授权（含 SharedPreferences 恢复）。
     */
    public static boolean isCapturePermissionGranted(Context context) {
        if (context != null) {
            Context appCtx = context.getApplicationContext();
            boolean fromPrefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_PERMISSION_GRANTED, false);
            try {
                if (!AISFSDKClient.getInstance().isInitialized()) {
                    return fromPrefs;
                }
            } catch (Exception ignored) {
                return fromPrefs;
            }
        }
        if (!hasCapturePermission.get()) {
            restorePermissionFromStorage();
        }
        return hasCapturePermission.get();
    }

    /** MediaProjection 截屏服务是否已就绪（可 takeScreenshot）。 */
    public static boolean isScreenshotReady() {
        MediaScreenshotService service = getInstance();
        return service != null && service.isRunning;
    }

    /**
     * 当前进程内 MediaProjection 对象是否仍在（可能 isRunning=false 仅暂停了 VirtualDisplay）。
     * 为 true 时可 start() 恢复，无需再弹系统授权框。
     */
    public static boolean hasUsableMediaProjection() {
        MediaScreenshotService service = getInstance();
        return service != null && service.mMediaProjection != null;
    }

    /**
     * 注册服务就绪回调
     *
     * <p>回调会在服务 onCreate() 完成后触发，解决 Netty 异步延迟问题。</p>
     *
     * @param callback 回调实例
     */
    public static void registerServiceReadyCallback(ServiceReadyCallback callback) {
        if (callback != null) {
            serviceReadyCallbacks.add(callback);
            LOGGER.info("ServiceReadyCallback registered: {}", callback.getClass().getSimpleName());
        }
    }

    /**
     * 注销服务就绪回调
     *
     * @param callback 回调实例
     */
    public static void unregisterServiceReadyCallback(ServiceReadyCallback callback) {
        if (callback != null) {
            serviceReadyCallbacks.remove(callback);
            LOGGER.info("ServiceReadyCallback unregistered: {}", callback.getClass().getSimpleName());
        }
    }

    /**
     * 清除所有服务就绪回调
     */
    public static void clearServiceReadyCallbacks() {
        serviceReadyCallbacks.clear();
        LOGGER.info("All ServiceReadyCallbacks cleared");
    }

    /**
     * 通知所有监听者服务已就绪
     *
     * @param service 服务实例
     */
    private static void notifyServiceReady(MediaScreenshotService service) {
        LOGGER.info("Notifying service ready to {} callbacks", serviceReadyCallbacks.size());
        for (ServiceReadyCallback callback : serviceReadyCallbacks) {
            try {
                callback.onServiceReady(service);
            } catch (Exception e) {
                LOGGER.error("ServiceReadyCallback.onServiceReady error: {}", e.getMessage());
            }
        }
    }

    /**
     * 通知所有监听者服务启动失败
     *
     * @param error 错误信息
     */
    private static void notifyServiceFailed(String error) {
        LOGGER.error("Notifying service failed to {} callbacks: {}", serviceReadyCallbacks.size(), error);
        for (ServiceReadyCallback callback : serviceReadyCallbacks) {
            try {
                callback.onServiceFailed(error);
            } catch (Exception e) {
                LOGGER.error("ServiceReadyCallback.onServiceFailed error: {}", e.getMessage());
            }
        }
    }

    /**
     * 服务创建时回调
     *
     * <p>初始化通知渠道、启动前台服务、设置实例引用。</p>
     */
    @Override
    public void onCreate() {
        LOGGER.error("MediaScreenshotService onCreate");
        super.onCreate();
        // 创建通知渠道
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Media Screenshot Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
        // 启动前台服务（Android Q及以上需要指定MEDIA_PROJECTION类型）
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Recording Service")
                .setContentText("Recording screen...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            LOGGER.error("MediaScreenshotService: SecurityException in startForeground: {}", e.getMessage());
            stopSelf();
            notifyServiceFailed("SecurityException in startForeground: " + e.getMessage());
            return;
        }
        instance = this;
        LOGGER.info("MediaScreenshotService instance set, notifying callbacks");
        notifyServiceReady(this);
    }

    /**
     * 启动截屏服务
     *
     * <p>该方法会请求MediaProjection权限并启动服务。</p>
     * <p>如果权限请求失败，服务不会启动。</p>
     * <p>支持从持久化存储恢复权限状态，Service被回收后可自动重建。</p>
     * <p>如果MediaProjection已存在（暂停状态），直接重建VirtualDisplay，无需用户重新授权。</p>
     * <p>使用回调机制解决 Netty 异步事件循环导致的时序问题。</p>
     */
    public static void start() {
        if (getInstance() != null && getInstance().isRunning) {
            LOGGER.info("MediaScreenshotService already running, notifying callbacks");
            notifyServiceReady(getInstance());
            return;
        }
        // MediaProjection仍存在（暂停状态），直接重建VirtualDisplay
        if (getInstance() != null && getInstance().mMediaProjection != null) {
            LOGGER.info("MediaScreenshotService: resuming from paused state");
            getInstance().setupMediaProjection();
            notifyServiceReady(getInstance());
            return;
        }
        if (!hasCapturePermission.get()) {
            restorePermissionFromStorage();
        }
        if (!hasCapturePermission.get()) {
            LOGGER.error("has not screenshot permission, requesting...");
            boolean result = requestMediaProjectionPermission();
            if (!result) {
                LOGGER.error("requestMediaProjectionPermission failed");
                notifyServiceFailed("requestMediaProjectionPermission failed");
                return;
            }
        }
        if (getInstance() == null) {
            LOGGER.info("permission granted but service not running, restarting service...");
            if (!restartServiceFromStorage()) {
                LOGGER.error("restart service from storage failed, clearing permission data");
                clearPermissionStorage();
                hasCapturePermission.set(false);
                boolean result = requestMediaProjectionPermission();
                if (!result) {
                    LOGGER.error("requestMediaProjectionPermission failed after restart failure");
                    notifyServiceFailed("requestMediaProjectionPermission failed after restart failure");
                    return;
                }
            }
        }
        if (getInstance() != null) {
            getInstance().setupMediaProjection();
            notifyServiceReady(getInstance());
        } else {
            LOGGER.info("service instance is still null, will notify when onCreate completes");
            // 回调将在 onCreate() 中触发，解决 Netty 异步延迟问题
        }
    }

    /**
     * 初始化MediaProjection
     *
     * <p>获取WindowManager和MediaProjectionManager，创建MediaProjection实例。</p>
     * <p>如果MediaProjection已存在（暂停状态），直接重建VirtualDisplay。</p>
     *
     * @return true表示初始化成功，false表示初始化失败
     */
    private boolean setupMediaProjection() {
        LOGGER.info("setupMediaProjection status:{}", isRunning);
        WINDOW_MANAGER = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (isRunning) {
            return true;
        }
        // MediaProjection仍存在（暂停状态），直接重建VirtualDisplay
        if (mMediaProjection != null) {
            LOGGER.info("setupMediaProjection: reusing existing MediaProjection");
            createImageReaderVirtualDisplay();
            isRunning = true;
            ThreadUtil.sleepNoExcept(200);
            return true;
        }
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        LOGGER.info("setupMediaProjection projectionManager end");
        try {
            mMediaProjection = projectionManager.getMediaProjection(mResultCode, mResultData);
        } catch (Throwable t) {
            LOGGER.error("setupMediaProjection getMediaProjection failed: {}", t.getMessage());
            mMediaProjection = null;
        }
        if (mMediaProjection == null) {
            return false;
        }
        LOGGER.info("setupMediaProjection getMediaProjection end");
        mMediaProjection.registerCallback(MEDIA_PROJECTION_CALLBACK, new Handler(Looper.getMainLooper()));
        LOGGER.info("setupMediaProjection mMediaProjection end");
        createImageReaderVirtualDisplay();
        LOGGER.info("setupMediaProjection createImageReaderVirtualDisplay end");
        isRunning = true;
        // 初始化 MediaProjection 等待 200ms 缓存截屏数据
        ThreadUtil.sleepNoExcept(200);
        return true;
    }

    /**
     * 创建ImageReader和VirtualDisplay
     *
     * <p>通过MediaProjection创建虚拟显示器，将屏幕内容输出到ImageReader。</p>
     */
    @SuppressLint("WrongConstant")
    private void createImageReaderVirtualDisplay() {
        LOGGER.info("createImageReaderVirtualDisplay");
        if (mMediaProjection != null) {
            DisplayMetrics dm = new DisplayMetrics();
            WINDOW_MANAGER.getDefaultDisplay().getRealMetrics(dm);
            mImageReader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 1);
            if (mVirtualDisplayImageReader == null) {
                mVirtualDisplayImageReader = mMediaProjection.createVirtualDisplay("ScreenCapture",
                        dm.widthPixels,
                        dm.heightPixels,
                        dm.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(),
                        null, new Handler(Looper.getMainLooper()));
            } else {
                mVirtualDisplayImageReader.setSurface(mImageReader.getSurface());
            }
        } else {
            LOGGER.info("mMediaProjection == null");
        }
    }

    /**
     * 停止截屏服务
     *
     * <p>释放VirtualDisplay和ImageReader资源，但保留MediaProjection以避免下次启动时重新弹窗授权。</p>
     */
    public static void stop() {
        if (getInstance() != null && getInstance().isRunning) {
            getInstance().releaseVirtualDisplay();
        }
    }

    /**
     * 释放VirtualDisplay和ImageReader资源（保留MediaProjection）
     *
     * <p>停止截屏功能但不释放MediaProjection，下次start()时可直接重建VirtualDisplay，无需用户重新授权。</p>
     */
    private void releaseVirtualDisplay() {
        LOGGER.info("releaseVirtualDisplay");
        if (mVirtualDisplayImageReader != null) {
            mVirtualDisplayImageReader.setSurface(null);
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        ThreadUtil.sleepNoExcept(100);
        isRunning = false;
    }

    /**
     * 释放所有MediaProjection相关资源
     *
     * <p>依次释放VirtualDisplay、ImageReader、MediaProjection，并重置运行状态。</p>
     */
    private void unSetupMediaProjection() {
        LOGGER.info("unSetupMediaProjection");
        if (mVirtualDisplayImageReader != null) {
            mVirtualDisplayImageReader.release();
            mVirtualDisplayImageReader = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(MEDIA_PROJECTION_CALLBACK);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        ThreadUtil.sleepNoExcept(200);
        isRunning = false;
    }

    /**
     * 服务启动命令回调
     *
     * <p>从Intent中获取MediaProjection授权结果，并初始化MediaProjection。</p>
     *
     * @param intent 启动Intent
     * @param flags 启动标志
     * @param startId 启动ID
     * @return 服务启动模式
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
        Intent data = intent != null ? intent.getParcelableExtra("projection_intent") : null;
        if (resultCode != Activity.RESULT_OK || data == null) {
            int savedCode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getInt(KEY_RESULT_CODE, -1);
            String dataUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_RESULT_DATA, null);
            if (savedCode == Activity.RESULT_OK && dataUri != null) {
                try {
                    resultCode = savedCode;
                    data = Intent.parseUri(dataUri, Intent.URI_INTENT_SCHEME);
                    LOGGER.info("onStartCommand: restored projection token from storage");
                } catch (Exception e) {
                    LOGGER.error("onStartCommand: restore uri failed: {}", e.getMessage());
                }
            }
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
            mResultCode = resultCode;
            mResultData = data;
            hasCapturePermission.set(true);
            new Thread(() -> {
                boolean ok = setupMediaProjection();
                LOGGER.info("onStartCommand setupMediaProjection={}", ok);
            }, "mp-setup").start();
        }
        return Service.START_STICKY;
    }

    /**
     * 服务销毁时回调
     *
     * <p>释放MediaProjection资源、清除实例引用、重置权限状态。</p>
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        unSetupMediaProjection();
        instance = null;
        hasCapturePermission.set(false);
    }

    /**
     * 保存权限授权数据到SharedPreferences
     *
     * @param resultCode Activity结果码
     * @param data       MediaProjection授权Intent
     */
    private static void savePermissionData(int resultCode, Intent data) {
        try {
            Context ctx = AISFSDKClient.getBaseApi().getAppContext();
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PERMISSION_GRANTED, true)
                    .putInt(KEY_RESULT_CODE, resultCode)
                    .putString(KEY_RESULT_DATA, data != null ? data.toUri(0) : null)
                    .apply();
        } catch (Exception e) {
            LOGGER.error("savePermissionData failed: {}", e.getMessage());
        }
    }

    /**
     * 从SharedPreferences恢复权限状态
     *
     * <p>进程重启或服务被回收后，恢复hasCapturePermission状态。</p>
     */
    private static void restorePermissionFromStorage() {
        try {
            Context ctx = AISFSDKClient.getBaseApi().getAppContext();
            boolean granted = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(KEY_PERMISSION_GRANTED, false);
            hasCapturePermission.set(granted);
            LOGGER.info("restorePermissionFromStorage: granted = {}", granted);
        } catch (Exception e) {
            LOGGER.error("restorePermissionFromStorage failed: {}", e.getMessage());
            hasCapturePermission.set(false);
        }
    }

    /**
     * 使用持久化数据重新启动Service
     *
     * <p>当权限已授予但Service实例为null时，从SharedPreferences读取数据并重启服务。</p>
     *
     * @return true表示重启成功，false表示重启失败
     */
    private static boolean restartServiceFromStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            LOGGER.info("restartServiceFromStorage: skip on Android 14+, requires fresh MediaProjection approval");
            return false;
        }
        try {
            Context ctx = AISFSDKClient.getBaseApi().getAppContext();
            int resultCode = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_RESULT_CODE, -1);
            String dataUri = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_RESULT_DATA, null);
            // toUri 存不下 MediaProjection 的 IBinder，空串不能当成可恢复凭证
            if (resultCode == Activity.RESULT_OK && dataUri != null && !dataUri.isEmpty()) {
                Intent data = Intent.parseUri(dataUri, Intent.URI_INTENT_SCHEME);
                Intent serviceIntent = new Intent(ctx, MediaScreenshotService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("projection_intent", data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ctx.startForegroundService(serviceIntent);
                } else {
                    ctx.startService(serviceIntent);
                }
                LOGGER.info("restartServiceFromStorage: service restarted");
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("restartServiceFromStorage failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 清除持久化的权限数据
     */
    private static void clearPermissionStorage() {
        try {
            Context ctx = AISFSDKClient.getBaseApi().getAppContext();
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
            LOGGER.info("clearPermissionStorage: permission data cleared");
        } catch (Exception e) {
            LOGGER.error("clearPermissionStorage failed: {}", e.getMessage());
        }
    }

    /**
     * 服务绑定回调
     *
     * <p>该服务不支持绑定，始终返回null。</p>
     *
     * @param intent 绑定Intent
     * @return 始终返回null
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 请求MediaProjection权限
     *
     * <p>该方法会弹出系统权限请求对话框，等待用户授权。</p>
     * <p>方法会阻塞当前线程，最长等待45秒。</p>
     *
     * @return true表示已获得权限，false表示未获得权限或请求超时
     */
    public static boolean requestMediaProjectionPermission() {
        if (hasCapturePermission.get() && (isScreenshotReady() || hasUsableMediaProjection())) {
            LOGGER.info("requestMediaProjectionPermission has started");
            return true;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            ThreadUtil.sleepNoExcept(200);
            Context appCtx = AISFSDKClient.getBaseApi().getAppContext();
            if (appCtx == null) {
                LOGGER.error("requestMediaProjectionPermission: appContext null");
                latch.countDown();
                return;
            }
            PermissionRequestActivity.start(appCtx, PermissionRequestActivity.REQUEST_TYPE_ACTIVITY,
                    new PermissionRequestActivity.PermissionCallback() {
                        @Override
                        public void onActivityResult(int resultCode, Intent data) {
                            LOGGER.error("onActivityResult cb enter.");
                            procActivityResult(resultCode, data, latch);
                        }
                        @Override
                        public Intent getRequestIntent() {
                            LOGGER.error("getRequestIntent enter.");
                            MediaProjectionManager projectionManager =
                                    (MediaProjectionManager) appCtx.getSystemService(MEDIA_PROJECTION_SERVICE);
                            return projectionManager.createScreenCaptureIntent();
                        }
                        @Override
                        public void onRequestPermissionsResult(boolean isSuccess) {
                            LOGGER.info("onRequestPermissionsResult ");
                            hasCapturePermission.set(isSuccess);
                            latch.countDown();
                        }
                    });
            LOGGER.error("requestPermission start");
        }).start();
        try {
            boolean requestPermissionResult = latch.await(45, TimeUnit.SECONDS);
            if (!requestPermissionResult) {
                LOGGER.error("requestPermission failed, 45 seconds time out");
            }
        } catch (InterruptedException e) {
            LOGGER.error("request exception:{}", e.getMessage());
            LOGGER.error(ExceptionUtil.getStackTrace(e));
        }
        return hasCapturePermission.get();
    }

    /**
     * 处理权限请求Activity的返回结果
     *
     * <p>当用户授权后，启动本服务并传递授权数据。</p>
     *
     * @param resultCode Activity返回的结果码
     * @param data Activity返回的Intent数据
     * @param latch 用于同步等待的CountDownLatch，可为null
     */
    public static void procActivityResult(int resultCode, Intent data, CountDownLatch latch) {
        LOGGER.info("onStartResult, resultCode = {}", resultCode);
        if (resultCode == Activity.RESULT_OK) {
            // 保存授权数据到持久化存储
            savePermissionData(resultCode, data);
            // 启动服务并传递 MediaProjection 对象
            Intent serviceIntent = new Intent(AISFSDKClient.getBaseApi().getAppContext(), MediaScreenshotService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("projection_intent", data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                LOGGER.error("onStartResult, startForegroundService");
                AISFSDKClient.getBaseApi().getAppContext().startForegroundService(serviceIntent);
            } else {
                LOGGER.error("onStartResult, startService");
                AISFSDKClient.getBaseApi().getAppContext().startService(serviceIntent);
            }
            hasCapturePermission.set(true);
        }
        new Thread(() -> {
            ThreadUtil.sleepNoExcept(500);
            if (latch != null) {
                latch.countDown();
            }
        }).start();
    }

    /**
     * 从ImageReader获取图像并转换为Bitmap
     *
     * <p>获取最新一帧图像，处理行填充问题，转换为Bitmap对象。</p>
     *
     * @return 截图的Bitmap对象，获取失败返回null
     */
    private Bitmap doTakeScreenshot() {
        LOGGER.warn("MediaScreenshotService takeScreenshot enter");
        if (mImageReader == null) {
            LOGGER.warn("MediaScreenshotService not init");
            return null;
        }
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            LOGGER.warn("image is null");
            return null;
        }
        // 获取数据
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane plane = image.getPlanes()[0];
        final ByteBuffer buffer = plane.getBuffer();
        // 重新计算Bitmap宽度，防止Bitmap显示错位
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        int bitmapWidth = width + rowPadding / pixelStride;
        // 创建Bitmap
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        // 释放资源
        image.close();
        LOGGER.error("on captureScreen, image rowWidth = {}/height = {}, bitmap width = {}, height = {}",
                width, height, bitmap.getWidth(), bitmap.getHeight());
        return bitmap;
    }

    /**
     * 检查并请求截屏权限
     *
     * <p>如果权限不存在或服务未初始化，会自动请求权限。</p>
     * <p>支持从持久化存储恢复权限状态。</p>
     * <p>如果MediaProjection已存在（暂停状态），直接恢复。</p>
     *
     * @return true表示权限检查通过，false表示权限请求失败
     */
    private static boolean checkAndRequestPermission() {
        if (getInstance() != null && getInstance().isRunning) {
            return true;
        }
        // MediaProjection仍存在（暂停状态），直接恢复
        if (getInstance() != null && getInstance().mMediaProjection != null) {
            LOGGER.info("checkAndRequestPermission: resuming from paused state");
            getInstance().setupMediaProjection();
            return getInstance().isRunning;
        }
        if (!hasCapturePermission.get()) {
            restorePermissionFromStorage();
        }
        if (!hasCapturePermission.get()) {
            LOGGER.error("has not screenshot permission, requesting...");
            boolean result = requestMediaProjectionPermission();
            if (!result) {
                LOGGER.error("requestMediaProjectionPermission failed");
                return false;
            }
        }
        if (getInstance() == null) {
            LOGGER.info("permission granted but service not running, restarting service...");
            if (!restartServiceFromStorage()) {
                LOGGER.error("restart service from storage failed, clearing permission data");
                clearPermissionStorage();
                hasCapturePermission.set(false);
                boolean result = requestMediaProjectionPermission();
                if (!result) {
                    LOGGER.error("requestMediaProjectionPermission failed after restart failure");
                    return false;
                }
            }
        }
        return getInstance() != null;
    }

    /**
     * 捕获当前屏幕
     *
     * <p>该方法会自动检查权限，如果权限不存在会请求权限。</p>
     * <p>如果服务未运行会自动启动服务。</p>
     * <p>如果服务未就绪，会等待最多5秒直到服务初始化完成。</p>
     *
     * @return 屏幕截图的Bitmap对象，截屏失败返回null
     */
    public static Bitmap takeScreenshot() {
        if (!checkAndRequestPermission()) {
            return null;
        }
        
        // ✅ 新增：如果服务未就绪，等待初始化完成（解决 Netty 异步延迟问题）
        if (getInstance() == null || !getInstance().isRunning) {
            LOGGER.info("takeScreenshot: service not ready, waiting for initialization...");
            final CountDownLatch readyLatch = new CountDownLatch(1);
            
            registerServiceReadyCallback(new ServiceReadyCallback() {
                @Override
                public void onServiceReady(MediaScreenshotService service) {
                    LOGGER.info("takeScreenshot: service ready callback received");
                    readyLatch.countDown();
                }
                @Override
                public void onServiceFailed(String error) {
                    LOGGER.error("takeScreenshot: service failed callback: {}", error);
                    readyLatch.countDown();
                }
            });
            
            // 如果服务完全未启动，触发启动
            if (getInstance() == null) {
                LOGGER.info("takeScreenshot: triggering service start");
                start();
            }
            
            // 等待服务就绪（最多 5 秒）
            try {
                boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
                if (!ready) {
                    LOGGER.error("takeScreenshot: service startup timeout after 5 seconds");
                    return null;
                }
            } catch (InterruptedException e) {
                LOGGER.error("takeScreenshot: interrupted while waiting for service: {}", e.getMessage());
                return null;
            }
            
            // 再次检查服务状态
            if (getInstance() == null) {
                LOGGER.error("takeScreenshot: service instance still null after waiting");
                return null;
            }
            if (!getInstance().isRunning) {
                LOGGER.warn("takeScreenshot: service not running after waiting, attempting setup");
                getInstance().setupMediaProjection();
                if (!getInstance().isRunning) {
                    LOGGER.error("takeScreenshot: service still not running after setup attempt");
                    return null;
                }
            }
        }
        
        return getInstance().doTakeScreenshot();
    }
}
