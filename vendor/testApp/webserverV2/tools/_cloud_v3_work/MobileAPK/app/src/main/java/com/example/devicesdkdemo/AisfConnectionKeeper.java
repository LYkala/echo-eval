package com.example.devicesdkdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.login.LoginApi;
import com.huawei.aisfsdkv2.api.login.LoginCallback;
import com.huawei.aisfsdkv2.api.login.LoginErrorEnum;
import com.huawei.aisfsdkv2.api.login.LoginParam;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AISF 保活：断链先 WS reconnect；若已 IDLE / 无 param / 重连失败，则用本地账号重新 login。
 * <p>不 createSession，会话 id 尽量保留；仅恢复底层链路。
 */
public final class AisfConnectionKeeper {
    private static final String TAG = "AisfConnKeeper";
    private static final long CHECK_INTERVAL_MS = 12_000L;
    private static final long DEFAULT_ENSURE_TIMEOUT_MS = 25_000L;
    private static final long MIN_RELOGIN_INTERVAL_MS = 25_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable checkRunnable;
    private static volatile boolean started;
    private static volatile Context appContext;
    private static volatile boolean reloginInFlight;
    private static volatile long lastReloginAtMs;
    private static volatile long lastWsReconnectAtMs;

    private AisfConnectionKeeper() {
    }

    public static synchronized void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        start();
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                tryEnsureReconnect("periodic");
                MAIN.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        MAIN.post(checkRunnable);
        Log.i(TAG, "started, interval=" + CHECK_INTERVAL_MS + "ms");
    }

    public static synchronized void stop() {
        started = false;
        if (checkRunnable != null) {
            MAIN.removeCallbacks(checkRunnable);
            checkRunnable = null;
        }
        Log.i(TAG, "stopped");
    }

    public static boolean isConnected() {
        try {
            if (!sdkReady()) {
                return false;
            }
            LoginStatus st = AISFSDKClient.getLoginApi().getLoginInfo().getStatus();
            boolean cm = ConnectionManager.getInstance().isConnected();
            return st == LoginStatus.CONNECTED && cm;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 若已断链：先 WS reconnect，不行则全量 login。 */
    public static void tryEnsureReconnect(String reason) {
        try {
            if (!sdkReady()) {
                Log.d(TAG, "sdk not ready, skip reason=" + reason);
                return;
            }
            if (isConnected()) {
                return;
            }
            LoginApi loginApi = AISFSDKClient.getLoginApi();
            LoginStatus st = loginApi.getLoginInfo().getStatus();
            ConnectionManager cm = ConnectionManager.getInstance();
            if (st == LoginStatus.AUTHING || st == LoginStatus.CONNECTING || cm.isConnecting()) {
                Log.d(TAG, "in-progress status=" + st + " cmConnecting=" + cm.isConnecting()
                        + " reason=" + reason);
                return;
            }
            if (reloginInFlight) {
                Log.d(TAG, "relogin in-flight, skip reason=" + reason);
                return;
            }

            Log.w(TAG, "not connected status=" + st
                    + " cmConnected=" + cm.isConnected()
                    + " reason=" + reason);

            // IDLE / 认证失败后 param 常被清空，WS reconnect 无效 → 直接重登
            if (st == LoginStatus.IDLE) {
                scheduleFullRelogin(reason + "_idle");
                return;
            }

            long now = SystemClock.elapsedRealtime();
            // DISCONNECTED 或状态不同步：先尝试 WS 重连，短时间无效再全量 login
            if (now - lastWsReconnectAtMs >= 8_000L) {
                lastWsReconnectAtMs = now;
                Log.w(TAG, "try WS reconnect reason=" + reason);
                cm.reconnect();
            }

            // 给 SDK 自动重连一点时间；仍不通则全量 login
            MAIN.postDelayed(() -> {
                if (!started || isConnected() || reloginInFlight) {
                    return;
                }
                LoginStatus st2;
                try {
                    st2 = AISFSDKClient.getLoginApi().getLoginInfo().getStatus();
                } catch (Throwable t) {
                    st2 = LoginStatus.IDLE;
                }
                if (st2 == LoginStatus.AUTHING || st2 == LoginStatus.CONNECTING
                        || ConnectionManager.getInstance().isConnecting()) {
                    return;
                }
                Log.w(TAG, "WS reconnect not restored, full relogin status=" + st2
                        + " reason=" + reason);
                scheduleFullRelogin(reason + "_after_ws");
            }, 10_000L);
        } catch (Throwable t) {
            Log.w(TAG, "tryEnsureReconnect: " + t.getMessage());
        }
    }

    /**
     * 阻塞等待直到已连接或超时。勿在主线程调用。
     */
    public static boolean ensureConnected(long timeoutMs, String reason) {
        long waitMs = timeoutMs > 0 ? timeoutMs : DEFAULT_ENSURE_TIMEOUT_MS;
        try {
            if (isConnected()) {
                return true;
            }
            Log.w(TAG, "ensureConnected start reason=" + reason + " timeoutMs=" + waitMs);
            tryEnsureReconnect(reason);
            // 立刻再推一次全量 login（发送路径更急）
            LoginStatus st = sdkReady()
                    ? AISFSDKClient.getLoginApi().getLoginInfo().getStatus()
                    : LoginStatus.IDLE;
            if (st == LoginStatus.IDLE || !ConnectionManager.getInstance().isConnected()) {
                scheduleFullRelogin(reason + "_ensure");
            }

            long deadline = SystemClock.elapsedRealtime() + waitMs;
            while (SystemClock.elapsedRealtime() < deadline) {
                if (isConnected()) {
                    Log.i(TAG, "ensureConnected OK reason=" + reason);
                    return true;
                }
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            boolean ok = isConnected();
            Log.w(TAG, "ensureConnected done ok=" + ok + " reason=" + reason
                    + " status=" + (sdkReady()
                    ? AISFSDKClient.getLoginApi().getLoginInfo().getStatus() : "n/a"));
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "ensureConnected error: " + t.getMessage());
            return false;
        }
    }

    private static void scheduleFullRelogin(String reason) {
        if (appContext == null || !sdkReady()) {
            Log.w(TAG, "cannot relogin: ctx/sdk missing reason=" + reason);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (reloginInFlight) {
            return;
        }
        if (now - lastReloginAtMs < MIN_RELOGIN_INTERVAL_MS) {
            Log.d(TAG, "relogin throttled reason=" + reason);
            return;
        }
        reloginInFlight = true;
        lastReloginAtMs = now;
        new Thread(() -> {
            try {
                doFullRelogin(reason);
            } finally {
                reloginInFlight = false;
            }
        }, "aisf-relogin").start();
    }

    private static void doFullRelogin(String reason) {
        SharedPreferences prefs = appContext.getSharedPreferences(
                ConfigActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String username = ConfigActivity.getUsername(prefs);
        String password = ConfigActivity.getPassword(prefs);
        Log.w(TAG, "full relogin begin user=" + username + " reason=" + reason);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        try {
            LoginParam param = new LoginParam.Builder()
                    .username(username)
                    .password(password)
                    .timeoutMs(60_000)
                    .autoReconnect(true)
                    .maxRetryCount(3)
                    .callback(new LoginCallback() {
                        @Override
                        public void onSuccess() {
                            ok.set(true);
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(LoginErrorEnum error, String message) {
                            Log.w(TAG, "full relogin failure err=" + error
                                    + " msg=" + message);
                            latch.countDown();
                        }

                        @Override
                        public void onConnectStatusChanged(LoginStatus oldStatus,
                                                           LoginStatus newStatus) {
                            Log.i(TAG, "status " + oldStatus + " -> " + newStatus);
                            if (newStatus == LoginStatus.CONNECTED) {
                                ok.set(true);
                                latch.countDown();
                            }
                        }
                    })
                    .build();
            AISFSDKClient.getLoginApi().login(param);
            boolean finished = latch.await(55, TimeUnit.SECONDS);
            Log.w(TAG, "full relogin end ok=" + ok.get()
                    + " finished=" + finished
                    + " connected=" + isConnected()
                    + " reason=" + reason);
        } catch (Throwable t) {
            Log.w(TAG, "full relogin error: " + t.getMessage());
        }
    }

    private static boolean sdkReady() {
        try {
            return AISFSDKClient.getInstance().isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }
}
