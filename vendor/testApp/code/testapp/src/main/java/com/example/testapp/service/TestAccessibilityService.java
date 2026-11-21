package com.example.testapp.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.RequiresApi;

import com.example.testapp.MainActivity;
import com.example.testapp.util.Logger;
import com.example.testapp.util.ScreenshotDiagLogger;
import com.example.testapp.util.ScreenshotManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 无障碍服务 - TestAccessibilityService
 *
 * 【核心功能】
 * 1. 提供UI自动化操作能力（点击、输入、滑动等）
 * 2. 提供节点查找能力（通过ID或文本）
 * 3. 提供截图能力（API 24+）
 *
 * 【工作原理】
 * 无障碍服务是Android系统提供的一种特殊服务，可以监听系统的UI事件，
 * 并模拟用户操作（点击、滑动、输入等）。
 *
 * 【注意事项】
 * 1. 用户需要手动在系统设置中开启此服务
 * 2. 服务启动后会自动获取instance引用
 * 3. 所有操作都需要在UI线程中执行
 */
public class TestAccessibilityService extends AccessibilityService {

    private static final String TAG = "TestAccessibilityService";

    /** 服务实例（单例模式，volatile保证线程可见性） */
    private static volatile TestAccessibilityService instance;

    /** 全局 ApplicationContext，用于在服务停止后检查无障碍服务状态 */
    private static android.content.Context globalContext;

    /** 无障碍侧保活心跳：小米冻 TestApp 普通线程时，a11y looper 仍常能跑 */
    private Handler keepAliveHandler;
    private Runnable keepAliveRunnable;
    private static final long KEEP_ALIVE_HEARTBEAT_MS = 8000L;
    private volatile long lastKeepAliveHeartbeatAt;

    /** adb/调试：模拟 GUI 收尾拉回 DeviceSDK */
    public static final String ACTION_HANDOFF_DEVICE_SDK =
            "com.example.testapp.ACTION_HANDOFF_DEVICE_SDK";
    private BroadcastReceiver handoffReceiver;

    /** 屏幕尺寸信息 */
    private DisplayMetrics displayMetrics;

    /** 截图相关 */
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    /** 截图管理器 */
    private ScreenshotManager screenshotManager;

    /** wait-response 期间前台包名变化回调（由 TestExecuteService 注册） */
    public interface ForegroundChangeListener {
        void onForegroundPackageChanged(String fromPkg, String toPkg);
    }

    private volatile ForegroundChangeListener foregroundChangeListener;
    private volatile String lastNotifiedForegroundPkg;

    /**
     * 获取服务实例
     * @return 服务实例，如果服务未启动则返回null
     */
    public static TestAccessibilityService getInstance() {
        return instance;
    }

    /** 注册/取消前台包名变化监听（仅 wait-response GUI 截图使用） */
    public void setForegroundChangeListener(ForegroundChangeListener listener) {
        foregroundChangeListener = listener;
        lastNotifiedForegroundPkg = listener != null
                ? getForegroundPackageNameUnsafe() : null;
    }

    /**
     * 将 TestApp 主界面拉到前台（GUI 收尾解冻用；无障碍进程相对不易被 Greezer 冻死）。
     */
    public boolean bringTestAppToForeground() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(getPackageName(), MainActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            Logger.safeInfo(TAG, "bringTestAppToForeground: startActivity MainActivity");
            return true;
        } catch (Exception e) {
            Logger.safeWarn(TAG, "bringTestAppToForeground失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查服务是否已启用
     * 核心原则：无障碍权限只需要在应用首次启动时授权一次
     * 之后只要应用没被完全杀死（后台没有清除），权限应该一直保持
     *
     * @return true表示服务应该可用（不主动检查系统API，避免误判导致反复弹窗）
     */
    public static boolean isServiceEnabled() {
        // 优先检查 instance 是否存在（服务正在运行）
        if (instance != null) {
            if (globalContext == null) {
                globalContext = instance.getApplicationContext();
            }
            return true;
        }

        // 服务已停止，但 globalContext 仍然存在，说明应用还在后台运行
        // 这种情况下，无障碍权限应该仍然是开启的
        if (globalContext != null) {
            // 不再进行实际的系统 API 检查，直接返回 true
            // 因为如果权限被用户手动关闭了，用户会自己知道去开启
            // 我们不应该反复弹窗打扰用户
            return true;
        }

        // globalContext 为 null，这是应用刚启动的情况
        // 尝试通过反射获取 ApplicationContext
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object currentApplication = activityThreadClass.getMethod("currentApplication").invoke(null);
            if (currentApplication != null) {
                globalContext = ((android.app.Application) currentApplication).getApplicationContext();
                // 应用刚启动时，如果 globalContext 能获取到，说明应用进程还在
                // 无障碍权限应该还是开启的
                return true;
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "获取Application失败", e);
        }

        // 极端情况：无法获取任何 Context
        // 这时返回 true，让用户能够启动测试
        // 如果权限真的没开启，测试执行时会失败，但不会反复弹窗
        Logger.safeWarn(TAG, "无法获取Context，假设无障碍服务已启用");
        return true;
    }

    /**
     * 强制检查无障碍服务状态（仅用于UI显示，不用于权限判断）
     * 这个方法会真正检查系统设置，但结果不会导致弹窗
     */
    public static boolean checkServiceReallyEnabled() {
        if (globalContext == null) {
            return false;
        }

        try {
            AccessibilityManager am = (AccessibilityManager) globalContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                for (AccessibilityServiceInfo info : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                    if (info != null && info.getResolveInfo() != null) {
                        String resolvedService = info.getResolveInfo().serviceInfo.name;
                        if (resolvedService != null && resolvedService.equals(TestAccessibilityService.class.getName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "检查无障碍服务状态失败", e);
        }
        return false;
    }

    /**
     * 服务连接回调
     * 当服务成功连接到系统时调用，在此进行初始化
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Logger.safeInfo(TAG, "=== onServiceConnected - 无障碍服务已连接 ===");

        // 保存服务实例
        instance = this;

        // 保存 ApplicationContext，用于在服务停止后检查无障碍服务状态
        globalContext = getApplicationContext();

        // 初始化屏幕尺寸
        initDisplayMetrics();

        // 初始化截图管理器
        screenshotManager = ScreenshotManager.getInstance(this);

        // 允许遍历全部窗口（含顶层 Dialog），便于处理权限/启动确认等系统弹窗
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                        | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                        | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
                setServiceInfo(info);
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "设置无障碍 flags 失败", e);
        }

        Logger.safeInfo(TAG, "无障碍服务初始化完成, 屏幕尺寸: " + screenWidth + "x" + screenHeight);
        startKeepAliveHeartbeat();
        registerHandoffReceiver();
    }

    private void registerHandoffReceiver() {
        if (handoffReceiver != null) {
            return;
        }
        handoffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION_HANDOFF_DEVICE_SDK.equals(intent.getAction())) {
                    return;
                }
                Logger.safeInfo(TAG, "收到 HANDOFF_DEVICE_SDK，执行拉回");
                handoffToDeviceSdk();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_HANDOFF_DEVICE_SDK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(handoffReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(handoffReceiver, filter);
        }
    }

    /**
     * GUI 收尾：直接 provider + startActivity QAActivity（不按 HOME，避免闪回桌面）。
     */
    public void handoffToDeviceSdk() {
        Runnable pull = () -> {
            try {
                com.example.testreply.TestReplySdk.bringDeviceToForeground(getApplicationContext());
            } catch (Exception e) {
                Logger.safeWarn(TAG, "provider bring_front: " + e.getMessage());
            }
            bringPackageToForeground(
                    "com.example.devicesdkdemo",
                    "com.example.devicesdkdemo.QAActivity");
        };
        postOnService(pull);
        for (int i = 1; i <= 4; i++) {
            final int attempt = i;
            postOnServiceDelayed(() -> {
                String fg = getForegroundPackageName();
                if ("com.example.devicesdkdemo".equals(fg)) {
                    return;
                }
                Logger.safeWarn(TAG, "handoff retry#" + attempt + " fg=" + fg + " (no HOME)");
                pull.run();
            }, attempt * 800L);
        }
    }

    /**
     * 在无障碍主线程执行（不易被小米 Greezer 冻死），用于收尾拉起 / 兜底上报。
     */
    public void postOnService(Runnable r) {
        if (r == null) {
            return;
        }
        if (keepAliveHandler == null) {
            keepAliveHandler = new Handler(Looper.getMainLooper());
        }
        keepAliveHandler.post(r);
    }

    public void postOnServiceDelayed(Runnable r, long delayMs) {
        if (r == null) {
            return;
        }
        if (keepAliveHandler == null) {
            keepAliveHandler = new Handler(Looper.getMainLooper());
        }
        keepAliveHandler.postDelayed(r, delayMs);
    }

    private void startKeepAliveHeartbeat() {
        if (keepAliveHandler == null) {
            keepAliveHandler = new Handler(Looper.getMainLooper());
        }
        if (keepAliveRunnable != null) {
            keepAliveHandler.removeCallbacks(keepAliveRunnable);
        }
        keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    tickHttpHeartbeat("a11y_timer");
                } finally {
                    if (keepAliveHandler != null && keepAliveRunnable == this) {
                        keepAliveHandler.postDelayed(this, KEEP_ALIVE_HEARTBEAT_MS);
                    }
                }
            }
        };
        keepAliveHandler.postDelayed(keepAliveRunnable, 2000L);
        Logger.safeInfo(TAG, "无障碍保活心跳已启动 intervalMs=" + KEEP_ALIVE_HEARTBEAT_MS);
    }

    private void tickHttpHeartbeat(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastKeepAliveHeartbeatAt < 4000L) {
            return;
        }
        lastKeepAliveHeartbeatAt = now;
        try {
            com.example.testapp.util.WebServerClient client =
                    com.example.testapp.util.WebServerClient.peekInstance();
            if (client != null) {
                client.sendHttpHeartbeat();
            }
        } catch (Exception e) {
            Logger.safeDebug(TAG, "keepAlive heartbeat fail (" + reason + "): " + e.getMessage());
        }
    }

    /**
     * 拉起任意包到前台（GUI 收尾拉回 DeviceSDK）。
     * 调用方应先 HOME；此处只负责 startActivity。
     */
    public boolean bringPackageToForeground(String packageName, String activityClass) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            Intent intent;
            if (activityClass != null && !activityClass.isEmpty()) {
                intent = new Intent();
                intent.setClassName(packageName, activityClass);
            } else {
                intent = getPackageManager().getLaunchIntentForPackage(packageName);
            }
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            Logger.safeInfo(TAG, "bringPackageToForeground ok pkg=" + packageName
                    + " act=" + (activityClass != null ? activityClass : "launcher"));
            return true;
        } catch (Exception e) {
            Logger.safeWarn(TAG, "bringPackageToForeground失败: " + e.getMessage());
            return false;
        }
    }

    /** 按 HOME，退出爱奇艺等全屏页，便于后续拉起目标 App。 */
    public boolean pressHomeForHandoff() {
        try {
            boolean ok = performGlobalAction(GLOBAL_ACTION_HOME);
            Logger.safeInfo(TAG, "pressHomeForHandoff ok=" + ok);
            return ok;
        } catch (Exception e) {
            Logger.safeWarn(TAG, "pressHomeForHandoff失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 初始化屏幕尺寸信息
     * 用于坐标计算和手势操作
     */
    private void initDisplayMetrics() {
        displayMetrics = new DisplayMetrics();
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                wm.getDefaultDisplay().getRealMetrics(displayMetrics);
            } else {
                displayMetrics.widthPixels = 1080;
                displayMetrics.heightPixels = 1920;
                displayMetrics.densityDpi = 320;
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "获取屏幕尺寸失败", e);
            displayMetrics.widthPixels = 1080;
            displayMetrics.heightPixels = 1920;
            displayMetrics.densityDpi = 320;
        }
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        screenDensity = displayMetrics.densityDpi;
    }

    /**
     * 无障碍事件回调
     * 监听界面变化
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();

        // 记录窗口变化日志，并在包名切换时通知 GUI 跳转截图
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkgSeq = event.getPackageName();
            Logger.safeDebug(TAG, "窗口变化: " + pkgSeq + " - " + event.getClassName());
            // 窗口切换时顺带心跳，跨 App 时刷新 last_seen
            tickHttpHeartbeat("window_changed");
            if (pkgSeq != null && foregroundChangeListener != null) {
                String pkg = pkgSeq.toString();
                if (!pkg.isEmpty() && !pkg.equals(lastNotifiedForegroundPkg)) {
                    String from = lastNotifiedForegroundPkg;
                    lastNotifiedForegroundPkg = pkg;
                    ForegroundChangeListener listener = foregroundChangeListener;
                    if (listener != null) {
                        listener.onForegroundPackageChanged(from, pkg);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        Logger.safeWarn(TAG, "onInterrupt - 无障碍服务中断");
    }

    @Override
    public void onDestroy() {
        Logger.safeWarn(TAG, "onDestroy - 服务销毁");
        if (handoffReceiver != null) {
            try {
                unregisterReceiver(handoffReceiver);
            } catch (Exception ignored) {
                // ignore
            }
            handoffReceiver = null;
        }
        if (keepAliveHandler != null && keepAliveRunnable != null) {
            keepAliveHandler.removeCallbacks(keepAliveRunnable);
        }
        keepAliveRunnable = null;
        foregroundChangeListener = null;
        lastNotifiedForegroundPkg = null;
        instance = null;
        super.onDestroy();
    }

    /**
     * 获取当前界面根节点
     */
    public AccessibilityNodeInfo getRootNode() {
        try {
            return getRootInActiveWindow();
        } catch (Exception e) {
            Logger.safeError(TAG, "getRootNode失败", e);
            return null;
        }
    }

    /**
     * 截图诊断：描述当前屏幕上的窗口分布（整屏截图应与此一致）。
     */
    public String buildScreenDiagSnapshot() {
        StringBuilder sb = new StringBuilder();
        try {
            AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
            if (activeRoot != null) {
                sb.append("activePkg=").append(activeRoot.getPackageName());
                activeRoot.recycle();
            } else {
                sb.append("activePkg=null");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null && !windows.isEmpty()) {
                    sb.append(" windows=");
                    boolean first = true;
                    for (AccessibilityWindowInfo window : windows) {
                        if (window == null) {
                            continue;
                        }
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root == null) {
                            continue;
                        }
                        if (!first) {
                            sb.append('|');
                        }
                        first = false;
                        sb.append(windowTypeLabel(window.getType())).append(':')
                                .append(root.getPackageName());
                        if (window.isActive()) {
                            sb.append('*');
                        }
                        if (window.isFocused()) {
                            sb.append('^');
                        }
                        root.recycle();
                    }
                }
            }
        } catch (Exception e) {
            sb.append(" err=").append(e.getMessage());
        }
        return sb.toString();
    }

    private static String windowTypeLabel(int type) {
        if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
            return "app";
        }
        if (type == AccessibilityWindowInfo.TYPE_SYSTEM) {
            return "sys";
        }
        if (type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
            return "a11y";
        }
        if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
            return "ime";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
            return "split";
        }
        return "t" + type;
    }

    /**
     * 通过资源ID查找元素
     */
    public AccessibilityNodeInfo findNodeById(String id) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Logger.safeWarn(TAG, "findNodeById: 根节点为空");
                return null;
            }

            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                Logger.safeDebug(TAG, "findNodeById: 找到节点 " + id);
                return nodes.get(0);
            }

            Logger.safeDebug(TAG, "findNodeById: 未找到节点 " + id);
            return null;
        } catch (Exception e) {
            Logger.safeError(TAG, "findNodeById失败: " + id, e);
            return null;
        }
    }

    /**
     * 通过文本查找元素
     */
    public AccessibilityNodeInfo findNodeByText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Logger.safeWarn(TAG, "findNodeByText: 根节点为空");
                return null;
            }

            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                Logger.safeDebug(TAG, "findNodeByText: 找到节点 " + text);
                return nodes.get(0);
            }

            Logger.safeDebug(TAG, "findNodeByText: 未找到节点 " + text);
            return null;
        } catch (Exception e) {
            Logger.safeError(TAG, "findNodeByText失败: " + text, e);
            return null;
        }
    }

    /**
     * 执行点击操作
     */
    public boolean performClick(AccessibilityNodeInfo node) {
        if (node == null) {
            Logger.safeWarn(TAG, "performClick: 节点为空");
            return false;
        }

        try {
            if (node.isClickable()) {
                Logger.safeDebug(TAG, "performClick: 直接点击节点");
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // 向上查找可点击的父节点
            AccessibilityNodeInfo parent = node.getParent();
            int depth = 0;
            while (parent != null && depth < 10) {
                if (parent.isClickable()) {
                    Logger.safeDebug(TAG, "performClick: 通过父节点点击, 深度=" + depth);
                    return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                parent = parent.getParent();
                depth++;
            }

            // 使用手势模拟点击
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                Logger.safeDebug(TAG, "performClick: 使用手势点击 (" + bounds.centerX() + "," + bounds.centerY() + ")");
                return performGestureClick(bounds.centerX(), bounds.centerY());
            }

            Logger.safeWarn(TAG, "performClick: 所有策略都失败");
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "performClick失败", e);
            return false;
        }
    }

    /**
     * 使用手势模拟点击
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean performGestureClick(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 100))
                    .build();

            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Logger.safeError(TAG, "performGestureClick失败", e);
            return false;
        }
    }

    /**
     * 执行输入操作
     */
    public boolean performInput(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            Logger.safeWarn(TAG, "performInput: 节点为空");
            return false;
        }

        try {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);

            Logger.safeDebug(TAG, "performInput: 输入文本 '" + text + "'");
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception e) {
            Logger.safeError(TAG, "performInput失败", e);
            return false;
        }
    }

    /**
     * 执行滑动手势
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean performSwipe(int startX, int startY, int endX, int endY, int duration) {
        try {
            Logger.safeDebug(TAG, "performSwipe: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ") duration=" + duration);

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                    .build();

            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Logger.safeError(TAG, "performSwipe失败", e);
            return false;
        }
    }

    /**
     * 执行返回操作
     */
    public boolean performBack() {
        Logger.safeDebug(TAG, "performBack: 执行返回操作");
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /**
     * 执行Home操作
     */
    public boolean performHome() {
        Logger.safeDebug(TAG, "performHome: 执行Home操作");
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /**
     * 执行最近任务操作
     */
    public boolean performRecents() {
        Logger.safeDebug(TAG, "performRecents: 执行最近任务操作");
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    /**
     * 检测并处理密码保存弹窗
     * 如果检测到"保存密码"相关弹窗，点击"取消"按钮
     * @return true表示检测到并处理了弹窗
     */
    public boolean handlePasswordSaveDialog() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return false;
            }

            // 检测弹窗关键词
            String[] dialogKeywords = {"保存密码", "保存", "自动填充", "记住密码", "Autofill", "Save password"};
            String[] cancelKeywords = {"取消", "拒绝", "以后", "不保存", "Cancel", "Later", "No"};

            boolean foundDialog = false;

            // 检查是否有弹窗文本
            for (String keyword : dialogKeywords) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(keyword);
                if (nodes != null && !nodes.isEmpty()) {
                    Logger.safeInfo(TAG, "检测到密码保存弹窗，关键词: " + keyword);
                    foundDialog = true;
                    break;
                }
            }

            if (!foundDialog) {
                return false;
            }

            // 查找并点击取消按钮
            for (String cancelKeyword : cancelKeywords) {
                List<AccessibilityNodeInfo> cancelNodes = root.findAccessibilityNodeInfosByText(cancelKeyword);
                if (cancelNodes != null && !cancelNodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : cancelNodes) {
                        if (node != null) {
                            Logger.safeInfo(TAG, "点击取消按钮: " + cancelKeyword);
                            boolean clicked = performClick(node);
                            if (clicked) {
                                Logger.safeInfo(TAG, "成功点击取消按钮");
                                return true;
                            }
                        }
                    }
                }
            }

            Logger.safeWarn(TAG, "检测到弹窗但未找到取消按钮");
            return false;

        } catch (Exception e) {
            Logger.safeError(TAG, "处理密码保存弹窗失败", e);
            return false;
        }
    }

    /**
     * 处理启动应用确认弹窗
     * Android 11+ 首次启动其他应用时会弹出确认对话框
     * 自动点击"始终允许"或"允许"按钮
     * @return true表示检测到并处理了弹窗
     */
    public boolean handleLaunchAppDialog() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Logger.safeDebug(TAG, "handleLaunchAppDialog: 根节点为空");
                return false;
            }
            if (!isLikelySystemDialog(root)) {
                Logger.safeDebug(TAG, "非系统弹窗，跳过启动确认处理");
                return false;
            }

            // 检测启动应用弹窗关键词
            String[] dialogKeywords = {"打开", "启动", "运行", "Open", "Launch", "始终允许", "允许", "Always", "Allow"};

            boolean foundDialog = false;

            // 检查是否有弹窗文本
            for (String keyword : dialogKeywords) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(keyword);
                if (nodes != null && !nodes.isEmpty()) {
                    Logger.safeInfo(TAG, "检测到启动应用弹窗，关键词: " + keyword);
                    foundDialog = true;
                    break;
                }
            }

            if (!foundDialog) {
                Logger.safeDebug(TAG, "未检测到启动应用弹窗");
                return false;
            }

            // 优先查找"始终允许"按钮
            String[] allowKeywords = {"始终允许", "始终", "允许", "打开", "确定", "Always allow", "Always", "Allow", "Open", "OK"};

            for (String keyword : allowKeywords) {
                List<AccessibilityNodeInfo> allowNodes = root.findAccessibilityNodeInfosByText(keyword);
                if (allowNodes != null && !allowNodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : allowNodes) {
                        if (node != null && node.isClickable()) {
                            Logger.safeInfo(TAG, "点击允许按钮: " + keyword);
                            boolean clicked = performClick(node);
                            if (clicked) {
                                Logger.safeInfo(TAG, "成功点击允许按钮");
                                return true;
                            }
                        }
                    }
                    // 如果节点本身不可点击，尝试点击父节点
                    for (AccessibilityNodeInfo node : allowNodes) {
                        if (node != null) {
                            boolean clicked = performClick(node);
                            if (clicked) {
                                Logger.safeInfo(TAG, "成功通过父节点点击允许按钮");
                                return true;
                            }
                        }
                    }
                }
            }

            Logger.safeWarn(TAG, "检测到启动弹窗但未找到允许按钮");
            return false;

        } catch (Exception e) {
            Logger.safeError(TAG, "处理启动应用弹窗失败", e);
            return false;
        }
    }

    /**
     * 系统弹窗处理结果：区分录屏弹窗与其它弹窗，决定是否拉回 JSON targetPackage。
     */
    public static final class BlockingDialogResult {
        private final boolean mediaProjectionDismissed;
        private final boolean otherDialogHandled;

        public BlockingDialogResult(boolean mediaProjectionDismissed, boolean otherDialogHandled) {
            this.mediaProjectionDismissed = mediaProjectionDismissed;
            this.otherDialogHandled = otherDialogHandled;
        }

        public boolean isMediaProjectionDismissed() {
            return mediaProjectionDismissed;
        }

        public boolean isAnyHandled() {
            return mediaProjectionDismissed || otherDialogHandled;
        }

        /** 录屏弹窗关掉后 DeviceSDK 常会切到任务 App（如高德），切勿拉回 targetPackage。 */
        public boolean shouldRelaunchTargetApp() {
            return otherDialogHandled && !mediaProjectionDismissed;
        }
    }

    /**
     * 仅关闭录制/投放弹窗（不切回 targetPackage）。
     */
    public boolean dismissMediaProjectionDialogsOnly() {
        Boolean mediaDismissed = runAccessibilityQuery(
                "dismiss_media_projection",
                () -> handleMediaProjectionDialog() || handleMediaProjectionConfirmDialog(),
                false);
        boolean dismissed = mediaDismissed != null && mediaDismissed;
        if (dismissed) {
            Logger.safeInfo(TAG, "截图前已关闭录制/投放弹窗");
            escapeSystemUiIfNeeded();
        }
        return dismissed;
    }

    /**
     * 联通云智专用：权限/启动确认等系统弹窗（不含录制/投放，联通无录屏场景）。
     */
    public boolean handleUnicomBlockingDialogs() {
        boolean handled = false;
        if (handleLaunchAppDialog()) {
            handled = true;
        }
        if (handlePasswordSaveDialog()) {
            handled = true;
        }
        if (handlePermissionDialog()) {
            handled = true;
        }
        if (handleOverlayPermissionDialog()) {
            handled = true;
        }
        if (handled) {
            Logger.safeInfo(TAG, "联通: 已处理系统遮挡弹窗");
        }
        return handled;
    }

    /**
     * DeviceSDK 专用：处理遮挡弹窗。
     * 注意：不要自动点掉「录制/投放」——DeviceSDK GUI Agent 依赖 MediaProjection 截屏控屏；
     * 以前点「取消」会导致 GUI 任务执行失败（爱奇艺等跨 App 操控起不来）。
     */
    public BlockingDialogResult handleDeviceSdkBlockingDialogs() {
        boolean mediaDismissed = false;
        // 若误点到「立即开始」确认框以外的系统遮挡，仍处理；录屏授权留给 DeviceSDK 自己确认
        boolean other = false;
        if (handleLaunchAppDialog()) {
            other = true;
        }
        // 密码保存弹窗只关掉即可，不要触发 targetApp relaunch（relaunch 会进 ConfigActivity）
        handlePasswordSaveDialog();
        if (handlePermissionDialog()) {
            other = true;
        }
        if (handleOverlayPermissionDialog()) {
            other = true;
        }
        if (mediaDismissed || other) {
            Logger.safeInfo(TAG, "DeviceSDK: 已处理系统遮挡弹窗 media=" + mediaDismissed + " other=" + other);
        }
        return new BlockingDialogResult(mediaDismissed, other);
    }

    /**
     * 处理运行时权限弹窗（位置、存储、电话等）。
     */
    public boolean handlePermissionDialog() {
        String[] dialogKeywords = {
                "需要以下权限", "需要权限", "申请权限", "权限申请",
                "想要", "访问您的", "Permission", "would like to"
        };
        String[] allowKeywords = {
                "仅在使用中允许", "仅在使用该应用时允许", "使用时允许", "使用应用时允许",
                "始终允许", "仅本次", "允许", "同意", "确定",
                "Allow", "While using", "Only this time", "OK"
        };
        return tryDismissSystemDialog(dialogKeywords, allowKeywords, "权限弹窗");
    }

    /**
     * 处理悬浮窗/显示在其他应用上层权限弹窗。
     */
    public boolean handleOverlayPermissionDialog() {
        String[] dialogKeywords = {
                "显示在其他应用的上层", "悬浮窗", "绘制在其他应用上层",
                "在其他应用上层显示", "Display over other apps", "Appear on top"
        };
        String[] allowKeywords = {
                "允许", "去开启", "开启", "确定", "Allow", "Turn on"
        };
        return tryDismissSystemDialog(dialogKeywords, allowKeywords, "悬浮窗权限弹窗");
    }

    private static final String[] MEDIA_PROJECTION_DIALOG_KEYWORDS = {
            "录制或投放", "录制或投射", "共享屏幕", "整个屏幕", "共享一个应用",
            "recording or casting", "Entire screen", "Share an app", "cast"
    };

    private static final String[] ENTIRE_SCREEN_OPTIONS = {
            "共享屏幕", "整个屏幕", "全屏", "Entire screen", "A full screen"
    };

    private static final String[] MEDIA_PROJECTION_CONFIRM_KEYWORDS = {
            "立即开始", "将开始截取", "开始录制", "Start now", "Start recording"
    };

    private static final long A11Y_QUERY_TIMEOUT_MS = 2500;

    private <T> T runAccessibilityQuery(String label, Callable<T> task, T fallback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<T> future = executor.submit(task);
        try {
            return future.get(A11Y_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            Logger.safeWarn(TAG, "无障碍查询超时(" + A11Y_QUERY_TIMEOUT_MS + "ms): " + label);
            ScreenshotDiagLogger.warn("无障碍查询超时: " + label);
            return fallback;
        } catch (Exception e) {
            Logger.safeError(TAG, "无障碍查询失败: " + label, e);
            return fallback;
        } finally {
            executor.shutdownNow();
        }
    }

    /** 当前界面是否显示录制/投放或确认弹窗（诊断用） */
    public boolean isMediaProjectionDialogVisible() {
        Boolean visible = runAccessibilityQuery(
                "media_dialog_visible",
                this::isMediaProjectionDialogVisibleUnsafe,
                false);
        return visible != null && visible;
    }

    private boolean isMediaProjectionDialogVisibleUnsafe() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        for (String keyword : MEDIA_PROJECTION_DIALOG_KEYWORDS) {
            if (hasTextInTree(root, keyword)) {
                return true;
            }
        }
        for (String keyword : MEDIA_PROJECTION_CONFIRM_KEYWORDS) {
            if (hasTextInTree(root, keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 当前前台窗口包名（诊断用，带查询超时） */
    public String getForegroundPackageName() {
        return runAccessibilityQuery(
                "foreground_pkg",
                this::getForegroundPackageNameUnsafe,
                null);
    }

    private String getForegroundPackageNameUnsafe() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return null;
        }
        return root.getPackageName().toString();
    }

    /**
     * 录屏弹窗关闭后若仍停留在 systemui，按 BACK 退出覆盖层。
     */
    public boolean escapeSystemUiIfNeeded() {
        String fg = getForegroundPackageName();
        if (fg == null || !fg.toLowerCase().contains("systemui")) {
            return false;
        }
        Logger.safeInfo(TAG, "仍在 systemui 界面，执行 BACK 关闭录屏相关层");
        performBack();
        sleepQuietly(400);
        return true;
    }

    /** 截图诊断：前台包名 + 录屏弹窗是否可见 */
    public String describeScreenForDiag() {
        String fg = getForegroundPackageName();
        boolean media = isMediaProjectionDialogVisible();
        return "foreground=" + (fg != null ? fg : "unknown")
                + " mediaDialog=" + media;
    }

    /**
     * 录屏授权只点「共享屏幕 / 整个屏幕」，再点「立即开始」。
     * 不点「共享一个应用」，否则第三方界面截图是黑的。
     */
    public boolean acceptEntireScreenShare() {
        Boolean accepted = runAccessibilityQuery(
                "accept_entire_screen",
                this::acceptEntireScreenShareUnsafe,
                false);
        return accepted != null && accepted;
    }

    private boolean acceptEntireScreenShareUnsafe() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !isMediaProjectionTree(root)) {
            return false;
        }
        for (String option : ENTIRE_SCREEN_OPTIONS) {
            if (clickNodesByText(root, option)) {
                Logger.safeInfo(TAG, "录屏已选择共享屏幕: " + option);
                root = getRootInActiveWindow();
                break;
            }
        }
        if (root == null) {
            return false;
        }
        for (String keyword : MEDIA_PROJECTION_CONFIRM_KEYWORDS) {
            if (clickNodesByText(root, keyword)) {
                Logger.safeInfo(TAG, "录屏已确认: " + keyword);
                return true;
            }
        }
        return false;
    }

    private boolean isMediaProjectionTree(AccessibilityNodeInfo root) {
        for (String keyword : MEDIA_PROJECTION_DIALOG_KEYWORDS) {
            if (hasTextInTree(root, keyword)) {
                return true;
            }
        }
        for (String keyword : MEDIA_PROJECTION_CONFIRM_KEYWORDS) {
            if (hasTextInTree(root, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理「录制/投放」弹窗：选共享屏幕，不取消。
     */
    public boolean handleMediaProjectionDialog() {
        return acceptEntireScreenShare();
    }

    /**
     * 处理「立即开始」确认。
     */
    public boolean handleMediaProjectionConfirmDialog() {
        return acceptEntireScreenShare();
    }

    private boolean isLikelySystemDialog(AccessibilityNodeInfo root) {
        if (root == null) {
            return false;
        }
        CharSequence pkgSeq = root.getPackageName();
        if (pkgSeq == null) {
            return false;
        }
        String pkg = pkgSeq.toString().toLowerCase();
        return pkg.contains("permission")
                || pkg.contains("packageinstaller")
                || pkg.contains("systemui")
                || pkg.equals("android")
                || pkg.contains("settings")
                || pkg.contains("securitycenter")
                || pkg.contains("safecenter");
    }

    private boolean hasTextInTree(AccessibilityNodeInfo root, String keyword) {
        if (root == null || keyword == null || keyword.isEmpty()) {
            return false;
        }
        if (nodeContainsKeyword(root, keyword)) {
            return true;
        }
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null && hasTextInTree(child, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean nodeContainsKeyword(AccessibilityNodeInfo node, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        CharSequence text = node.getText();
        if (text != null && text.toString().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        CharSequence desc = node.getContentDescription();
        return desc != null && desc.toString().toLowerCase().contains(lowerKeyword);
    }

    private boolean clickNodesByTextDeep(AccessibilityNodeInfo root, String keyword) {
        if (root == null) {
            return false;
        }
        CharSequence text = root.getText();
        CharSequence desc = root.getContentDescription();
        if ((text != null && keyword.equals(text.toString()))
                || (desc != null && keyword.equals(desc.toString()))) {
            if (performClick(root)) {
                return true;
            }
        }
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null && clickNodesByTextDeep(child, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickNodesByText(AccessibilityNodeInfo root, String keyword) {
        if (clickNodesByTextDeep(root, keyword)) {
            return true;
        }
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(keyword);
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (AccessibilityNodeInfo node : nodes) {
            if (node != null && performClick(node)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryDismissSystemDialog(
            String[] dialogKeywords, String[] buttonKeywords, String logTag) {
        return tryDismissDialog(dialogKeywords, buttonKeywords, logTag, true);
    }

    private boolean tryDismissDialog(
            String[] dialogKeywords, String[] buttonKeywords, String logTag,
            boolean requireSystemDialog) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return false;
            }
            if (requireSystemDialog && !isLikelySystemDialog(root)) {
                return false;
            }

            boolean foundDialog = false;
            for (String keyword : dialogKeywords) {
                if (hasTextInTree(root, keyword)) {
                    foundDialog = true;
                    break;
                }
            }
            if (!foundDialog) {
                return false;
            }

            Logger.safeInfo(TAG, "检测到" + logTag);
            for (String keyword : buttonKeywords) {
                if (clickNodesByText(root, keyword)) {
                    Logger.safeInfo(TAG, logTag + "已点击: " + keyword);
                    return true;
                }
            }
            Logger.safeWarn(TAG, logTag + "未找到可点击按钮");
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "处理" + logTag + "失败", e);
            return false;
        }
    }

    /**
     * 检测指定包名的窗口是否处于活动状态（比进程 IMPORTANCE 更准确）
     */
    public boolean isPackageWindowActive(String targetPackage) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null || !window.isActive()) {
                        continue;
                    }
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && root.getPackageName() != null
                            && targetPackage.contentEquals(root.getPackageName().toString())) {
                        return true;
                    }
                }
            }
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        return root != null && root.getPackageName() != null
                && targetPackage.contentEquals(root.getPackageName().toString());
    }

    /**
     * 通过最近任务恢复应用到前台
     * 使用 getWindows 遍历多任务 UI，优先匹配 TestApp 卡片标题
     */
    public boolean bringAppToFrontViaRecents(String targetPackage) {
        try {
            Logger.safeInfo(TAG, "通过最近任务恢复应用: " + targetPackage);
            RecentsSearchCriteria criteria = buildRecentsSearchCriteria(targetPackage);

            if (!isRecentsScreenActive()) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            } else {
                Logger.safeInfo(TAG, "已在最近任务界面，不再重复打开");
            }

            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Thread.sleep(attempt == 0 ? 800 : 400);
                } catch (InterruptedException ignored) {
                }

                if (findAndClickTestAppInRecents(criteria)) {
                    sleepQuietly(400);
                    if (MainActivity.isActivityVisible()) {
                        Logger.safeInfo(TAG, "MainActivity 已回到前台");
                        return true;
                    }
                    Logger.safeWarn(TAG, "点击 TestApp 卡片后 MainActivity 仍未可见");
                }
            }

            Logger.safeWarn(TAG, "在最近任务中未找到testapp: " + targetPackage);
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "通过最近任务恢复应用失败", e);
            return false;
        }
    }

    /**
     * 是否处于最近任务/多任务界面
     */
    public boolean isRecentsScreenActive() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return false;
        }
        String pkg = root.getPackageName().toString();
        return pkg.contains("systemui")
                || pkg.contains("recents")
                || pkg.contains("quickstep");
    }

    /**
     * 用 BACK 关闭最近任务（不用 HOME，避免直接回桌面）
     */
    public void closeRecentsIfVisible() {
        if (!isRecentsScreenActive()) {
            return;
        }
        Logger.safeInfo(TAG, "关闭最近任务界面(BACK)");
        performGlobalAction(GLOBAL_ACTION_BACK);
        sleepQuietly(300);
    }

    private static final class RecentsSearchCriteria {
        final String targetPackage;
        final String appLabel;

        RecentsSearchCriteria(String targetPackage, String appLabel) {
            this.targetPackage = targetPackage;
            this.appLabel = appLabel;
        }
    }

    private RecentsSearchCriteria buildRecentsSearchCriteria(String targetPackage) {
        String appLabel = "TestApp";
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(targetPackage, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null && label.length() > 0) {
                appLabel = label.toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            Logger.safeWarn(TAG, "无法解析应用名称: " + targetPackage);
        }
        return new RecentsSearchCriteria(targetPackage, appLabel);
    }

    private boolean findAndClickTestAppInRecents(RecentsSearchCriteria criteria) {
        List<AccessibilityNodeInfo> roots = collectRecentsRoots();
        if (roots.isEmpty()) {
            Logger.safeWarn(TAG, "最近任务界面根节点为空");
            return false;
        }

        Logger.safeInfo(TAG, "最近任务窗口根节点数: " + roots.size());

        for (AccessibilityNodeInfo root : roots) {
            if (root == null) {
                continue;
            }
            List<AccessibilityNodeInfo> labelNodes = root.findAccessibilityNodeInfosByText(criteria.appLabel);
            if (labelNodes != null) {
                for (AccessibilityNodeInfo node : labelNodes) {
                    if (node != null && matchesRecentsTestAppNode(node, criteria, true)
                            && clickRecentsTaskNode(node)) {
                        return true;
                    }
                }
            }
        }

        for (AccessibilityNodeInfo root : roots) {
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo targetNode = findRecentsTestAppNodeRecursive(root, criteria, true);
            if (targetNode != null && clickRecentsTaskNode(targetNode)) {
                return true;
            }
        }

        for (AccessibilityNodeInfo root : roots) {
            if (root == null) {
                continue;
            }
            AccessibilityNodeInfo targetNode = findRecentsTestAppNodeRecursive(root, criteria, false);
            if (targetNode != null && clickRecentsTaskNode(targetNode)) {
                return true;
            }
        }

        return false;
    }

    private List<AccessibilityNodeInfo> collectRecentsRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        if (activeRoot != null) {
            roots.add(activeRoot);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) {
                        continue;
                    }
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null && !roots.contains(root)) {
                        roots.add(root);
                    }
                }
            }
        }

        return roots;
    }

    private boolean clickRecentsTaskNode(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        AccessibilityNodeInfo tapTarget = findRecentsCardTapTarget(node);
        Rect bounds = new Rect();
        tapTarget.getBoundsInScreen(bounds);
        Logger.safeInfo(TAG, "准备点击 TestApp 任务卡片: text=" + text + ", desc=" + desc
                + ", bounds=" + bounds.width() + "x" + bounds.height());

        if (isRecentsBottomDangerZone(bounds)) {
            Logger.safeWarn(TAG, "跳过底部危险区域点击，避免误触清除全部/回桌面");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && bounds.width() > 0 && bounds.height() > 0) {
            if (performGestureClick(bounds.centerX(), bounds.centerY())) {
                Logger.safeInfo(TAG, "手势点击 TestApp 任务卡片中心");
                return true;
            }
        }

        if (performClick(tapTarget)) {
            Logger.safeInfo(TAG, "成功点击 TestApp 任务卡片");
            return true;
        }

        AccessibilityNodeInfo parent = tapTarget.getParent();
        int depth = 0;
        while (parent != null && depth < 12) {
            if (performClick(parent)) {
                Logger.safeInfo(TAG, "成功通过父节点点击 TestApp 任务卡片, 深度=" + depth);
                return true;
            }
            parent = parent.getParent();
            depth++;
        }

        Logger.safeWarn(TAG, "TestApp 任务卡片点击失败");
        return false;
    }

    private AccessibilityNodeInfo findRecentsCardTapTarget(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo best = node;
        int bestArea = 0;
        AccessibilityNodeInfo current = node;

        for (int depth = 0; depth < 15 && current != null; depth++) {
            Rect bounds = new Rect();
            current.getBoundsInScreen(bounds);
            int area = bounds.width() * bounds.height();
            if (bounds.width() >= 240 && bounds.height() >= 300 && area > bestArea) {
                bestArea = area;
                best = current;
            }
            current = current.getParent();
        }

        return best;
    }

    private boolean isRecentsBottomDangerZone(Rect bounds) {
        if (screenHeight <= 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            screenHeight = metrics.heightPixels;
        }
        if (screenHeight <= 0) {
            return false;
        }
        return bounds.centerY() > screenHeight * 0.82f;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private AccessibilityNodeInfo findRecentsTestAppNodeRecursive(AccessibilityNodeInfo node,
                                                                  RecentsSearchCriteria criteria,
                                                                  boolean strict) {
        if (node == null) {
            return null;
        }

        if (matchesRecentsTestAppNode(node, criteria, strict)) {
            CharSequence text = node.getText();
            CharSequence contentDesc = node.getContentDescription();
            String viewId = node.getViewIdResourceName();
            Logger.safeInfo(TAG, "匹配 TestApp 节点: text=" + text + ", desc=" + contentDesc + ", viewId=" + viewId);
            return node;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findRecentsTestAppNodeRecursive(child, criteria, strict);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private boolean matchesRecentsTestAppNode(AccessibilityNodeInfo node,
                                              RecentsSearchCriteria criteria,
                                              boolean strict) {
        if (node == null) {
            return false;
        }

        CharSequence text = node.getText();
        CharSequence contentDesc = node.getContentDescription();
        String viewId = node.getViewIdResourceName();

        String nodeText = text != null ? text.toString().trim() : "";
        String nodeDesc = contentDesc != null ? contentDesc.toString().trim() : "";
        String nodeViewId = viewId != null ? viewId : "";
        String combined = (nodeText + " " + nodeDesc + " " + nodeViewId).toLowerCase();
        String appLabelLower = criteria.appLabel.toLowerCase();

        if (criteria.appLabel.equalsIgnoreCase(nodeText) || criteria.appLabel.equalsIgnoreCase(nodeDesc)) {
            return true;
        }

        if (strict) {
            if (combined.contains(appLabelLower)) {
                return true;
            }
            if (combined.contains(criteria.targetPackage.toLowerCase())) {
                return true;
            }
            if (combined.contains("testapp")) {
                return true;
            }
            if (combined.contains("测试控制器")) {
                return true;
            }
            return false;
        }

        return combined.contains("testapp") || nodeViewId.toLowerCase().contains("testapp");
    }

    /**
     * 递归查找可点击节点
     */
    private void findClickableNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> result) {
        if (node == null) return;

        if (node.isClickable()) {
            result.add(node);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findClickableNodes(child, result);
            }
        }
    }

    /**
     * 系统要求两次 Accessibility takeScreenshot 至少间隔约 333ms，
     * 过短会失败：ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3。
     */
    private static final long A11Y_SCREENSHOT_MIN_INTERVAL_MS = 400L;
    private static final Object a11yScreenshotLock = new Object();
    private static volatile long lastA11yScreenshotAtMs;

    private static void awaitA11yScreenshotInterval() {
        synchronized (a11yScreenshotLock) {
            long elapsed = System.currentTimeMillis() - lastA11yScreenshotAtMs;
            if (lastA11yScreenshotAtMs > 0 && elapsed < A11Y_SCREENSHOT_MIN_INTERVAL_MS) {
                long waitMs = A11Y_SCREENSHOT_MIN_INTERVAL_MS - elapsed;
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void markA11yScreenshotAttempt() {
        lastA11yScreenshotAtMs = System.currentTimeMillis();
    }

    private static String describeScreenshotError(int errorCode) {
        switch (errorCode) {
            case 1:
                return "INTERNAL_ERROR(内部错误)";
            case 2:
                return "NO_ACCESSIBILITY_ACCESS(无障碍无截图权限)";
            case 3:
                return "INTERVAL_TIME_SHORT(距上次截图间隔过短，系统要求≥333ms)";
            case 4:
                return "INVALID_DISPLAY(无效display)";
            case 5:
                return "INVALID_WINDOW(无效窗口)";
            case 6:
                return "SECURE_WINDOW(安全窗口禁止截图)";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 截图并保存到文件
     * 使用AccessibilityService的takeScreenshot API (API 30+)
     *
     * 注意：需要无障碍服务有截图权限 (android:canTakeScreenshot="true")
     *
     * @param outputFile 输出文件
     * @return true表示截图成功
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean takeScreenshot(final File outputFile) {
        try {
            awaitA11yScreenshotInterval();
            markA11yScreenshotAttempt();

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] result = {false};

            // Android 11+ (API 30+) 使用takeScreenshot API
            // 参数: displayId, Executor, TakeScreenshotCallback
            java.util.concurrent.Executor executor = Runnable::run;
            int displayId = android.view.Display.DEFAULT_DISPLAY;

            takeScreenshot(displayId, executor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        // 获取HardwareBuffer并转换为Bitmap
                        android.hardware.HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null);

                        if (bitmap == null) {
                            Logger.safeError(TAG, "无法从HardwareBuffer创建Bitmap");
                            hardwareBuffer.close();
                            latch.countDown();
                            return;
                        }

                        // 转换为软件Bitmap
                        Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        bitmap.recycle();

                        // 保存到文件
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                        softwareBitmap.recycle();

                        result[0] = true;
                        Logger.safeInfo(TAG, "截图成功: " + outputFile.getAbsolutePath() + ", 大小: " + outputFile.length());
                    } catch (Exception e) {
                        Logger.safeError(TAG, "保存截图失败", e);
                    } finally {
                        screenshotResult.getHardwareBuffer().close();
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Logger.safeError(TAG, "截图失败: errorCode=" + errorCode
                            + " " + describeScreenshotError(errorCode));
                    latch.countDown();
                }
            });

            // 等待截图完成（最多5秒）
            return latch.await(5, TimeUnit.SECONDS) && result[0];

        } catch (Exception e) {
            Logger.safeError(TAG, "takeScreenshot失败", e);
            return false;
        }
    }

    private static final long A11Y_SCREENSHOT_TIMEOUT_SEC = 8;

    /** DeviceSDK 专用：带超时与诊断的截图 */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public boolean takeScreenshot(final File outputFile, final String stepName, final String attempt) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<Boolean> future = worker.submit(() ->
                takeScreenshotInternal(outputFile, stepName, attempt));
        try {
            Boolean ok = future.get(A11Y_SCREENSHOT_TIMEOUT_SEC, TimeUnit.SECONDS);
            return ok != null && ok;
        } catch (TimeoutException e) {
            future.cancel(true);
            Logger.safeWarn(TAG, "a11y截图超时(" + A11Y_SCREENSHOT_TIMEOUT_SEC + "s): " + stepName);
            ScreenshotDiagLogger.capture(stepName, attempt + "_a11y", false, 0,
                    "超时" + A11Y_SCREENSHOT_TIMEOUT_SEC + "s");
            return false;
        } catch (Exception e) {
            Logger.safeError(TAG, "takeScreenshot失败", e);
            ScreenshotDiagLogger.error("takeScreenshot异常 step=" + stepName, e);
            return false;
        } finally {
            worker.shutdownNow();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean takeScreenshotInternal(final File outputFile, final String stepName,
            final String attempt) {
        try {
            awaitA11yScreenshotInterval();
            markA11yScreenshotAttempt();

            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] result = {false};

            java.util.concurrent.Executor executor = Runnable::run;
            int displayId = android.view.Display.DEFAULT_DISPLAY;

            takeScreenshot(displayId, executor, new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    try {
                        android.hardware.HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                        Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null);

                        if (bitmap == null) {
                            Logger.safeError(TAG, "无法从HardwareBuffer创建Bitmap");
                            hardwareBuffer.close();
                            latch.countDown();
                            return;
                        }

                        Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                        bitmap.recycle();

                        FileOutputStream fos = new FileOutputStream(outputFile);
                        softwareBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                        fos.close();
                        softwareBitmap.recycle();

                        result[0] = true;
                        Logger.safeInfo(TAG, "截图成功: " + outputFile.getAbsolutePath() + ", 大小: " + outputFile.length());
                    } catch (Exception e) {
                        Logger.safeError(TAG, "保存截图失败", e);
                    } finally {
                        screenshotResult.getHardwareBuffer().close();
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(int errorCode) {
                    Logger.safeError(TAG, "截图失败: errorCode=" + errorCode
                            + " " + describeScreenshotError(errorCode)
                            + " step=" + stepName + " attempt=" + attempt);
                    latch.countDown();
                }
            });

            return latch.await(A11Y_SCREENSHOT_TIMEOUT_SEC, TimeUnit.SECONDS) && result[0];

        } catch (Exception e) {
            Logger.safeError(TAG, "takeScreenshot失败", e);
            return false;
        }
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }
}