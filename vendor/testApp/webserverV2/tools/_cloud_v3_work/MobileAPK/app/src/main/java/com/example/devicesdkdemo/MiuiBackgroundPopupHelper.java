package com.example.devicesdkdemo;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 小米「后台弹出界面」检测与跳转设置。
 * MIUI 自定义 op：10020 / 10021；放开后用例收尾可直接 startActivity，不必 HOME 再点图标。
 */
public final class MiuiBackgroundPopupHelper {
    private static final String TAG = "MiuiBgPopup";
    /** 后台弹出界面（常见） */
    public static final int OP_BACKGROUND_START_ACTIVITY = 10020;
    public static final int OP_BACKGROUND_START_ACTIVITY_ALT = 10021;

    private MiuiBackgroundPopupHelper() {
    }

    public static boolean isAllowed(Context context) {
        if (context == null) {
            return false;
        }
        Context app = context.getApplicationContext();
        int mode20 = checkOp(app, OP_BACKGROUND_START_ACTIVITY);
        int mode21 = checkOp(app, OP_BACKGROUND_START_ACTIVITY_ALT);
        if (mode20 == AppOpsManager.MODE_ALLOWED || mode21 == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        // 小米/红米：必须以 10020/10021 allow 为准，不能用悬浮窗冒充
        if (isXiaomiFamily()) {
            return false;
        }
        if (mode20 == AppOpsManager.MODE_IGNORED || mode20 == AppOpsManager.MODE_ERRORED
                || mode21 == AppOpsManager.MODE_IGNORED || mode21 == AppOpsManager.MODE_ERRORED) {
            return false;
        }
        try {
            return Settings.canDrawOverlays(app);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isXiaomiFamily() {
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
        String b = Build.BRAND != null ? Build.BRAND : "";
        String s = (m + " " + b).toLowerCase();
        return s.contains("xiaomi") || s.contains("redmi") || s.contains("poco");
    }

    public static int checkOp(Context context, int op) {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return -1;
            }
            Method m = AppOpsManager.class.getMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            Object result = m.invoke(appOps, op, Process.myUid(), context.getPackageName());
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception e) {
            Log.w(TAG, "checkOp " + op + ": " + e.getMessage());
        }
        return -1;
    }

    public static String statusLabel(Context context) {
        if (isAllowed(context)) {
            return "后台弹出权限: 已允许 ✓";
        }
        return "后台弹出权限: 未允许 ✗ (点击去设置)";
    }

    /** 跳转小米权限编辑页 / 应用详情，引导用户打开「后台弹出界面」。 */
    public static void openSettings(Context context) {
        if (context == null) {
            return;
        }
        String pkg = context.getPackageName();
        Intent[] candidates = new Intent[]{
                miuiPermEditor(pkg, "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                miuiPermEditor(pkg, "com.miui.permcenter.permissions.AppPermissionsEditorActivity"),
                miuiPermEditor(pkg, "com.miui.securitycenter.permission.AppPermissionsEditorActivity"),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + pkg)),
        };
        for (Intent intent : candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(context.getPackageManager()) != null
                        || intent.getComponent() != null) {
                    context.startActivity(intent);
                    Log.i(TAG, "openSettings via " + intent);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "openSettings candidate fail: " + e.getMessage());
            }
        }
        Log.e(TAG, "openSettings: no intent worked");
    }

    private static Intent miuiPermEditor(String pkg, String activity) {
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.setClassName("com.miui.securitycenter", activity);
        intent.putExtra("extra_pkgname", pkg);
        return intent;
    }
}
