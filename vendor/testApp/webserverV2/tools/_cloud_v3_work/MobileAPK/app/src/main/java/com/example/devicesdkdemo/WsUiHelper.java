package com.example.devicesdkdemo;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl.AccessibilityManagerImpl;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.ClickResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨 App 收尾拉回。小米上普通/自调 startActivity 常被标 invisible launch abort
 *（后台弹出界面 MIUIOP 10020/10021）。可靠顺序：
 * HOME → a11y startActivity → 桌面点图标 → 最近任务点卡片 → 全屏通知。
 */
public final class WsUiHelper {
    private static final String TAG = "WsUiHelper";
    private static final String PKG = "com.example.devicesdkdemo";
    private static final String CONFIG_ACTIVITY = "com.example.devicesdkdemo.ConfigActivity";
    private static final String QA_ACTIVITY = "com.example.devicesdkdemo.QAActivity";
    private static final String ID_CONFIRM = PKG + ":id/btn_confirm";
    private static final String ID_LOGIN = PKG + ":id/btn_login";
    private static final String ID_QA = PKG + ":id/btn_qa";
    private static final String ID_INPUT = PKG + ":id/et_input";
    private static final String ID_SEND = PKG + ":id/btn_send";
    private static final String ID_STOP_TASK = PKG + ":id/btn_stop_task";
    private static final String ID_SESSION_MANAGE = PKG + ":id/btn_session_manage";
    private static final String ID_ALERT_BUTTON1 = "android:id/button1";
    private static final Uri CAPTURE_URI =
            Uri.parse("content://" + GuiCaptureProvider.AUTHORITY);
    private static final String[] LAUNCHER_LABELS = {
            "DeviceSDK V2 Demo", "DeviceSDK", "Demo", "devicesdkdemo"
    };

    private WsUiHelper() {
    }

    private static AccessibilityManagerImpl a11y() {
        return AccessibilityManagerImpl.getInstance();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void launchDemo(Context ctx) {
        final Context app = ctx.getApplicationContext();
        // 已在 QA 聊天页：不要拉到 Config，否则会话前置点不到「会话 ▼」
        if (isDemoForeground() && findCenterById(ID_INPUT) != null) {
            Log.i(TAG, "launchDemo: already on QA chat, skip pull");
            return;
        }
        pullUntilForeground(app, "launchDemo");
        sleep(400);
        // 不在聊天页：配置页滚动进测试 → 登录 → QA
        if (!ensureQaChatPage()) {
            Log.w(TAG, "launchDemo: still not on QA after openQaIfNeeded");
        }
    }

    /** 用例/GUI 结束后一键直达 QA 聊天页（仅 startActivity QAActivity）。 */
    public static void returnAfterCase(Context ctx) {
        returnToQaChatDirect(ctx);
    }

    /** 一键直达 QA 聊天页，不走 Config→Login→QA 导航。 */
    public static void returnToQaChatDirect(Context ctx) {
        final Context app = ctx.getApplicationContext();
        ensureForegroundService(app);
        if (isDemoForeground() && findCenterById(ID_INPUT) != null) {
            Log.i(TAG, "returnToQaChatDirect: already on QA chat, skip");
            return;
        }
        Log.i(TAG, "returnToQaChatDirect: startActivity QAActivity");
        bringQaActivityToFront(app);
    }

    private static void bringQaActivityToFront(Context app) {
        AccessibilityManagerImpl acc = a11y();
        Runnable start = () -> {
            try {
                Intent intent = new Intent();
                intent.setClassName(PKG, QA_ACTIVITY);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if (acc != null) {
                    acc.startActivity(intent);
                    Log.i(TAG, "bringQaActivityToFront via a11y");
                } else {
                    app.startActivity(intent);
                    Log.i(TAG, "bringQaActivityToFront via app context");
                }
            } catch (Exception e) {
                Log.w(TAG, "bringQaActivityToFront: " + e.getMessage());
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start.run();
        } else {
            new Handler(Looper.getMainLooper()).post(start);
            sleep(300);
            if (acc != null) {
                try {
                    start.run();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 不经 HOME：a11y / Provider 直接把 Demo 拉到前台。 */
    private static boolean pullDirect(Context app, String reason) {
        Log.i(TAG, "pullDirect reason=" + reason);
        bringViaAccessibilityService(false);
        sleep(500);
        if (isDemoForeground()) {
            return true;
        }
        callBringFrontProvider(app);
        sleep(400);
        if (isDemoForeground()) {
            return true;
        }
        bringViaAccessibilityService(true);
        sleep(500);
        return isDemoForeground();
    }

    /** @return true if Demo package is foreground */
    private static boolean pullUntilForeground(Context app, String reason) {
        Log.i(TAG, "pullUntilForeground reason=" + reason);
        // 1) 无障碍服务 startActivity（软 flags，避免 CLEAR_TASK 竞态 abort）
        bringViaAccessibilityService(false);
        sleep(600);
        if (isDemoForeground()) {
            return true;
        }

        // 2) Provider（同进程；MIUI 后台弹出允许时有效）
        callBringFrontProvider(app);
        sleep(500);
        if (isDemoForeground()) {
            return true;
        }

        // 3) 桌面点图标（不依赖后台弹出权限）
        if (clickDemoOnLauncher()) {
            sleep(800);
            if (isDemoForeground()) {
                Log.i(TAG, "pulled via launcher icon");
                return true;
            }
        }

        // 4) 最近任务点卡片
        if (clickDemoInRecents()) {
            sleep(800);
            if (isDemoForeground()) {
                Log.i(TAG, "pulled via recents");
                return true;
            }
        }

        // 5) 全屏通知 + 再试 a11y（带 CLEAR_TASK）
        launchViaFullscreenNotification(app);
        sleep(500);
        bringViaAccessibilityService(true);
        sleep(700);
        if (isDemoForeground()) {
            return true;
        }

        // 最后重试：再 HOME + 点图标
        AccessibilityManagerImpl acc = a11y();
        if (acc != null) {
            try {
                acc.goHome();
            } catch (Exception ignored) {
            }
            sleep(600);
            if (clickDemoOnLauncher()) {
                sleep(800);
            }
        }
        return isDemoForeground();
    }

    private static boolean isDemoForeground() {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return false;
        }
        try {
            return PKG.equals(acc.getForegroundPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private static void openQaIfNeeded() {
        ensureQaChatPage();
    }

    /**
     * 若不在 QA 聊天页，则走配置页 → 进入测试 → 登录 → QA。
     * @return true 表示 et_input 已可见（可点「会话 ▼」）
     */
    public static boolean ensureQaChatPage() {
        if (findCenterById(ID_INPUT) != null) {
            return true;
        }
        if (!isDemoForeground()) {
            Log.w(TAG, "ensureQaChatPage skipped: not on Demo");
            return false;
        }
        Log.i(TAG, "ensureQaChatPage: not on chat, navigate Config→Login→QA");
        // 配置页：上滑直到「进入测试」可见
        for (int i = 0; i < 6; i++) {
            if (findCenterById(ID_CONFIRM) != null) {
                break;
            }
            if (findCenterById(ID_INPUT) != null) {
                return true;
            }
            if (findCenterById(ID_LOGIN) != null || findCenterById(ID_QA) != null) {
                break;
            }
            swipeUp();
            sleep(350);
        }
        if (findCenterById(ID_CONFIRM) != null) {
            tapById(ID_CONFIRM, 2);
            sleep(500);
            dismissDialogs();
        }
        if (findCenterById(ID_INPUT) != null) {
            return true;
        }
        dismissDialogs();
        if (tapById(ID_LOGIN, 3)) {
            sleep(900);
            dismissDialogs();
        }
        if (findCenterById(ID_INPUT) != null) {
            return true;
        }
        if (tapById(ID_QA, 4)) {
            sleep(700);
        }
        boolean ok = findCenterById(ID_INPUT) != null;
        if (!ok) {
            Log.w(TAG, "ensureQaChatPage: et_input still missing");
        } else {
            Log.i(TAG, "ensureQaChatPage: reached QA chat");
        }
        return ok;
    }

    private static void ensureForegroundService(Context app) {
        try {
            Intent svc = new Intent(app, DemoForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(svc);
            } else {
                app.startService(svc);
            }
        } catch (Exception e) {
            Log.w(TAG, "start FGS: " + e.getMessage());
        }
    }

    private static void bringViaAccessibilityService(boolean hardClear) {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            Log.w(TAG, "bringViaA11y: service null");
            return;
        }
        Runnable start = () -> {
            try {
                Intent intent = new Intent();
                intent.setClassName(PKG, QA_ACTIVITY);
                int flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP;
                if (hardClear) {
                    flags |= Intent.FLAG_ACTIVITY_CLEAR_TASK;
                }
                intent.addFlags(flags);
                acc.startActivity(intent);
                Log.i(TAG, "bringViaA11y QAActivity hardClear=" + hardClear);
            } catch (Exception e) {
                Log.w(TAG, "bringViaA11y: " + e.getMessage());
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start.run();
        } else {
            new Handler(Looper.getMainLooper()).post(start);
            sleep(300);
            // 再直接调一次（AccessibilityService 允许）
            try {
                start.run();
            } catch (Exception ignored) {
            }
        }
    }

    private static void callBringFrontProvider(Context app) {
        try {
            Bundle result = app.getContentResolver().call(
                    CAPTURE_URI,
                    GuiCaptureProvider.METHOD_BRING_FRONT,
                    null,
                    null);
            boolean ok = result != null
                    && result.getBoolean(GuiCaptureProvider.EXTRA_OK, false);
            Log.i(TAG, "provider bring_front ok=" + ok);
        } catch (Exception e) {
            Log.w(TAG, "provider bring_front: " + e.getMessage());
        }
    }

    /** 在桌面找 Demo 图标并点击（不依赖后台弹出）。 */
    private static boolean clickDemoOnLauncher() {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return false;
        }
        for (String label : LAUNCHER_LABELS) {
            if (tapByText(label, 2)) {
                Log.i(TAG, "clickDemoOnLauncher hit text=" + label);
                return true;
            }
            int[] center = findCenterByTextContains(label);
            if (center != null) {
                ClickResult result = acc.click(center[0], center[1], null);
                if (result != null && result.isSuccess()) {
                    Log.i(TAG, "clickDemoOnLauncher gesture text=" + label);
                    return true;
                }
            }
        }
        Log.w(TAG, "clickDemoOnLauncher: icon not found");
        return false;
    }

    /** 打开最近任务并点击 Demo 卡片。 */
    private static boolean clickDemoInRecents() {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return false;
        }
        try {
            boolean ok = acc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            Log.i(TAG, "GLOBAL_ACTION_RECENTS ok=" + ok);
        } catch (Exception e) {
            Log.w(TAG, "RECENTS: " + e.getMessage());
            return false;
        }
        sleep(900);
        for (String label : LAUNCHER_LABELS) {
            if (tapByText(label, 2)) {
                Log.i(TAG, "clickDemoInRecents hit=" + label);
                return true;
            }
            int[] center = findCenterByTextContains(label);
            if (center != null) {
                ClickResult result = acc.click(center[0], center[1], null);
                if (result != null && result.isSuccess()) {
                    Log.i(TAG, "clickDemoInRecents gesture=" + label);
                    return true;
                }
            }
        }
        Log.w(TAG, "clickDemoInRecents: card not found");
        return false;
    }

    private static void launchViaFullscreenNotification(Context app) {
        try {
            String channelId = "demo_pull_channel";
            NotificationManager nm =
                    (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                        channelId, "Demo 拉回", NotificationManager.IMPORTANCE_HIGH);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
            Intent intent = new Intent(app, QAActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    app,
                    (int) (System.currentTimeMillis() & 0xffff),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(app, channelId);
            } else {
                builder = new Notification.Builder(app);
            }
            Notification notification = builder
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("用例结束")
                    .setContentText("返回 DeviceSDK Demo")
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setCategory(Notification.CATEGORY_CALL)
                    .build();
            int id = 0x0D3E01;
            nm.cancel(id);
            nm.notify(id, notification);
            Log.i(TAG, "fullscreen notification posted");
            // 稍后清掉，避免打扰
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    nm.cancel(id);
                } catch (Exception ignored) {
                }
            }, 4000L);
        } catch (Exception e) {
            Log.w(TAG, "fullscreen notification: " + e.getMessage());
        }
    }

    public static boolean tapStopTaskButton() {
        return tapById(ID_STOP_TASK, 4);
    }

    public static boolean tapById(String resourceId, int retries) {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return false;
        }
        for (int i = 0; i < Math.max(1, retries); i++) {
            int[] center = findCenterById(resourceId);
            if (center != null) {
                ClickResult result = acc.click(center[0], center[1], null);
                if (result != null && result.isSuccess()) {
                    return true;
                }
            }
            sleep(250);
        }
        return false;
    }

    public static boolean tapByText(String text, int retries) {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null || text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < Math.max(1, retries); i++) {
            int[] center = findCenterByText(text);
            if (center == null) {
                center = findCenterByTextContains(text);
            }
            if (center != null) {
                ClickResult result = acc.click(center[0], center[1], null);
                if (result != null && result.isSuccess()) {
                    return true;
                }
            }
            sleep(200);
        }
        return false;
    }

    /**
     * 聊天页：点「会话 ▼」→ 等弹窗 → 点「新增会话」。
     * 自定义弹窗或旧 AlertDialog 均在 a11y 树中按文本/id 查找。
     */
    public static boolean openNewQaSession() {
        if (!ensureQaChatPage()) {
            Log.w(TAG, "openNewQaSession: not on QA");
            return false;
        }
        if (!tapById(ID_SESSION_MANAGE, 4)) {
            Log.w(TAG, "openNewQaSession: btn_session_manage not found");
            return false;
        }
        // 等弹窗出现（任务里原先只 sleep 200ms，经常点不到）
        boolean dialogReady = false;
        for (int i = 0; i < 12; i++) {
            sleep(150);
            if (findCenterById(ID_ALERT_BUTTON1) != null
                    || findCenterByTextContains("新增会话") != null
                    || findCenterByText("+ 新增会话") != null) {
                dialogReady = true;
                break;
            }
        }
        if (!dialogReady) {
            Log.w(TAG, "openNewQaSession: session dialog not ready");
            return false;
        }
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            int[] center = findCenterById(ID_ALERT_BUTTON1);
            if (center == null) {
                center = findCenterByText("+ 新增会话");
            }
            if (center == null) {
                center = findCenterByTextContains("新增会话");
            }
            if (center != null) {
                ClickResult result = acc.click(center[0], center[1], null);
                if (result != null && result.isSuccess()) {
                    Log.i(TAG, "openNewQaSession: tapped new session");
                    sleep(2000);
                    return true;
                }
            }
            sleep(200);
        }
        Log.w(TAG, "openNewQaSession: failed to tap 新增会话");
        return false;
    }

    public static void swipeUp() {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return;
        }
        int w = 1080;
        int h = 2400;
        Context ctx = DemoApp.getForegroundActivity();
        if (ctx != null) {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            w = dm.widthPixels;
            h = dm.heightPixels;
        }
        acc.swipe(w / 2, (int) (h * 0.72), w / 2, (int) (h * 0.28), 160);
    }

    public static void dismissDialogs() {
        for (String label : new String[]{"知道了", "我知道了", "确定", "取消"}) {
            if (tapByText(label, 1)) {
                sleep(120);
                return;
            }
        }
    }

    public static void inputToTarget(String resourceId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AccessibilityManagerImpl acc = a11y();
        if (acc == null) {
            return;
        }
        int[] center = null;
        if (resourceId != null && !resourceId.isEmpty()) {
            tapById(resourceId, 2);
            sleep(250);
            center = findCenterById(resourceId);
        }
        if (center == null) {
            center = findCenterById(ID_INPUT);
            if (center != null) {
                acc.click(center[0], center[1], null);
                sleep(250);
            }
        }
        int x = center != null ? center[0] : 0;
        int y = center != null ? center[1] : 0;
        acc.inputText(x, y, text);
        sleep(600);
    }

    public static void sendClarify(String clarify) {
        if (clarify == null || clarify.isEmpty()) {
            return;
        }
        if (findCenterById(ID_INPUT) == null) {
            tapById(ID_QA, 2);
            sleep(1000);
        }
        inputToTarget(ID_INPUT, clarify);
        if (!tapById(ID_SEND, 3)) {
            throw new RuntimeException("找不到发送按钮 btn_send");
        }
        sleep(500);
    }

    public static void pressBack() {
        AccessibilityManagerImpl acc = a11y();
        if (acc != null) {
            acc.goBack();
        }
        sleep(200);
    }

    private static int[] findCenterById(String resourceId) {
        return findCenterInWindows(root -> findNodeByResourceId(root, resourceId));
    }

    private static int[] findCenterByText(String text) {
        return findCenterInWindows(root -> findNodeByText(root, text, false));
    }

    private static int[] findCenterByTextContains(String text) {
        return findCenterInWindows(root -> findNodeByText(root, text, true));
    }

    private interface NodeFinder {
        AccessibilityNodeInfo find(AccessibilityNodeInfo root);
    }

    /** 跨 active window + getWindows()，否则 AlertDialog 上的按钮找不到。 */
    private static int[] findCenterInWindows(NodeFinder finder) {
        AccessibilityManagerImpl acc = a11y();
        if (acc == null || finder == null) {
            return null;
        }
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        AccessibilityNodeInfo active = null;
        try {
            active = acc.getRootInActiveWindow();
            if (active != null) {
                roots.add(active);
            }
            List<AccessibilityWindowInfo> windows = acc.getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    if (w == null) {
                        continue;
                    }
                    try {
                        AccessibilityNodeInfo root = w.getRoot();
                        if (root != null) {
                            roots.add(root);
                        }
                    } finally {
                        w.recycle();
                    }
                }
            }
            for (AccessibilityNodeInfo root : roots) {
                AccessibilityNodeInfo node = finder.find(root);
                if (node != null) {
                    return boundsCenter(node);
                }
            }
            return null;
        } catch (Exception e) {
            Log.w(TAG, "findCenterInWindows: " + e.getMessage());
            return null;
        } finally {
            for (AccessibilityNodeInfo root : roots) {
                try {
                    root.recycle();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static AccessibilityNodeInfo findNodeByResourceId(
            AccessibilityNodeInfo node, String resourceId) {
        if (node == null) {
            return null;
        }
        if (matchesResourceId(node, resourceId)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findNodeByResourceId(child, resourceId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static AccessibilityNodeInfo findNodeByText(
            AccessibilityNodeInfo node, String text, boolean contains) {
        if (node == null) {
            return null;
        }
        CharSequence nodeText = node.getText();
        CharSequence desc = node.getContentDescription();
        if (textMatches(nodeText, text, contains) || textMatches(desc, text, contains)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findNodeByText(child, text, contains);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean textMatches(CharSequence hay, String needle, boolean contains) {
        if (hay == null || needle == null) {
            return false;
        }
        String h = hay.toString();
        return contains ? h.contains(needle) : needle.contentEquals(h);
    }

    private static boolean matchesResourceId(AccessibilityNodeInfo node, String resourceId) {
        String vid = node.getViewIdResourceName();
        if (vid == null || resourceId == null) {
            return false;
        }
        if (vid.equals(resourceId)) {
            return true;
        }
        int slash = resourceId.indexOf(":id/");
        if (slash >= 0) {
            String shortName = resourceId.substring(slash + 4);
            return vid.endsWith(":id/" + shortName) || vid.endsWith("/" + shortName);
        }
        return vid.endsWith("/" + resourceId);
    }

    private static int[] boundsCenter(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) {
            return null;
        }
        return new int[]{(bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2};
    }
}
