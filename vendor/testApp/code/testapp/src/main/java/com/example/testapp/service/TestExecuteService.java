package com.example.testapp.service;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.example.testapp.MainActivity;
import com.example.testapp.core.CommandExecutor;
import com.example.testapp.model.Params;
import com.example.testapp.model.TestFlow;
import com.example.testapp.model.TestStep;
import com.example.testapp.service.TestAccessibilityService;
import com.example.testapp.util.JsonParser;
import com.example.testapp.util.LiveLogStreamer;
import com.example.testapp.util.Logger;
import com.example.testapp.util.ScreenshotDiagLogger;
import com.example.testapp.util.ScreenshotManager;
import com.example.testapp.util.ScreenshotUtil;
import com.example.testapp.util.WebServerClient;
import com.example.testreply.TestReplyEvent;
import com.example.testreply.TestReplySdk;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestExecuteService
extends Service {
    private static final String TAG = "TestExecuteService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "test_execute";
    public static final String ACTION_START_TEST = "com.example.testapp.START_TEST";
    public static final String ACTION_STOP_TEST = "com.example.testapp.STOP_TEST";
    public static final String ACTION_TEST_FINISHED = "com.example.testapp.TEST_FINISHED";
    public static final String EXTRA_TEST_FILE = "test_file";
    private static final long STEP_TIMEOUT_MS = 10000L;
    private static final long DEFAULT_TOTAL_TIMEOUT_MS = 60000L;
    private static final long BASE_TIMEOUT_BUFFER_MS = 15000L;
    private static final long PER_STEP_TIMEOUT_BUFFER_MS = 1000L;
    private static final long SCREENSHOT_DELAY_MS = 300L;
    private static final Set<String> DEVICE_SDK_PRE_QA_STEP_IDS = new HashSet<String>(Arrays.asList("scroll-to-enter-test", "wait-test-home", "dismiss-need-login", "click-login", "wait-login", "dismiss-after-login", "click-qa", "wait-qa-page"));
    private static final long SCREENSHOT_PREPARE_WAIT_MS = 500L;
    private static final long MEDIA_DISMISS_SETTLE_MS = 1200L;
    private static final long MEDIA_PREP_MAX_MS = 8000L;
    private static final long UNICOM_SCREENSHOT_DELAY_MS = 1000L;
    private static final String DEVICE_SDK_PACKAGE = "com.example.devicesdkdemo";
    private static final String VOICE_ASSISTANT_PACKAGE = "com.huawei.aisftvassistant";
    private static volatile TestExecuteService runningInstance;
    private static final String DEVICE_SDK_QA_ACTIVITY = "com.example.devicesdkdemo.QAActivity";
    private static final String DEVICE_SDK_CONFIG_ACTIVITY = "com.example.devicesdkdemo.ConfigActivity";
    private static final String UNICOM_CLOUD_PHONE_PACKAGE = "com.unicom.cloudphone";
    private static final long DEVICE_SDK_WAIT_RESPONSE_SAFETY_MS = 300000L;
    /** TEXT 优先：未收到 GUI_TASK 前的短文本超时，避免 TEXT 轮整轮静默消失。 */
    private static final long DEVICE_SDK_TEXT_FIRST_TIMEOUT_MS = 120000L;
    private static final long DEVICE_SDK_MEDIA_CAPTURE_TIMEOUT_MS = 8000L;
    private static final long DEVICE_SDK_GUI_POLL_MEDIA_TIMEOUT_MS = 2500L;
    private static final AtomicLong guiCaptureRequestIdSeq;
    private static final long DEVICE_SDK_GUI_POLL_INTERVAL_MS = 1500L;
    /** AISF 执行完成后，停留再拉回 QA 聊天页的等待时长。 */
    private static final long DEVICE_SDK_RETURN_QA_DELAY_MS = 3000L;
    private static final boolean DEVICE_SDK_GUI_POLL_BLIND_MODE = true;
    private static final long DEVICE_SDK_POST_SEND_CAPTURE_MS = 1500L;
    private static final long DEVICE_SDK_POST_SEND_CAPTURE2_MS = 4000L;
    private static final int MAX_GUI_POLL_CAPTURES = 2;
    private static final long DEVICE_SDK_GUI_POLL_FIRST_DELAY_MS = 500L;
    private static final long DEVICE_SDK_FOREGROUND_SHELL_POLL_MS = 3000L;
    private static final long DEVICE_SDK_GUI_JUMP_CAPTURE_SETTLE_MS = 400L;
    private static final String ANDROID_SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final long DEVICE_SDK_GUI_TAKEOVER_MAX_MS = 300000L;
    private static final AtomicBoolean isRunning;
    private static final AtomicBoolean shouldStop;
    private TestFlow currentFlow;
    private String targetPackage;
    private int currentStepIndex = 0;
    private CommandExecutor commandExecutor;
    private Handler handler = new Handler(Looper.getMainLooper());
    private HandlerThread testScheduleThread;
    private Handler testScheduleHandler;
    private HandlerThread deviceSdkGuiPollThread;
    private Handler deviceSdkGuiPollHandler;
    private final Runnable deviceSdkBlockingDialogRunnable = this::safeDismissDeviceSdkBlockingDialogs;
    private final Runnable deviceSdkBeforeStepDialogRunnable = this::safeDismissDeviceSdkDialogsBeforeStep;
    private final Runnable deviceSdkAfterScreenshotRunnable = this::dismissDeviceSdkDialogsAfterScreenshot;
    private final Runnable mediaProjectionDialogRunnable = this::safeDismissMediaProjectionDialog;
    private ScreenshotManager screenshotManager;
    private Runnable totalTimeoutRunnable;
    private Runnable stepTimeoutRunnable;
    private long testStartTime;
    private volatile long testRunId = 0L;
    private final AtomicBoolean screenshotInProgress = new AtomicBoolean(false);
    private volatile long lastMediaProjectionDismissAt = 0L;
    private final AtomicBoolean testCompletionNotified = new AtomicBoolean(false);
    private final AtomicBoolean testLogsExported = new AtomicBoolean(false);
    private volatile boolean pendingTestSuccess = false;
    private volatile String pendingTestId = "unknown";
    private volatile String pendingTestMessage = "";
    private Runnable testCompletionFallbackRunnable;
    private Runnable exitFlowStep1Runnable;
    private Runnable exitFlowStep2Runnable;
    private Runnable exitFlowStep3Runnable;
    private BroadcastReceiver commandReceiver = new BroadcastReceiver(){

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Logger.safeInfo(TestExecuteService.TAG, "\u6536\u5230\u5e7f\u64ad: " + action);
            if (TestExecuteService.ACTION_STOP_TEST.equals(action)) {
                String reason = intent != null ? intent.getStringExtra("reason") : null;
                if (reason == null || reason.isEmpty()) {
                    reason = "\u7528\u6237\u505c\u6b62";
                }
                Logger.safeInfo(TestExecuteService.TAG, "\u6267\u884c\u505c\u6b62\u6d4b\u8bd5: " + reason);
                TestExecuteService.this.stopTest(reason);
            }
        }
    };
    private BroadcastReceiver testReplyReceiver;
    private BroadcastReceiver guiScreenCaptureResultReceiver;
    private volatile boolean waitingForDeviceReply;
    private volatile int expectedReplyRound;
    /** 已上报「轮次开始」的 GUI 轮，避免同一轮重复上报。 */
    private final Set<Integer> reportedRoundStarts = ConcurrentHashMap.newKeySet();
    private volatile String pendingWaitShotStepName;
    /** 本轮是否已由 TEXT 优先升级为 GUI 轮。 */
    private volatile boolean waitResponseIsGuiUpgraded;
    private Runnable waitResponseTimeoutRunnable;
    private final Map<Integer, TestReplyEvent> pendingDeviceReplies = new ConcurrentHashMap<Integer, TestReplyEvent>();
    private volatile TestReplyEvent pendingTaskCompletedEvent;
    private volatile boolean deviceSdkQaFinishedByAisf;
    private Runnable deviceSdkGuiPollRunnable;
    private Runnable deviceSdkForegroundShellPollRunnable;
    private int deviceSdkGuiPollIndex;
    /** 最近一条操控指令的名称，补拍时沿用，避免重试帧丢掉指令名。 */
    private String lastControlCmdName;
    private volatile String lastGuiPollForegroundPkg;
    private Runnable foregroundJumpCaptureRunnable;
    private volatile boolean pendingGuiPollCapture;
    private final AtomicInteger guiPollCapturesInFlight = new AtomicInteger(0);
    private final TestAccessibilityService.ForegroundChangeListener deviceSdkForegroundListener = this::scheduleForegroundJumpCapture;
    private final Map<Integer, String> clarifyMessagesByRound = new HashMap<Integer, String>();
    private Runnable deviceSdkTakeoverWatchdogRunnable;

    private boolean isUnicomCloudPhoneTarget() {
        return UNICOM_CLOUD_PHONE_PACKAGE.equals(this.targetPackage);
    }

    private boolean isDeviceSdkDemoTarget() {
        return DEVICE_SDK_PACKAGE.equals(this.targetPackage);
    }

    private boolean isVoiceAssistantTarget() {
        return VOICE_ASSISTANT_PACKAGE.equals(this.targetPackage);
    }

    /** 语音助手注入后的长等待：这段时间里收 AISF 回复，并拍第三方界面。 */
    private boolean isVoiceGuiWaitStep(TestStep step) {
        if (!this.isVoiceAssistantTarget() || step == null || !"wait".equals(step.getAction())) {
            return false;
        }
        String stepId = step.getStepId() != null ? step.getStepId() : "";
        if (stepId.startsWith("wait-utterance") || stepId.startsWith("wait-gui")
                || stepId.startsWith("wait-clarify")) {
            return true;
        }
        long duration = step.getParams() != null ? step.getParams().getDuration() : 0L;
        return duration >= 10000L;
    }

    private void ensureTestScheduleThread() {
        if (this.testScheduleThread == null || !this.testScheduleThread.isAlive()) {
            this.testScheduleThread = new HandlerThread("test-schedule");
            this.testScheduleThread.start();
            this.testScheduleHandler = new Handler(this.testScheduleThread.getLooper());
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8 test-schedule \u8c03\u5ea6\u7ebf\u7a0b");
        }
    }

    private void clearTestScheduleCallbacks() {
        if (this.testScheduleHandler != null) {
            this.testScheduleHandler.removeCallbacksAndMessages(null);
        }
    }

    private void shutdownTestScheduleThread() {
        this.clearTestScheduleCallbacks();
        if (this.testScheduleThread != null) {
            this.testScheduleThread.quitSafely();
            this.testScheduleThread = null;
            this.testScheduleHandler = null;
            Logger.safeInfo(TAG, "\u5df2\u505c\u6b62 test-schedule \u8c03\u5ea6\u7ebf\u7a0b");
        }
    }

    private void ensureDeviceSdkGuiPollThread() {
        if (this.deviceSdkGuiPollThread == null || !this.deviceSdkGuiPollThread.isAlive()) {
            this.deviceSdkGuiPollThread = new HandlerThread("device-sdk-gui-poll");
            this.deviceSdkGuiPollThread.start();
            this.deviceSdkGuiPollHandler = new Handler(this.deviceSdkGuiPollThread.getLooper());
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8 device-sdk-gui-poll \u7ebf\u7a0b");
        }
    }

    private void clearDeviceSdkGuiPollCallbacks() {
        if (this.deviceSdkGuiPollHandler != null) {
            this.deviceSdkGuiPollHandler.removeCallbacksAndMessages(null);
        }
    }

    private void shutdownDeviceSdkGuiPollThread() {
        this.clearDeviceSdkGuiPollCallbacks();
        if (this.deviceSdkGuiPollThread != null) {
            this.deviceSdkGuiPollThread.quitSafely();
            this.deviceSdkGuiPollThread = null;
            this.deviceSdkGuiPollHandler = null;
            Logger.safeInfo(TAG, "\u5df2\u505c\u6b62 device-sdk-gui-poll \u7ebf\u7a0b");
        }
    }

    private void postOnGuiPollScheduler(Runnable runnable) {
        if (!this.isDeviceSdkDemoTarget()) {
            this.handler.post(runnable);
            return;
        }
        this.ensureDeviceSdkGuiPollThread();
        this.deviceSdkGuiPollHandler.post(runnable);
    }

    private void postDelayedOnGuiPollScheduler(Runnable runnable, long delayMs) {
        if (!this.isDeviceSdkDemoTarget()) {
            this.handler.postDelayed(runnable, delayMs);
            return;
        }
        this.ensureDeviceSdkGuiPollThread();
        this.deviceSdkGuiPollHandler.postDelayed(runnable, delayMs);
    }

    private void postOnTestScheduler(Runnable runnable) {
        if (this.isDeviceSdkDemoTarget()) {
            this.ensureTestScheduleThread();
            this.testScheduleHandler.post(runnable);
        } else {
            this.handler.post(runnable);
        }
    }

    private void postDelayedOnTestScheduler(Runnable runnable, long delayMs) {
        if (this.isDeviceSdkDemoTarget()) {
            this.ensureTestScheduleThread();
            this.testScheduleHandler.postDelayed(runnable, delayMs);
        } else {
            this.handler.postDelayed(runnable, delayMs);
        }
    }

    private void cancelDeviceSdkDialogHandlers() {
        this.handler.removeCallbacks(this.deviceSdkBlockingDialogRunnable);
        this.handler.removeCallbacks(this.deviceSdkBeforeStepDialogRunnable);
        this.handler.removeCallbacks(this.deviceSdkAfterScreenshotRunnable);
        this.handler.removeCallbacks(this.mediaProjectionDialogRunnable);
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.phase("dialog_handlers_cancelled", "\u5df2\u53d6\u6d88\u4e3b\u7ebf\u7a0b\u5f85\u6267\u884c\u7684 DeviceSDK \u6d88\u5f39\u4efb\u52a1");
        }
    }

    public static boolean isRunning() {
        return isRunning.get();
    }

    public void onCreate() {
        super.onCreate();
        Logger.safeInfo(TAG, "=== TestExecuteService onCreate ===");
        runningInstance = this;
        try {
            this.commandExecutor = new CommandExecutor((Context)this);
            this.registerCommandReceiver();
            this.registerTestReplyReceiver();
            this.registerGuiScreenCaptureResultReceiver();
            this.screenshotManager = ScreenshotManager.getInstance((Context)this);
            this.createNotificationChannel();
            this.startForeground(1001, this.createNotification("\u6d4b\u8bd5\u670d\u52a1\u51c6\u5907\u4e2d"));
            Logger.safeInfo(TAG, "TestExecuteService \u521d\u59cb\u5316\u5b8c\u6210");
        }
        catch (Exception e) {
            Logger.safeError(TAG, "onCreate\u5931\u8d25", e);
        }
    }

    public static void dispatchExternalReply(TestReplyEvent event) {
        TestExecuteService svc = runningInstance;
        if (svc == null || event == null) {
            Logger.safeWarn(TAG, "dispatchExternalReply: service \u672a\u8fd0\u884c\uff0c\u5ffd\u7565\u56de\u590d");
            return;
        }
        svc.handleDeviceReply(event);
    }

    /** 操控指令的结果截图已上传。此时画面还是这一步执行完的样子，补拍 gui_poll。 */
    public static void requestGuiPollAfterControl(String cmdName) {
        TestExecuteService svc = runningInstance;
        if (svc == null) {
            Logger.safeWarn(TAG, "gui_poll after control: service \u672a\u8fd0\u884c cmd=" + cmdName);
            return;
        }
        svc.captureGuiPollAfterControl(cmdName);
    }

    public static void dispatchExternalGuiStepLatency(
            String taskId, String caseId, int round, String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isEmpty()) {
            return;
        }
        TestExecuteService svc = runningInstance;
        WebServerClient client = WebServerClient.getInstance(null);
        if (client == null) {
            Logger.safeWarn(TAG, "dispatchExternalGuiStepLatency: WebServerClient missing");
            return;
        }
        String tid = taskId;
        String cid = caseId;
        if (tid == null || tid.isEmpty()) {
            tid = client.getCurrentTaskId();
        }
        if (cid == null || cid.isEmpty()) {
            cid = client.getCurrentCaseId();
        }
        if ((cid == null || cid.isEmpty()) && svc != null) {
            if (svc.currentFlow != null) {
                cid = svc.currentFlow.getTestId();
            }
            if (cid == null || cid.isEmpty()) {
                cid = svc.pendingTestId;
            }
        }
        client.sendGuiStepLatency(tid, cid, round, jsonPayload);
    }

    private void registerCommandReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_STOP_TEST);
        if (Build.VERSION.SDK_INT >= 33) {
            this.registerReceiver(this.commandReceiver, filter, 2);
        } else {
            this.registerReceiver(this.commandReceiver, filter);
        }
    }

    private void registerTestReplyReceiver() {
        this.testReplyReceiver = TestReplySdk.registerListener((Context)this, this::handleDeviceReply);
    }

    private void registerGuiScreenCaptureResultReceiver() {
        this.guiScreenCaptureResultReceiver = TestReplySdk.registerGuiScreenCaptureResultListener((Context)this);
    }

    private int parseReplyRoundFromStepId(String stepId) {
        if (stepId == null || !stepId.startsWith("wait-response")) {
            return 1;
        }
        String suffix = stepId.substring("wait-response".length());
        if (suffix.startsWith("-")) {
            suffix = suffix.substring(1);
        }
        if (suffix.isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(suffix);
        }
        catch (NumberFormatException e) {
            return 1;
        }
    }

    private void cancelWaitResponseTimeout() {
        if (this.waitResponseTimeoutRunnable == null) {
            return;
        }
        if (this.testScheduleHandler != null) {
            this.testScheduleHandler.removeCallbacks(this.waitResponseTimeoutRunnable);
        }
        this.handler.removeCallbacks(this.waitResponseTimeoutRunnable);
        this.waitResponseTimeoutRunnable = null;
    }

    private void stopDeviceSdkGuiPollScreenshots() {
        this.clearDeviceSdkGuiPollCallbacks();
        if (this.deviceSdkGuiPollRunnable != null) {
            if (this.testScheduleHandler != null) {
                this.testScheduleHandler.removeCallbacks(this.deviceSdkGuiPollRunnable);
            }
            this.handler.removeCallbacks(this.deviceSdkGuiPollRunnable);
            this.deviceSdkGuiPollRunnable = null;
        }
        this.stopDeviceSdkForegroundShellPoll();
        this.stopDeviceSdkForegroundCapture();
        this.guiPollCapturesInFlight.set(0);
    }

    private void stopDeviceSdkForegroundShellPoll() {
        if (this.deviceSdkForegroundShellPollRunnable != null) {
            if (this.deviceSdkGuiPollHandler != null) {
                this.deviceSdkGuiPollHandler.removeCallbacks(this.deviceSdkForegroundShellPollRunnable);
            }
            this.handler.removeCallbacks(this.deviceSdkForegroundShellPollRunnable);
            this.deviceSdkForegroundShellPollRunnable = null;
        }
    }

    private void startDeviceSdkGuiPollScreenshots() {
        this.stopDeviceSdkGuiPollScreenshots();
        if (!this.isDeviceSdkDemoTarget()) {
            return;
        }
        this.deviceSdkGuiPollIndex = 0;
        this.lastGuiPollForegroundPkg = null;
        this.guiPollCapturesInFlight.set(0);
        Logger.safeInfo(TAG, "DeviceSDK GUI \u622a\u56fe\u6539\u4e3a\u64cd\u63a7\u5b8c\u6210\u540e\u62cd\u6444\uff0c\u4e0d\u518d\u5b9a\u65f6\u8f6e\u8be2");
        ScreenshotDiagLogger.phase("gui_poll_start", "mode=after_control");
    }

    private void captureGuiPollAfterControl(String cmdName) {
        this.captureGuiPollAfterControlNow(cmdName);
    }

    private void captureGuiPollAfterControlNow(String cmdName) {
        if (!this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()
                || !(this.isDeviceSdkDemoTarget() || this.isVoiceAssistantTarget())) {
            Logger.safeInfo(TAG, "gui_poll after control skipped cmd=" + cmdName);
            return;
        }
        this.deviceSdkGuiPollIndex++;
        this.lastControlCmdName = cmdName;
        String stepName = guiPollStepName(this.deviceSdkGuiPollIndex, cmdName);
        Logger.safeInfo(TAG, "gui_poll after control cmd=" + cmdName + " " + stepName);
        ScreenshotDiagLogger.phase("gui_poll_after_control", "cmd=" + cmdName + " step=" + stepName);
        this.captureDeviceSdkScreenOnly(stepName, false);
    }

    /** 操控截图文件名带上指令，例如 gui_poll_2_点击。 */
    private static String guiPollStepName(int index, String cmdName) {
        String cmd = cmdName == null ? "" : cmdName.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "");
        if (cmd.isEmpty()) {
            return "gui_poll_" + index;
        }
        return "gui_poll_" + index + "_" + cmd;
    }

    /** 在当前线程拍一张并入队。收尾 final_result 用它，避免异步截图时已经拉回 QA。 */
    private void captureNamedScreenSync(String stepName) {
        try {
            String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
            File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + testId);
            if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
                Logger.safeWarn(TAG, "创建截图目录失败: " + screenshotDir);
            }
            String fileName = ScreenshotUtil.buildCaptureFileName(stepName);
            File screenshotFile = new File(screenshotDir, fileName);
            if (this.captureDeviceSdkStepScreenshot(stepName, screenshotFile)) {
                this.saveDeviceSdkStepScreenshot(testId, stepName, screenshotFile);
            } else if (screenshotFile.exists() && !screenshotFile.delete()) {
                Logger.safeDebug(TAG, "删除失败截图: " + screenshotFile.getName());
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "截图失败: " + stepName, e);
            ScreenshotDiagLogger.error("capture_named", e);
        }
    }

    private void startDeviceSdkForegroundShellPoll() {
        this.stopDeviceSdkForegroundShellPoll();
        this.ensureDeviceSdkGuiPollThread();
        this.deviceSdkForegroundShellPollRunnable = new Runnable(){

            @Override
            public void run() {
                if (!TestExecuteService.this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()) {
                    return;
                }
                String fg = TestExecuteService.this.readForegroundPackageViaShell();
                if (fg == null || fg.isEmpty()) {
                    fg = TestExecuteService.this.readForegroundPackageName();
                }
                if (!(fg == null || fg.isEmpty() || TestExecuteService.ANDROID_SYSTEMUI_PACKAGE.equals(fg) || fg.equals(TestExecuteService.this.lastGuiPollForegroundPkg) || TestExecuteService.DEVICE_SDK_PACKAGE.equals(fg) || TestExecuteService.VOICE_ASSISTANT_PACKAGE.equals(fg) || "com.example.testapp".equals(fg))) {
                    Logger.safeInfo(TestExecuteService.TAG, "shell \u524d\u53f0\u53d8\u5316 -> " + fg);
                    ScreenshotDiagLogger.phase("shell_foreground", "pkg=" + fg);
                    TestExecuteService.this.scheduleForegroundJumpCapture(TestExecuteService.this.lastGuiPollForegroundPkg, fg);
                }
                if (TestExecuteService.this.waitingForDeviceReply && isRunning.get() && !shouldStop.get() && TestExecuteService.this.deviceSdkForegroundShellPollRunnable == this) {
                    TestExecuteService.this.postDelayedOnGuiPollScheduler(this, DEVICE_SDK_FOREGROUND_SHELL_POLL_MS);
                }
            }
        };
        this.postDelayedOnGuiPollScheduler(this.deviceSdkForegroundShellPollRunnable, 800L);
    }

    private void startDeviceSdkForegroundCapture() {
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        if (a11y != null) {
            a11y.setForegroundChangeListener(this.deviceSdkForegroundListener);
        }
    }

    private void stopDeviceSdkForegroundCapture() {
        TestAccessibilityService a11y;
        if (this.foregroundJumpCaptureRunnable != null) {
            if (this.deviceSdkGuiPollHandler != null) {
                this.deviceSdkGuiPollHandler.removeCallbacks(this.foregroundJumpCaptureRunnable);
            }
            if (this.testScheduleHandler != null) {
                this.testScheduleHandler.removeCallbacks(this.foregroundJumpCaptureRunnable);
            }
            this.foregroundJumpCaptureRunnable = null;
        }
        if ((a11y = TestAccessibilityService.getInstance()) != null) {
            a11y.setForegroundChangeListener(null);
        }
        this.lastGuiPollForegroundPkg = null;
    }

    private void scheduleForegroundJumpCapture(String fromPkg, String toPkg) {
        if (!this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()) {
            return;
        }
        if (toPkg == null || toPkg.isEmpty()) {
            return;
        }
        if (ANDROID_SYSTEMUI_PACKAGE.equals(toPkg) || VOICE_ASSISTANT_PACKAGE.equals(toPkg)
                || "com.example.testapp".equals(toPkg)) {
            return;
        }
        if (toPkg.equals(this.lastGuiPollForegroundPkg)) {
            return;
        }
        if (this.foregroundJumpCaptureRunnable != null) {
            if (this.deviceSdkGuiPollHandler != null) {
                this.deviceSdkGuiPollHandler.removeCallbacks(this.foregroundJumpCaptureRunnable);
            }
            if (this.testScheduleHandler != null) {
                this.testScheduleHandler.removeCallbacks(this.foregroundJumpCaptureRunnable);
            }
        }
        String targetPkg = toPkg;
        this.foregroundJumpCaptureRunnable = () -> {
            if (!this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()) {
                return;
            }
            String current = this.readForegroundPackageViaShell();
            if (current == null || current.isEmpty()) {
                current = this.readForegroundPackageName();
            }
            if (current == null || current.isEmpty()) {
                current = targetPkg;
            }
            if (ANDROID_SYSTEMUI_PACKAGE.equals(current)) {
                return;
            }
            if (current.equals(this.lastGuiPollForegroundPkg)) {
                return;
            }
            this.lastGuiPollForegroundPkg = current;
            ++this.deviceSdkGuiPollIndex;
            String stepName = "gui_poll_" + this.deviceSdkGuiPollIndex;
            Logger.safeInfo(TAG, "GUI \u8df3\u8f6c\u622a\u56fe: " + (fromPkg != null ? fromPkg : "?") + " -> " + current + " as " + stepName);
            ScreenshotDiagLogger.phase("gui_jump_capture", "from=" + (fromPkg != null ? fromPkg : "-") + " to=" + current + " step=" + stepName);
            this.captureDeviceSdkScreenOnly(stepName, true);
        };
        this.postDelayedOnGuiPollScheduler(this.foregroundJumpCaptureRunnable, 400L);
    }

    private boolean isGuiPollCaptureStepName(String stepName) {
        return stepName != null && (stepName.startsWith("gui_poll_") || stepName.startsWith("post_send_"));
    }

    private void schedulePostSendCaptures(int round) {
        this.postDelayedOnGuiPollScheduler(() -> this.triggerPostSendCapture(round, 1), 1500L);
        this.postDelayedOnGuiPollScheduler(() -> this.triggerPostSendCapture(round, 2), 4000L);
        ScreenshotDiagLogger.phase("post_send_capture_scheduled", "round=" + round + " atMs=" + 1500L + "," + 4000L);
    }

    private void triggerPostSendCapture(int round, int sequence) {
        if (!this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()) {
            return;
        }
        ++this.deviceSdkGuiPollIndex;
        this.captureDeviceSdkScreenOnly("post_send_" + round + "_" + sequence, true);
    }

    private boolean isClickSendBeforeWaitResponse(TestStep step) {
        if (step == null || this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return false;
        }
        String stepId = step.getStepId();
        if (stepId == null || !stepId.startsWith("click-send")) {
            return false;
        }
        int nextIdx = this.currentStepIndex + 1;
        if (nextIdx >= this.currentFlow.getSteps().size()) {
            return false;
        }
        TestStep next = this.currentFlow.getSteps().get(nextIdx);
        String nextId = next.getStepId();
        return nextId != null && nextId.startsWith("wait-response");
    }

    private String readForegroundPackageName() {
        String shellFg;
        String fg;
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        String string = fg = a11y != null ? a11y.getForegroundPackageName() : null;
        if (this.waitingForDeviceReply && this.shouldUseShellForegroundFallback(fg) && (shellFg = this.readForegroundPackageViaShell()) != null && !shellFg.isEmpty()) {
            return shellFg;
        }
        return fg;
    }

    private boolean shouldUseShellForegroundFallback(String a11yForeground) {
        if (a11yForeground == null || a11yForeground.isEmpty()) {
            return true;
        }
        return DEVICE_SDK_PACKAGE.equals(a11yForeground) || "com.example.testapp".equals(a11yForeground);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private String readForegroundPackageViaShell() {
        Process process = null;
        try {
            String line;
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp' ; dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            Pattern pkgPat = Pattern.compile("([a-zA-Z][a-zA-Z0-9_.]{2,})/");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pkgPat.matcher(line);
                if (!matcher.find()) continue;
                String string = matcher.group(1);
                return string;
            }
            process.waitFor(2L, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            Logger.safeDebug(TAG, "shell foreground \u8bfb\u53d6\u5931\u8d25: " + e.getMessage());
        }
        finally {
            if (process != null) {
                process.destroy();
            }
        }
        return null;
    }

    private void captureDeviceSdkScreenOnly(String stepName) {
        this.captureDeviceSdkScreenOnly(stepName, false);
    }

    private void captureDeviceSdkScreenOnly(String stepName, boolean jumpTriggered) {
        new Thread(() -> {
            if (shouldStop.get() || !isRunning.get()) {
                return;
            }
            boolean guiPollCapture = this.isGuiPollCaptureStepName(stepName);
            if (guiPollCapture) {
                if (this.guiPollCapturesInFlight.incrementAndGet() > 2) {
                    this.guiPollCapturesInFlight.decrementAndGet();
                    this.pendingGuiPollCapture = true;
                    Logger.safeWarn(TAG, "GUI\u8f6e\u8be2\u622a\u56fe\u5e76\u53d1\u5df2\u6ee1\uff0c\u7a0d\u540e\u8865\u622a: " + stepName);
                    ScreenshotDiagLogger.warn("GUI\u8f6e\u8be2\u622a\u56fe\u5e76\u53d1\u5df2\u6ee1\uff0c\u7a0d\u540e\u8865\u622a: " + stepName);
                    return;
                }
            } else if (!this.screenshotInProgress.compareAndSet(false, true)) {
                this.pendingGuiPollCapture = true;
                Logger.safeWarn(TAG, "GUI\u8f6e\u8be2\u622a\u56fe\u91cd\u53e0\uff0c\u7a0d\u540e\u8865\u622a: " + stepName);
                ScreenshotDiagLogger.warn("GUI\u8f6e\u8be2\u622a\u56fe\u91cd\u53e0\uff0c\u7a0d\u540e\u8865\u622a: " + stepName);
                return;
            }
            try {
                String fileName;
                File screenshotFile;
                boolean success;
                String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + testId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                if ((success = this.captureDeviceSdkStepScreenshot(stepName, screenshotFile = new File(screenshotDir, fileName = ScreenshotUtil.buildCaptureFileName(stepName)))) && (guiPollCapture || !shouldStop.get() && isRunning.get())) {
                    this.saveDeviceSdkStepScreenshot(testId, stepName, screenshotFile);
                } else if (!success) {
                    if (screenshotFile.exists() && !screenshotFile.delete()) {
                        Logger.safeDebug(TAG, "\u5220\u9664\u5931\u8d25\u622a\u56fe: " + screenshotFile.getName());
                    }
                    Logger.safeWarn(TAG, "GUI\u8f6e\u8be2\u622a\u56fe\u5931\u8d25: " + fileName);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "GUI\u8f6e\u8be2\u622a\u56fe\u5f02\u5e38: " + stepName, e);
                ScreenshotDiagLogger.error("gui_poll\u5f02\u5e38: " + stepName, e);
            }
            finally {
                if (guiPollCapture) {
                    this.guiPollCapturesInFlight.decrementAndGet();
                } else {
                    this.screenshotInProgress.set(false);
                }
                if (this.pendingGuiPollCapture && this.waitingForDeviceReply && isRunning.get() && !shouldStop.get()) {
                    this.pendingGuiPollCapture = false;
                    this.postDelayedOnGuiPollScheduler(() -> {
                        if (!this.waitingForDeviceReply || !isRunning.get() || shouldStop.get()) {
                            return;
                        }
                        if (jumpTriggered) {
                            String fg = this.readForegroundPackageViaShell();
                            if (fg == null || fg.isEmpty()) {
                                fg = this.readForegroundPackageName();
                            }
                            if (fg != null && !fg.equals(this.lastGuiPollForegroundPkg) && !ANDROID_SYSTEMUI_PACKAGE.equals(fg)) {
                                this.scheduleForegroundJumpCapture(null, fg);
                            }
                        } else {
                            ++this.deviceSdkGuiPollIndex;
                            this.captureDeviceSdkScreenOnly(
                                    guiPollStepName(this.deviceSdkGuiPollIndex, this.lastControlCmdName),
                                    false);
                        }
                    }, 200L);
                }
            }
        }, "device-sdk-gui-poll").start();
    }

    private void waitForGuiPollCapturesIdle(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (this.guiPollCapturesInFlight.get() <= 0 && !this.screenshotInProgress.get()) {
                return;
            }
            try {
                Thread.sleep(200L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Logger.safeWarn(TAG, "\u7b49\u5f85 GUI \u622a\u56fe\u7ed3\u675f\u8d85\u65f6 inFlight=" + this.guiPollCapturesInFlight.get() + " shotBusy=" + this.screenshotInProgress.get());
    }

    private void resetWaitResponseState() {
        this.waitingForDeviceReply = false;
        this.expectedReplyRound = 0;
        this.pendingWaitShotStepName = null;
        this.deviceSdkQaFinishedByAisf = false;
        this.pendingTaskCompletedEvent = null;
        this.pendingDeviceReplies.clear();
        this.cancelWaitResponseTimeout();
        this.stopDeviceSdkGuiPollScreenshots();
        this.stopDeviceSdkTakeoverWatchdog();
        this.clarifyMessagesByRound.clear();
    }

    private void stopDeviceSdkTakeoverWatchdog() {
        if (this.deviceSdkTakeoverWatchdogRunnable == null) {
            return;
        }
        if (this.testScheduleHandler != null) {
            this.testScheduleHandler.removeCallbacks(this.deviceSdkTakeoverWatchdogRunnable);
        }
        this.handler.removeCallbacks(this.deviceSdkTakeoverWatchdogRunnable);
        this.deviceSdkTakeoverWatchdogRunnable = null;
    }

    private void startDeviceSdkTakeoverWatchdog(int round, String shotStepName) {
        this.stopDeviceSdkTakeoverWatchdog();
        if (!this.isDeviceSdkDemoTarget()) {
            return;
        }
        this.deviceSdkTakeoverWatchdogRunnable = () -> {
            if (!isRunning.get() || shouldStop.get() || !this.waitingForDeviceReply || this.expectedReplyRound != round) {
                return;
            }
            Logger.safeWarn(TAG, "GUI \u63a5\u7ba1 watchdog \u8d85\u65f6 round=" + round + "\uff0c\u8bf7\u6c42 Device \u5f3a\u5236\u9000\u51fa\u5e76\u7ed3\u675f\u672c\u8f6e");
            ScreenshotDiagLogger.phase("takeover_watchdog", "round=" + round);
            TestReplySdk.requestForceEndTakeover((Context)this, (String)DEVICE_SDK_PACKAGE, (String)("watchdog_round_" + round));
            this.postDelayedOnTestScheduler(() -> {
                if (!this.waitingForDeviceReply || this.expectedReplyRound != round) {
                    return;
                }
                this.reportTimeoutReply(round, "takeover_watchdog");
                TestReplyEvent synthetic = new TestReplyEvent.Builder().round(round).replyText("[\u5f3a\u5236\u7ed3\u675f] GUI\u63a5\u7ba1 watchdog \u8d85\u65f6").outputType("TEXT").finalReply(true).taskCompleted(false).timestampMs(System.currentTimeMillis()).build();
                this.proceedWaitResponseByEvent(synthetic, "takeover_watchdog");
            }, 3000L);
        };
        this.postDelayedOnTestScheduler(this.deviceSdkTakeoverWatchdogRunnable, DEVICE_SDK_GUI_TAKEOVER_MAX_MS);
        Logger.safeInfo(TAG, "GUI \u63a5\u7ba1 watchdog \u5df2\u542f\u52a8 round=" + round + " maxMs=" + DEVICE_SDK_GUI_TAKEOVER_MAX_MS);
    }

    private void loadClarifyMessagesFromFlow() {
        this.clarifyMessagesByRound.clear();
        if (this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return;
        }
        for (TestStep step : this.currentFlow.getSteps()) {
            String clarify;
            String stepId = step.getStepId();
            if (stepId == null || !stepId.startsWith("wait-response-")) continue;
            int round = this.parseReplyRoundFromStepId(stepId);
            Params params = step.getParams();
            if (params == null || (clarify = params.getClarifyMessage()) == null || clarify.trim().isEmpty()) continue;
            this.clarifyMessagesByRound.put(round, clarify.trim());
        }
        if (!this.clarifyMessagesByRound.isEmpty()) {
            Logger.safeInfo(TAG, "\u5df2\u52a0\u8f7d\u6f84\u6e05\u6d88\u606f\u914d\u7f6e: " + this.clarifyMessagesByRound);
        }
    }

    private String getClarifyMessageForRound(int round) {
        return this.clarifyMessagesByRound.get(round);
    }

    private void requestDeviceClarifySend(String clarifyText, int round) {
        if (clarifyText == null || clarifyText.isEmpty()) {
            return;
        }
        Intent intent = new Intent("com.example.testreply.ACTION_CLARIFY_SEND");
        intent.putExtra("clarify_text", clarifyText);
        intent.putExtra("round", round);
        intent.setPackage(DEVICE_SDK_PACKAGE);
        this.sendBroadcast(intent);
        Logger.safeInfo(TAG, "\u5df2\u8bf7\u6c42 Device \u53d1\u9001\u6f84\u6e05\u6d88\u606f round=" + round + " text=" + clarifyText);
        ScreenshotDiagLogger.phase("clarify_send", "round=" + round);
    }

    private void handleClarificationDialog(TestReplyEvent event) {
        this.logAisfReplyToLiveLog(event);
        int round = this.expectedReplyRound > 0 ? this.expectedReplyRound : (event != null ? event.getRound() : 0);
        String clarify = this.getClarifyMessageForRound(round);
        if (clarify == null || clarify.isEmpty()) {
            Logger.safeInfo(TAG, "\u6536\u5230\u6f84\u6e05 DIALOG \u4f46\u672c\u8f6e\u65e0\u6f84\u6e05\u6d88\u606f\u914d\u7f6e round=" + round);
            return;
        }
        Logger.safeInfo(TAG, "\u6536\u5230\u6f84\u6e05 DIALOG\uff0c\u53d1\u9001\u6f84\u6e05\u6d88\u606f round=" + round);
        this.requestDeviceClarifySend(clarify, round);
    }

    private boolean hasRemainingWaitResponseSteps() {
        if (this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return false;
        }
        List<TestStep> steps = this.currentFlow.getSteps();
        for (int i = this.currentStepIndex; i < steps.size(); ++i) {
            String stepId = steps.get(i).getStepId();
            if (stepId == null || !stepId.startsWith("wait-response")) continue;
            return true;
        }
        return false;
    }

    private boolean isDeviceSdkSkippableAfterAisfComplete(TestStep step) {
        String stepId;
        if (step == null) {
            return false;
        }
        String string = stepId = step.getStepId() != null ? step.getStepId() : "";
        if ("wait".equals(step.getAction())) {
            return stepId.startsWith("wait-screenshot") || stepId.startsWith("wait-response");
        }
        return stepId.startsWith("click-input-") || stepId.startsWith("input-message-") || stepId.startsWith("click-send-");
    }

    private boolean skipDeviceSdkTimedPaddingIfNeeded(TestStep step) {
        boolean skipAfterAisf;
        if (!this.isDeviceSdkDemoTarget() || step == null) {
            return false;
        }
        String stepId = step.getStepId() != null ? step.getStepId() : "";
        boolean skipScreenshotPad = "wait".equals(step.getAction()) && stepId.startsWith("wait-screenshot");
        boolean bl = skipAfterAisf = this.deviceSdkQaFinishedByAisf && this.isDeviceSdkSkippableAfterAisfComplete(step);
        if (!skipScreenshotPad && !skipAfterAisf) {
            return false;
        }
        Logger.safeInfo(TAG, "\u8df3\u8fc7\u56fa\u5b9a\u7b49\u5f85/\u6b8b\u7559\u95ee\u7b54\u6b65\u9aa4(AISF\u4e8b\u4ef6\u9a71\u52a8): " + stepId + (this.deviceSdkQaFinishedByAisf ? " qaFinishedByAisf=true" : ""));
        ScreenshotDiagLogger.phase("skip_timed_wait", "stepId=" + stepId + " qaFinished=" + this.deviceSdkQaFinishedByAisf);
        ++this.currentStepIndex;
        this.stopStepTimeoutMonitor();
        this.postOnTestScheduler(this::executeNextStep);
        return true;
    }

    private void markDeviceSdkQaFinishedByAisf(String reason) {
        if (!this.deviceSdkQaFinishedByAisf) {
            Logger.safeInfo(TAG, "AISF \u5224\u5b9a\u672c\u7528\u4f8b\u95ee\u7b54\u5b8c\u6210: " + reason);
        }
        this.deviceSdkQaFinishedByAisf = true;
    }

    private TestReplyEvent takePendingReplyForRound(int round) {
        TestReplyEvent exact;
        TestReplyEvent taskDone = this.pendingTaskCompletedEvent;
        if (taskDone != null) {
            int doneRound;
            int n = doneRound = taskDone.getRound() > 0 ? taskDone.getRound() : round;
            if (doneRound == round) {
                this.pendingTaskCompletedEvent = null;
                return taskDone;
            }
            if (doneRound < round) {
                this.pendingTaskCompletedEvent = null;
                Logger.safeInfo(TAG, "\u4e22\u5f03\u8fc7\u671f AISF taskCompleted round=" + doneRound + "\uff08\u5f53\u524d\u7b49\u5f85 wait-response-" + round + "\uff09");
            }
        }
        if ((exact = this.pendingDeviceReplies.remove(round)) != null) {
            return exact;
        }
        TestReplyEvent best = null;
        Integer bestKey = null;
        for (Map.Entry<Integer, TestReplyEvent> e : this.pendingDeviceReplies.entrySet()) {
            if (e.getKey() == null || e.getKey() < round || best != null && e.getKey() <= bestKey) continue;
            best = e.getValue();
            bestKey = e.getKey();
        }
        if (bestKey != null) {
            this.pendingDeviceReplies.remove(bestKey);
            return best;
        }
        return null;
    }

    private void cacheDeviceReply(TestReplyEvent event) {
        if (event == null || !event.signalsWaitComplete()) {
            return;
        }
        if (event.isTaskCompleted()) {
            int round = event.getRound() > 0 ? event.getRound() : 1;
            this.pendingTaskCompletedEvent = event;
            if (!this.hasRemainingWaitResponseStepsAfterRound(round)) {
                this.markDeviceSdkQaFinishedByAisf("task_completed_cached_last_round=" + round);
            } else {
                Logger.safeInfo(TAG, "\u7f13\u5b58 AISF taskCompleted round=" + round + "\uff08\u8fd8\u6709\u540e\u7eed wait-response\uff0c\u4e0d\u7ed3\u675f\u6574\u7528\u4f8b\u95ee\u7b54\uff09");
            }
            return;
        }
        int round = event.getRound() > 0 ? event.getRound() : 1;
        this.pendingDeviceReplies.put(round, event);
        Logger.safeInfo(TAG, "\u7f13\u5b58 AISF \u56de\u590d round=" + round + "\uff08wait-response \u5c1a\u672a\u5f00\u59cb\uff0c\u8fdb\u5165\u6b65\u9aa4\u540e\u7acb\u5373\u6d88\u8d39\uff09");
        if (!this.hasRemainingWaitResponseStepsAfterRound(round)) {
            this.markDeviceSdkQaFinishedByAisf("last_round_reply_cached round=" + round);
        }
    }

    private boolean hasRemainingWaitResponseStepsAfterRound(int completedRound) {
        if (this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return false;
        }
        for (TestStep step : this.currentFlow.getSteps()) {
            int stepRound;
            String stepId = step.getStepId();
            if (stepId == null || !stepId.startsWith("wait-response") || (stepRound = this.parseReplyRoundFromStepId(stepId)) <= completedRound) continue;
            return true;
        }
        return false;
    }

    private boolean isQaCaseType() {
        return this.currentFlow != null && "QA".equalsIgnoreCase(this.currentFlow.getCaseType());
    }

    private boolean isGuiCaseType() {
        return this.currentFlow != null && "GUI".equalsIgnoreCase(this.currentFlow.getCaseType());
    }

    private static boolean isPrematureGuiPlanReply(TestReplyEvent event) {
        if (event == null || event.isTaskCompleted()) {
            return false;
        }
        if (!"TEXT".equals(event.getOutputType())) {
            return false;
        }
        String text = event.getReplyText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.contains("GUI\u4efb\u52a1\u6267\u884c\u5931\u8d25") || text.startsWith("[\u5f3a\u5236\u7ed3\u675f]")) {
            return false;
        }
        return text.contains("\u4efb\u52a1\u6267\u884c\u89c4\u5212") || text.contains("\u5df2\u4e3a\u60a8\u751f\u6210\u4efb\u52a1\u6267\u884c\u89c4\u5212");
    }

    private void continueAfterStep(String stepName) {
        if (this.isQaCaseType() || this.shouldSkipDeviceSdkPreQaStepScreenshot()) {
            this.executeNextStep();
        } else {
            this.captureScreenAndContinue(stepName);
        }
    }

    private boolean shouldSkipDeviceSdkPreQaStepScreenshot() {
        if (!this.isDeviceSdkDemoTarget() || this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return false;
        }
        int completedIdx = this.currentStepIndex - 1;
        if (completedIdx < 0 || completedIdx >= this.currentFlow.getSteps().size()) {
            return false;
        }
        TestStep completed = this.currentFlow.getSteps().get(completedIdx);
        String stepId = completed != null ? completed.getStepId() : null;
        return stepId != null && DEVICE_SDK_PRE_QA_STEP_IDS.contains(stepId);
    }

    private void beginWaitForDeviceReply(TestStep step, long ignoredExcelWaitMs, String shotStepName) {
        int round = this.parseReplyRoundFromStepId(step.getStepId());
        this.waitingForDeviceReply = true;
        this.expectedReplyRound = round;
        this.pendingWaitShotStepName = shotStepName;
        TestReplyEvent cached = this.takePendingReplyForRound(round);
        if (cached != null) {
            if (TestExecuteService.isPrematureGuiPlanReply(cached)) {
                Logger.safeInfo(TAG, "wait-response round=" + round + " \u7f13\u5b58\u4e3a GUI \u89c4\u5212\u56de\u590d\uff0c\u7ee7\u7eed\u7b49\u5f85\u63a5\u7ba1\u7ed3\u675f");
                WebServerClient midClient = WebServerClient.getInstance(null);
                if (midClient != null) {
                    midClient.sendCaseReply(cached);
                }
            } else {
                String reason = cached.isTaskCompleted() ? "task_completed_cached" : "reply_cached";
                Logger.safeInfo(TAG, "wait-response round=" + round + " \u547d\u4e2d\u5df2\u7f13\u5b58 AISF \u4e8b\u4ef6\uff0c\u7acb\u5373\u7ed3\u675f");
                this.proceedWaitResponseByEvent(cached, reason);
                return;
            }
        }
        Logger.safeInfo(TAG, "wait-response \u4ec5\u4e8b\u4ef6\u7b49\u5f85 AISF(TEXT \u4f18\u5148): round=" + round
                + " textFirstTimeoutMs=" + DEVICE_SDK_TEXT_FIRST_TIMEOUT_MS
                + " (Excel duration=" + ignoredExcelWaitMs + " \u5ffd\u7565\uff0c\u4e0d\u7528\u56fa\u5b9a\u65f6\u957f\u7ed3\u675f)");
        ScreenshotDiagLogger.phase("wait_response", "round=" + round
                + " textFirstTimeoutMs=" + DEVICE_SDK_TEXT_FIRST_TIMEOUT_MS + " mode=aisf_text_first");
        this.cancelDeviceSdkDialogHandlers();
        // \u5148\u9a8c\u4e3a TEXT\uff1a\u4e0d\u542f\u52a8 GUI \u622a\u56fe\u8f6e\u8be2\u4e0e 300s watchdog\uff0c
        // \u6536\u5230 GUI_TASK \u4e8b\u4ef6\u540e\u518d\u5347\u7ea7\uff08upgradeWaitResponseToGui\uff09\u3002
        this.waitResponseIsGuiUpgraded = false;
        this.scheduleWaitResponseTimeout(round, shotStepName,
                DEVICE_SDK_TEXT_FIRST_TIMEOUT_MS, "text_timeout");
    }

    /** \u5b89\u6392\u672c\u8f6e wait-response \u7684\u8d85\u65f6\u515c\u5e95\uff08\u91cd\u65b0\u8ba1\u65f6\uff09\u3002 */
    private void scheduleWaitResponseTimeout(
            int round, String shotStepName, long timeoutMs, String reason) {
        this.cancelWaitResponseTimeout();
        this.waitResponseTimeoutRunnable = () -> {
            if (!this.waitingForDeviceReply) {
                return;
            }
            this.waitingForDeviceReply = false;
            this.stopDeviceSdkGuiPollScreenshots();
            this.stopDeviceSdkTakeoverWatchdog();
            Logger.safeWarn(TAG, "wait-response round=" + round + " \u8d85\u65f6\uff08\u672a\u6536\u5230 AISF \u56de\u590d\uff09 reason="
                    + reason + " timeoutMs=" + timeoutMs + "\uff0c\u7ee7\u7eed\u4e0b\u4e00\u6b65");
            ScreenshotDiagLogger.phase("wait_response_timeout", "round=" + round + " reason=" + reason);
            this.reportTimeoutReply(round, reason);
            // 与 proceedWaitResponseByEvent 一致：仅 GUI 轮/已离开 Device 时才拉回
            final boolean needReturnChat = this.isDeviceSdkDemoTarget()
                    && this.hasRemainingWaitResponseStepsAfterRound(round)
                    && this.shouldReturnDeviceSdkChatAfterRound();
            final String continueShot = shotStepName;
            final int roundForReturn = round;
            final String timeoutReason = reason;
            Runnable next = () -> {
                if (needReturnChat && isRunning.get() && !shouldStop.get()) {
                    Logger.safeInfo(TAG, "\u975e\u672b\u8f6e wait-response-" + roundForReturn
                            + " \u8d85\u65f6\u7ed3\u675f\uff0c\u62c9\u56de DeviceSDK \u804a\u5929\u9875\u4ee5\u4fbf\u4e0b\u4e00\u8f6e\u8f93\u5165 reason="
                            + timeoutReason);
                    ScreenshotDiagLogger.phase("return_chat",
                            "between_rounds_timeout_r" + roundForReturn + "_" + timeoutReason);
                    this.bringDeviceSdkChatToFront(
                            "between_rounds_timeout_r" + roundForReturn + "_" + timeoutReason);
                }
                this.continueAfterStep(continueShot);
            };
            if (needReturnChat) {
                this.postDelayedOnTestScheduler(next, DEVICE_SDK_RETURN_QA_DELAY_MS);
            } else {
                next.run();
            }
        };
        this.postDelayedOnTestScheduler(this.waitResponseTimeoutRunnable, timeoutMs);
    }

    /**
     * 非末轮结束后是否需要把 DeviceSDK 聊天页拉回前台。
     * 纯文字轮人本来就在 Device 上，不必每轮强行 bring（会闪屏/重进 QA）；
     * 仅本轮发生过 GUI 接管，或当前前台已不在 Device 时才拉回。
     */
    private boolean shouldReturnDeviceSdkChatAfterRound() {
        if (this.waitResponseIsGuiUpgraded) {
            return true;
        }
        if (!this.isTargetAppAlreadyForeground()) {
            Logger.safeInfo(TAG, "本轮非 GUI，但 DeviceSDK 已不在前台，仍需拉回聊天页");
            return true;
        }
        Logger.safeInfo(TAG, "本轮为文字回复且 DeviceSDK 已在前台，跳过拉回");
        return false;
    }

    /** \u6536\u5230 GUI_TASK\uff1a\u628a\u5f53\u524d\u8f6e\u4ece TEXT \u4f18\u5148\u5347\u7ea7\u4e3a GUI \u8f6e\uff08\u622a\u56fe\u8f6e\u8be2 + 300s watchdog + \u957f\u5b89\u5168\u8d85\u65f6\uff09\u3002 */
    private void upgradeWaitResponseToGui(int round) {
        if (!this.waitingForDeviceReply || this.waitResponseIsGuiUpgraded) {
            return;
        }
        if (round > 0 && this.expectedReplyRound > 0 && round != this.expectedReplyRound) {
            return;
        }
        this.waitResponseIsGuiUpgraded = true;
        long safetyMs = DEVICE_SDK_WAIT_RESPONSE_SAFETY_MS;
        if (this.currentFlow != null && this.currentFlow.getTimeout() > 0) {
            safetyMs = Math.max(safetyMs, (long)this.currentFlow.getTimeout());
        }
        Logger.safeInfo(TAG, "wait-response round=" + round + " \u6536\u5230 GUI_TASK\uff0c\u5347\u7ea7\u4e3a GUI \u8f6e\uff1a\u542f\u52a8\u622a\u56fe\u8f6e\u8be2 + watchdog\uff0c\u5b89\u5168\u8d85\u65f6=" + safetyMs);
        ScreenshotDiagLogger.phase("wait_response_upgrade_gui", "round=" + round + " safetyMs=" + safetyMs);
        this.startDeviceSdkGuiPollScreenshots();
        this.startDeviceSdkTakeoverWatchdog(this.expectedReplyRound, this.pendingWaitShotStepName);
        this.scheduleWaitResponseTimeout(round, this.pendingWaitShotStepName, safetyMs, "safety_timeout");
    }

    // AISF 事件缺失时也要给平台留痕，否则该轮在 replies.json 中完全不存在
    private void reportTimeoutReply(int round, String reason) {
        WebServerClient client = WebServerClient.getInstance(null);
        if (client == null) {
            Logger.safeWarn(TAG, "\u8d85\u65f6\u8865\u62a5\u5931\u8d25\uff1aWebServer \u5ba2\u6237\u7aef\u672a\u521d\u59cb\u5316 round=" + round + " reason=" + reason);
            return;
        }
        String text = "\uff08\u8d85\u65f6\u672a\u6536\u5230 AISF \u56de\u590d\uff1a" + reason + "\uff09";
        TestReplyEvent timeoutEvent = new TestReplyEvent.Builder()
                .round(round)
                .replyText(text)
                .outputType("TEXT")
                .finalReply(true)
                .taskCompleted(false)
                .timestampMs(System.currentTimeMillis())
                .latencyMs(0L)
                .firstTokenLatencyMs(0L)
                .build();
        client.sendCaseReply(timeoutEvent);
        Logger.safeWarn(TAG, "\u5df2\u8865\u62a5\u8d85\u65f6\u56de\u590d round=" + round + " reason=" + reason);
    }

    private void proceedWaitResponseByEvent(TestReplyEvent event, String reason) {
        if (!this.waitingForDeviceReply) {
            return;
        }
        this.waitingForDeviceReply = false;
        this.cancelWaitResponseTimeout();
        this.stopDeviceSdkGuiPollScreenshots();
        this.stopDeviceSdkTakeoverWatchdog();
        String shotStepName = this.pendingWaitShotStepName;
        this.pendingWaitShotStepName = null;
        int completedRound = this.expectedReplyRound;
        if (event != null && event.getRound() > 0) {
            completedRound = event.getRound();
        }
        boolean lastWaitRound = !this.hasRemainingWaitResponseStepsAfterRound(completedRound);
        if (lastWaitRound) {
            if (event != null && event.isTaskCompleted()) {
                this.markDeviceSdkQaFinishedByAisf("task_completed_last_round=" + completedRound);
            } else {
                this.markDeviceSdkQaFinishedByAisf("last_round_reply round=" + completedRound);
            }
            // 末轮 AISF 一结束就报 finishing：DeviceSDK 回 QA 时网页不应仍显示「执行中」，
            // 避免误以为上一例还在跑时已开始输下一例（下一例仍须等 test_completed 后才下发）。
            if (this.currentFlow != null && this.currentFlow.getSteps() != null
                    && !this.currentFlow.getSteps().isEmpty()) {
                int totalSteps = this.currentFlow.getSteps().size();
                this.reportStepProgress(totalSteps, totalSteps, "done", "finishing");
            }
        } else if (event != null && event.isTaskCompleted()) {
            Logger.safeInfo(TAG, "AISF taskCompleted \u4ec5\u7ed3\u675f\u672c\u8f6e wait-response-" + completedRound + "\uff0c\u8fd8\u6709\u540e\u7eed\u8f6e\u6b21");
        } else if (!this.hasRemainingWaitResponseSteps()) {
            this.markDeviceSdkQaFinishedByAisf("no_more_wait_response");
            if (this.currentFlow != null && this.currentFlow.getSteps() != null
                    && !this.currentFlow.getSteps().isEmpty()) {
                int totalSteps = this.currentFlow.getSteps().size();
                this.reportStepProgress(totalSteps, totalSteps, "done", "finishing");
            }
        }
        long latency = event != null ? event.getLatencyMs() : 0L;
        Logger.safeInfo(TAG, "wait-response round=" + completedRound + " \u7531 AISF \u4e8b\u4ef6\u7ed3\u675f: " + reason + " latencyMs=" + latency);
        ScreenshotDiagLogger.phase("wait_response_event", "round=" + completedRound + " latencyMs=" + latency + " reason=" + reason);
        // AISF 完成后先停留 3s。末轮拍 final_result；非末轮先拉回聊天页，方便下一轮输入/发送。
        String finalShot = shotStepName != null ? shotStepName : "step_unknown";
        if (this.isDeviceSdkDemoTarget()) {
            final boolean takeFinalResult = lastWaitRound && !this.isQaCaseType();
            // 仅 GUI 轮（或已离开 Device）结束后才拉回；纯文字轮且已在 Device 上不刷屏拉起
            final boolean returnChatForNextRound = !lastWaitRound && this.shouldReturnDeviceSdkChatAfterRound();
            final int roundForReturn = completedRound;
            this.postDelayedOnTestScheduler(() -> {
                if (takeFinalResult && isRunning.get() && !shouldStop.get()) {
                    Logger.safeInfo(TAG, "执行结束已等待 3s，截 final_result");
                    ScreenshotDiagLogger.phase("final_result", "after_ms=" + DEVICE_SDK_RETURN_QA_DELAY_MS);
                    this.captureNamedScreenSync("final_result");
                }
                if (returnChatForNextRound && isRunning.get() && !shouldStop.get()) {
                    Logger.safeInfo(TAG, "非末轮 wait-response-" + roundForReturn
                            + " 结束，拉回 DeviceSDK 聊天页以便下一轮输入");
                    ScreenshotDiagLogger.phase("return_chat", "between_rounds_r" + roundForReturn);
                    this.bringDeviceSdkChatToFront("between_rounds_r" + roundForReturn);
                }
                this.continueAfterStep(finalShot);
            }, DEVICE_SDK_RETURN_QA_DELAY_MS);
        } else {
            this.postDelayedOnTestScheduler(() -> this.continueAfterStep(finalShot), this.isQaCaseType() ? 0L : 300L);
        }
    }

    /** \u62c9\u56de DeviceSDK QA \u804a\u5929\u9875\uff08\u975e Config\uff09\u3002\u8fd4\u56de\u662f\u5426\u786e\u8ba4\u524d\u53f0\u3002 */
    private boolean bringDeviceSdkChatToFront(String reason) {
        try {
            Logger.safeInfo(TAG, "\u62c9\u56de DeviceSDK \u804a\u5929\u6846 QAActivity (" + reason + ")");
            ScreenshotDiagLogger.phase("return_chat", reason);
            // provider bring_front \u73b0\u5df2\u6539\u4e3a QAActivity\uff1b\u4e0d\u8981\u518d\u8d70 legacy MAIN(\u4f1a\u8fdb Config)
            boolean providerOk = com.example.testreply.TestReplySdk.bringDeviceToForeground(
                    getApplicationContext());
            Logger.safeInfo(TAG, "bring_front provider ok=" + providerOk + " (" + reason + ")");
            this.launchDeviceSdkQaActivity(reason);
            this.bringDeviceSdkViaA11y(reason);
            if (this.waitForTargetAppForeground(2500L)) {
                return true;
            }
            // MIUI \u4e0a\u5168\u5c4f App \u5360\u524d\u53f0\u65f6\uff0c\u5148 HOME \u518d\u62c9\u66f4\u7a33
            TestAccessibilityService a11y = TestAccessibilityService.getInstance();
            if (a11y != null) {
                a11y.pressHomeForHandoff();
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                this.bringDeviceSdkViaA11y(reason + "_after_home");
            }
            return this.waitForTargetAppForeground(3000L);
        } catch (Exception e) {
            Logger.safeWarn(TAG, "\u62c9\u56de DeviceSDK \u804a\u5929\u6846\u5931\u8d25: " + e.getMessage());
            return false;
        }
    }

    /** 收到 GUI_TASK 说明该轮是 GUI 轮：立刻上报轮次开始，避免长轮进行中截图被归到上一轮。 */
    private void reportRoundStart(int round, long sentAtMs, String outputType) {
        if (round <= 0 || !this.reportedRoundStarts.add(round)) {
            return;
        }
        WebServerClient client = WebServerClient.getInstance(null);
        if (client == null) {
            this.reportedRoundStarts.remove(round);
            Logger.safeWarn(TAG, "round_start 未上报：WebServerClient 未初始化 round=" + round);
            return;
        }
        Logger.safeInfo(TAG, "round_start 上报 GUI 轮开始 round=" + round + " sentAtMs=" + sentAtMs);
        client.sendRoundStart(null, null, round, sentAtMs, outputType);
    }

    private void logAisfReplyToLiveLog(TestReplyEvent event) {
        boolean hasReply;
        if (event == null) {
            return;
        }
        String reply = event.getReplyText();
        boolean bl = hasReply = reply != null && !reply.trim().isEmpty();
        if (!hasReply && !event.isTaskCompleted()) {
            return;
        }
        String userMsg = event.getUserMessage();
        if (userMsg != null && !userMsg.trim().isEmpty()) {
            Logger.safeInfo(TAG, "[AISF] \u7528\u6237 round=" + event.getRound() + " type=" + event.getOutputType() + " text=" + userMsg.trim());
        }
        if (event.isTaskCompleted()) {
            Logger.safeInfo(TAG, "[AISF] \u4efb\u52a1\u5b8c\u6210 round=" + event.getRound() + " type=" + event.getOutputType() + " latencyMs=" + event.getLatencyMs() + " firstTokenLatencyMs=" + event.getFirstTokenLatencyMs() + (hasReply ? " text=" + reply.trim() : ""));
            return;
        }
        if (hasReply) {
            String stage = event.isFinalReply() ? "\u6700\u7ec8\u56de\u590d" : "\u56de\u590d";
            Logger.safeInfo(TAG, "[AISF] " + stage + " round=" + event.getRound() + " type=" + event.getOutputType() + " final=" + event.isFinalReply() + " latencyMs=" + event.getLatencyMs() + " firstTokenLatencyMs=" + event.getFirstTokenLatencyMs() + " text=" + reply.trim());
        }
    }

    private void handleDeviceReply(TestReplyEvent event) {
        if (event == null) {
            return;
        }
        if ("DIALOG".equals(event.getOutputType()) && this.isDeviceSdkDemoTarget() && this.waitingForDeviceReply) {
            this.handleClarificationDialog(event);
            return;
        }
        this.logAisfReplyToLiveLog(event);
        if (this.isDeviceSdkDemoTarget() && this.waitingForDeviceReply && "GUI_TASK".equals(event.getOutputType()) && !this.isQaCaseType()) {
            int guiRound = event.getRound() > 0 ? event.getRound() : this.expectedReplyRound;
            this.reportRoundStart(guiRound, event.getSentAtMs(), event.getOutputType());
            this.upgradeWaitResponseToGui(this.expectedReplyRound);
        }
        WebServerClient client = WebServerClient.getInstance(null);
        // 完成信号 + 有文本的中途 draft 都写入网页；后者不代表本轮 wait 结束。
        String replyText = event.getReplyText();
        boolean hasReplyText = replyText != null && !replyText.trim().isEmpty();
        if (event.signalsWaitComplete() || hasReplyText) {
            TestReplyEvent toSave = event;
            if (this.waitingForDeviceReply && this.expectedReplyRound > 0 && !event.isTaskCompleted() && event.getRound() != this.expectedReplyRound) {
                toSave = new TestReplyEvent.Builder().sessionId(event.getSessionId()).round(this.expectedReplyRound).userMessage(event.getUserMessage()).replyText(event.getReplyText()).outputType(event.getOutputType()).finalReply(event.isFinalReply()).taskCompleted(event.isTaskCompleted()).timestampMs(event.getTimestampMs()).sentAtMs(event.getSentAtMs()).latencyMs(event.getLatencyMs()).firstTokenLatencyMs(event.getFirstTokenLatencyMs()).taskId(event.getTaskId()).caseId(event.getCaseId()).segments(event.getSegments()).build();
            }
            if (client != null) {
                client.sendCaseReply(toSave);
            } else {
                Logger.safeWarn(TAG, "\u6536\u5230 Device \u56de\u590d\u4f46 WebServer \u5ba2\u6237\u7aef\u672a\u521d\u59cb\u5316");
            }
        }
        if (!this.isDeviceSdkDemoTarget() || !event.signalsWaitComplete()) {
            return;
        }
        if (TestExecuteService.isGuiTakeoverForceComplete(event)) {
            if (!this.waitingForDeviceReply) {
                Logger.safeInfo(TAG, "\u5ffd\u7565\u975e\u7b49\u5f85\u4e2d\u7684 GUI \u63a5\u7ba1\u5f3a\u5236\u7ed3\u675f\u4fe1\u53f7: " + event.getReplyText());
                return;
            }
            Logger.safeWarn(TAG, "\u6536\u5230 GUI \u63a5\u7ba1\u5f3a\u5236\u7ed3\u675f\u4fe1\u53f7\uff0c\u7ed3\u675f wait-response-" + this.expectedReplyRound + " text=" + event.getReplyText());
            this.proceedWaitResponseByEvent(event, "gui_takeover_force");
            return;
        }
        if (!this.waitingForDeviceReply) {
            this.cacheDeviceReply(event);
            return;
        }
        if (event.isTaskCompleted()) {
            this.proceedWaitResponseByEvent(event, "task_completed");
            return;
        }
        if (this.isDeviceSdkDemoTarget() && (this.isGuiCaseType() || !this.isQaCaseType()) && TestExecuteService.isPrematureGuiPlanReply(event)) {
            Logger.safeInfo(TAG, "GUI \u89c4\u5212\u56de\u590d\u5c1a\u672a\u5b8c\u6210\u63a5\u7ba1\uff0c\u7ee7\u7eed wait-response-" + this.expectedReplyRound + " text=" + event.getReplyText());
            WebServerClient midClient = WebServerClient.getInstance(null);
            if (midClient != null) {
                midClient.sendCaseReply(event);
            }
            this.upgradeWaitResponseToGui(this.expectedReplyRound);
            return;
        }
        // GUI 接管已开始但尚未 taskCompleted：中途消息上面已上报网页，
        // 这里只禁止结束 wait / 拉回 Device，避免打断第三方 App 操控。
        if (this.waitResponseIsGuiUpgraded) {
            Logger.safeInfo(TAG, "GUI \u63a5\u7ba1\u8fdb\u884c\u4e2d\uff0c\u4e2d\u9014 AISF \u5df2\u4e0a\u62a5\u7f51\u9875\uff0c\u4e0d\u7ed3\u675f wait/\u4e0d\u62c9\u56de Device"
                    + " wait-response-" + this.expectedReplyRound
                    + " type=" + event.getOutputType()
                    + " final=" + event.isFinalReply()
                    + " text=" + event.getReplyText());
            return;
        }
        int round = event.getRound();
        if (round > 0 && this.expectedReplyRound > 0 && round < this.expectedReplyRound) {
            Logger.safeDebug(TAG, "\u5ffd\u7565\u8fc7\u671f AISF \u56de\u590d round=" + round + " expected=" + this.expectedReplyRound);
            return;
        }
        if (round > 0 && this.expectedReplyRound > 0 && round != this.expectedReplyRound) {
            Logger.safeWarn(TAG, "AISF round=" + round + " \u4e0e wait-response-" + this.expectedReplyRound + " \u4e0d\u4e00\u81f4\uff0c\u6309\u7528\u4f8b\u5185\u8f6e\u6b21\u5bf9\u9f50");
            if (TestExecuteService.isPrematureGuiPlanReply(event)) {
                Logger.safeInfo(TAG, "\u8df3\u8fc7\u89c4\u5212\u7c7b round \u5bf9\u9f50\u63d0\u524d\u7ed3\u675f");
                return;
            }
            this.proceedWaitResponseByEvent(event, "reply_final_round_align");
            return;
        }
        this.proceedWaitResponseByEvent(event, "reply_final");
    }

    private static boolean isGuiTakeoverForceComplete(TestReplyEvent event) {
        if (event == null || !event.isFinalReply()) {
            return false;
        }
        String text = event.getReplyText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.startsWith("[\u5f3a\u5236\u7ed3\u675f]") || text.contains("GUI\u63a5\u7ba1\u5f3a\u5236\u7ed3\u675f") || text.contains("GUI\u63a5\u7ba1 watchdog");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.safeDebug(TAG, "onStartCommand");
        if (intent != null && ACTION_START_TEST.equals(intent.getAction())) {
            String testFile = intent.getStringExtra(EXTRA_TEST_FILE);
            Logger.safeInfo(TAG, "\u542f\u52a8\u6d4b\u8bd5: " + testFile);
            this.startTest(testFile);
        }
        return 2;
    }

    private void startTest(String testFile) {
        Logger.safeInfo(TAG, "=== \u5f00\u59cb\u6d4b\u8bd5\u6d41\u7a0b: " + testFile + " ===");
        if (!isRunning.compareAndSet(false, true)) {
            Logger.safeWarn(TAG, "\u6d4b\u8bd5\u5df2\u5728\u8fd0\u884c\u4e2d");
            this.showToast("\u6d4b\u8bd5\u5df2\u5728\u8fd0\u884c\u4e2d");
            return;
        }
        this.handler.removeCallbacksAndMessages(null);
        this.clearTestScheduleCallbacks();
        this.cancelDeviceSdkDialogHandlers();
        Logger.safeInfo(TAG, "\u5df2\u6e05\u7406\u4e4b\u524d\u7684handler\u56de\u8c03");
        long runId = ++this.testRunId;
        this.testCompletionNotified.set(false);
        this.testLogsExported.set(false);
        this.pendingTestId = "";
        this.pendingTestSuccess = false;
        this.pendingTestMessage = "";
        this.resetWaitResponseState();
        shouldStop.set(false);
        this.currentStepIndex = 0;
        this.testStartTime = System.currentTimeMillis();
        try {
            TestAccessibilityService accessibilityService;
            String jsonContent = this.readTestFile(testFile);
            Logger.safeDebug(TAG, "JSON\u5185\u5bb9\u957f\u5ea6: " + jsonContent.length());
            JsonParser parser = new JsonParser();
            this.currentFlow = parser.parse(jsonContent);
            if (this.currentFlow == null) {
                Logger.safeError(TAG, "\u89e3\u6790\u6d4b\u8bd5\u6d41\u7a0b\u5931\u8d25");
                this.showToast("\u89e3\u6790\u6d4b\u8bd5\u6587\u4ef6\u5931\u8d25");
                this.finishTest(false, "\u89e3\u6790\u5931\u8d25");
                return;
            }
            Logger.safeInfo(TAG, "\u6d4b\u8bd5ID: " + this.currentFlow.getTestId() + ", \u6b65\u9aa4\u6570: " + this.currentFlow.getSteps().size());
            Logger fileLogger = Logger.getInstance((Context)this);
            if (fileLogger != null) {
                fileLogger.startSession(this.currentFlow.getTestId());
            }
            LiveLogStreamer.start();
            this.targetPackage = this.currentFlow.getTargetPackage();
            Logger.safeInfo(TAG, "\u76ee\u6807\u5e94\u7528: " + this.targetPackage);
            Logger.safeInfo(TAG, "\u6d4b\u8bd5ID: " + this.currentFlow.getTestId() + ", \u6b65\u9aa4\u6570: " + this.currentFlow.getSteps().size());
            if (this.isDeviceSdkDemoTarget()) {
                String caseId;
                this.reportedRoundStarts.clear();
                this.loadClarifyMessagesFromFlow();
                WebServerClient wsClient = WebServerClient.getInstance(null);
                String taskId = wsClient != null ? wsClient.getCurrentTaskId() : null;
                String string = caseId = wsClient != null ? wsClient.getCurrentCaseId() : null;
                if (caseId == null || caseId.isEmpty()) {
                    caseId = this.currentFlow.getTestId();
                }
                String serverUrl = null;
                if (wsClient != null && wsClient.getConfig() != null) {
                    serverUrl = wsClient.getConfig().getServerUrl();
                }
                TestReplySdk.notifyCaseStart((Context)this, (String)taskId, (String)caseId, (String)DEVICE_SDK_PACKAGE, serverUrl);
                Logger.safeInfo(TAG, "\u5df2\u901a\u77e5 Device case_start \u91cd\u7f6e\u8f6e\u6b21: taskId=" + taskId + " caseId=" + caseId + " serverUrl=" + serverUrl);
            }
            if ((accessibilityService = TestAccessibilityService.getInstance()) == null) {
                Logger.safeError(TAG, "\u65e0\u969c\u788d\u670d\u52a1\u672a\u8fde\u63a5");
                this.showToast("\u8bf7\u5148\u5f00\u542f\u65e0\u969c\u788d\u670d\u52a1");
                this.finishTest(false, "\u65e0\u969c\u788d\u670d\u52a1\u672a\u8fde\u63a5");
                return;
            }
            this.commandExecutor.setAccessibilityService(accessibilityService);
            this.screenshotManager.startTest(this.currentFlow.getTestId());
            if (this.isDeviceSdkDemoTarget()) {
                this.screenshotManager.clearLocalScreenshots(this.currentFlow.getTestId());
                ScreenshotDiagLogger.startSession(this.currentFlow.getTestId());
                this.ensureTestScheduleThread();
                ScreenshotDiagLogger.phase("test_start", "testId=" + this.currentFlow.getTestId() + " steps=" + this.currentFlow.getSteps().size() + " scheduler=test-schedule-thread");
            }
            this.updateNotification("\u6d4b\u8bd5\u8fdb\u884c\u4e2d: " + this.currentFlow.getTestId());
            this.launchTargetApp();
            if (this.isUnicomCloudPhoneTarget()) {
                this.handler.postDelayed(this::handleLaunchAppDialog, 500L);
                this.handler.postDelayed(this::handleLaunchAppDialog, 1500L);
            } else if (this.isDeviceSdkDemoTarget()) {
                this.scheduleDeviceSdkBlockingDialogHandlers();
            } else {
                this.scheduleGenericBlockingDialogHandlers();
            }
            this.startTotalTimeoutMonitor();
            this.postDelayedOnTestScheduler(this::executeNextStep, 2000L);
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u542f\u52a8\u6d4b\u8bd5\u5931\u8d25", e);
            this.showToast("\u542f\u52a8\u6d4b\u8bd5\u5931\u8d25: " + e.getMessage());
            this.finishTest(false, "\u542f\u52a8\u5931\u8d25: " + e.getMessage());
        }
    }

    private long computeTimeoutBufferMs() {
        int stepCount = 0;
        if (this.currentFlow != null && this.currentFlow.getSteps() != null) {
            stepCount = this.currentFlow.getSteps().size();
        }
        return 15000L + (long)stepCount * 1000L;
    }

    private long computeDeviceSdkCrossAppWaitMs() {
        if (!this.isDeviceSdkDemoTarget() || this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return 0L;
        }
        long total = 0L;
        for (TestStep step : this.currentFlow.getSteps()) {
            if (!this.isDeviceSdkCrossAppPollingStep(step)) continue;
            long duration = 500L;
            if (step.getParams() != null && step.getParams().getDuration() > 0) {
                duration = step.getParams().getDuration();
            }
            total += duration + 300L + 4000L;
        }
        return total;
    }

    private void startTotalTimeoutMonitor() {
        long baseTimeoutMs = 60000L;
        if (this.currentFlow != null && this.currentFlow.getTimeout() > 0) {
            baseTimeoutMs = this.currentFlow.getTimeout();
        }
        long bufferMs = this.computeTimeoutBufferMs();
        long crossAppWaitMs = this.isDeviceSdkDemoTarget() ? this.computeDeviceSdkCrossAppWaitMs() : 0L;
        long finalTimeoutMs = baseTimeoutMs + bufferMs + crossAppWaitMs;
        int stepCount = this.currentFlow != null && this.currentFlow.getSteps() != null ? this.currentFlow.getSteps().size() : 0;
        Logger.safeInfo(TAG, "\u8bbe\u7f6e\u6574\u4f53\u8d85\u65f6: " + baseTimeoutMs + "ms(JSON) + " + bufferMs + "ms(\u7f13\u51b2: 15s + " + stepCount + "\u6b65\u00d71s)" + (crossAppWaitMs > 0L ? " + " + crossAppWaitMs + "ms(\u8de8App\u8f6e\u8be2)" : "") + " = " + finalTimeoutMs + "ms" + (this.isUnicomCloudPhoneTarget() ? " (\u8054\u901a\u5df2\u542b\u7f13\u51b2)" : ""));
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.configuredTimeout(baseTimeoutMs, bufferMs + crossAppWaitMs, finalTimeoutMs);
        }
        this.totalTimeoutRunnable = () -> {
            if (isRunning.get()) {
                Logger.safeWarn(TAG, "\u6d4b\u8bd5\u6574\u4f53\u8d85\u65f6(" + finalTimeoutMs + "ms)\uff0c\u5f3a\u5236\u7ed3\u675f");
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.warn("\u6d4b\u8bd5\u6574\u4f53\u8d85\u65f6: limit=" + finalTimeoutMs + "ms stepIndex=" + (this.currentStepIndex + 1) + " capturedSteps\u2248" + this.currentStepIndex);
                }
                this.finishTest(false, "\u6d4b\u8bd5\u8d85\u65f6");
            }
        };
        this.postDelayedOnTestScheduler(this.totalTimeoutRunnable, finalTimeoutMs);
    }

    private void stopTotalTimeoutMonitor() {
        if (this.totalTimeoutRunnable != null) {
            this.handler.removeCallbacks(this.totalTimeoutRunnable);
            if (this.testScheduleHandler != null) {
                this.testScheduleHandler.removeCallbacks(this.totalTimeoutRunnable);
            }
            this.totalTimeoutRunnable = null;
        }
    }

    private void startStepTimeoutMonitor() {
        this.stopStepTimeoutMonitor();
        this.stepTimeoutRunnable = () -> {
            Logger.safeWarn(TAG, "\u6b65\u9aa4 " + (this.currentStepIndex + 1) + " \u8d85\u65f6\uff0c\u8df3\u8fc7");
            ++this.currentStepIndex;
            this.executeNextStep();
        };
        this.postDelayedOnTestScheduler(this.stepTimeoutRunnable, 10000L);
    }

    private void stopStepTimeoutMonitor() {
        if (this.stepTimeoutRunnable != null) {
            this.handler.removeCallbacks(this.stepTimeoutRunnable);
            if (this.testScheduleHandler != null) {
                this.testScheduleHandler.removeCallbacks(this.stepTimeoutRunnable);
            }
            this.stepTimeoutRunnable = null;
        }
    }

    private void executeNextStep() {
        if (shouldStop.get() || !isRunning.get()) {
            Logger.safeInfo(TAG, "\u6d4b\u8bd5\u88ab\u505c\u6b62\uff0c\u9000\u51fa\u6267\u884c");
            return;
        }
        if (this.currentFlow == null || this.currentFlow.getSteps() == null) {
            this.finishTest(false, "\u6d41\u7a0b\u4e3a\u7a7a");
            return;
        }
        if (this.currentStepIndex >= this.currentFlow.getSteps().size()) {
            Logger.safeInfo(TAG, "\u6240\u6709\u6b65\u9aa4\u6267\u884c\u5b8c\u6210\uff0c\u5f53\u524d\u7d22\u5f15: " + this.currentStepIndex + ", \u603b\u6b65\u9aa4: " + this.currentFlow.getSteps().size());
            this.finishTest(true, "\u6240\u6709\u6b65\u9aa4\u5b8c\u6210");
            return;
        }
        if (this.isUnicomCloudPhoneTarget()) {
            this.executeNextStepUnicomLegacy();
            return;
        }
        this.stopStepTimeoutMonitor();
        this.startStepTimeoutMonitor();
        TestStep step = this.currentFlow.getSteps().get(this.currentStepIndex);
        if (this.skipDeviceSdkTimedPaddingIfNeeded(step)) {
            return;
        }
        if ("screenshot".equals(step.getAction()) && (this.isQaCaseType() || this.isDeviceSdkDemoTarget() && this.isGuiCaseType())) {
            Logger.safeInfo(TAG, "\u8df3\u8fc7 screenshot \u6b65\u9aa4: " + step.getStepId() + (this.isQaCaseType() ? " (QA)" : " (DeviceSDK GUI)"));
            ++this.currentStepIndex;
            this.executeNextStep();
            return;
        }
        int stepNum = this.currentStepIndex + 1;
        int totalSteps = this.currentFlow.getSteps().size();
        boolean crossAppPolling = this.isDeviceSdkDemoTarget() && this.isDeviceSdkCrossAppPollingStep(step);
        Logger.safeInfo(TAG, "=== \u6267\u884c\u6b65\u9aa4 " + stepNum + "/" + totalSteps + ": " + step.getAction() + " ===");
        this.reportStepProgress(stepNum, totalSteps, step.getStepId(), step.getAction());
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.stepBegin(stepNum, totalSteps, step.getAction(), step.getStepId(), crossAppPolling);
            if (crossAppPolling) {
                this.cancelDeviceSdkDialogHandlers();
                ScreenshotDiagLogger.phase("cross_app_poll", "stepId=" + step.getStepId() + " \u8df3\u8fc7\u6b65\u9aa4\u524d\u6d88\u5f39\uff0c\u622a\u56fe\u524d\u4ec5\u5173\u5f55\u5c4f\u5f39\u7a97");
            }
        }
        if (this.isDeviceSdkDemoTarget()) {
            if (!crossAppPolling) {
                this.safeDismissDeviceSdkDialogsBeforeStep();
            }
        } else {
            this.dismissGenericDialogsBeforeStep();
        }
        this.updateNotification("\u6b65\u9aa4 " + (this.currentStepIndex + 1) + "/" + this.currentFlow.getSteps().size());
        if ("wait".equals(step.getAction())) {
            String stepId;
            long duration = 500L;
            if (step.getParams() != null && step.getParams().getDuration() > 0) {
                duration = step.getParams().getDuration();
            }
            Logger.safeInfo(TAG, "\u5f02\u6b65\u7b49\u5f85 " + duration + "ms");
            ++this.currentStepIndex;
            this.stopStepTimeoutMonitor();
            if (this.isDeviceSdkDemoTarget() && (stepId = step.getStepId()) != null && stepId.startsWith("wait-response")) {
                this.scheduleMediaProjectionDialogHandlers();
                int shotIndex = this.currentStepIndex;
                String shotStepName = "step_" + shotIndex;
                ScreenshotDiagLogger.scheduleCapture(shotStepName, duration, 300L);
                this.beginWaitForDeviceReply(step, duration, shotStepName);
                return;
            }
            int shotIndex = this.currentStepIndex;
            String shotStepName = "step_" + shotIndex;
            if (this.isDeviceSdkDemoTarget()) {
                ScreenshotDiagLogger.scheduleCapture(shotStepName, duration, 300L);
            }
            if (this.isVoiceGuiWaitStep(step)) {
                Logger.safeInfo(TAG, "语音助手等待期间接收 AISF 回复并拍摄操控截图 step=" + step.getStepId());
                this.waitingForDeviceReply = true;
                this.postDelayedOnTestScheduler(() -> {
                    this.waitingForDeviceReply = false;
                    this.stopDeviceSdkGuiPollScreenshots();
                    this.continueAfterStep(shotStepName);
                }, duration + 300L);
                return;
            }
            this.postDelayedOnTestScheduler(() -> this.continueAfterStep(shotStepName), duration + (this.isQaCaseType() ? 0L : 300L));
            return;
        }
        try {
            boolean success = this.commandExecutor.execute(step);
            Logger.safeInfo(TAG, "\u6b65\u9aa4\u6267\u884c\u7ed3\u679c: " + (success ? "\u6210\u529f" : "\u5931\u8d25"));
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u6b65\u9aa4\u6267\u884c\u5f02\u5e38", e);
        }
        if ("launchApp".equals(step.getAction())) {
            if (this.isDeviceSdkDemoTarget()) {
                this.onDeviceSdkLaunchAppStep();
            } else {
                this.onGenericLaunchAppStep();
            }
        }
        if ("click".equals(step.getAction())) {
            if (this.isDeviceSdkDemoTarget()) {
                if (this.isClickSendBeforeWaitResponse(step)) {
                    this.cancelDeviceSdkDialogHandlers();
                    Logger.safeInfo(TAG, "click-send \u540e\u5c06\u8fdb\u5165 wait-response\uff0c\u8df3\u8fc7 post-click \u6d88\u5f39");
                    ScreenshotDiagLogger.phase("skip_post_click_dialogs", "stepId=" + step.getStepId());
                } else {
                    this.scheduleDeviceSdkPostClickDialogHandlers();
                }
            } else {
                this.scheduleGenericPostClickDialogHandlers();
            }
        }
        ++this.currentStepIndex;
        this.stopStepTimeoutMonitor();
        long screenshotDelay = 300L;
        int shotIndex = this.currentStepIndex;
        this.postDelayedOnTestScheduler(() -> this.continueAfterStep("step_" + shotIndex), this.isQaCaseType() ? 0L : 300L);
    }

    private void executeNextStepUnicomLegacy() {
        this.stopStepTimeoutMonitor();
        this.startStepTimeoutMonitor();
        TestStep step = this.currentFlow.getSteps().get(this.currentStepIndex);
        int stepNum = this.currentStepIndex + 1;
        int totalSteps = this.currentFlow.getSteps().size();
        Logger.safeInfo(TAG, "=== \u6267\u884c\u6b65\u9aa4 " + stepNum + "/" + totalSteps + ": " + step.getAction() + " ===");
        this.reportStepProgress(stepNum, totalSteps, step.getStepId(), step.getAction());
        this.updateNotification("\u6b65\u9aa4 " + stepNum + "/" + totalSteps);
        try {
            boolean success = this.commandExecutor.execute(step);
            Logger.safeInfo(TAG, "\u6b65\u9aa4\u6267\u884c\u7ed3\u679c: " + (success ? "\u6210\u529f" : "\u5931\u8d25"));
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u6b65\u9aa4\u6267\u884c\u5f02\u5e38", e);
        }
        ++this.currentStepIndex;
        this.handler.postDelayed(() -> this.captureScreenAndContinue("step_" + this.currentStepIndex), 1000L);
    }

    private void captureScreenAndContinue(String stepName) {
        if (this.isUnicomCloudPhoneTarget()) {
            this.captureScreenAndContinueUnicomLegacy(stepName);
            return;
        }
        if (this.isDeviceSdkDemoTarget()) {
            this.captureScreenAndContinueDeviceSdk(stepName);
        } else {
            this.captureScreenAndContinueGeneric(stepName);
        }
    }

    private void captureScreenAndContinueDeviceSdk(String stepName) {
        ScreenshotDiagLogger.phase("capture_begin", "stepName=" + stepName + " stepIndex=" + this.currentStepIndex + " isRunning=" + isRunning.get());
        new Thread(() -> {
            if (shouldStop.get() || !isRunning.get()) {
                ScreenshotDiagLogger.warn("\u622a\u56fe\u5df2\u53d6\u6d88(\u6d4b\u8bd5\u5df2\u505c\u6b62): " + stepName);
                return;
            }
            if (!this.screenshotInProgress.compareAndSet(false, true)) {
                Logger.safeWarn(TAG, "\u622a\u56fe\u91cd\u53e0\uff0c\u8df3\u8fc7: " + stepName);
                ScreenshotDiagLogger.warn("\u622a\u56fe\u91cd\u53e0\uff0c\u8df3\u8fc7: " + stepName);
                this.postOnTestScheduler(() -> {
                    if (!shouldStop.get() && isRunning.get()) {
                        this.executeNextStep();
                    }
                });
                return;
            }
            try {
                String fileName;
                File screenshotFile;
                boolean success;
                String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + testId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                if ((success = this.captureDeviceSdkStepScreenshot(stepName, screenshotFile = new File(screenshotDir, fileName = ScreenshotUtil.buildCaptureFileName(stepName)))) && !shouldStop.get() && isRunning.get()) {
                    boolean isLastStepShot;
                    this.saveDeviceSdkStepScreenshot(testId, stepName, screenshotFile);
                    boolean bl = isLastStepShot = this.currentFlow != null && this.currentFlow.getSteps() != null && this.currentStepIndex >= this.currentFlow.getSteps().size();
                    if (isLastStepShot) {
                        this.uploadLastStepAsResultFinal(testId, screenshotFile);
                    }
                } else {
                    Logger.safeWarn(TAG, "\u6b65\u9aa4\u622a\u56fe\u5931\u8d25: " + fileName);
                    ScreenshotDiagLogger.warn("\u6b65\u9aa4\u672a\u4fdd\u5b58(\u622a\u56fe\u5931\u8d25): " + fileName);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u622a\u56fe\u5931\u8d25", e);
                ScreenshotDiagLogger.error("captureScreenAndContinue\u5f02\u5e38: " + stepName, e);
            }
            finally {
                this.screenshotInProgress.set(false);
            }
            this.postOnTestScheduler(() -> {
                if (shouldStop.get() || !isRunning.get()) {
                    Logger.safeWarn(TAG, "\u6d4b\u8bd5\u5df2\u7ed3\u675f\uff0c\u4e0d\u518d\u7ee7\u7eed\u6b65\u9aa4: " + stepName);
                    ScreenshotDiagLogger.warn("\u6d4b\u8bd5\u5df2\u7ed3\u675f\uff0c\u8df3\u8fc7\u7ee7\u7eed\u6b65\u9aa4: " + stepName);
                    return;
                }
                ScreenshotDiagLogger.phase("capture_done_continue", stepName);
                this.executeNextStep();
            });
        }, "device-sdk-screenshot").start();
    }

    private void captureScreenAndContinueGeneric(String stepName) {
        this.runStepScreenshotWorker(stepName, this::captureGenericStepScreenshot);
    }

    private void captureScreenAndContinueUnicomLegacy(String stepName) {
        new Thread(() -> {
            try {
                TestAccessibilityService accessibilityService;
                String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + testId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                String fileName = ScreenshotUtil.buildCaptureFileName(stepName);
                File screenshotFile = new File(screenshotDir, fileName);
                boolean success = false;
                if (Build.VERSION.SDK_INT >= 30 && (accessibilityService = TestAccessibilityService.getInstance()) != null) {
                    success = accessibilityService.takeScreenshot(screenshotFile);
                }
                if (!success) {
                    success = this.captureScreenViaShell(screenshotFile);
                }
                if (success) {
                    Logger.safeInfo(TAG, "\u6b65\u9aa4\u622a\u56fe\u6210\u529f: " + fileName);
                    this.screenshotManager.addToUploadQueue(testId, screenshotFile);
                } else {
                    Logger.safeWarn(TAG, "\u6b65\u9aa4\u622a\u56fe\u5931\u8d25: " + fileName);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u622a\u56fe\u5931\u8d25", e);
            }
            this.handler.post(() -> {
                if (shouldStop.get() || !isRunning.get()) {
                    Logger.safeWarn(TAG, "\u6d4b\u8bd5\u5df2\u7ed3\u675f\uff0c\u4e0d\u518d\u7ee7\u7eed\u6b65\u9aa4: " + stepName);
                    return;
                }
                this.executeNextStep();
            });
        }).start();
    }

    private void runStepScreenshotWorker(String stepName, StepScreenshotCapturer capturer) {
        new Thread(() -> {
            try {
                String fileName;
                File screenshotFile;
                boolean success;
                String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + testId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                if (success = capturer.capture(screenshotFile = new File(screenshotDir, fileName = ScreenshotUtil.buildCaptureFileName(stepName)))) {
                    boolean isLastStepShot;
                    this.saveStepScreenshot(testId, screenshotFile);
                    boolean bl = isLastStepShot = this.currentFlow != null && this.currentFlow.getSteps() != null && this.currentStepIndex >= this.currentFlow.getSteps().size();
                    if (isLastStepShot) {
                        this.uploadLastStepAsResultFinal(testId, screenshotFile);
                    }
                } else {
                    Logger.safeWarn(TAG, "\u6b65\u9aa4\u622a\u56fe\u5931\u8d25: " + fileName);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u622a\u56fe\u5931\u8d25", e);
            }
            this.handler.post(() -> {
                if (shouldStop.get() || !isRunning.get()) {
                    Logger.safeWarn(TAG, "\u6d4b\u8bd5\u5df2\u7ed3\u675f\uff0c\u4e0d\u518d\u7ee7\u7eed\u6b65\u9aa4: " + stepName);
                    return;
                }
                this.executeNextStep();
            });
        }).start();
    }

    private void captureFinalScreenshot(String stepName) {
        String testId = this.currentFlow != null ? this.currentFlow.getTestId() : this.pendingTestId;
        this.captureFinalScreenshot(stepName, testId, null);
    }

    private void captureFinalScreenshot(String stepName, String testId, Runnable onComplete) {
        if (this.isUnicomCloudPhoneTarget()) {
            this.captureFinalScreenshotUnicomLegacy(stepName, testId, onComplete);
            return;
        }
        if (this.isDeviceSdkDemoTarget()) {
            this.captureFinalScreenshotDeviceSdk(stepName, testId, onComplete);
        } else {
            this.captureFinalScreenshotGeneric(stepName, testId, onComplete);
        }
    }

    private void captureFinalScreenshotUnicomLegacy(String stepName, String testId, Runnable onComplete) {
        String resolvedTestId = testId != null && !testId.isEmpty() ? testId : "unknown";
        new Thread(() -> {
            try {
                File latestStep;
                TestAccessibilityService accessibilityService;
                String fileName;
                File screenshotFile;
                boolean success;
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + resolvedTestId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                if (!(success = this.captureScreenViaShell(screenshotFile = new File(screenshotDir, fileName = ScreenshotUtil.buildCaptureFileName(stepName)))) && Build.VERSION.SDK_INT >= 30 && (accessibilityService = TestAccessibilityService.getInstance()) != null && !(success = accessibilityService.takeScreenshot(screenshotFile))) {
                    try {
                        Thread.sleep(500L);
                    }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    success = accessibilityService.takeScreenshot(screenshotFile);
                }
                if (!success && (latestStep = this.findLatestUnicomStepScreenshot(screenshotDir)) != null) {
                    try {
                        Files.copy(latestStep.toPath(), screenshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        success = screenshotFile.exists() && screenshotFile.length() > 0L;
                        Logger.safeWarn(TAG, "result_final \u6355\u83b7\u5931\u8d25\uff0c\u6539\u7528\u6700\u8fd1\u6b65\u9aa4\u622a\u56fe: " + latestStep.getName() + " -> " + fileName);
                    }
                    catch (Exception copyEx) {
                        Logger.safeError(TAG, "\u590d\u5236\u6700\u8fd1\u6b65\u9aa4\u622a\u56fe\u4e3a result_final \u5931\u8d25", copyEx);
                    }
                }
                if (success) {
                    Logger.safeInfo(TAG, "\u6700\u7ec8\u622a\u56fe\u6210\u529f: " + fileName);
                    this.uploadResultFinalWithRetry(resolvedTestId, screenshotFile);
                } else {
                    Logger.safeWarn(TAG, "\u6700\u7ec8\u622a\u56fe\u5931\u8d25: " + fileName + "\uff08\u5e38\u89c1\uff1a\u65e0\u969c\u788d errorCode=3 \u95f4\u9694\u8fc7\u77ed\uff0c\u6216 screencap \u4e0d\u53ef\u7528\uff09");
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u6700\u7ec8\u622a\u56fe\u5931\u8d25", e);
            }
            finally {
                if (onComplete != null) {
                    this.handler.post(onComplete);
                }
            }
        }, "unicom-result-final").start();
    }

    private File findLatestUnicomStepScreenshot(File screenshotDir) {
        if (screenshotDir == null || !screenshotDir.isDirectory()) {
            return null;
        }
        File[] files = screenshotDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && !lower.contains("result_final");
        });
        if (files == null || files.length == 0) {
            return null;
        }
        File best = files[0];
        for (int i = 1; i < files.length; ++i) {
            if (files[i].lastModified() <= best.lastModified()) continue;
            best = files[i];
        }
        return best.length() > 0L ? best : null;
    }

    private void captureFinalScreenshotDeviceSdk(String stepName, String testId, Runnable onComplete) {
        this.runFinalScreenshotWorker(stepName, testId, onComplete, this::captureDeviceSdkFinalScreen);
    }

    private void captureFinalScreenshotGeneric(String stepName, String testId, Runnable onComplete) {
        this.runFinalScreenshotWorker(stepName, testId, onComplete, this::captureGenericFinalScreen);
    }

    private void runFinalScreenshotWorker(String stepName, String testId, Runnable onComplete, FinalScreenshotCapturer capturer) {
        String resolvedTestId = testId != null && !testId.isEmpty() ? testId : "unknown";
        new Thread(() -> {
            try {
                File screenshotDir = new File(this.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots/" + resolvedTestId);
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }
                String fileName = ScreenshotUtil.buildCaptureFileName(stepName);
                File screenshotFile = new File(screenshotDir, fileName);
                boolean success = capturer.capture(screenshotFile);
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.resultFinalEvent("capture_start", "file=" + fileName);
                }
                if (success) {
                    boolean uploaded;
                    screenshotFile = ScreenshotUtil.compressResultImage(screenshotFile);
                    Logger.safeInfo(TAG, "\u6700\u7ec8\u622a\u56fe\u6210\u529f: " + screenshotFile.getName());
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.resultFinalEvent("capture_ok", "file=" + screenshotFile.getName() + " size=" + screenshotFile.length());
                    }
                    if (!(uploaded = this.uploadResultFinalWithRetry(resolvedTestId, screenshotFile))) {
                        Logger.safeWarn(TAG, "result_final \u4e0a\u4f20\u672a\u786e\u8ba4\uff0c\u5df2\u63d2\u961f\u5230\u4e0a\u4f20\u961f\u5217: " + fileName);
                        if (this.isDeviceSdkDemoTarget()) {
                            ScreenshotDiagLogger.resultFinalEvent("upload_queued", "file=" + fileName);
                        }
                    }
                } else {
                    Logger.safeWarn(TAG, "\u6700\u7ec8\u622a\u56fe\u5931\u8d25: " + fileName);
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.resultFinalEvent("capture_failed", "file=" + fileName);
                    }
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u6700\u7ec8\u622a\u56fe\u5931\u8d25", e);
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.error("result_final \u5f02\u5e38", e);
                }
            }
            finally {
                if (onComplete != null) {
                    this.handler.post(onComplete);
                }
            }
        }).start();
    }

    private boolean captureDeviceSdkFinalScreen(File screenshotFile) {
        this.prepareScreenBeforeCapture("result_final");
        boolean success = this.tryCaptureScreenForDeviceSdk(screenshotFile, "result_final", "attempt1");
        if (!success) {
            this.waitForScreenshotStable(500L);
            success = this.tryCaptureScreenForDeviceSdk(screenshotFile, "result_final", "attempt2");
        }
        if (!this.isDeviceSdkCrossAppPollingStepName("result_final")) {
            this.dismissDeviceSdkDialogsAfterScreenshotAsync();
        }
        return success;
    }

    private boolean captureGenericFinalScreen(File screenshotFile) {
        boolean success = this.tryCaptureScreenWithRetry(screenshotFile);
        this.dismissGenericDialogsAfterScreenshotAsync();
        return success;
    }

    private boolean tryCaptureScreenWithRetry(File screenshotFile) {
        boolean success = this.tryCaptureScreen(screenshotFile);
        if (!success) {
            this.waitForScreenshotStable(500L);
            success = this.tryCaptureScreen(screenshotFile);
        }
        return success;
    }

    private boolean captureScreenViaShell(File screenshotFile) {
        try {
            Process process = Runtime.getRuntime().exec("screencap -p " + screenshotFile.getAbsolutePath());
            process.waitFor(3000L, TimeUnit.MILLISECONDS);
            return screenshotFile.exists() && screenshotFile.length() > 0L;
        }
        catch (Exception e) {
            Logger.safeError(TAG, "captureScreenViaShell\u5931\u8d25", e);
            return false;
        }
    }

    private void finishTest(boolean success, String message) {
        String testId;
        if (!isRunning.compareAndSet(true, false)) {
            Logger.safeWarn(TAG, "finishTest\u5df2\u6267\u884c\uff0c\u8df3\u8fc7\u91cd\u590d\u8c03\u7528");
            return;
        }
        Logger.safeInfo(TAG, "=== \u6d4b\u8bd5\u7ed3\u675f: " + (success ? "\u6210\u529f" : "\u5931\u8d25") + " - " + message + " ===");
        Logger.safeInfo(TAG, "\u6d4b\u8bd5\u8017\u65f6: " + (System.currentTimeMillis() - this.testStartTime) + "ms");
        String string = testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
        if (this.currentFlow != null && this.currentFlow.getSteps() != null && !this.currentFlow.getSteps().isEmpty()) {
            int totalSteps = this.currentFlow.getSteps().size();
            this.reportStepProgress(totalSteps, totalSteps, "done", "finishing");
        }
        if (this.isDeviceSdkDemoTarget()) {
            String finishingDetail = "success=" + success + " message=" + message + " runId=" + this.testRunId + " stepIndex=" + this.currentStepIndex + "/" + (this.currentFlow != null && this.currentFlow.getSteps() != null ? this.currentFlow.getSteps().size() : 0) + " \u7b49\u5f85 result_final \u540e\u518d test_completed";
            ScreenshotDiagLogger.beginFinishingPhase(finishingDetail);
            ScreenshotDiagLogger.summaryAndReset(testId, finishingDetail);
        } else {
            Logger.safeInfo(TAG, "\u8fdb\u5165\u6536\u5c3e\u9636\u6bb5: testId=" + testId + " \u7b49\u5f85 result_final \u540e\u518d test_completed");
        }
        shouldStop.set(true);
        this.stopTotalTimeoutMonitor();
        this.stopStepTimeoutMonitor();
        this.clearTestScheduleCallbacks();
        this.resetWaitResponseState();
        this.cancelDeviceSdkDialogHandlers();
        this.screenshotManager.endTest();
        this.updateNotification(success ? "\u6d4b\u8bd5\u5b8c\u6210" : "\u6d4b\u8bd5\u5931\u8d25");
        boolean testSuccess = success;
        String testMessage = message;
        this.pendingTestSuccess = testSuccess;
        this.pendingTestId = testId;
        this.pendingTestMessage = testMessage;
        Intent intent = new Intent(ACTION_TEST_FINISHED);
        intent.putExtra("success", testSuccess);
        intent.putExtra("testId", testId);
        intent.putExtra("message", testMessage);
        this.sendBroadcast(intent);
        this.showToast(testSuccess ? "\u6d4b\u8bd5\u5b8c\u6210" : "\u6d4b\u8bd5\u5931\u8d25: " + testMessage);
        this.startExitFlow();
    }

    private void notifyServerTestCompleted(String source) {
        WebServerClient client;
        if (!this.testCompletionNotified.compareAndSet(false, true)) {
            Logger.safeInfo(TAG, "test_completed \u5df2\u4e0a\u62a5\uff0c\u5ffd\u7565\u91cd\u590d: source=" + source);
            if (this.isDeviceSdkDemoTarget()) {
                ScreenshotDiagLogger.warn("test_completed \u91cd\u590d\u56de\u8c03 source=" + source);
            }
            return;
        }
        if (this.testCompletionFallbackRunnable != null) {
            this.handler.removeCallbacks(this.testCompletionFallbackRunnable);
            this.testCompletionFallbackRunnable = null;
        }
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.testCompletedNotify(source, this.pendingTestSuccess, this.pendingTestMessage);
        }
        if (this.currentFlow != null && this.currentFlow.getSteps() != null && !this.currentFlow.getSteps().isEmpty()) {
            int totalSteps = this.currentFlow.getSteps().size();
            this.reportStepProgress(totalSteps, totalSteps, "done", "finish");
        }
        if ((client = WebServerClient.getInstance(null)) != null && client.isConnected()) {
            client.sendTestCompleted(this.pendingTestId, this.pendingTestSuccess, this.pendingTestMessage);
            Logger.safeInfo(TAG, "\u5df2\u901a\u77e5\u670d\u52a1\u5668\u6d4b\u8bd5\u5b8c\u6210: testId=" + this.pendingTestId + " source=" + source + " success=" + this.pendingTestSuccess);
        } else {
            Logger.safeWarn(TAG, "WebServer \u672a\u8fde\u63a5\uff0c\u65e0\u6cd5\u4e0a\u62a5 test_completed: source=" + source);
            if (this.isDeviceSdkDemoTarget()) {
                ScreenshotDiagLogger.warn("WebServer \u672a\u8fde\u63a5\uff0ctest_completed \u672a\u53d1\u9001 source=" + source);
            }
        }
        this.exportTestLogsOnce(this.pendingTestId);
    }

    private void scheduleTestCompletionFallback() {
        if (this.testCompletionFallbackRunnable != null) {
            this.handler.removeCallbacks(this.testCompletionFallbackRunnable);
        }
        this.testCompletionFallbackRunnable = () -> {
            this.notifyServerTestCompleted("fallback_12s");
            this.scheduleStopServiceAfterExit(1000L);
        };
        this.handler.postDelayed(this.testCompletionFallbackRunnable, 12000L);
        Logger.safeInfo(TAG, "\u5df2\u5b89\u6392 test_completed \u515c\u5e95\u4e0a\u62a5: 12s");
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.completionFallbackScheduled(12000L);
        }
    }

    private void scheduleStopServiceAfterExit(long delayMs) {
        if (this.exitFlowStep3Runnable != null) {
            this.handler.removeCallbacks(this.exitFlowStep3Runnable);
        }
        this.exitFlowStep3Runnable = () -> {
            block3: {
                try {
                    Logger.safeInfo(TAG, "\u9000\u51fa\u6b65\u9aa43: \u505c\u6b62\u670d\u52a1");
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.exitFlow("step3_stop_service", "after_test_completed");
                    }
                    this.stopForeground(true);
                    this.stopSelf();
                }
                catch (Exception e) {
                    Logger.safeError(TAG, "\u505c\u6b62\u670d\u52a1\u5f02\u5e38", e);
                    if (!this.isDeviceSdkDemoTarget()) break block3;
                    ScreenshotDiagLogger.error("exit_flow step3 \u5f02\u5e38", e);
                }
            }
        };
        this.handler.postDelayed(this.exitFlowStep3Runnable, Math.max(500L, delayMs));
    }

    private void uploadLastStepAsResultFinal(String testId, File stepScreenshot) {
        try {
            String lowerName = stepScreenshot.getName().toLowerCase();
            String ext = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ? ".jpg" : ".png";
            String resultName = ScreenshotUtil.buildCaptureFileName("result_final").replaceAll("\\.(png|jpe?g)$", ext);
            File resultFile = new File(stepScreenshot.getParentFile(), resultName);
            Files.copy(stepScreenshot.toPath(), resultFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            boolean uploaded = this.uploadResultFinalWithRetry(testId, resultFile);
            Logger.safeInfo(TAG, "\u6700\u540e\u4e00\u6b65\u622a\u56fe\u4f5c\u4e3a result_final \u4e0a\u4f20: " + uploaded + ", " + resultName);
            if (!uploaded && resultFile.exists() && !resultFile.delete()) {
                Logger.safeWarn(TAG, "result_final \u526f\u672c\u5220\u9664\u5931\u8d25: " + resultName);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u6700\u540e\u4e00\u6b65\u8f6c result_final \u5931\u8d25", e);
        }
    }

    private boolean uploadResultFinalWithRetry(String testId, File screenshotFile) {
        for (int attempt = 1; attempt <= 10; ++attempt) {
            WebServerClient client = WebServerClient.getInstance(null);
            if (client == null) {
                Logger.safeWarn(TAG, "WebServerClient \u4e0d\u53ef\u7528\uff0c\u91cd\u8bd5 " + attempt + "/10");
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.resultFinalEvent("upload_retry", "attempt=" + attempt + "/10 client=null");
                }
            } else {
                if (client.uploadScreenshotHttp(testId, screenshotFile)) {
                    if (!screenshotFile.delete()) {
                        Logger.safeWarn(TAG, "\u6700\u7ec8\u622a\u56fe\u4e0a\u4f20\u540e\u5220\u9664\u672c\u5730\u6587\u4ef6\u5931\u8d25: " + screenshotFile.getName());
                    }
                    Logger.safeInfo(TAG, "\u6700\u7ec8\u622a\u56fe\u5df2\u901a\u8fc7 HTTP \u4e0a\u4f20(\u7b2c" + attempt + "\u6b21): " + screenshotFile.getName());
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.resultFinalEvent("upload_ok", "via=http attempt=" + attempt + " file=" + screenshotFile.getName());
                    }
                    return true;
                }
                if (client.isConnected() && client.uploadScreenshot(testId, screenshotFile)) {
                    if (!screenshotFile.delete()) {
                        Logger.safeWarn(TAG, "\u6700\u7ec8\u622a\u56fe\u4e0a\u4f20\u540e\u5220\u9664\u672c\u5730\u6587\u4ef6\u5931\u8d25: " + screenshotFile.getName());
                    }
                    Logger.safeInfo(TAG, "\u6700\u7ec8\u622a\u56fe\u5df2\u901a\u8fc7 WebSocket \u4e0a\u4f20(\u7b2c" + attempt + "\u6b21): " + screenshotFile.getName());
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.resultFinalEvent("upload_ok", "via=ws attempt=" + attempt + " file=" + screenshotFile.getName());
                    }
                    return true;
                }
                Logger.safeWarn(TAG, "\u6700\u7ec8\u622a\u56fe\u4e0a\u4f20\u5931\u8d25\uff0c\u91cd\u8bd5 " + attempt + "/10: " + screenshotFile.getName());
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.resultFinalEvent("upload_retry", "attempt=" + attempt + "/10 file=" + screenshotFile.getName());
                }
            }
            try {
                Thread.sleep(800L);
                continue;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (screenshotFile.exists()) {
            this.screenshotManager.addToUploadQueueHead(testId, screenshotFile);
            this.screenshotManager.ensureUploading();
        }
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.resultFinalEvent("upload_failed", "file=" + screenshotFile.getName() + " queued=true");
        }
        return false;
    }

    private void cancelExitFlow() {
        if (this.testCompletionFallbackRunnable != null) {
            this.handler.removeCallbacks(this.testCompletionFallbackRunnable);
            this.testCompletionFallbackRunnable = null;
        }
        if (this.exitFlowStep1Runnable != null) {
            this.handler.removeCallbacks(this.exitFlowStep1Runnable);
            this.exitFlowStep1Runnable = null;
        }
        if (this.exitFlowStep2Runnable != null) {
            this.handler.removeCallbacks(this.exitFlowStep2Runnable);
            this.exitFlowStep2Runnable = null;
        }
        if (this.exitFlowStep3Runnable != null) {
            this.handler.removeCallbacks(this.exitFlowStep3Runnable);
            this.exitFlowStep3Runnable = null;
        }
    }

    private void startExitFlow() {
        if (this.isQaCaseType()) {
            Logger.safeInfo(TAG, "QA \u7528\u4f8b\u8df3\u8fc7 result_final \u622a\u56fe\uff0c\u76f4\u63a5\u4e0a\u62a5 test_completed: testId=" + this.pendingTestId);
            this.scheduleTestCompletionFallback();
            this.notifyServerTestCompleted("qa_no_screenshot");
            this.scheduleStopServiceAfterExit(this.isUnicomCloudPhoneTarget() || this.isDeviceSdkDemoTarget() ? 8000L : 3000L);
            return;
        }
        if (this.isDeviceSdkDemoTarget() && this.isGuiCaseType()) {
            Logger.safeInfo(TAG, "DeviceSDK GUI \u6536\u5c3e(a11y): testId=" + this.pendingTestId);
            ScreenshotDiagLogger.exitFlow("gui_a11y_exit", "testId=" + this.pendingTestId
                    + " reason=drain_then_test_completed");
            String exitTestId = this.pendingTestId;
            TestAccessibilityService a11y = TestAccessibilityService.getInstance();
            // 必须先传完本用例截图再 test_completed，否则服务端会提前评判/开下一例，导致串图
            final long drainTimeoutMs = 25000L;
            Runnable exitJob = () -> {
                try {
                    Logger.safeInfo(TAG, "收尾: 先排空本用例截图队列 testId=" + exitTestId);
                    ScreenshotDiagLogger.exitFlow("step1_drain_uploads", "timeoutMs=" + drainTimeoutMs);
                    this.waitForGuiPollCapturesIdle(3000L);
                    if (this.screenshotManager != null && exitTestId != null) {
                        this.screenshotManager.requeueLocalScreenshots(exitTestId, this.testStartTime);
                        boolean drained = this.screenshotManager.awaitQueueDrained(drainTimeoutMs);
                        Logger.safeInfo(TAG, "收尾截图队列 drained=" + drained
                                + " queue=" + this.screenshotManager.getQueueSize());
                        ScreenshotDiagLogger.exitFlow("step1_drain_done", "drained=" + drained);
                    }
                    Logger.safeInfo(TAG, "\u9000\u51fa\u6b65\u9aa4: \u62c9\u56de DeviceSDK \u804a\u5929\u6846\u5e76\u786e\u8ba4\u524d\u53f0");
                    ScreenshotDiagLogger.exitFlow("step2_return_chat", "confirm_fg_before_next_case");
                    // \u5fc5\u987b\u7b49\u5230 Device \u5728\u524d\u53f0\uff08\u6216\u8017\u5c3d\u91cd\u8bd5\uff09\u518d\u901a\u77e5\u670d\u52a1\u7aef\uff0c
                    // \u5426\u5219\u4e0b\u4e00\u4f8b test_start \u53ef\u80fd\u843d\u5728\u684c\u9762/\u7b2c\u4e09\u65b9 App\u3002
                    boolean fgOk = false;
                    for (int attempt = 0; attempt < 6 && !fgOk; attempt++) {
                        String reason = attempt == 0 ? "gui_exit" : ("gui_exit_retry_" + attempt);
                        if (attempt > 0) {
                            String fg = a11y != null ? a11y.getForegroundPackageName() : "?";
                            Logger.safeWarn(TAG, "\u62c9\u56de\u804a\u5929\u6846\u91cd\u8bd5#" + attempt + " fg=" + fg);
                            try {
                                Thread.sleep(800L);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        fgOk = this.bringDeviceSdkChatToFront(reason);
                        if (fgOk) {
                            Logger.safeInfo(TAG, "\u62c9\u56de\u804a\u5929\u6846\u786e\u8ba4\u524d\u53f0 ok attempt=" + attempt);
                        }
                    }
                    if (!fgOk) {
                        Logger.safeWarn(TAG, "gui_exit \u672a\u786e\u8ba4 DeviceSDK \u524d\u53f0\uff0c\u4ecd\u901a\u77e5\u5b8c\u6210\uff08\u4e0b\u4e00\u4f8b test_start \u4f1a\u518d\u62c9\uff09");
                    }
                }
                catch (Exception e) {
                    Logger.safeError(TAG, "\u62c9\u56de DeviceSDK \u5931\u8d25", e);
                }
                this.notifyServerTestCompleted("gui_a11y_exit");
                this.scheduleStopServiceAfterExit(8000L);
            };
            // 阻塞收尾放到后台线程，避免卡住 a11y；兜底延迟需大于 drainTimeout
            new Thread(exitJob, "gui-exit-drain-then-complete").start();
            if (a11y != null) {
                a11y.postOnServiceDelayed(
                        () -> this.notifyServerTestCompleted("fallback_a11y_40s"), 40000L);
            } else {
                this.scheduleTestCompletionFallback();
            }
            return;
        }
        boolean stayOnChat = this.isUnicomCloudPhoneTarget() || this.isDeviceSdkDemoTarget();
        Logger.safeInfo(TAG, "\u5f00\u59cb\u9000\u51fa\u6d41\u7a0b: testId=" + this.pendingTestId + (stayOnChat ? "\uff08\u622a\u804a\u5929 result_final \u2192 \u4e0a\u62a5 \u2192 \u7559\u5728\u804a\u5929\u9875\u7ee7\u7eed\u4e0b\u4e00\u7528\u4f8b\uff09" : "\uff08\u622a result_final \u2192 \u4e0a\u62a5 \u2192 \u56de TestApp\uff09"));
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.exitFlow("start", "testId=" + this.pendingTestId + " order=chat_final_stay_on_chat");
        }
        this.scheduleTestCompletionFallback();
        String testId = this.pendingTestId;
        this.exitFlowStep2Runnable = () -> {
            try {
                Logger.safeInfo(TAG, "\u9000\u51fa\u6b65\u9aa41: \u5728\u88ab\u6d4b\u804a\u5929\u754c\u9762\u622a result_final");
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.exitFlow("step1_result_final_on_chat", "delay=500ms");
                }
                this.captureFinalScreenshot("result_final", testId, () -> {
                    this.notifyServerTestCompleted("result_final");
                    if (stayOnChat) {
                        Logger.safeInfo(TAG, "\u9000\u51fa\u6b65\u9aa42: \u7559\u5728\u804a\u5929\u754c\u9762\uff0c\u4e0d\u62c9\u56de TestApp\uff08\u4fbf\u4e8e\u4e0b\u4e00\u7528\u4f8b\u7ee7\u7eed\uff09");
                        if (this.isDeviceSdkDemoTarget()) {
                            ScreenshotDiagLogger.exitFlow("step2_stay_on_chat", "skip_testapp");
                        }
                        this.scheduleStopServiceAfterExit(8000L);
                    } else {
                        this.exitFlowStep1Runnable = () -> {
                            try {
                                Logger.safeInfo(TAG, "\u9000\u51fa\u6b65\u9aa42: \u542f\u52a8 TestApp \u4e3b\u754c\u9762");
                                this.launchTestAppMainFromHome();
                            }
                            catch (Exception e) {
                                Logger.safeError(TAG, "\u542f\u52a8testapp\u5f02\u5e38", e);
                            }
                        };
                        this.handler.postDelayed(this.exitFlowStep1Runnable, 300L);
                        this.scheduleStopServiceAfterExit(3000L);
                    }
                });
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u6700\u7ec8\u622a\u56fe\u5f02\u5e38", e);
                if (this.isDeviceSdkDemoTarget()) {
                    ScreenshotDiagLogger.error("exit_flow result_final \u5f02\u5e38", e);
                }
                this.notifyServerTestCompleted("result_final_error");
                if (!stayOnChat) {
                    try {
                        this.launchTestAppMainFromHome();
                    }
                    catch (Exception launchEx) {
                        Logger.safeError(TAG, "\u542f\u52a8testapp\u5f02\u5e38", launchEx);
                    }
                } else {
                    Logger.safeInfo(TAG, "\u622a\u56fe\u5f02\u5e38\u540e\u4ecd\u7559\u5728\u804a\u5929\u754c\u9762\uff0c\u4e0d\u62c9\u56de TestApp");
                }
                this.scheduleStopServiceAfterExit(stayOnChat ? 8000L : 3000L);
            }
        };
        this.handler.postDelayed(this.exitFlowStep2Runnable, 500L);
    }

    private void pressBackKey() {
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                boolean result = accessibilityService.performBack();
                Logger.safeDebug(TAG, "\u8fd4\u56de\u952e\u6267\u884c\u7ed3\u679c: " + result);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u6267\u884c\u8fd4\u56de\u952e\u5931\u8d25", e);
        }
    }

    private void pressHomeKey() {
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                boolean result = accessibilityService.performHome();
                Logger.safeInfo(TAG, "HOME\u952e\u6267\u884c\u7ed3\u679c: " + result);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u6267\u884cHOME\u952e\u5931\u8d25", e);
        }
    }

    private void handlePasswordSaveDialog() {
        try {
            boolean handled;
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null && (handled = accessibilityService.handlePasswordSaveDialog())) {
                Logger.safeInfo(TAG, "\u5bc6\u7801\u4fdd\u5b58\u5f39\u7a97\u5df2\u5904\u7406");
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u5904\u7406\u5bc6\u7801\u4fdd\u5b58\u5f39\u7a97\u5931\u8d25", e);
        }
    }

    private void handleLaunchAppDialog() {
        try {
            boolean handled;
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null && (handled = accessibilityService.handleLaunchAppDialog())) {
                Logger.safeInfo(TAG, "\u542f\u52a8\u5e94\u7528\u786e\u8ba4\u5f39\u7a97\u5df2\u5904\u7406");
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u5904\u7406\u542f\u52a8\u5e94\u7528\u786e\u8ba4\u5f39\u7a97\u5931\u8d25", e);
        }
    }

    private void onDeviceSdkLaunchAppStep() {
        this.scheduleDeviceSdkBlockingDialogHandlers();
    }

    private void bringTestAppForegroundForExit(String reason) {
        Logger.safeInfo(TAG, "GUI \u6536\u5c3e\u89e3\u51bb\u62c9\u8d77 TestApp: " + reason);
        ScreenshotDiagLogger.exitFlow("unfreeze_testapp", reason);
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        if (a11y != null) {
            try {
                a11y.closeRecentsIfVisible();
            }
            catch (Exception exception) {
                // empty catch block
            }
            if (a11y.bringTestAppToForeground()) {
                Logger.safeInfo(TAG, "\u65e0\u969c\u788d\u5df2\u62c9\u8d77 TestApp MainActivity (" + reason + ")");
            }
        }
        try {
            Intent intent = new Intent((Context)this, MainActivity.class);
            intent.addFlags(0x30220000);
            this.startActivityFromService(intent);
        }
        catch (Exception e) {
            Logger.safeWarn(TAG, "Intent \u62c9\u8d77 TestApp \u5931\u8d25: " + e.getMessage());
        }
        try {
            String cmd = "am start -n " + this.getPackageName() + "/.MainActivity";
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            p.waitFor(3L, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            Logger.safeWarn(TAG, "am \u62c9\u8d77 TestApp \u5931\u8d25: " + e.getMessage());
        }
        try {
            this.launchViaFullscreenNotification();
        }
        catch (Exception e) {
            Logger.safeDebug(TAG, "fullscreen \u62c9\u8d77\u5931\u8d25: " + e.getMessage());
        }
    }

    /**
     * \u7528\u65e0\u969c\u788d\u670d\u52a1 startActivity \u62c9 DeviceSDK\uff08MIUI \u4e0a\u6bd4 Service
     * startActivityFromService \u66f4\u53ef\u9760\uff1b\u8f6e\u6b21\u95f4 return_chat \u5df2\u9a8c\u8bc1\u53ef\u7528\uff09\u3002
     */
    private boolean bringDeviceSdkViaA11y(String reason) {
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        if (a11y == null) {
            Logger.safeWarn(TAG, "a11y \u672a\u5c31\u7eea\uff0c\u65e0\u6cd5 bring DeviceSDK (" + reason + ")");
            return false;
        }
        AtomicBoolean ok = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        a11y.postOnService(() -> {
            try {
                ok.set(a11y.bringPackageToForeground(DEVICE_SDK_PACKAGE, DEVICE_SDK_QA_ACTIVITY));
            } catch (Exception e) {
                Logger.safeWarn(TAG, "a11y bring DeviceSDK \u5f02\u5e38: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(3L, TimeUnit.SECONDS)) {
                Logger.safeWarn(TAG, "a11y bring DeviceSDK \u8d85\u65f6 (" + reason + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Logger.safeInfo(TAG, "a11y bring DeviceSDK ok=" + ok.get() + " (" + reason + ")");
        return ok.get();
    }

    private void launchDeviceSdkTargetApp(String reason) {
        // 1) \u4f18\u5148\u65e0\u969c\u788d\u62c9\u8d77\uff08\u8f6e\u6b21\u95f4\u5df2\u9a8c\u8bc1\uff09\uff0c\u907f\u5f00 MIUI \u540e\u53f0 Service \u8d77 Activity \u9650\u5236
        if (this.bringDeviceSdkViaA11y(reason) && this.waitForTargetAppForeground(2500L)) {
            Logger.safeInfo(TAG, "DeviceSDK \u5df2\u5207\u5230\u524d\u53f0 (a11y, " + reason + ")");
            return;
        }
        // 2) Service PendingIntent / startActivity \u5907\u7528
        if (this.launchDeviceSdkQaActivity(reason) && this.waitForTargetAppForeground(2000L)) {
            Logger.safeInfo(TAG, "DeviceSDK \u5df2\u5207\u5230\u524d\u53f0 (QAActivity, " + reason + ")");
            return;
        }
        Logger.safeWarn(TAG, "QAActivity \u672a\u5207\u5230 DeviceSDK \u524d\u53f0\uff0cHOME \u540e\u518d\u62c9 (" + reason + ")");
        // 3) \u5148\u9000\u51fa\u7b2c\u4e09\u65b9\u5168\u5c4f\uff08\u5065\u5eb7\u4e91\u7b49\uff09\u518d a11y \u62c9
        TestAccessibilityService a11y = TestAccessibilityService.getInstance();
        if (a11y != null) {
            a11y.pressHomeForHandoff();
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (this.bringDeviceSdkViaA11y(reason + "_after_home") && this.waitForTargetAppForeground(3000L)) {
                Logger.safeInfo(TAG, "DeviceSDK \u5df2\u5207\u5230\u524d\u53f0 (a11y after HOME, " + reason + ")");
                return;
            }
        }
        Logger.safeWarn(TAG, "a11y/HOME \u672a\u786e\u8ba4\u524d\u53f0\uff0c\u56de\u9000 legacy (" + reason + ")");
        if (this.launchViaLegacyIntent(DEVICE_SDK_PACKAGE, reason) && this.waitForTargetAppForeground(2500L)) {
            Logger.safeInfo(TAG, "DeviceSDK \u5df2\u5207\u5230\u524d\u53f0 (legacy Launcher, " + reason + ")");
            return;
        }
        // am \u4ece App \u8fdb\u7a0b exec \u5728 MIUI \u4e0a\u51e0\u4e4e\u603b\u662f 255\uff0c\u4ec5\u4f5c\u6700\u540e\u8bb0\u5f55
        Logger.safeWarn(TAG, "legacy Launcher \u672a\u786e\u8ba4\u524d\u53f0\uff0c\u5c1d\u8bd5 am (" + reason + ")");
        if (this.launchPackageViaAmCommand(DEVICE_SDK_PACKAGE)) {
            this.waitForTargetAppForeground(2000L);
        } else if (!this.isTargetAppAlreadyForeground()) {
            Logger.safeError(TAG, "DeviceSDK \u62c9\u8d77\u5931\u8d25\uff0c\u4ecd\u4e0d\u5728\u524d\u53f0 (" + reason + ")");
        }
    }

    private boolean waitForTargetAppForeground(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            if (this.isTargetAppAlreadyForeground()) {
                return true;
            }
            try {
                Thread.sleep(200L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return this.isTargetAppAlreadyForeground();
            }
        }
        return this.isTargetAppAlreadyForeground();
    }

    private void launchDeviceSdkConfigActivity(String reason) {
        try {
            Intent intent = new Intent();
            intent.setClassName(DEVICE_SDK_PACKAGE, DEVICE_SDK_CONFIG_ACTIVITY);
            intent.addFlags(0x34000000);
            this.startActivityFromService(intent);
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8 DeviceSDK ConfigActivity (" + reason + ")");
            if (this.isDeviceSdkDemoTarget()) {
                ScreenshotDiagLogger.exitFlow("config_relaunch", reason);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "Intent \u542f\u52a8 ConfigActivity \u5931\u8d25\uff0c\u5c1d\u8bd5 am", e);
            try {
                String command = "am start -n com.example.devicesdkdemo/.ConfigActivity";
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                boolean finished = process.waitFor(5L, TimeUnit.SECONDS);
                int exitCode = finished ? process.exitValue() : -1;
                Logger.safeInfo(TAG, "am \u542f\u52a8 ConfigActivity: exitCode=" + exitCode + " (" + reason + ")");
            }
            catch (Exception amEx) {
                Logger.safeError(TAG, "am \u542f\u52a8 ConfigActivity \u5931\u8d25", amEx);
            }
        }
    }

    private boolean launchDeviceSdkQaActivity(String reason) {
        try {
            Intent intent = new Intent();
            intent.setClassName(DEVICE_SDK_PACKAGE, DEVICE_SDK_QA_ACTIVITY);
            intent.addFlags(0x30020000);
            this.startActivityFromService(intent);
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8 DeviceSDK QAActivity (" + reason + ")");
            return true;
        }
        catch (Exception e) {
            Logger.safeError(TAG, "Intent \u542f\u52a8 QAActivity \u5931\u8d25\uff0c\u5c1d\u8bd5 am", e);
            try {
                String command = "am start -n com.example.devicesdkdemo/.QAActivity";
                Process process = Runtime.getRuntime().exec(command);
                boolean finished = process.waitFor(5L, TimeUnit.SECONDS);
                int exitCode = finished ? process.exitValue() : -1;
                Logger.safeInfo(TAG, "am \u542f\u52a8 QAActivity: exitCode=" + exitCode + " (" + reason + ")");
                return exitCode == 0;
            }
            catch (Exception e2) {
                Logger.safeError(TAG, "am \u542f\u52a8 QAActivity \u5931\u8d25", e2);
                return false;
            }
        }
    }

    private void applyDeviceSdkDialogResult(TestAccessibilityService.BlockingDialogResult result) {
        if (result == null || !result.isAnyHandled()) {
            return;
        }
        boolean relaunch = result.shouldRelaunchTargetApp();
        ScreenshotDiagLogger.dialog("device_sdk_dialog", result.isMediaProjectionDismissed(), relaunch);
        if (result.isMediaProjectionDismissed()) {
            this.lastMediaProjectionDismissAt = System.currentTimeMillis();
        }
        if (relaunch) {
            this.launchDeviceSdkTargetApp("dialog_relaunch");
        }
    }

    private void dismissDeviceSdkDialogsBeforeStep() {
        if (this.screenshotInProgress.get()) {
            return;
        }
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                TestAccessibilityService.BlockingDialogResult result = accessibilityService.handleDeviceSdkBlockingDialogs();
                ScreenshotDiagLogger.dialog("before_step", result.isMediaProjectionDismissed(), result.shouldRelaunchTargetApp());
                this.applyDeviceSdkDialogResult(result);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "DeviceSDK: \u5904\u7406\u7cfb\u7edf\u5f39\u7a97\u5931\u8d25", e);
            ScreenshotDiagLogger.error("before_step\u5f39\u7a97\u5904\u7406\u5931\u8d25", e);
        }
    }

    private void safeDismissDeviceSdkDialogsBeforeStep() {
        if (this.screenshotInProgress.get()) {
            ScreenshotDiagLogger.phase("dialog_skip", "before_step screenshot_in_progress");
            return;
        }
        this.dismissDeviceSdkDialogsBeforeStep();
    }

    private void dismissDeviceSdkDialogsAfterScreenshot() {
        if (this.screenshotInProgress.get()) {
            return;
        }
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                this.applyDeviceSdkDialogResult(accessibilityService.handleDeviceSdkBlockingDialogs());
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "DeviceSDK: \u622a\u56fe\u540e\u5904\u7406\u7cfb\u7edf\u5f39\u7a97\u5931\u8d25", e);
        }
    }

    private void dismissDeviceSdkDialogsAfterScreenshotAsync() {
        this.handler.postDelayed(this.deviceSdkAfterScreenshotRunnable, 300L);
    }

    private boolean dismissDeviceSdkBlockingDialogs() {
        if (this.screenshotInProgress.get()) {
            return false;
        }
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                TestAccessibilityService.BlockingDialogResult result = accessibilityService.handleDeviceSdkBlockingDialogs();
                this.applyDeviceSdkDialogResult(result);
                return result.isAnyHandled();
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "DeviceSDK: \u5904\u7406\u906e\u6321\u5f39\u7a97\u5931\u8d25", e);
        }
        return false;
    }

    private void safeDismissDeviceSdkBlockingDialogs() {
        if (this.screenshotInProgress.get()) {
            ScreenshotDiagLogger.phase("dialog_skip", "blocking_dialog screenshot_in_progress");
            return;
        }
        this.dismissDeviceSdkBlockingDialogs();
    }

    private void scheduleDeviceSdkBlockingDialogHandlers() {
        long[] delaysMs;
        for (long delayMs : delaysMs = new long[]{300L, 800L, 1500L, 2500L, 4000L, 6000L}) {
            this.handler.postDelayed(this.deviceSdkBlockingDialogRunnable, delayMs);
        }
    }

    private void scheduleDeviceSdkPostClickDialogHandlers() {
        long[] delaysMs;
        for (long delayMs : delaysMs = new long[]{400L, 1000L, 2000L, 3500L, 5000L, 8000L}) {
            this.handler.postDelayed(this.deviceSdkBeforeStepDialogRunnable, delayMs);
            this.handler.postDelayed(this.mediaProjectionDialogRunnable, delayMs);
        }
    }

    private void scheduleMediaProjectionDialogHandlers() {
        // TestApp 已接管 MediaProjection：不再自动取消录屏授权弹窗
        Logger.safeInfo(TAG, "skip media projection auto-dismiss (TestApp owns capture)");
    }

    private void safeDismissMediaProjectionDialog() {
        // no-op：避免打断 TestApp 录屏授权
    }

    private boolean captureDeviceSdkStepScreenshot(String stepName, File screenshotFile) {
        this.prepareScreenBeforeCapture(stepName);
        boolean success = this.tryCaptureScreenForDeviceSdk(screenshotFile, stepName, "attempt1");
        if (!success) {
            this.waitForScreenshotStable(300L);
            success = this.tryCaptureScreenForDeviceSdk(screenshotFile, stepName, "attempt2");
        }
        ScreenshotDiagLogger.phase("step_screenshot_result", "step=" + stepName + " file=" + screenshotFile.getName() + " success=" + success);
        this.logForegroundAfterCapture(stepName, success);
        if (!this.isDeviceSdkCrossAppPollingStepName(stepName)) {
            this.dismissDeviceSdkDialogsAfterScreenshotAsync();
        }
        return success;
    }

    private void prepareScreenBeforeCapture(String stepName) {
        TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
        if (accessibilityService != null) {
            boolean visibleBefore = accessibilityService.isMediaProjectionDialogVisible();
            ScreenshotDiagLogger.mediaDialogAtCapture(stepName, "before_prep", visibleBefore, false, accessibilityService.describeScreenForDiag());
        }
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.phase("capture_prep_skip_media_dismiss", "step=" + stepName);
            this.waitForScreenshotStable(200L);
            return;
        }
        boolean anyDismissed = false;
        if (accessibilityService != null) {
            long prepDeadline = System.currentTimeMillis() + 8000L;
            for (int attempt = 1; attempt <= 3; ++attempt) {
                if (System.currentTimeMillis() >= prepDeadline) {
                    ScreenshotDiagLogger.warn("\u5f55\u5c4f\u5f39\u7a97\u5904\u7406\u8fbe\u5230\u4e0a\u9650 8000ms\uff0c\u5f3a\u5236\u7ee7\u7eed: " + stepName);
                    break;
                }
                if (!accessibilityService.isMediaProjectionDialogVisible()) break;
                boolean dismissed = accessibilityService.dismissMediaProjectionDialogsOnly();
                ScreenshotDiagLogger.mediaDialogAtCapture(stepName, "dismiss_try" + attempt, true, dismissed, accessibilityService.describeScreenForDiag());
                if (dismissed) {
                    anyDismissed = true;
                    this.lastMediaProjectionDismissAt = System.currentTimeMillis();
                    ScreenshotDiagLogger.dialog("capture_prep", true, false);
                    this.waitForScreenshotStable(400L);
                    continue;
                }
                this.waitForScreenshotStable(300L);
            }
            for (int backTry = 0; backTry < 2 && System.currentTimeMillis() < prepDeadline && accessibilityService.escapeSystemUiIfNeeded(); ++backTry) {
                ScreenshotDiagLogger.phase("media_escape_back", "step=" + stepName + " try=" + (backTry + 1));
                this.waitForScreenshotStable(400L);
            }
        }
        if (accessibilityService != null) {
            boolean visibleAfter = accessibilityService.isMediaProjectionDialogVisible();
            ScreenshotDiagLogger.mediaDialogAtCapture(stepName, "after_prep", visibleAfter, anyDismissed, accessibilityService.describeScreenForDiag());
        }
        long sinceMedia = System.currentTimeMillis() - this.lastMediaProjectionDismissAt;
        if (this.lastMediaProjectionDismissAt > 0L && sinceMedia < 1200L) {
            long waitMs = 1200L - sinceMedia;
            ScreenshotDiagLogger.phase("capture_wait_after_media", "step=" + stepName + " ms=" + waitMs);
            this.waitForScreenshotStable(waitMs);
        }
        this.waitForScreenshotStable(500L);
        this.logForegroundForScreenshot(stepName);
    }

    private void logForegroundForScreenshot(String stepName) {
        TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
        if (accessibilityService == null) {
            return;
        }
        ScreenshotDiagLogger.screenState(stepName, "before_capture", accessibilityService.describeScreenForDiag() + " target=" + this.targetPackage);
    }

    private void logForegroundAfterCapture(String stepName, boolean success) {
        TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
        if (accessibilityService == null) {
            return;
        }
        ScreenshotDiagLogger.screenState(stepName, "after_capture", "success=" + success + " " + accessibilityService.describeScreenForDiag());
    }

    private boolean tryCaptureScreenForDeviceSdk(File screenshotFile, String stepName, String attempt) {
        // ????????? MediaProjection??? a11y ? screencap
        if (this.tryCaptureViaAccessibility(screenshotFile, stepName, attempt)) {
            return true;
        }
        boolean shellOk = this.captureScreenViaShell(screenshotFile);
        long shellSize = screenshotFile.exists() ? screenshotFile.length() : 0L;
        ScreenshotDiagLogger.capture(stepName, attempt + "_screencap", shellOk, shellSize, shellOk ? "" : "screencap\u8fd4\u56de\u7a7a\u6587\u4ef6");
        return shellOk;
    }

    private boolean tryCaptureViaAccessibility(File screenshotFile, String stepName, String attempt) {
        if (Build.VERSION.SDK_INT < 30) {
            ScreenshotDiagLogger.capture(stepName, attempt + "_a11y", false, 0L, "API<30");
            return false;
        }
        TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
        if (accessibilityService == null) {
            ScreenshotDiagLogger.capture(stepName, attempt + "_a11y", false, 0L, "\u65e0\u969c\u788d\u670d\u52a1\u4e3a\u7a7a");
            return false;
        }
        boolean a11yOk = accessibilityService.takeScreenshot(screenshotFile, stepName, attempt);
        long a11ySize = screenshotFile.exists() ? screenshotFile.length() : 0L;
        ScreenshotDiagLogger.capture(stepName, attempt + "_a11y", a11yOk, a11ySize, "");
        return a11yOk && a11ySize > 0L;
    }

    private boolean tryCaptureViaMediaProjection(File screenshotFile, String stepName, String attempt) {
        return this.tryCaptureViaMediaProjection(screenshotFile, stepName, attempt, 8000L);
    }

    private boolean tryCaptureViaMediaProjection(File screenshotFile, String stepName, String attempt, long timeoutMs) {
        if (!this.isDeviceSdkDemoTarget()) {
            return false;
        }
        long requestId = guiCaptureRequestIdSeq.incrementAndGet();
        try {
            TestReplySdk.GuiCaptureResult result = TestReplySdk.awaitGuiScreenCapture((Context)this, (String)DEVICE_SDK_PACKAGE, (long)requestId, (String)stepName, (long)timeoutMs);
            if (result.isSuccess() && result.getUri() != null && !result.getUri().isEmpty()) {
                boolean copied = this.copyCaptureUriToFile(Uri.parse((String)result.getUri()), screenshotFile);
                long size = screenshotFile.exists() ? screenshotFile.length() : 0L;
                ScreenshotDiagLogger.capture(stepName, attempt + "_media", copied, size, copied ? "" : "uri_copy_failed");
                if (copied && size > 0L) {
                    Logger.safeInfo(TAG, "MediaProjection \u622a\u56fe\u6210\u529f: " + stepName + " bytes=" + size + " requestId=" + requestId);
                    return true;
                }
            }
            String extra = result.isTimedOut() ? "timeout" : (result.getError() != null ? result.getError() : "media_failed");
            ScreenshotDiagLogger.capture(stepName, attempt + "_media", false, 0L, extra);
            Logger.safeDebug(TAG, "MediaProjection \u622a\u56fe\u672a\u7528: " + stepName + " " + extra);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ScreenshotDiagLogger.capture(stepName, attempt + "_media", false, 0L, "interrupted");
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean copyCaptureUriToFile(Uri uri, File dest) {
        boolean bl;
        if (uri == null || dest == null) {
            return false;
        }
        InputStream in = null;
        FileOutputStream out = null;
        try {
            int n;
            in = this.getContentResolver().openInputStream(uri);
            if (in == null) {
                boolean bl2 = false;
                return bl2;
            }
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            while ((n = in.read(buf)) >= 0) {
                if (n <= 0) continue;
                out.write(buf, 0, n);
            }
            out.flush();
            bl = dest.exists() && dest.length() > 0L;
        }
        catch (IOException e) {
            Logger.safeWarn(TAG, "copyCaptureUriToFile\u5931\u8d25: " + e.getMessage());
            boolean bl3 = false;
            return bl3;
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException iOException) {}
            }
            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException iOException) {}
            }
        }
        return bl;
    }

    private void saveDeviceSdkStepScreenshot(String testId, String stepName, File screenshotFile) {
        long beforeBytes = screenshotFile.length();
        screenshotFile = ScreenshotUtil.compressResultImage(screenshotFile);
        Logger.safeInfo(TAG, "\u6b65\u9aa4\u622a\u56fe\u6210\u529f: " + screenshotFile.getName());
        this.screenshotManager.addToUploadQueue(testId, screenshotFile);
        ScreenshotDiagLogger.save(stepName, true, screenshotFile.getName(), screenshotFile.length() > 0L ? screenshotFile.length() : beforeBytes);
    }

    private boolean isDeviceSdkCrossAppPollingStep(TestStep step) {
        if (!this.isDeviceSdkDemoTarget() || step == null) {
            return false;
        }
        String stepId = step.getStepId();
        if (stepId != null && stepId.startsWith("wait-screenshot")) {
            return true;
        }
        return "screenshot".equals(step.getAction());
    }

    private boolean isDeviceSdkCrossAppPollingStepName(String stepName) {
        if (!this.isDeviceSdkDemoTarget()) {
            return false;
        }
        if (stepName != null && (stepName.startsWith("gui_poll_") || stepName.startsWith("post_send_") || "final_result".equals(stepName))) {
            return true;
        }
        if (this.currentFlow == null || this.currentFlow.getSteps() == null) {
            return false;
        }
        int stepIndex = this.parseStepNumber(stepName);
        if (stepIndex <= 0) {
            return "result_final".equals(stepName);
        }
        int flowIndex = stepIndex - 1;
        if (flowIndex < 0 || flowIndex >= this.currentFlow.getSteps().size()) {
            return false;
        }
        return this.isDeviceSdkCrossAppPollingStep(this.currentFlow.getSteps().get(flowIndex));
    }

    private int parseStepNumber(String stepName) {
        if (stepName == null || !stepName.startsWith("step_")) {
            return -1;
        }
        try {
            return Integer.parseInt(stepName.substring("step_".length()));
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    private void onGenericLaunchAppStep() {
        this.launchGenericTargetApp("launch_app_step");
        this.scheduleGenericBlockingDialogHandlers();
    }

    private void launchGenericTargetApp(String reason) {
        if (this.targetPackage == null || this.targetPackage.isEmpty()) {
            return;
        }
        if (this.launchPackageViaAmCommand(this.targetPackage)) {
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8\u5e94\u7528(am): " + this.targetPackage + " (" + reason + ")");
            return;
        }
        try {
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(this.targetPackage);
            if (intent == null) {
                Logger.safeWarn(TAG, "\u65e0\u6cd5\u83b7\u53d6\u542f\u52a8 Intent: " + this.targetPackage);
                return;
            }
            intent.addFlags(0x30020000);
            this.startActivityFromService(intent);
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8\u5e94\u7528(Intent): " + this.targetPackage + " (" + reason + ")");
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u542f\u52a8\u5e94\u7528\u5931\u8d25: " + this.targetPackage, e);
        }
    }

    private void dismissGenericDialogsBeforeStep() {
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                accessibilityService.handleUnicomBlockingDialogs();
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u901a\u7528: \u5904\u7406\u7cfb\u7edf\u5f39\u7a97\u5931\u8d25", e);
        }
    }

    private void dismissGenericDialogsAfterScreenshot() {
        this.dismissGenericDialogsBeforeStep();
    }

    private void dismissGenericDialogsAfterScreenshotAsync() {
        this.handler.postDelayed(this::dismissGenericDialogsAfterScreenshot, 300L);
    }

    private boolean dismissGenericBlockingDialogs() {
        try {
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null) {
                return accessibilityService.handleUnicomBlockingDialogs();
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u901a\u7528: \u5904\u7406\u906e\u6321\u5f39\u7a97\u5931\u8d25", e);
        }
        return false;
    }

    private void scheduleGenericBlockingDialogHandlers() {
        long[] delaysMs;
        for (long delayMs : delaysMs = new long[]{300L, 800L, 1500L, 2500L, 4000L, 6000L}) {
            this.handler.postDelayed(this::dismissGenericBlockingDialogs, delayMs);
        }
    }

    private void scheduleGenericPostClickDialogHandlers() {
        long[] delaysMs;
        for (long delayMs : delaysMs = new long[]{400L, 1000L, 2000L, 3500L}) {
            this.handler.postDelayed(this::dismissGenericDialogsBeforeStep, delayMs);
        }
    }

    private boolean captureGenericStepScreenshot(File screenshotFile) {
        this.waitForScreenshotStable(300L);
        boolean success = this.tryCaptureScreen(screenshotFile);
        if (!success) {
            this.waitForScreenshotStable(300L);
            success = this.tryCaptureScreen(screenshotFile);
        }
        this.dismissGenericDialogsAfterScreenshotAsync();
        return success;
    }

    private void reportStepProgress(int stepIndex, int totalSteps, String stepId, String action) {
        WebServerClient client = WebServerClient.peekInstance();
        if (client != null && client.isConnected()) {
            client.sendStepProgress(stepIndex, totalSteps, stepId, action);
        }
    }

    private void exportTestLogsOnce(String testId) {
        if (!this.testLogsExported.compareAndSet(false, true)) {
            Logger.safeInfo(TAG, "\u6d4b\u8bd5\u65e5\u5fd7\u5df2\u5bfc\u51fa\uff0c\u8df3\u8fc7\u91cd\u590d: testId=" + testId);
            return;
        }
        this.exportTestLogs(testId);
    }

    private void exportTestLogs(String testId) {
        block6: {
            try {
                File diagFile;
                if (this.isDeviceSdkDemoTarget() && (diagFile = ScreenshotDiagLogger.exportSessionFile((Context)this, testId)) != null) {
                    Logger.safeInfo(TAG, "\u622a\u56fe\u8bca\u65ad\u65e5\u5fd7\u5df2\u5bfc\u51fa: " + diagFile.getAbsolutePath());
                    this.uploadExportedLogToServer(testId, diagFile, "screenshot_diag");
                }
                LiveLogStreamer.flush();
                LiveLogStreamer.stop();
                Logger fileLogger = Logger.getInstance((Context)this);
                if (fileLogger != null) {
                    File deviceLog = fileLogger.saveToFile(testId);
                    fileLogger.endSession();
                    if (deviceLog != null && deviceLog.exists()) {
                        Logger.safeInfo(TAG, "\u8bbe\u5907\u65e5\u5fd7\u5df2\u5bfc\u51fa: " + deviceLog.getAbsolutePath());
                        this.uploadExportedLogToServer(testId, deviceLog, "device");
                    } else {
                        Logger.safeWarn(TAG, "\u8bbe\u5907\u65e5\u5fd7\u6587\u4ef6\u4e0d\u5b58\u5728\uff0c\u8df3\u8fc7\u4e0a\u4f20: testId=" + testId);
                    }
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u5bfc\u51fa\u6d4b\u8bd5\u65e5\u5fd7\u5931\u8d25", e);
                if (!this.isDeviceSdkDemoTarget()) break block6;
                ScreenshotDiagLogger.error("\u5bfc\u51fa\u6d4b\u8bd5\u65e5\u5fd7\u5931\u8d25", e);
            }
        }
    }

    private void uploadExportedLogToServer(String testId, File logFile, String kind) {
        new Thread(() -> {
            try {
                WebServerClient client = WebServerClient.getInstance(null);
                if (client == null) {
                    Logger.safeWarn(TAG, "\u65e5\u5fd7\u4e0a\u4f20\u8df3\u8fc7: WebServerClient \u672a\u521d\u59cb\u5316");
                    if (this.isDeviceSdkDemoTarget()) {
                        ScreenshotDiagLogger.warn("\u65e5\u5fd7\u4e0a\u4f20\u8df3\u8fc7(" + kind + "): WebServerClient \u672a\u521d\u59cb\u5316");
                    }
                    return;
                }
                if (client.uploadLogHttp(testId, logFile)) {
                    Logger.safeInfo(TAG, "\u6d4b\u8bd5\u65e5\u5fd7\u5df2\u4e0a\u4f20: " + logFile.getName());
                    ScreenshotDiagLogger.upload(logFile.getName(), true, "http", "kind=" + kind);
                } else {
                    Logger.safeWarn(TAG, "\u65e5\u5fd7\u4e0a\u4f20\u5931\u8d25: " + logFile.getName());
                    ScreenshotDiagLogger.upload(logFile.getName(), false, "http", "kind=" + kind);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u65e5\u5fd7\u4e0a\u4f20\u5f02\u5e38", e);
                ScreenshotDiagLogger.error("\u65e5\u5fd7\u4e0a\u4f20\u5f02\u5e38: " + logFile.getName(), e);
            }
        }, "UploadTestLog").start();
    }

    private void saveStepScreenshot(String testId, File screenshotFile) {
        screenshotFile = ScreenshotUtil.compressResultImage(screenshotFile);
        Logger.safeInfo(TAG, "\u6b65\u9aa4\u622a\u56fe\u6210\u529f: " + screenshotFile.getName());
        this.screenshotManager.addToUploadQueue(testId, screenshotFile);
    }

    private void waitForScreenshotStable(long waitMs) {
        try {
            Thread.sleep(waitMs);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean tryCaptureScreen(File screenshotFile) {
        TestAccessibilityService accessibilityService;
        boolean success = false;
        if (Build.VERSION.SDK_INT >= 30 && (accessibilityService = TestAccessibilityService.getInstance()) != null) {
            success = accessibilityService.takeScreenshot(screenshotFile);
        }
        if (!success) {
            success = this.captureScreenViaShell(screenshotFile);
        }
        return success && screenshotFile.exists() && screenshotFile.length() > 0L;
    }

    private boolean launchViaLegacyIntent(String packageName, String reason) {
        try {
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent == null) {
                Logger.safeWarn(TAG, "\u65e0\u6cd5\u83b7\u53d6\u542f\u52a8 Intent: " + packageName);
                return false;
            }
            intent.addFlags(0x10000000);
            this.startActivityFromService(intent);
            Logger.safeInfo(TAG, "\u5df2\u542f\u52a8\u5e94\u7528(legacy Intent): " + packageName + " (" + reason + ")");
            return true;
        }
        catch (Exception e) {
            Logger.safeError(TAG, "legacy Intent \u542f\u52a8\u5931\u8d25: " + packageName, e);
            return false;
        }
    }

    private void startActivityFromService(Intent intent) throws PendingIntent.CanceledException {
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)intent.hashCode(), (Intent)intent, (int)0x4000000);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityStartMode(1);
            pendingIntent.send(null, 0, null, null, null, null, options.toBundle());
            return;
        }
        this.startActivity(intent);
    }

    private boolean launchPackageViaAmCommand(String packageName) {
        try {
            Intent launchIntent = this.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null || launchIntent.getComponent() == null) {
                return false;
            }
            ComponentName component = launchIntent.getComponent();
            String command = "am start -n " + component.flattenToShortString();
            Process process = Runtime.getRuntime().exec(command);
            boolean finished = process.waitFor(5L, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;
            Logger.safeInfo(TAG, "am \u542f\u52a8: " + command + ", exitCode=" + exitCode);
            return exitCode == 0;
        }
        catch (Exception e) {
            Logger.safeError(TAG, "am \u542f\u52a8\u5931\u8d25: " + packageName, e);
            return false;
        }
    }

    private void launchTestAppMainFromHome() {
        Logger.safeInfo(TAG, "\u5f00\u59cb\u542f\u52a8testapp\u4e3b\u754c\u9762 - \u4f7f\u7528\u5168\u5c4f\u901a\u77e5\u65b9\u6848");
        this.launchViaFullscreenNotification();
        this.handler.postDelayed(() -> {
            if (!this.isMainActivityInForeground()) {
                this.restoreMainActivityAfterRecents();
            }
        }, 800L);
    }

    private void restoreMainActivityAfterRecents() {
        new Thread(() -> {
            boolean success;
            TestAccessibilityService accessibilityService = TestAccessibilityService.getInstance();
            if (accessibilityService != null && (success = accessibilityService.bringAppToFrontViaRecents(this.getPackageName()))) {
                Logger.safeInfo(TAG, "\u901a\u8fc7\u6700\u8fd1\u4efb\u52a1\u6210\u529f\u6062\u590d\u5e94\u7528");
                return;
            }
            this.sleepQuietly(300L);
            this.launchMainActivityFromBackground(accessibilityService);
            this.sleepQuietly(1200L);
            if (!MainActivity.isActivityVisible()) {
                this.launchMainActivityFromBackground(accessibilityService);
                this.handler.post(this::tryMoveTaskToFront);
            }
        }, "exit-restore").start();
    }

    private void launchMainActivityFromBackground(TestAccessibilityService accessibilityService) {
        if (MainActivity.isActivityVisible()) {
            return;
        }
        this.handler.post(() -> {
            if (accessibilityService != null) {
                accessibilityService.closeRecentsIfVisible();
            }
            Logger.safeInfo(TAG, "\u5173\u95ed\u591a\u4efb\u52a1\u540e\u5c1d\u8bd5 Intent/am \u62c9\u8d77 MainActivity");
            this.tryLaunchIntent();
            this.launchViaAmCommandAsync();
        });
        this.sleepQuietly(500L);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void launchViaAmCommandAsync() {
        new Thread(this::launchViaAmCommand, "exit-am-start").start();
    }

    private void launchViaFullscreenNotification() {
        try {
            String uniqueAction = "com.example.testapp.LAUNCH_MAIN_" + System.currentTimeMillis();
            Intent intent = new Intent(uniqueAction);
            intent.setComponent(new ComponentName((Context)this, MainActivity.class));
            intent.addFlags(268599296);
            int requestCode = (int)(System.currentTimeMillis() % Integer.MAX_VALUE);
            PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)requestCode, (Intent)intent, (int)0xC000000);
            NotificationCompat.Builder builder = new NotificationCompat.Builder((Context)this, CHANNEL_ID).setSmallIcon(17301569).setContentTitle((CharSequence)"\u6d4b\u8bd5\u5b8c\u6210").setContentText((CharSequence)"\u70b9\u51fb\u8fd4\u56detestapp\u4e3b\u754c\u9762").setPriority(1).setCategory("call").setFullScreenIntent(pendingIntent, true).setAutoCancel(true).setTimeoutAfter(3000L);
            int notificationId = 1001 + (int)(System.currentTimeMillis() % 1000L);
            NotificationManager notificationManager = (NotificationManager)this.getSystemService(NotificationManager.class);
            notificationManager.cancel(notificationId);
            notificationManager.notify(notificationId, builder.build());
            Logger.safeInfo(TAG, "\u5168\u5c4f\u901a\u77e5\u5df2\u53d1\u9001, \u901a\u77e5ID: " + notificationId);
        }
        catch (Exception e) {
            Logger.safeError(TAG, "\u5168\u5c4f\u901a\u77e5\u53d1\u9001\u5931\u8d25", e);
            this.tryLaunchIntent();
        }
    }

    private boolean isMainActivityInForeground() {
        return MainActivity.isActivityVisible();
    }

    private void tryLaunchIntent() {
        try {
            Intent intent = new Intent((Context)this, MainActivity.class);
            intent.addFlags(268599296);
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)0, (Intent)intent, (int)0x4000000);
                pendingIntent.send();
                Logger.safeInfo(TAG, "PendingIntent\u542f\u52a8\u6210\u529f (Android 14+)");
            } else if (Build.VERSION.SDK_INT >= 29) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(1);
                this.startActivity(intent, options.toBundle());
                Logger.safeInfo(TAG, "Intent\u542f\u52a8\u6210\u529f (Android 10-13)");
            } else {
                this.startActivity(intent);
                Logger.safeInfo(TAG, "Intent\u542f\u52a8\u6210\u529f");
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "Intent\u542f\u52a8\u5931\u8d25\uff0c\u5c1d\u8bd5\u5907\u7528\u65b9\u6848", e);
            try {
                Intent launchIntent = this.getPackageManager().getLaunchIntentForPackage(this.getPackageName());
                if (launchIntent != null) {
                    launchIntent.addFlags(0x10008000);
                    if (Build.VERSION.SDK_INT >= 29) {
                        ActivityOptions options = ActivityOptions.makeBasic();
                        options.setPendingIntentBackgroundActivityStartMode(1);
                        this.startActivity(launchIntent, options.toBundle());
                    } else {
                        this.startActivity(launchIntent);
                    }
                    Logger.safeInfo(TAG, "PackageManager\u542f\u52a8\u6210\u529f");
                }
            }
            catch (Exception e2) {
                Logger.safeError(TAG, "PackageManager\u542f\u52a8\u4e5f\u5931\u8d25", e2);
                this.tryMoveTaskToFront();
            }
        }
    }

    private void launchViaAmCommand() {
        try {
            String line;
            String command = "am start -n " + this.getPackageName() + "/.MainActivity";
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            Logger.safeInfo(TAG, "am\u547d\u4ee4\u542f\u52a8\u5b8c\u6210: " + command + ", exitCode=" + exitCode);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            if (output.length() > 0) {
                Logger.safeInfo(TAG, "am\u547d\u4ee4\u8f93\u51fa: " + output);
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "am\u547d\u4ee4\u542f\u52a8\u5931\u8d25", e);
        }
    }

    private void tryMoveTaskToFront() {
        try {
            List<ActivityManager.AppTask> appTasks;
            ActivityManager am = (ActivityManager)this.getSystemService("activity");
            if (am != null && (appTasks = am.getAppTasks()) != null && !appTasks.isEmpty()) {
                for (ActivityManager.AppTask task : appTasks) {
                    ActivityManager.RecentTaskInfo taskInfo = task.getTaskInfo();
                    if (taskInfo == null || !this.getPackageName().equals(taskInfo.baseActivity.getPackageName())) continue;
                    task.moveToFront();
                    Logger.safeInfo(TAG, "moveTaskToFront\u6210\u529f");
                    return;
                }
            }
        }
        catch (Exception e) {
            Logger.safeError(TAG, "moveTaskToFront\u5931\u8d25", e);
        }
    }

    private void stopTest(String reason) {
        Logger.safeInfo(TAG, "\u505c\u6b62\u6d4b\u8bd5: " + reason);
        // 先通知 Demo 停 AISF（等同点「停止任务」）；即使本机用例已不在跑也要发
        this.notifyDeviceSdkStopAisf(reason);
        if (!isRunning.compareAndSet(true, false)) {
            Logger.safeWarn(TAG, "\u6d4b\u8bd5\u672a\u5728\u8fd0\u884c");
            return;
        }
        shouldStop.set(true);
        this.testCompletionNotified.set(true);
        this.testLogsExported.set(false);
        this.stopTotalTimeoutMonitor();
        this.stopStepTimeoutMonitor();
        this.clearTestScheduleCallbacks();
        this.cancelDeviceSdkDialogHandlers();
        this.cancelExitFlow();
        this.screenshotManager.endTest();
        this.screenshotManager.stopAndClearUploads();
        this.updateNotification("\u6d4b\u8bd5\u5df2\u505c\u6b62");
        String testId = this.currentFlow != null ? this.currentFlow.getTestId() : "unknown";
        this.pendingTestSuccess = false;
        this.pendingTestId = testId;
        this.pendingTestMessage = reason;
        if (this.isDeviceSdkDemoTarget()) {
            ScreenshotDiagLogger.beginFinishingPhase("\u7528\u6237\u505c\u6b62: " + reason);
            ScreenshotDiagLogger.warn("\u7528\u6237\u505c\u6b62\uff1a\u8df3\u8fc7\u9000\u51fa\u6d41\u7a0b\u4e0e result_final");
        } else {
            Logger.safeInfo(TAG, "\u7528\u6237\u505c\u6b62\uff0c\u8df3\u8fc7\u9000\u51fa\u6d41\u7a0b");
        }
        Intent intent = new Intent(ACTION_TEST_FINISHED);
        intent.putExtra("success", false);
        intent.putExtra("testId", testId);
        intent.putExtra("message", reason);
        this.sendBroadcast(intent);
        this.showToast("\u6d4b\u8bd5\u5df2\u505c\u6b62: " + reason);
        this.exportTestLogsOnce(testId);
        this.handler.postDelayed(() -> {
            try {
                this.stopForeground(true);
                this.stopSelf();
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u505c\u6b62\u670d\u52a1\u5f02\u5e38", e);
            }
        }, 300L);
    }

    /** 9526 stop_test → Demo：ContentProvider/广播触发 stopAisfFromWebServer（点停止任务等价）。 */
    private void notifyDeviceSdkStopAisf(String reason) {
        try {
            String why = (reason != null && !reason.isEmpty()) ? reason : "webserver_stop";
            TestReplySdk.requestForceEndTakeover((Context)this, DEVICE_SDK_PACKAGE, why);
            Logger.safeInfo(TAG, "notified Demo stop_aisf reason=" + why);
        } catch (Exception e) {
            Logger.safeWarn(TAG, "notifyDeviceSdkStopAisf failed: " + e.getMessage());
        }
    }

    private void launchTargetApp() {
        if (this.targetPackage == null || this.targetPackage.isEmpty()) {
            Logger.safeError(TAG, "\u76ee\u6807\u5e94\u7528\u5305\u540d\u4e3a\u7a7a");
            return;
        }
        if (this.isTargetAppAlreadyForeground()) {
            Logger.safeInfo(TAG, "\u76ee\u6807\u5e94\u7528\u5df2\u5728\u524d\u53f0\uff0c\u8df3\u8fc7\u542f\u52a8: " + this.targetPackage);
            return;
        }
        if (this.isUnicomCloudPhoneTarget()) {
            try {
                Intent intent = this.getPackageManager().getLaunchIntentForPackage(this.targetPackage);
                if (intent != null) {
                    intent.addFlags(0x10000000);
                    this.startActivity(intent);
                    Logger.safeInfo(TAG, "\u76ee\u6807\u5e94\u7528\u5df2\u542f\u52a8: " + this.targetPackage);
                } else {
                    Logger.safeError(TAG, "\u65e0\u6cd5\u542f\u52a8\u76ee\u6807\u5e94\u7528: " + this.targetPackage);
                }
            }
            catch (Exception e) {
                Logger.safeError(TAG, "\u542f\u52a8\u76ee\u6807\u5e94\u7528\u5931\u8d25", e);
            }
            return;
        }
        if (this.isDeviceSdkDemoTarget()) {
            this.launchDeviceSdkTargetApp("test_start");
        } else {
            this.launchGenericTargetApp("test_start");
        }
    }

    private boolean isTargetAppAlreadyForeground() {
        if (this.targetPackage == null || this.targetPackage.isEmpty()) {
            return false;
        }
        try {
            TestAccessibilityService a11y = TestAccessibilityService.getInstance();
            if (a11y == null) {
                return false;
            }
            String fg = a11y.getForegroundPackageName();
            return this.targetPackage.equals(fg);
        }
        catch (Exception e) {
            return false;
        }
    }

    private String readTestFile(String fileName) throws IOException {
        String line;
        File testFile = new File(this.getFilesDir(), "testflows/" + fileName);
        if (testFile.exists()) {
            String line2;
            Logger.safeInfo(TAG, "\u4ecefilesDir\u8bfb\u53d6: " + testFile.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new FileReader(testFile));
            StringBuilder sb = new StringBuilder();
            while ((line2 = reader.readLine()) != null) {
                sb.append(line2);
            }
            reader.close();
            return sb.toString();
        }
        Logger.safeInfo(TAG, "\u4eceassets\u8bfb\u53d6: " + fileName);
        InputStream is = this.getAssets().open("testflows/" + fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, (CharSequence)"\u6d4b\u8bd5\u6267\u884c", 2);
            NotificationManager manager = (NotificationManager)this.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent((Context)this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)0, (Intent)notificationIntent, (int)0x4000000);
        return new NotificationCompat.Builder((Context)this, CHANNEL_ID).setContentTitle((CharSequence)"TestApp").setContentText((CharSequence)text).setSmallIcon(17301569).setContentIntent(pendingIntent).build();
    }

    private void updateNotification(String text) {
        Notification notification = this.createNotification(text);
        NotificationManager manager = (NotificationManager)this.getSystemService(NotificationManager.class);
        manager.notify(1001, notification);
    }

    private void showToast(String message) {
        this.handler.post(() -> Toast.makeText((Context)this, (CharSequence)message, (int)0).show());
    }

    public void onDestroy() {
        Logger.safeInfo(TAG, "TestExecuteService onDestroy");
        isRunning.set(false);
        shouldStop.set(true);
        if (this.handler != null) {
            this.handler.removeCallbacksAndMessages(null);
            Logger.safeInfo(TAG, "\u5df2\u6e05\u7406\u6240\u6709handler\u56de\u8c03");
        }
        this.cancelDeviceSdkDialogHandlers();
        this.shutdownTestScheduleThread();
        this.shutdownDeviceSdkGuiPollThread();
        this.stopTotalTimeoutMonitor();
        this.stopStepTimeoutMonitor();
        try {
            this.unregisterReceiver(this.commandReceiver);
        }
        catch (Exception e) {
            Logger.safeError(TAG, "unregisterReceiver\u5931\u8d25", e);
        }
        TestReplySdk.unregisterListener((Context)this, (BroadcastReceiver)this.testReplyReceiver);
        this.testReplyReceiver = null;
        if (this.guiScreenCaptureResultReceiver != null) {
            TestReplySdk.unregisterListener((Context)this, (BroadcastReceiver)this.guiScreenCaptureResultReceiver);
            this.guiScreenCaptureResultReceiver = null;
        }
        if (runningInstance == this) {
            runningInstance = null;
        }
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    static {
        guiCaptureRequestIdSeq = new AtomicLong(1L);
        isRunning = new AtomicBoolean(false);
        shouldStop = new AtomicBoolean(false);
    }

    private static interface StepScreenshotCapturer {
        public boolean capture(File var1);
    }

    private static interface FinalScreenshotCapturer {
        public boolean capture(File var1);
    }
}
