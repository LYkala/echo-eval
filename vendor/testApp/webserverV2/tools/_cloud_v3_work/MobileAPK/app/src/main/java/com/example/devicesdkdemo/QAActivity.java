package com.example.devicesdkdemo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.content.SharedPreferences;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.api.log.SdkLocalLogConfig;
import com.huawei.aisfsdkv2.api.log.SdkLogConfig;
import com.huawei.aisfsdkv2.api.session.SessionApi;
import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionRequest;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.DialogInfo;
import com.huawei.aisfsdkv2.api.session.DialogControl;
import com.huawei.aisfsdkv2.api.session.OutputTypeEnum;
import com.huawei.aisfsdkv2.api.session.TextInfo;
import com.huawei.aisfsdkv2.api.session.TaskInfo;
import com.huawei.aisfsdkv2.api.session.GuiTaskInfo;
import com.huawei.aisfsdkv2.api.session.GuiTaskEnum;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.SimpleDateFormat;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * QA 对话页面
 * 使用 SessionApi 创建会话并收发消息
 */
public class QAActivity extends AppCompatActivity {

    private static final String TAG = "QAActivity";
    private LinearLayout chatContainer;
    private TextView tvSessionTitle;
    private EditText etInput;
    private Button btnSend;
    private ScrollView scrollView;
    private View rootLayout;
    private final Rect keyboardVisibleRect = new Rect();

    // 开发者配置（在「日志设置」对话框内）
    private AlertDialog devSettingsDialog;

    // 静态Handler，不依赖Activity实例
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** AISF 执行完成后，停留再拉回 QA 聊天页的等待时长。 */
    private static final long RETURN_QA_AFTER_DONE_DELAY_MS = 3000L;
    private static Runnable pendingReturnToQaRunnable;

    // Session 相关（静态，跨 Activity 生命周期保持）
    private static SessionApi sessionApi;
    private static final List<String> sessionIdList = new ArrayList<>();
    private static final Map<String, List<QaChatRenderer.ChatLine>> chatLinesMap = new LinkedHashMap<>();
    private static String currentSessionId = null;
    private static int sessionCounter = 0; // 会话编号计数器
    // 流式响应拼接相关
    private static final Map<String, StringBuilder> streamingTextMap = new HashMap<>();
    private static final Map<String, String> streamingTimestampMap = new HashMap<>();
    /** TEXT 流静默这么久即认为本轮文本结束：避免只有 TEXT 的轮次一直空等到 TestApp watchdog。 */
    private static final long STREAM_IDLE_FINALIZE_MS = 8000L;
    private static final Map<String, Runnable> streamingIdleTasks = new HashMap<>();

    // 当前Activity实例的弱引用，供静态回调使用
    private static WeakReference<QAActivity> currentActivityRef = null;

    // 悬浮球相关
    private View floatingBallView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingBallParams;
    private boolean isFloatingBallShowing = false;

    /** 用户/无人值守已请求终止 GUI，忽略后续接管弹窗，且不再 resumeMessageHandle。 */
    private static volatile boolean guiAbortRequested = false;
    /** 下一次「停止任务」上报给 webserver 的原因（人工接管等）。 */
    private static volatile String pendingCaseStopReason = null;
    private static volatile boolean unattendedCancelStopInFlight = false;
    private static volatile long lastUnattendedCancelStopMs = 0L;
    private static final long UNATTENDED_CANCEL_DEBOUNCE_MS = 8000L;

    private static void clearGuiAbortState() {
        guiAbortRequested = false;
        pendingCaseStopReason = null;
        unattendedCancelStopInFlight = false;
    }
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qa);

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        getWindow().setStatusBarColor(getColor(R.color.qa_surface));

        // 保存当前Activity引用
        currentActivityRef = new WeakReference<>(this);

        // 初始化视图
        chatContainer = findViewById(R.id.chat_container);
        tvSessionTitle = findViewById(R.id.tv_session_title);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);
        scrollView = findViewById(R.id.scroll_view);
        rootLayout = findViewById(R.id.qa_root);
        setupKeyboardScrollAssist();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 清屏按钮点击事件
        findViewById(R.id.btn_clear).setOnClickListener(v -> clearMessages());

        // 发送按钮点击事件
        btnSend.setOnClickListener(v -> sendMessage());

        // 停止任务按钮点击事件
        findViewById(R.id.btn_stop_task).setOnClickListener(v -> stopTask());

        // 停止算力主机任务按钮点击事件
        findViewById(R.id.btn_stop_compute_instance).setOnClickListener(v -> stopComputeInstanceTask());

        // 会话管理按钮点击事件
        findViewById(R.id.btn_session_manage).setOnClickListener(v -> showSessionManageDialog());

        findViewById(R.id.btn_dev_settings).setOnClickListener(v -> showDisplaySettingsDialog());

        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        applySdkLogConfigFromPrefs(prefs);

        // 恢复消息内容
        if (currentSessionId != null && chatLinesMap.containsKey(currentSessionId)) {
            refreshChatDisplay();
        }
        updateSessionTitle();

        // 创建/恢复 Session
        setupSession();
    }

    /**
     * 将 QA 页日志开关即时应用到 AISFSDK（不必重登）。
     */
    private void applySdkLogConfigFromPrefs(SharedPreferences prefs) {
        try {
            if (!AISFSDKClient.getInstance().isInitialized()) {
                Log.w(TAG, "applySdkLogConfigFromPrefs: SDK not initialized yet");
                return;
            }
            SdkLogConfig logConfig = new SdkLogConfig();
            SdkLocalLogConfig localLogConfig = new SdkLocalLogConfig();
            localLogConfig.setLogLevel(LogLevelEnum.INFO);
            localLogConfig.setMaxLogFileSize(10 * 1024 * 1024);
            localLogConfig.setMaxLogFileCount(5);
            logConfig.setSdkLocalLogConfig(localLogConfig);
            logConfig.setStdoutEnable(true);
            logConfig.setSecurityFilterEnable(ConfigActivity.isSecurityFilterEnabled(prefs));
            logConfig.setLogEnable(ConfigActivity.isLogEnable(prefs));
            AISFSDKClient.getLogApi().setSdkLogConfig(logConfig);
            Log.i(TAG, "applySdkLogConfigFromPrefs: logEnable=" + ConfigActivity.isLogEnable(prefs)
                    + " securityFilter=" + ConfigActivity.isSecurityFilterEnabled(prefs));
        } catch (Exception e) {
            Log.e(TAG, "applySdkLogConfigFromPrefs failed", e);
        }
    }

    /**
     * 设置 Session：首次进入时自动创建第一个会话
     */
    private void setupSession() {
        if (sessionApi == null) {
            sessionApi = AISFSDKClient.getSessionApi();
        }

        if (currentSessionId == null) {
            // 首次进入，创建第一个会话
            createNewSession();
        } else {
            appendMessage("系统", "恢复已有会话: " + shortId(currentSessionId));
        }
    }

    /**
     * 新增会话（核心逻辑）
     */
    private void createNewSession() {
        if (sessionApi == null) {
            Toast.makeText(this, "SDK未初始化，无法创建会话", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] idHolder = {null};
        sessionCounter++;
        final int sessionNum = sessionCounter;
        String newId = sessionApi.createSession(new SessionCallback() {
            @Override
            public void onReceived(int errorCode, String errorMessage, SessionResponseData data) {
                String sid = idHolder[0];
                // 立即打印日志，确认回调触发时机
                Log.i(TAG, "[回调触发] thread=" + Thread.currentThread().getName()
                        + ", errorCode=" + errorCode + ", sessionId=" + shortId(sid));

                if (data != null) {
                    Log.i(TAG, "[回调详情] outputType=" + data.getOutputType()
                            + ", hasGuiTask=" + (data.getGuiTaskInfo() != null)
                            + ", hasDialog=" + (data.getDialogInfo() != null));
                }

                // GUI_TASK 类型：立即在回调线程停止消息处理
                if (errorCode == 0 && data != null
                        && data.getOutputType() == OutputTypeEnum.GUI_TASK
                        && data.getGuiTaskInfo() != null) {
                    Log.w(TAG, "[关键] 检测到GUI_TASK，调用stopMessageHandle()");
                    if (sessionApi != null) {
                        sessionApi.stopMessageHandle();
                    }
                }

                // 使用同步锁确保回调串行处理完成
                final Object lock = new Object();
                final boolean[] completed = {false};

                final String logMsg = "回调触发 - errorCode=" + errorCode
                        + ", errorMsg=" + (errorMessage != null ? errorMessage : "null")
                        + ", outputType=" + (data != null ? data.getOutputType() : "null");

                // 使用静态Handler post到主线程（不依赖Activity实例）
                mainHandler.post(() -> {
                    Log.i(TAG, "[UI更新] 开始处理回调, currentThread=" + Thread.currentThread().getName());

                    // 获取当前Activity实例
                    QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;

                    // 测试聚焦模式：如果消息发送成功（errorCode=0 且 errorMsg为空），不显示调试信息
                    SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
                    boolean focusMode = ConfigActivity.isFocusModeEnabled(prefs);
                    boolean compactChat = activity != null
                            ? QaChatRenderer.shouldCompactIngest(activity)
                            : QaChatRenderer.shouldCompactIngest(QAActivity.this);
                    boolean shouldSkipDebug = compactChat && errorCode == 0 
                            && (errorMessage == null || errorMessage.isEmpty());
                    
                    if (!shouldSkipDebug) {
                        // 更新消息到静态map（不依赖Activity）
                        appendMessageToSession(sid, "调试", logMsg);
                    }

                    // 流式收口：切勿在「尚无 TEXT」时被 NOTIFY/DIALOG 提前 finalize ReplyMetrics，
                    // 否则 UI 仍能显示后续 TEXT，但 State.finalized=true → TestApp wait-response 永远等不到 final。
                    if (errorCode != 0) {
                        finalizeStreaming(sid, true);
                    } else if (data != null && data.getOutputType() != OutputTypeEnum.TEXT) {
                        boolean guiRelated = data.getOutputType() == OutputTypeEnum.GUI_TASK
                                || data.getOutputType() == OutputTypeEnum.TASK_STATUS;
                        boolean hasStreamingText = streamingTextMap.containsKey(sid);
                        if (hasStreamingText) {
                            // 已有文本流：非 TEXT 信号收口；GUI 相关只收 UI 不发 final
                            finalizeStreaming(sid, !guiRelated);
                        } else if (guiRelated) {
                            // GUI 开始：只停 UI 流式拼接，不锁死 ReplyMetrics
                            finalizeStreaming(sid, false);
                        }
                        // NOTIFY / DIALOG / 其它：无文本流时不碰 ReplyMetrics，等 TEXT 或 idle 收口
                    }

                    if (errorCode == 0 && data != null) {
                        if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                            String dialogHint = data.getDialogInfo().getTitle() != null
                                    ? data.getDialogInfo().getTitle() : "dialog";
                            ReplyMetrics.onFirstSignal(sid, "DIALOG", dialogHint);
                            // 澄清弹窗也要通知 TestApp（澄清自动回复 / 避免干等）
                            ReplyMetrics.publishDialogSignal(sid, dialogHint);
                            if (activity != null && !activity.isFinishing()) {
                                activity.showDialog(data.getDialogInfo());
                            }
                        } else if (data.getOutputType() == OutputTypeEnum.GUI_TASK && data.getGuiTaskInfo() != null) {
                            ReplyMetrics.onFirstSignal(sid, "GUI_TASK",
                                    String.valueOf(data.getGuiTaskInfo().getGuiTaskType()));
                            if (activity != null && !activity.isFinishing()) {
                                activity.showGuiTaskDialog(data.getGuiTaskInfo());
                            }
                        } else {
                            // 测试聚焦模式过滤逻辑（使用前面已定义的 prefs 和 focusMode）
                            
                            // TASK_STATUS：完成时收口时延；GUI 结束后拉回 QA
                            // 无人值守/自动化跑测：不要在这里提前拉回（TestApp 仍在 wait-response/传截图，
                            // 网页会显示「执行中」；由 TestApp 收尾传完后再拉 QA）
                            if (data.getOutputType() == OutputTypeEnum.TASK_STATUS
                                    && data.getTaskInfo() != null
                                    && data.getTaskInfo().isTaskCompleted()) {
                                boolean returnToQa = ReplyMetrics.shouldReturnToQaAfterTask(sid);
                                ReplyMetrics.onTaskCompleted(sid);
                                boolean unattended = ConfigActivity.isUnattendedEnabled(prefs);
                                if (returnToQa && activity != null && !activity.isFinishing()) {
                                    if (unattended) {
                                        Log.i(TAG, "TASK_STATUS: unattended skip early returnToQa");
                                        activity.appendMessage("系统",
                                                "TASK_STATUS: 无人值守跳过提前回 QA（等 TestApp 收尾）");
                                    } else {
                                        activity.returnToQaChatAfterGui("TASK_STATUS");
                                    }
                                }
                            }
                            // TASK_STATUS 且已无挂起 GUI：硬性收口，不再等文本流/下一个信号
                            // （taskCompleted 时 onTaskCompleted 已 sync 收口，这里是兜底，finalize 内部去重）
                            if (data.getOutputType() == OutputTypeEnum.TASK_STATUS
                                    && !ReplyMetrics.hasPendingGui()
                                    && (data.getTaskInfo() == null || data.getTaskInfo().isTaskCompleted())) {
                                ReplyMetrics.finalize(sid, "task_status");
                            }

                            // TASK_STATUS 类型：如果 taskCompleted=true 且 taskStatusCode=200，跳过显示
                            boolean shouldSkip = compactChat && data.getOutputType() == OutputTypeEnum.TASK_STATUS 
                                    && data.getTaskInfo() != null 
                                    && data.getTaskInfo().isTaskCompleted() 
                                    && data.getTaskInfo().getTaskStatusCode() == 200;
                            
                            if (shouldSkip) {
                                // 不显示，跳过
                            } else if (compactChat && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 聚焦模式：流式响应拼接，将多个TEXT chunk合并为一条显示
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                if (streamingTextMap.containsKey(sid)) {
                                    // 继续拼接：追加到已有的流式文本
                                    streamingTextMap.get(sid).append(textContent);
                                } else {
                                    // 新的流式响应开始
                                    String streamTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                                    streamingTimestampMap.put(sid, streamTimestamp);
                                    streamingTextMap.put(sid, new StringBuilder(textContent));
                                }
                                // 空 chunk 不续命，否则服务端心跳/空包会让本轮永远收不了口
                                if (textContent != null && !textContent.isEmpty()) {
                                    scheduleStreamingIdleFinalize(sid);
                                }
                                // 流式文本在UI刷新时单独拼接显示，不调用 appendMessageToSession
                            } else if (!compactChat && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 非紧凑模式：逐条显示每个TEXT chunk
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                appendMessageToSession(sid, "服务端", "text=" + textContent);
                                if (textContent != null && !textContent.isEmpty()) {
                                    scheduleStreamingIdleFinalize(sid);
                                }
                            } else {
                                // 正常模式：打印完整 data
                                appendMessageToSession(sid, "服务端", "data=" + data.toString());
                            }
                        }
                    } else {
                        appendMessageToSession(sid, "错误", "Code: " + errorCode + ", Msg: " + errorMessage);
                    }

                    // 刷新当前Activity的UI（如果存在且是正确的会话）
                    if (activity != null && !activity.isFinishing() && sid.equals(currentSessionId)) {
                        activity.refreshChatDisplay(sid);
                    }

                    Log.i(TAG, "[UI更新] 回调处理完成");

                    // 通知回调线程处理完成
                    synchronized (lock) {
                        completed[0] = true;
                        lock.notify();
                    }
                });

                // 等待UI更新完成（同步阻塞）
                synchronized (lock) {
                    while (!completed[0]) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "等待UI更新被中断", e);
                            break;
                        }
                    }
                }
                Log.i(TAG, "[回调返回] 回调真正完成，返回SDK处理下一条");
            }
        });
        idHolder[0] = newId;

        if (newId != null) {
            sessionIdList.add(newId);
            chatLinesMap.put(newId, new ArrayList<>());
            // 切换到新会话
            switchToSession(newId);
            appendMessage("系统", "会话" + sessionNum + " 创建成功！ID: " + shortId(newId));
        } else {
            sessionCounter--; // 回滚计数
            Toast.makeText(this, "创建会话失败，请检查SDK是否已初始化并登录", Toast.LENGTH_SHORT).show();
            appendMessage("错误", "创建会话失败，请检查SDK是否已初始化并登录");
        }
    }

    /**
     * 删除指定会话
     */
    private void deleteSession(String targetId) {
        Set<String> one = new LinkedHashSet<>();
        one.add(targetId);
        deleteSessions(one);
    }

    private void deleteSessions(Set<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        boolean deletingCurrent = currentSessionId != null && targetIds.contains(currentSessionId);
        for (String targetId : targetIds) {
            if (sessionApi != null) {
                sessionApi.destroySession(targetId);
            }
            sessionIdList.remove(targetId);
            chatLinesMap.remove(targetId);
            streamingTextMap.remove(targetId);
            streamingTimestampMap.remove(targetId);
        }
        if (deletingCurrent) {
            if (!sessionIdList.isEmpty()) {
                switchToSession(sessionIdList.get(0));
                appendMessage("系统", "已切换到会话: " + shortId(currentSessionId));
            } else {
                currentSessionId = null;
                chatContainer.removeAllViews();
                updateSessionTitle();
            }
        } else {
            updateSessionTitle();
        }
        Toast.makeText(this, "已删除 " + targetIds.size() + " 个会话", Toast.LENGTH_SHORT).show();
    }

    /**
     * 切换到指定会话
     */
    private void switchToSession(String targetId) {
        if (targetId.equals(currentSessionId)) return;
        currentSessionId = targetId;
        refreshChatDisplay(targetId);
        updateSessionTitle();
    }

    /**
     * 更新标题栏会话信息
     */
    private void updateSessionTitle() {
        if (currentSessionId == null || sessionIdList.isEmpty()) {
            tvSessionTitle.setText("QA 对话");
            return;
        }
        int index = sessionIdList.indexOf(currentSessionId) + 1;
        tvSessionTitle.setText("QA 对话 (" + index + "/" + sessionIdList.size() + ")");
    }

    /**
     * 显示设置：纯净版 / 日志 / 聚焦等。
     */
    private void showDisplaySettingsDialog() {
        if (isFinishing()) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_qa_dev_settings, null, false);
        SwitchCompat switchPure = content.findViewById(R.id.switch_pure_mode);
        SwitchCompat switchLog = content.findViewById(R.id.switch_log_enable);
        SwitchCompat switchSecurity = content.findViewById(R.id.switch_security_filter);
        SwitchCompat switchFocus = content.findViewById(R.id.switch_focus_mode);

        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        switchPure.setChecked(ConfigActivity.isPureModeEnabled(prefs));
        switchLog.setChecked(ConfigActivity.isLogEnable(prefs));
        switchSecurity.setChecked(prefs.getBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, true));
        switchFocus.setChecked(ConfigActivity.isFocusModeEnabled(prefs));

        switchPure.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(ConfigActivity.KEY_PURE_MODE_ENABLED, isChecked).apply();
            refreshChatDisplay();
        });
        switchLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences p = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
            p.edit().putBoolean(ConfigActivity.KEY_LOG_ENABLE, isChecked).apply();
            applySdkLogConfigFromPrefs(p);
            Toast.makeText(this, isChecked ? "SDK 日志已开启" : "SDK 日志已关闭", Toast.LENGTH_SHORT).show();
        });
        switchSecurity.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences p = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
            p.edit().putBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, isChecked).apply();
            applySdkLogConfigFromPrefs(p);
        });
        switchFocus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(ConfigActivity.KEY_FOCUS_MODE_ENABLED, isChecked).apply();
            finalizeStreaming(currentSessionId);
            refreshChatDisplay();
        });

        if (devSettingsDialog != null && devSettingsDialog.isShowing()) {
            devSettingsDialog.dismiss();
        }
        content.findViewById(R.id.btn_settings_close).setOnClickListener(v -> devSettingsDialog.dismiss());
        devSettingsDialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();
        showQaStyledDialog(devSettingsDialog);
    }

    private void showQaStyledDialog(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * 显示会话管理弹窗
     */
    private void showSessionManageDialog() {
        if (isFinishing()) {
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.dialog_qa_session_manage, null, false);
        ListView listView = content.findViewById(R.id.list_sessions);
        TextView emptyView = content.findViewById(R.id.tv_session_empty);
        View selectBar = content.findViewById(R.id.layout_session_select_bar);
        CheckBox cbSelectAll = content.findViewById(R.id.cb_session_select_all);
        TextView tvSelectedCount = content.findViewById(R.id.tv_session_selected_count);
        TextView btnDeleteSelected = content.findViewById(R.id.btn_session_delete_selected);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .create();

        final Set<String> selectedIds = new LinkedHashSet<>();
        final BaseAdapter[] adapterHolder = new BaseAdapter[1];

        Runnable refreshSelectUi = () -> {
            int selected = selectedIds.size();
            int total = sessionIdList.size();
            if (total == 0) {
                tvSelectedCount.setText("");
                btnDeleteSelected.setEnabled(false);
                btnDeleteSelected.setAlpha(0.4f);
                cbSelectAll.setOnCheckedChangeListener(null);
                cbSelectAll.setChecked(false);
                cbSelectAll.setEnabled(false);
                return;
            }
            cbSelectAll.setEnabled(true);
            tvSelectedCount.setText(selected > 0 ? ("已选 " + selected + " 项") : "");
            btnDeleteSelected.setEnabled(selected > 0);
            btnDeleteSelected.setAlpha(selected > 0 ? 1f : 0.4f);
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(selected > 0 && selected == total);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                selectedIds.clear();
                if (isChecked) {
                    selectedIds.addAll(sessionIdList);
                }
                if (adapterHolder[0] != null) {
                    adapterHolder[0].notifyDataSetChanged();
                }
                buttonView.post(() -> {
                    int sel = selectedIds.size();
                    tvSelectedCount.setText(sel > 0 ? ("已选 " + sel + " 项") : "");
                    btnDeleteSelected.setEnabled(sel > 0);
                    btnDeleteSelected.setAlpha(sel > 0 ? 1f : 0.4f);
                });
            });
        };

        if (sessionIdList.isEmpty()) {
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            selectBar.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = listView.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                listView.setLayoutParams(lp);
            }
            refreshSelectUi.run();
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            selectBar.setVisibility(View.VISIBLE);
            float density = getResources().getDisplayMetrics().density;
            int maxListPx = Math.round(240f * density);
            ViewGroup.LayoutParams lp = listView.getLayoutParams();
            if (lp != null) {
                lp.height = maxListPx;
                listView.setLayoutParams(lp);
            }
            BaseAdapter adapter = new BaseAdapter() {
                @Override
                public int getCount() {
                    return sessionIdList.size();
                }

                @Override
                public Object getItem(int position) {
                    return sessionIdList.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View row = convertView;
                    if (row == null) {
                        row = getLayoutInflater().inflate(R.layout.item_qa_session, parent, false);
                    }
                    String sid = sessionIdList.get(position);
                    boolean current = sid.equals(currentSessionId);
                    CheckBox cb = row.findViewById(R.id.cb_session);
                    TextView name = row.findViewById(R.id.tv_session_name);
                    TextView id = row.findViewById(R.id.tv_session_id);
                    View dot = row.findViewById(R.id.view_dot);
                    TextView badge = row.findViewById(R.id.tv_current_badge);
                    name.setText("会话 " + (position + 1));
                    id.setText(shortId(sid));
                    dot.setBackgroundResource(current
                            ? R.drawable.bg_qa_session_dot_active
                            : R.drawable.bg_qa_session_dot_inactive);
                    name.setTextColor(getColor(current ? R.color.qa_primary : R.color.qa_text_primary));
                    badge.setVisibility(current ? View.VISIBLE : View.GONE);
                    row.setBackgroundResource(current
                            ? R.drawable.bg_qa_session_item_active
                            : R.drawable.bg_qa_session_item);

                    cb.setOnCheckedChangeListener(null);
                    cb.setChecked(selectedIds.contains(sid));
                    cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            selectedIds.add(sid);
                        } else {
                            selectedIds.remove(sid);
                        }
                        refreshSelectUi.run();
                    });
                    return row;
                }
            };
            adapterHolder[0] = adapter;
            listView.setAdapter(adapter);
            refreshSelectUi.run();
        }

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < sessionIdList.size()) {
                switchToSession(sessionIdList.get(position));
                dialog.dismiss();
            }
        });

        btnDeleteSelected.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) {
                return;
            }
            int count = selectedIds.size();
            new AlertDialog.Builder(QAActivity.this)
                    .setTitle("删除会话")
                    .setMessage("确认删除已选的 " + count + " 个会话？\n对应消息记录将被清除。")
                    .setPositiveButton("删除", (d, which) -> {
                        deleteSessions(new LinkedHashSet<>(selectedIds));
                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        content.findViewById(R.id.btn_session_close).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btn_session_new).setOnClickListener(v -> {
            dialog.dismiss();
            createNewSession();
        });

        showQaStyledDialog(dialog);
    }

    /**
     * 发送消息（复用当前会话；断链时先 reconnect 再发，不 createSession）。
     */
    private void sendMessage() {
        String message = etInput.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }

        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，请先初始化SDK并登录");
            return;
        }

        // 结束之前的流式拼接（用户发送新消息标志着上一轮响应结束）
        finalizeStreaming(currentSessionId);
        clearGuiAbortState();
        if (sessionApi != null && sessionApi.isMessageHandlePaused()) {
            sessionApi.resumeMessageHandle();
        }

        // 显示发送的消息
        appendMessage("我", message);
        etInput.setText("");
        scrollChatToBottom();

        withAisfConnection("user_send", () -> {
            // 必须先登记 SEND，再调 SDK：否则极快回包会在 onUserSend 前到达并被丢弃
            ReplyMetrics.onUserSend(currentSessionId, message);
            SessionRequest request = new SessionRequest.Builder()
                    .userInputText(message)
                    .build();
            boolean success = sessionApi.sendMessage(currentSessionId, request);
            if (!success) {
                appendMessage("错误", "发送失败，会话可能已断开");
            }
        });
    }

    /**
     * 已连接则立刻执行；否则后台 reconnect 等待后再回主线程发送。
     */
    private void withAisfConnection(String reason, Runnable sendAction) {
        if (AisfConnectionKeeper.isConnected()) {
            sendAction.run();
            return;
        }
        appendMessage("系统", "AISF 已断链，正在重连…");
        new Thread(() -> {
            boolean ok = AisfConnectionKeeper.ensureConnected(15_000L, reason);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (!ok) {
                    appendMessage("错误", "AISF 重连失败，消息未发送");
                    return;
                }
                appendMessage("系统", "AISF 已恢复连接");
                sendAction.run();
            });
        }, "aisf-ensure-" + reason).start();
    }

    /**
     * 停止当前任务（并尝试停止算力主机任务）。停止后保持消息处理暂停，避免 AISF 继续弹接管窗循环。
     */
    private void stopTask() {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法停止任务");
            return;
        }
        guiAbortRequested = true;
        hideFloatingBall();
        cancelPendingEndTakeover();
        String stopMsg = pendingCaseStopReason != null
                ? pendingCaseStopReason : "用户停止";
        pendingCaseStopReason = null;
        // 同步打断 webserver 用例对 GUI taskCompleted 的等待
        WebServerCaseRunner.abortActiveCaseFromDemoStop(stopMsg);

        boolean success = sessionApi.stopTask(currentSessionId);
        boolean computeStopped = sessionApi.stopOtherDeviceCategoryTask(currentSessionId, "3");
        if (success) {
            appendMessage("系统", "任务已停止");
        } else if (computeStopped) {
            appendMessage("系统", "本机无任务，已向算力主机发送停止");
        } else {
            appendMessage("错误", "停止任务失败（本地栈空且算力主机停止未发出）");
        }
        //  deliberately 不 resumeMessageHandle，防止队列里接管/GUI 消息继续循环
    }

    /**
     * webserver 下发 stop_test：回 QA 并执行与「停止任务」按钮相同的 AISF 停止。
     * CaseRunner 的打断由 WebServerWsClient 先行 requestStop。
     */
    public static void stopAisfFromWebServer(Context appContext, String reason) {
        guiAbortRequested = true;
        final String stopMsg = (reason != null && !reason.trim().isEmpty())
                ? reason.trim() : "用户停止";
        new Thread(() -> {
            try {
                if (appContext != null) {
                    WsUiHelper.returnToQaChatDirect(appContext.getApplicationContext());
                }
                sleepQuiet(700);
                QAActivity act = currentActivityRef != null ? currentActivityRef.get() : null;
                if (act != null && !act.isFinishing()) {
                    act.runOnUiThread(() -> act.stopTaskFromWebServer(stopMsg));
                    return;
                }
                // Activity 不在前台：直接 API 停 + 尝试点按钮
                if (sessionApi != null && currentSessionId != null) {
                    try {
                        sessionApi.stopTask(currentSessionId);
                        sessionApi.stopOtherDeviceCategoryTask(currentSessionId, "3");
                    } catch (Throwable t) {
                        Log.w(TAG, "stopAisfFromWebServer api: " + t.getMessage());
                    }
                }
                WsUiHelper.tapStopTaskButton();
            } catch (Throwable t) {
                Log.w(TAG, "stopAisfFromWebServer: " + t.getMessage());
            }
        }, "ws-stop-aisf").start();
    }

    private void stopTaskFromWebServer(String reason) {
        hideFloatingBall();
        cancelPendingEndTakeover();
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("系统", "webserver 停止：无会话，尝试点击停止任务");
            WsUiHelper.tapStopTaskButton();
            return;
        }
        appendMessage("系统", "webserver 停止任务: " + reason);
        boolean success = sessionApi.stopTask(currentSessionId);
        boolean computeStopped = sessionApi.stopOtherDeviceCategoryTask(currentSessionId, "3");
        if (!success && !computeStopped) {
            if (!WsUiHelper.tapStopTaskButton()) {
                appendMessage("错误", "webserver 停止：API 与按钮均未成功");
            }
        }
    }

    /**
     * 使用SDK专用停止其他设备类型接口
     * deviceCategory=3表示算力主机
     */
    private void stopComputeInstanceTask() {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法停止算力主机任务");
            return;
        }
        boolean success = sessionApi.stopOtherDeviceCategoryTask(currentSessionId, "3");
        if (success) {
            appendMessage("系统", "算力主机任务已停止");
        }
        else {
            appendMessage("错误", "停止算力主机任务失败");
        }
    }

    /**
     * 清空当前会话消息
     */
    private void clearMessages() {
        if (currentSessionId != null) {
            List<QaChatRenderer.ChatLine> lines = chatLinesMap.get(currentSessionId);
            if (lines != null) {
                lines.clear();
            }
            streamingTextMap.remove(currentSessionId);
            streamingTimestampMap.remove(currentSessionId);
        }
        if (chatContainer != null) {
            chatContainer.removeAllViews();
        }
    }

    private void refreshChatDisplay() {
        refreshChatDisplay(currentSessionId);
    }

    private void refreshChatDisplay(String sid) {
        if (sid == null || chatContainer == null) {
            return;
        }
        String streamPreview = null;
        StringBuilder streamSb = streamingTextMap.get(sid);
        if (streamSb != null && streamSb.length() > 0) {
            streamPreview = streamSb.toString();
        }
        QaChatRenderer.render(
                this,
                chatContainer,
                scrollView,
                chatLinesMap.get(sid),
                streamPreview);
        scrollChatToBottom();
    }

    private void setupKeyboardScrollAssist() {
        if (rootLayout == null) {
            return;
        }
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (scrollView == null || rootLayout == null) {
                return;
            }
            rootLayout.getWindowVisibleDisplayFrame(keyboardVisibleRect);
            int screenHeight = rootLayout.getRootView().getHeight();
            int keyboardHeight = screenHeight - keyboardVisibleRect.bottom;
            if (keyboardHeight > screenHeight / 5) {
                scrollChatToBottom();
            }
        });
    }

    private void scrollChatToBottom() {
        if (scrollView != null) {
            QaChatRenderer.scrollToBottom(scrollView);
        }
    }

    /**
     * 追加消息到当前会话（存储数据并刷新UI）
     */
    private void appendMessage(String sender, String message) {
        appendMessageToSession(currentSessionId, sender, message);
        refreshChatDisplay();
    }

    /**
     * 追加消息到指定会话（只存储数据，不刷新UI）
     */
    private static void appendMessageToSession(String sid, String sender, String message) {
        if (sid == null) {
            return;
        }
        List<QaChatRenderer.ChatLine> lines = chatLinesMap.get(sid);
        if (lines == null) {
            return;
        }
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
        lines.add(new QaChatRenderer.ChatLine(timestamp, sender, message));
    }

    /**
     * 结束流式响应拼接，将累积的文本作为一条消息写入 chatLinesMap
     * @param emitReplyMetrics false 时只收口 UI，不通知 wait-response（用于 GUI_TASK 进行中）
     */
    private static void finalizeStreaming(String sid, boolean emitReplyMetrics) {
        cancelStreamingIdleFinalize(sid);
        if (sid == null || !streamingTextMap.containsKey(sid)) {
            if (emitReplyMetrics) {
                ReplyMetrics.finalizeStream(sid);
            }
            return;
        }
        StringBuilder accumulated = streamingTextMap.remove(sid);
        String timestamp = streamingTimestampMap.remove(sid);
        List<QaChatRenderer.ChatLine> sessionLines = chatLinesMap.get(sid);
        if (sessionLines != null && accumulated != null) {
            sessionLines.add(new QaChatRenderer.ChatLine(
                    timestamp != null ? timestamp : "",
                    "服务端",
                    accumulated.toString()));
        }
        if (emitReplyMetrics) {
            ReplyMetrics.finalizeStream(sid);
        }
    }

    private static void finalizeStreaming(String sid) {
        finalizeStreaming(sid, true);
    }

    /** TEXT 流静默计时：每次收到非空 chunk 重新计时，静默超时即收口本轮。 */
    private static void scheduleStreamingIdleFinalize(String sid) {
        if (sid == null) {
            return;
        }
        cancelStreamingIdleFinalize(sid);
        final String sessionId = sid;
        Runnable task = () -> {
            streamingIdleTasks.remove(sessionId);
            Log.i(TAG, "STREAM_IDLE_FINALIZE sid=" + shortSessionId(sessionId)
                    + " idleMs=" + STREAM_IDLE_FINALIZE_MS);
            finalizeStreaming(sessionId);
        };
        streamingIdleTasks.put(sid, task);
        mainHandler.postDelayed(task, STREAM_IDLE_FINALIZE_MS);
    }

    private static void cancelStreamingIdleFinalize(String sid) {
        if (sid == null) {
            return;
        }
        Runnable prev = streamingIdleTasks.remove(sid);
        if (prev != null) {
            mainHandler.removeCallbacks(prev);
        }
    }

    private static String shortSessionId(String sid) {
        return sid == null || sid.length() <= 8 ? String.valueOf(sid) : sid.substring(0, 8);
    }

    /**
     * 取 sessionId 前8位用于显示
     */
    private String shortId(String sid) {
        if (sid == null) return "null";
        return sid.substring(0, Math.min(8, sid.length())) + "...";
    }

    /**
     * 显示弹窗
     */
    private void showDialog(DialogInfo dialogInfo) {
        if (dialogInfo == null || dialogInfo.getControlList() == null || dialogInfo.getControlList().isEmpty()) {
            appendMessage("错误", "弹窗信息为空或无按钮");
            return;
        }
        
        // 检查 Activity 是否还活着
        if (isFinishing()) {
            appendMessage("错误", "Activity 正在销毁，无法显示弹窗");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        if (ConfigActivity.isUnattendedEnabled(prefs) && ReplyMetrics.hasPendingGui() && !guiAbortRequested) {
            String cancel = pickUnattendedCancelContent(dialogInfo.getControlList());
            if (cancel != null) {
                handleUnattendedTakeoverCancelAndStop(
                        "弹窗[" + cancel + "]",
                        () -> sendDialogResponse(cancel));
                return;
            }
        }
        if (ConfigActivity.isUnattendedEnabled(prefs)) {
            DialogControl primary = dialogInfo.getControlList().get(0);
            String content = primary.getContent() != null ? primary.getContent() : "确定";
            appendMessage("系统", "无人值守: 自动确认弹窗 [" + content + "]");
            sendDialogResponse(content);
            return;
        }
        
        // 确保在主线程执行
        runOnUiThread(() -> {
            // 再次检查 Activity 状态
            if (isFinishing()) {
                return;
            }

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(QAActivity.this);
            builder.setTitle(dialogInfo.getTitle() != null ? dialogInfo.getTitle() : "提示");
            
            // 根据按钮数量设置不同的按钮
            List<DialogControl> controls = dialogInfo.getControlList();
            
            if (controls.size() >= 1) {
                DialogControl btn1 = controls.get(0);
                builder.setPositiveButton(btn1.getContent() != null ? btn1.getContent() : "确定", (dialog, which) -> {
                    sendDialogResponse(btn1.getContent());
                });
            }
            
            if (controls.size() >= 2) {
                DialogControl btn2 = controls.get(1);
                builder.setNegativeButton(btn2.getContent() != null ? btn2.getContent() : "取消", (dialog, which) -> {
                    sendDialogResponse(btn2.getContent());
                });
            }
            
            if (controls.size() >= 3) {
                DialogControl btn3 = controls.get(2);
                builder.setNeutralButton(btn3.getContent() != null ? btn3.getContent() : "其他", (dialog, which) -> {
                    sendDialogResponse(btn3.getContent());
                });
            }
            
            // 显示弹窗
            builder.setCancelable(false);
            try {
                builder.show();
                appendMessage("系统", "显示弹窗: " + dialogInfo.getTitle());
            } catch (Exception e) {
                appendMessage("错误", "显示弹窗失败: " + e.getMessage());
            }
        });
    }

    /**
     * 显示 GUI_TASK 提醒弹窗
     * 弹窗期间调用 stopMessageHandle 阻止后续消息处理，用户点击后再 resume
     * START_TAKEOVER 类型显示双按钮（取消/确认），其他类型显示单按钮
     */
    private void showGuiTaskDialog(GuiTaskInfo guiTaskInfo) {
        if (isFinishing()) {
            return;
        }
        final GuiTaskEnum guiTaskType = guiTaskInfo.getGuiTaskType();
        if (guiAbortRequested && guiTaskType != GuiTaskEnum.STOP_GUI) {
            appendMessage("系统", "已停止/忽略 GUI: "
                    + (guiTaskType != null ? guiTaskType.name() : "GUI_TASK"));
            return;
        }
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        boolean unattended = ConfigActivity.isUnattendedEnabled(prefs);
        // START_GUI / STOP_GUI：无人值守或关闭 GUI 确认时自动放行
        boolean guiConfirmEnabled = ConfigActivity.isGuiConfirmEnabled(prefs);
        if (guiTaskType == GuiTaskEnum.START_GUI && (unattended || !guiConfirmEnabled)) {
            runOnUiThread(() -> {
                appendMessage("系统", unattended ? "无人值守: START_GUI 自动确认" : "START_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
            });
            return;
        }
        if (guiTaskType == GuiTaskEnum.STOP_GUI && (unattended || !guiConfirmEnabled)) {
            runOnUiThread(() -> {
                appendMessage("系统", unattended ? "无人值守: STOP_GUI 自动确认" : "STOP_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
                handleStopGuiMessage();
            });
            return;
        }
        // 无人值守：人工接管 / 支付 / 开始接管 — 自动取消、拉回 QA、停止任务
        if (unattended && isTakeoverGuiTask(guiTaskType)) {
            handleUnattendedTakeoverCancelAndStop(
                    guiTaskType.name(),
                    () -> sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER));
            return;
        }
        if (unattended) {
            runOnUiThread(() -> {
                appendMessage("系统", "无人值守: " + (guiTaskType != null ? guiTaskType.name() : "GUI_TASK")
                        + " 自动放行");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
            });
            return;
        }
        // 其他GUI任务类型仍需用户确认
        runOnUiThread(() -> {
            if (isFinishing()) {
                if (sessionApi != null) {
                    sessionApi.resumeMessageHandle();
                }
                return;
            }
            String text = guiTaskInfo.getText();
            if (text == null || text.isEmpty()) {
                text = guiTaskInfo.getGuiTaskType() != null
                        ? guiTaskInfo.getGuiTaskType().getDescription()
                        : "GUI_TASK";
            }
            final String displayText = text;
            final boolean isTakeover = guiTaskInfo.getGuiTaskType() == GuiTaskEnum.START_TAKEOVER;
            final boolean isPayment = guiTaskInfo.getGuiTaskType() == GuiTaskEnum.START_PAYMENT;
            final boolean isManual = guiTaskInfo.getGuiTaskType() == GuiTaskEnum.START_MANUAL;
            final boolean isStopGui = guiTaskInfo.getGuiTaskType() == GuiTaskEnum.STOP_GUI;

            if (Settings.canDrawOverlays(QAActivity.this)) {
                // 有悬浮窗权限：用 Application Context 创建，才能设置 TYPE_APPLICATION_OVERLAY
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getApplicationContext());
                builder.setTitle("提示")
                        .setMessage(displayText)
                        .setCancelable(false);
                if (isTakeover) {
                    builder.setPositiveButton("确认", (d, which) -> {
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
                        pendingCaseStopReason = "人工接管";
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isPayment || isManual) {
                    builder.setPositiveButton("确定", (d, which) -> {
                        // 先发送 START_TAKEOVER，然后显示红色悬浮球
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        showFloatingBall();
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
                        pendingCaseStopReason = "人工接管";
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isStopGui) {
                    // STOP_GUI：点击确定后检查前台应用，不是自己则拉起
                    builder.setPositiveButton("确定", (d, which) -> {
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                        handleStopGuiMessage();
                    });
                } else {
                    builder.setPositiveButton("确定", (d, which) -> {
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                }
                android.app.AlertDialog sysDialog = builder.create();
                sysDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                sysDialog.show();
                appendMessage("系统", "显示GUI提醒(顶层): " + displayText);
            } else {
                // 无悬浮窗权限：普通弹窗
                androidx.appcompat.app.AlertDialog.Builder builder =
                        new androidx.appcompat.app.AlertDialog.Builder(QAActivity.this);
                builder.setTitle("提示")
                        .setMessage(displayText)
                        .setCancelable(false);
                if (isTakeover) {
                    builder.setPositiveButton("确认", (d, which) -> {
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
                        pendingCaseStopReason = "人工接管";
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isPayment || isManual) {
                    builder.setPositiveButton("确定", (d, which) -> {
                        // 先发送 START_TAKEOVER，然后显示红色悬浮球
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        showFloatingBall();
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
                        pendingCaseStopReason = "人工接管";
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isStopGui) {
                    // STOP_GUI：点击确定后检查前台应用，不是自己则拉起
                    builder.setPositiveButton("确定", (d, which) -> {
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                        handleStopGuiMessage();
                    });
                } else {
                    builder.setPositiveButton("确定", (d, which) -> {
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                }
                builder.create().show();
                appendMessage("系统", "显示GUI提醒: " + displayText);
            }
        });
    }

    /** 无人值守：确认接管后短暂延迟自动发送结束接管（保留供非 GUI 场景复用） */
    private static final long UNATTENDED_END_TAKEOVER_DELAY_MS = 1000L;
    private Runnable pendingEndTakeoverRunnable;

    private static boolean isTakeoverGuiTask(GuiTaskEnum type) {
        return type == GuiTaskEnum.START_MANUAL
                || type == GuiTaskEnum.START_PAYMENT
                || type == GuiTaskEnum.START_TAKEOVER;
    }

    private static String pickUnattendedCancelContent(List<DialogControl> controls) {
        if (controls == null || controls.isEmpty()) {
            return null;
        }
        for (DialogControl c : controls) {
            String t = c.getContent();
            if (t == null) {
                continue;
            }
            String lower = t.toLowerCase(Locale.US);
            if (t.contains("取消") || t.contains("拒绝") || lower.contains("cancel")) {
                return t;
            }
        }
        if (controls.size() >= 2) {
            DialogControl second = controls.get(1);
            return second.getContent() != null ? second.getContent() : "取消";
        }
        return null;
    }

    private interface UnattendedCancelAction {
        void run();
    }

    /**
     * 无人值守 GUI 接管：发送取消 → 拉回 QA → 点「停止任务」。
     */
    private void handleUnattendedTakeoverCancelAndStop(String reason, UnattendedCancelAction cancelAction) {
        long now = System.currentTimeMillis();
        if (unattendedCancelStopInFlight) {
            Log.i(TAG, "handleUnattendedTakeoverCancelAndStop: already in flight, skip");
            return;
        }
        if (now - lastUnattendedCancelStopMs < UNATTENDED_CANCEL_DEBOUNCE_MS) {
            Log.i(TAG, "handleUnattendedTakeoverCancelAndStop: debounced");
            return;
        }
        unattendedCancelStopInFlight = true;
        lastUnattendedCancelStopMs = now;
        guiAbortRequested = true;
        pendingCaseStopReason = "人工接管";
        cancelPendingEndTakeover();
        runOnUiThread(() -> {
            appendMessage("系统", "无人值守: " + reason + "，取消接管并停止任务");
            hideFloatingBall();
            // 先停任务，再回取消接管；不要 resumeMessageHandle，否则会立刻处理下一条接管消息形成死循环
            stopTaskInternalQuiet();
            if (cancelAction != null) {
                cancelAction.run();
            } else {
                sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
            }
            new Thread(() -> {
                try {
                    WsUiHelper.returnToQaChatDirect(getApplicationContext());
                    sleepQuiet(800);
                    runOnUiThread(() -> {
                        appendMessage("系统", "无人值守: 点击停止任务");
                        if (!WsUiHelper.tapStopTaskButton()) {
                            stopTaskInternalQuiet();
                        }
                    });
                } finally {
                    unattendedCancelStopInFlight = false;
                }
            }, "unattended-cancel-stop").start();
        });
    }

    /** 停止任务但不改 guiAbortRequested / 不重复刷 UI。 */
    private void stopTaskInternalQuiet() {
        if (currentSessionId == null || sessionApi == null) {
            return;
        }
        String stopMsg = pendingCaseStopReason != null
                ? pendingCaseStopReason : "人工接管";
        pendingCaseStopReason = null;
        WebServerCaseRunner.abortActiveCaseFromDemoStop(stopMsg);
        sessionApi.stopTask(currentSessionId);
        sessionApi.stopOtherDeviceCategoryTask(currentSessionId, "3");
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cancelPendingEndTakeover() {
        if (pendingEndTakeoverRunnable != null) {
            mainHandler.removeCallbacks(pendingEndTakeoverRunnable);
            pendingEndTakeoverRunnable = null;
        }
    }

    private void scheduleAutoEndTakeover() {
        cancelPendingEndTakeover();
        pendingEndTakeoverRunnable = () -> {
            if (isFinishing()) {
                return;
            }
            appendMessage("系统", "无人值守: 自动结束接管");
            sendGuiTaskResponse(GuiTaskEnum.STOP_TAKEOVER);
            hideFloatingBall();
            pendingEndTakeoverRunnable = null;
        };
        mainHandler.postDelayed(pendingEndTakeoverRunnable, UNATTENDED_END_TAKEOVER_DELAY_MS);
    }

    /**
     * 发送 GUI_TASK 回包
     */
    private void sendGuiTaskResponse(GuiTaskEnum guiTask) {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送GUI响应");
            return;
        }
        withAisfConnection("gui_task_" + guiTask.name(), () -> {
            Map<String, String> params = new HashMap<>();
            boolean success = sessionApi.sendMessage(currentSessionId, guiTask, params);
            if (success) {
                appendMessage("我", "[GUI响应] " + guiTask.getServerType());
            } else {
                appendMessage("错误", "GUI响应发送失败");
            }
        });
    }

    /**
     * 发送弹窗按钮响应
     */
    private void sendDialogResponse(String content) {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送响应");
            return;
        }
        withAisfConnection("dialog_resp", () -> {
            SessionRequest request = new SessionRequest.Builder()
                    .userInputText(content != null ? content : "")
                    .build();
            boolean success = sessionApi.sendMessage(currentSessionId, request);
            if (success) {
                appendMessage("我", "[弹窗响应] " + content);
            } else {
                appendMessage("错误", "弹窗响应发送失败");
            }
        });
    }

    /**
     * 显示悬浮球（"结束接管"按钮）
     * 点击后发送 STOPTAKEOVER 并关闭悬浮球
     */
    private void showFloatingBall() {
        if (isFloatingBallShowing || !Settings.canDrawOverlays(this)) {
            return;
        }
        runOnUiThread(() -> {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            if (floatingBallView == null) {
                // 创建悬浮球视图 - 使用简单的按钮
                Button ballButton = new Button(getApplicationContext());
                ballButton.setText("结束接管");
                ballButton.setTextSize(12);
                ballButton.setBackgroundColor(0xFFF44336); // 红色背景
                ballButton.setTextColor(0xFFFFFFFF); // 白色文字
                ballButton.setOnClickListener(v -> {
                    // 点击发送 STOP_TAKEOVER
                    sendGuiTaskResponse(GuiTaskEnum.STOP_TAKEOVER);
                    hideFloatingBall();
                });
                floatingBallView = ballButton;
            }
            if (floatingBallParams == null) {
                floatingBallParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        android.graphics.PixelFormat.TRANSLUCENT);
                floatingBallParams.gravity = Gravity.TOP | Gravity.END;
                floatingBallParams.x = 20;
                floatingBallParams.y = 100;
            }
            try {
                windowManager.addView(floatingBallView, floatingBallParams);
                isFloatingBallShowing = true;
                appendMessage("系统", "显示悬浮球: 结束接管");
            } catch (Exception e) {
                appendMessage("错误", "显示悬浮球失败: " + e.getMessage());
            }
        });
    }

    /**
     * 隐藏并移除悬浮球
     */
    private void hideFloatingBall() {
        if (!isFloatingBallShowing || windowManager == null || floatingBallView == null) {
            return;
        }
        runOnUiThread(() -> {
            try {
                windowManager.removeView(floatingBallView);
                isFloatingBallShowing = false;
                appendMessage("系统", "悬浮球已关闭");
            } catch (Exception e) {
                appendMessage("错误", "关闭悬浮球失败: " + e.getMessage());
            }
        });
    }

    /** GUI 结束后一键直达 QA 聊天页（TASK_STATUS / STOP_GUI 共用）。先等 3s 再拉回。 */
    private void returnToQaChatAfterGui(String reason) {
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        if (ConfigActivity.isUnattendedEnabled(prefs)) {
            // 自动化：STOP_GUI/TASK_STATUS 也不提前回 QA，避免与 TestApp 收尾抢前台
            Log.i(TAG, reason + ": unattended skip returnToQaChatDirect");
            appendMessage("系统", reason + ": 无人值守跳过提前回 QA");
            return;
        }
        if (pendingReturnToQaRunnable != null) {
            mainHandler.removeCallbacks(pendingReturnToQaRunnable);
            pendingReturnToQaRunnable = null;
        }
        final Context appCtx = getApplicationContext();
        Log.i(TAG, reason + ": schedule returnToQaChatDirect in " + RETURN_QA_AFTER_DONE_DELAY_MS + "ms");
        appendMessage("系统", reason + ": " + (RETURN_QA_AFTER_DONE_DELAY_MS / 1000) + "s 后拉回 QA 聊天页");
        pendingReturnToQaRunnable = () -> {
            pendingReturnToQaRunnable = null;
            Log.i(TAG, reason + ": returnToQaChatDirect");
            WsUiHelper.returnToQaChatDirect(appCtx);
            QAActivity act = currentActivityRef != null ? currentActivityRef.get() : null;
            if (act != null && !act.isFinishing()) {
                act.appendMessage("系统", reason + ": 已拉回 QA 聊天页");
            }
        };
        mainHandler.postDelayed(pendingReturnToQaRunnable, RETURN_QA_AFTER_DONE_DELAY_MS);
    }

    /** 处理 STOP_GUI：拉回 QA 聊天页。 */
    private void handleStopGuiMessage() {
        returnToQaChatAfterGui("STOP_GUI");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (devSettingsDialog != null && devSettingsDialog.isShowing()) {
            devSettingsDialog.dismiss();
        }
        // 销毁悬浮球
        hideFloatingBall();
        // 注意：这里不销毁会话，保持单例
    }
}
