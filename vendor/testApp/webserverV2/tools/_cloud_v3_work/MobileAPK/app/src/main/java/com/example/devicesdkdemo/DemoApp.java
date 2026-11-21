package com.example.devicesdkdemo;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;

public class DemoApp extends Application {
    private static volatile Activity foregroundActivity;

    public static Activity getForegroundActivity() {
        return foregroundActivity;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                foregroundActivity = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (foregroundActivity == activity) {
                    foregroundActivity = null;
                }
            }

            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        CrashHandler.install(this);
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        ConfigActivity.ensureWebServerDefaults(prefs);
        ConfigActivity.syncUnattendedToSdk(ConfigActivity.isUnattendedEnabled(prefs));
        // AISF 断链：WS reconnect，失败则用本地账号重新 login（不 createSession）
        AisfConnectionKeeper.init(this);
        ReplyMetrics.init(this);
        try {
            com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                    .addSnapshotListener(ReplyMetrics::onGuiStepLatencySnapshot);
            com.huawei.aisfsdkv2.component.gui.GuiStepLatencyTracker.getInstance()
                    .addControlShotListener(ReplyMetrics::onControlStepShotReady);
        } catch (Throwable t) {
            android.util.Log.w("DemoApp", "GuiStepLatencyTracker listener: " + t.getMessage());
        }
        WebServerReporter.init(this);
        // 默认已关闭直连；仅当配置页打开 WebServer 直报时才会真正连 WS
        WebServerWsClient.init(this);
        try {
            com.example.testreply.TestReplySdk.registerForceEndTakeoverListener(
                    this,
                    reason -> {
                        android.util.Log.i("DemoApp",
                                "FORCE_END_TAKEOVER → stopAisf reason=" + reason);
                        QAActivity.stopAisfFromWebServer(
                                getApplicationContext(),
                                reason != null ? reason : "force_end_broadcast");
                    });
        } catch (Throwable t) {
            android.util.Log.w("DemoApp", "FORCE_END listener: " + t.getMessage());
        }
    }
}
