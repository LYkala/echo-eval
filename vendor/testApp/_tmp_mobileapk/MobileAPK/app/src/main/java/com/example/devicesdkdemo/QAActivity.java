package com.example.devicesdkdemo;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
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

import com.example.testreply.TestReplyContract;
import com.example.testreply.TestReplyConfig;
import com.example.testreply.TestReplySdk;

import android.app.ActivityManager;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * QA 对话页面
 * 使用 SessionApi 创建会话并收发消息
 */
public class QAActivity extends AppCompatActivity {

    private static final String TAG = "QAActivity";
    public static final String ACTION_INPUT_TEXT = "com.example.devicesdkdemo.ACTION_INPUT_TEXT";
    public static final String ACTION_SEND_TEXT = "com.example.devicesdkdemo.ACTION_SEND_TEXT";
    public static final String EXTRA_TEXT = "text";

    private TextView tvMessages;
    private TextView tvSessionTitle;
    private EditText etInput;
    private Button btnSend;
    private ScrollView scrollView;

    // 开发者配置开关
    private SwitchCompat switchLogEnable;
    private SwitchCompat switchSecurityFilter;
    private SwitchCompat switchFocusMode;

    // 静态Handler，不依赖Activity实例
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Session 相关（静态，跨 Activity 生命周期保持）
    private static SessionApi sessionApi;
    private static final List<String> sessionIdList = new ArrayList<>();
    private static final Map<String, StringBuilder> messageMap = new LinkedHashMap<>();
    private static String currentSessionId = null;
    private static int sessionCounter = 0; // 会话编号计数器
    // 流式响应拼接相关
    private static final Map<String, StringBuilder> streamingTextMap = new HashMap<>();
    private static final Map<String, String> streamingTimestampMap = new HashMap<>();
    /**
     * 流式 TEXT 空闲多久后视为「本轮回复结束」并上报 TestReply。
     * 不宜过短：AISF 常分段下发，段与段之间可能停顿数秒；过短会只上报第一段。
     */
    private static final long STREAM_IDLE_FINALIZE_MS = 8000L;
    private static final Map<String, Runnable> streamingFinalizeTasks = new HashMap<>();

    // 当前Activity实例的弱引用，供静态回调使用
    private static WeakReference<QAActivity> currentActivityRef = null;
    /** 应用级 Context，用于 Activity 销毁后仍能广播 TestReply */
    private static Context appContext;
    
    // 动态注册的广播接收器
    private BroadcastReceiver textInputReceiver;
    
    // 悬浮球相关
    private View floatingBallView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingBallParams;
    private boolean isFloatingBallShowing = false;
    /** 无人值守：确认接管后延时自动点「结束接管」 */
    private static final long AUTO_END_TAKEOVER_DELAY_MS = 8000L;
    /** 悬浮球显示后倒计时，到期强制结束接管并通知 TestApp 进入下一轮 */
    private static final int TAKEOVER_BALL_COUNTDOWN_SEC = 30;
    /** 发送 STOP_TAKEOVER 后冷却，避免平台反复弹出「结束接管」死循环 */
    private static final long TAKEOVER_BALL_SUPPRESS_MS = 15000L;
    /** 同一接管会话内多次 STOP 仍反复弹出按钮时，强制退出 */
    private static final int MAX_STOP_TAKEOVER_ATTEMPTS = 2;

    private Runnable autoEndTakeoverRunnable;
    private Runnable floatingBallCountdownRunnable;
    private long floatingBallCountdownDeadlineMs;
    private static volatile long suppressFloatingBallUntilMs;
    private static int stopTakeoverAttemptsInSession;
    /** 冷却期内平台再次请求显示「结束接管」的次数（死循环信号） */
    private static int suppressedShowAttempts;
    private static volatile boolean isForceEndingTakeover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qa);

        // 保存当前Activity引用
        currentActivityRef = new WeakReference<>(this);
        appContext = getApplicationContext();

        // TestReply 初始化与 case_start 轮次重置在 DemoApp 完成

        // 初始化视图
        tvMessages = findViewById(R.id.tv_messages);
        tvMessages.setOnClickListener(v -> {
            String text = tvMessages.getText().toString();
            if (!text.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("", text);
                clipboard.setPrimaryClip(clip);
            }
        });
        tvSessionTitle = findViewById(R.id.tv_session_title);
        etInput = findViewById(R.id.et_input);
        btnSend = findViewById(R.id.btn_send);
        scrollView = findViewById(R.id.scroll_view);
        Button btnBack = findViewById(R.id.btn_back);

        // 返回按钮点击事件
        btnBack.setOnClickListener(v -> finish());

        // 清屏按钮点击事件
        Button btnClear = findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(v -> clearMessages());

        // 发送按钮点击事件
        btnSend.setOnClickListener(v -> sendMessage());

        // 停止任务按钮点击事件
        Button btnStopTask = findViewById(R.id.btn_stop_task);
        btnStopTask.setOnClickListener(v -> stopTask());

        // 停止算力主机任务按钮点击事件
        Button btnStopComputeInstanceTask = findViewById(R.id.btn_stop_compute_instance);
        btnStopComputeInstanceTask.setOnClickListener(v -> stopComputeInstanceTask());

        // 会话管理按钮点击事件
        Button btnSessionManage = findViewById(R.id.btn_session_manage);
        btnSessionManage.setOnClickListener(v -> showSessionManageDialog());

        // 开发者配置开关
        switchLogEnable = findViewById(R.id.switch_log_enable);
        switchSecurityFilter = findViewById(R.id.switch_security_filter);
        switchFocusMode = findViewById(R.id.switch_focus_mode);

        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        ConfigActivity.ensureDefaultPrefs(prefs);
        switchLogEnable.setChecked(ConfigActivity.isLogEnable(prefs));
        switchSecurityFilter.setChecked(prefs.getBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, true));
        switchFocusMode.setChecked(ConfigActivity.isFocusModeEnabled(prefs));

        switchLogEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(ConfigActivity.KEY_LOG_ENABLE, isChecked).apply();
        });
        switchSecurityFilter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, isChecked).apply();
        });
        switchFocusMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(ConfigActivity.KEY_FOCUS_MODE_ENABLED, isChecked).apply();
            // 聚焦模式切换时，结束当前的流式拼接，使拼接结果立即写入messageMap
            finalizeStreaming(currentSessionId);
        });

        // 恢复消息内容
        if (currentSessionId != null && messageMap.containsKey(currentSessionId)) {
            tvMessages.setText(messageMap.get(currentSessionId).toString());
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
        updateSessionTitle();

        // 创建/恢复 Session
        setupSession();

        // 注册文本输入广播接收器 (用于 ADB 自动化测试)
        registerTextInputReceiver();
    }

    /**
     * 供 DemoApp 澄清广播回调：Activity 不在前台时也能发送澄清消息。
     */
    public static void sendClarifyMessageFromAutomation(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> activity.sendClarifyMessage(text));
            return;
        }
        if (currentSessionId == null || sessionApi == null) {
            Log.w(TAG, "澄清消息无法发送：会话未就绪");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder()
                .userInputText(message)
                .build();
        boolean success = sessionApi.sendMessage(currentSessionId, request);
        if (success) {
            TestReplySdk.sendClarify(currentSessionId, message);
            Log.i(TAG, "[澄清] " + message);
        }
    }

    /**
     * 发送澄清消息（不增加问答轮次）
     */
    private void sendClarifyMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送澄清消息");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder()
                .userInputText(message)
                .build();
        boolean success = sessionApi.sendMessage(currentSessionId, request);
        if (success) {
            TestReplySdk.sendClarify(currentSessionId, message);
            appendMessage("我", "[澄清] " + message);
            etInput.setText("");
        } else {
            appendMessage("错误", "澄清消息发送失败");
        }
    }
    
    /**
     * 注册文本输入广播接收器
     */
    private void registerTextInputReceiver() {
        textInputReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.i(TAG, "Broadcast received: " + action);
                if (action == null) return;
                
                String text = intent.getStringExtra(EXTRA_TEXT);
                if (text == null || text.isEmpty()) {
                    Log.w(TAG, "Text is empty");
                    return;
                }
                Log.i(TAG, "Setting text: " + text);
                
                // 设置文本到输入框
                runOnUiThread(() -> {
                    etInput.setText(text);
                    etInput.setSelection(text.length());
                    
                    // 如果是 SEND_TEXT 动作，输入后自动发送
                    if (ACTION_SEND_TEXT.equals(action)) {
                        mainHandler.postDelayed(() -> {
                            sendMessage();
                        }, 300);
                    }
                });
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_INPUT_TEXT);
        filter.addAction(ACTION_SEND_TEXT);
        // 使用 RECEIVER_NOT_EXPORTED 确保可以接收 ADB 广播
        registerReceiver(textInputReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.i(TAG, "TextInputReceiver registered");
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
                    boolean shouldSkipDebug = focusMode && errorCode == 0 
                            && (errorMessage == null || errorMessage.isEmpty());
                    
                    if (!shouldSkipDebug) {
                        // 更新消息到静态map（不依赖Activity）
                        appendMessageToSession(sid, "调试", logMsg);
                    }

                    // 仅在错误/真正结束信号时 finalize；中间 TASK_STATUS 等不要切断多段 TEXT
                    // 硬性收口：只要本轮任务被标记完成（不论 outputType），必须立即 finalize 并广播，
                    // 否则流式 TEXT 会一直压在缓冲里，TestApp 侧只能等到 watchdog 超时。
                    boolean taskCompletedSignal = data != null && data.getTaskInfo() != null
                            && data.getTaskInfo().isTaskCompleted();
                    if (errorCode != 0 || data == null) {
                        finalizeStreaming(sid);
                    } else if (taskCompletedSignal || shouldFinalizeStreamingForPayload(data)) {
                        finalizeStreaming(sid);
                    } else if (data.getOutputType() == OutputTypeEnum.GUI_TASK
                            && data.getGuiTaskInfo() != null
                            && data.getGuiTaskInfo().getGuiTaskType() == GuiTaskEnum.START_GUI) {
                        // 接管开始：取消规划 TEXT 的空闲 finalize，等 STOP_GUI / taskCompleted
                        cancelStreamingFinalize(sid);
                        Log.i(TAG, "[TestReply] START_GUI：推迟流式 finalize，等待 GUI 结束");
                    }

                    if (errorCode == 0 && data != null) {
                        if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                            Context replyCtx = appContext != null
                                    ? appContext
                                    : (activity != null ? activity.getApplicationContext() : null);
                            String dialogTitle = data.getDialogInfo().getTitle();
                            if (replyCtx != null) {
                                TestReplySdk.onDialogReceived(replyCtx, sid, dialogTitle);
                            }
                            if (activity != null && !activity.isFinishing()) {
                                activity.showDialog(data.getDialogInfo());
                            }
                        } else if (data.getOutputType() == OutputTypeEnum.GUI_TASK && data.getGuiTaskInfo() != null) {
                            if (activity != null && !activity.isFinishing()) {
                                activity.showGuiTaskDialog(data.getGuiTaskInfo());
                            }
                        } else {
                            // 测试聚焦模式过滤逻辑（使用前面已定义的 prefs 和 focusMode）
                            
                            // TASK_STATUS 类型：如果 taskCompleted=true 且 taskStatusCode=200，跳过显示
                            boolean shouldSkip = focusMode && data.getOutputType() == OutputTypeEnum.TASK_STATUS 
                                    && data.getTaskInfo() != null 
                                    && data.getTaskInfo().isTaskCompleted() 
                                    && data.getTaskInfo().getTaskStatusCode() == 200;
                            
                            if (shouldSkip) {
                                // finalizeStreaming 已在上方 shouldFinalize 中执行，带上完整 TEXT
                                Context replyCtx = appContext != null
                                        ? appContext
                                        : (activity != null ? activity.getApplicationContext() : null);
                                if (replyCtx != null) {
                                    TestReplySdk.onTaskCompleted(
                                            replyCtx,
                                            sid,
                                            data.getTaskInfo().getTaskStatusCode());
                                }
                            } else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 聚焦模式：多段 TEXT 持续拼到同一缓冲，空闲一段时间后再一次上报完整回复
                                String textContent = data.getTextInfo().getText();
                                if (textContent == null) {
                                    textContent = "";
                                }
                                TestReplySdk.onReplyChunk(sid, textContent);
                                boolean grew = false;
                                if (streamingTextMap.containsKey(sid)) {
                                    StringBuilder streamBuf = streamingTextMap.get(sid);
                                    int beforeLen = streamBuf.length();
                                    streamBuf.append(textContent);
                                    grew = streamBuf.length() > beforeLen;
                                } else {
                                    String streamTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                                    streamingTimestampMap.put(sid, streamTimestamp);
                                    streamingTextMap.put(sid, new StringBuilder(textContent));
                                    grew = !textContent.isEmpty();
                                }
                                // 只有内容真正增长才重置空闲计时；重复/空 chunk 不续命，保证 8s 后必定收口
                                if (grew || !streamingFinalizeTasks.containsKey(sid)) {
                                    scheduleStreamingFinalize(sid);
                                }
                            } else if (!focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 非聚焦模式：UI 逐条显示；TestReply 按轮次累积后更新完整文本
                                String textContent = data.getTextInfo().getText();
                                appendMessageToSession(sid, "服务端", "text=" + textContent);
                                Context replyCtx = appContext != null
                                        ? appContext
                                        : (activity != null ? activity.getApplicationContext() : null);
                                if (replyCtx != null && textContent != null && !textContent.isEmpty()) {
                                    TestReplySdk.publishTextReply(replyCtx, sid, textContent);
                                }
                            } else if (data.getOutputType() != OutputTypeEnum.TEXT
                                    && data.getOutputType() != OutputTypeEnum.TASK_STATUS) {
                                // 其它类型：正常模式打印（中间状态 TASK_STATUS 不强行切断）
                                appendMessageToSession(sid, "服务端", "data=" + data.toString());
                            }
                        }
                    } else {
                        appendMessageToSession(sid, "错误", "Code: " + errorCode + ", Msg: " + errorMessage);
                    }

                    // 刷新当前Activity的UI（如果存在且是正确的会话）
                    if (activity != null && !activity.isFinishing() && sid.equals(currentSessionId)) {
                        StringBuilder sb = messageMap.get(sid);
                        StringBuilder streamSb = streamingTextMap.get(sid);
                        if (sb != null) {
                            String displayText = sb.toString();
                            if (streamSb != null) {
                                // 添加流式响应预览（尚未最终写入messageMap的部分）
                                String streamTimestamp = streamingTimestampMap.get(sid);
                                displayText += "[" + streamTimestamp + "] [服务端] text=" + streamSb.toString() + "\n\n";
                            }
                            activity.tvMessages.setText(displayText);
                            activity.scrollView.post(() -> activity.scrollView.fullScroll(View.FOCUS_DOWN));
                        }
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
            messageMap.put(newId, new StringBuilder());
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
        if (sessionApi != null) {
            sessionApi.destroySession(targetId);
        }
        sessionIdList.remove(targetId);
        messageMap.remove(targetId);
        streamingTextMap.remove(targetId);
        streamingTimestampMap.remove(targetId);

        if (targetId.equals(currentSessionId)) {
            if (!sessionIdList.isEmpty()) {
                switchToSession(sessionIdList.get(0));
                appendMessage("系统", "已切换到会话: " + shortId(currentSessionId));
            } else {
                currentSessionId = null;
                tvMessages.setText("");
                updateSessionTitle();
            }
        }
    }

    /**
     * 切换到指定会话
     */
    private void switchToSession(String targetId) {
        if (targetId.equals(currentSessionId)) return;
        currentSessionId = targetId;
        StringBuilder sb = messageMap.get(targetId);
        tvMessages.setText(sb != null ? sb.toString() : "");
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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
     * 显示会话管理弹窗
     */
    private void showSessionManageDialog() {
        if (isFinishing()) return;

        // 构建会话列表显示文本
        final List<String> displayList = new ArrayList<>();
        for (int i = 0; i < sessionIdList.size(); i++) {
            String sid = sessionIdList.get(i);
            String mark = sid.equals(currentSessionId) ? "● " : "  ";
            displayList.add(mark + "会话" + (i + 1) + ": " + shortId(sid));
        }
        if (displayList.isEmpty()) {
            displayList.add("（暂无会话）");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("会话管理");

        // 用 ListView 承载会话列表
        ListView listView = new ListView(this);
        listView.setPadding(0, 0, 0, 0);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                // 高亮当前会话
                if (position < sessionIdList.size() && sessionIdList.get(position).equals(currentSessionId)) {
                    tv.setTextColor(0xFF1976D2);
                } else {
                    tv.setTextColor(0xFF212121);
                }
                return view;
            }
        };
        listView.setAdapter(adapter);

        builder.setView(listView);
        builder.setPositiveButton("+ 新增会话", null); // 后面手动设置，防止点击后自动关闭
        builder.setNegativeButton("关闭", null);

        AlertDialog dialog = builder.create();

        // 列表点击：切换会话
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < sessionIdList.size()) {
                switchToSession(sessionIdList.get(position));
                dialog.dismiss();
            }
        });

        // 列表长按：删除会话
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= sessionIdList.size()) return true;
            String targetId = sessionIdList.get(position);
            new AlertDialog.Builder(QAActivity.this)
                    .setTitle("删除会话")
                    .setMessage("确认删除会话" + (position + 1) + "（" + shortId(targetId) + "）？\n该会话消息记录将被清除。")
                    .setPositiveButton("删除", (d, which) -> {
                        deleteSession(targetId);
                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        dialog.show();

        // 新增会话按钮：不自动关闭弹窗
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss();
            createNewSession();
        });
    }

    /**
     * 发送消息
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

        // 显示发送的消息
        appendMessage("我", message);
        etInput.setText("");

        // 调用 SessionApi 发送消息
        SessionRequest request = new SessionRequest.Builder()
                .userInputText(message)
                .build();
        boolean success = sessionApi.sendMessage(currentSessionId, request);

        if (success) {
            TestReplySdk.onUserMessageSent(currentSessionId, message);
        } else {
            appendMessage("错误", "发送失败，会话可能已断开");
        }
    }

    /**
     * 停止当前任务
     */
    private void stopTask() {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法停止任务");
            return;
        }

        boolean success = sessionApi.stopTask(currentSessionId);
        if (success) {
            appendMessage("系统", "任务已停止");
        } else {
            appendMessage("错误", "停止任务失败");
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
            if (messageMap.containsKey(currentSessionId)) {
                messageMap.get(currentSessionId).setLength(0);
            }
            streamingTextMap.remove(currentSessionId);
            streamingTimestampMap.remove(currentSessionId);
        }
        tvMessages.setText("");
    }

    /**
     * 追加消息到当前会话（存储数据并刷新UI）
     */
    private void appendMessage(String sender, String message) {
        appendMessageToSession(currentSessionId, sender, message);
        // 刷新UI
        if (currentSessionId != null) {
            StringBuilder sb = messageMap.get(currentSessionId);
            if (sb != null) {
                tvMessages.setText(sb.toString());
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        }
    }

    /**
     * 追加消息到指定会话（只存储数据，不刷新UI）
     */
    private static void appendMessageToSession(String sid, String sender, String message) {
        if (sid == null) return;
        StringBuilder sb = messageMap.get(sid);
        if (sb == null) return;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        sb.append("[").append(timestamp).append("] ")
                .append("[").append(sender).append("] ")
                .append(message).append("\n\n");
        // UI刷新由调用方负责，这里只存储数据
    }

    private static void cancelStreamingFinalize(String sid) {
        if (sid == null) {
            return;
        }
        Runnable task = streamingFinalizeTasks.remove(sid);
        if (task != null) {
            mainHandler.removeCallbacks(task);
        }
    }

    /**
     * 自动化新用例开始：清掉上一用例残留的流式缓冲（轮次已在 SDK 内按用例复位）。
     */
    public static void onAutomationCaseStart(String caseId) {
        String sid = currentSessionId;
        if (sid != null) {
            cancelStreamingFinalize(sid);
            streamingTextMap.remove(sid);
            streamingTimestampMap.remove(sid);
        }
        Log.i(TAG, "[TestReply] 自动化新用例开始, caseId=" + caseId
                + ", session=" + (sid == null ? "null" : sid.substring(0, Math.min(8, sid.length())) + "..."));
        resetTakeoverUiState();
    }

    /**
     * TestApp 请求强制结束 GUI 接管（退出第三方界面）。
     */
    public static void forceEndTakeoverFromAutomation() {
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> activity.forceEndTakeoverSession("testapp_force"));
            return;
        }
        Context ctx = appContext;
        if (currentSessionId != null && sessionApi != null) {
            Map<String, String> params = new HashMap<>();
            sessionApi.sendMessage(currentSessionId, GuiTaskEnum.STOP_TAKEOVER, params);
        }
        suppressFloatingBallUntilMs = System.currentTimeMillis() + TAKEOVER_BALL_SUPPRESS_MS;
        if (ctx != null) {
            TestReplySdk.publishGuiTakeoverForceComplete(ctx, "testapp_force_no_activity");
        }
    }

    private static void resetTakeoverUiState() {
        suppressFloatingBallUntilMs = 0L;
        stopTakeoverAttemptsInSession = 0;
        suppressedShowAttempts = 0;
        isForceEndingTakeover = false;
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> {
                activity.cancelAutoEndTakeover();
                activity.cancelFloatingBallCountdown();
                activity.hideFloatingBall();
            });
        }
    }

    private static boolean isFloatingBallSuppressed() {
        return System.currentTimeMillis() < suppressFloatingBallUntilMs;
    }

    private void maybeShowFloatingBallForTakeover(boolean scheduleAutoEnd) {
        // 冷却期内又请求显示 = 平台「结束接管」死循环，直接强制退出并完成本轮
        if (isFloatingBallSuppressed()) {
            suppressedShowAttempts++;
            appendMessage("系统", "检测到「结束接管」重复弹出("
                    + suppressedShowAttempts + ")，强制退出第三方界面");
            forceEndTakeoverSession("takeover_loop_suppress");
            return;
        }
        if (stopTakeoverAttemptsInSession >= MAX_STOP_TAKEOVER_ATTEMPTS) {
            appendMessage("系统", "结束接管已重试"
                    + stopTakeoverAttemptsInSession + "次仍未退出，强制结束");
            forceEndTakeoverSession("takeover_loop_attempts");
            return;
        }
        showFloatingBall();
        if (scheduleAutoEnd) {
            scheduleAutoEndTakeover();
        }
    }

    /** 支付/人工接管确认：冷却或重试超限时不再 START，直接走死循环强制退出 */
    private void confirmPaymentOrManualTakeover(boolean scheduleAutoEnd) {
        if (isFloatingBallSuppressed()
                || stopTakeoverAttemptsInSession >= MAX_STOP_TAKEOVER_ATTEMPTS) {
            maybeShowFloatingBallForTakeover(false);
            return;
        }
        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
        maybeShowFloatingBallForTakeover(scheduleAutoEnd);
    }

    private void onUserEndTakeoverClick() {
        cancelAutoEndTakeover();
        // 多次点击仍反复出现按钮：不再只发 STOP，直接强制退出
        if (stopTakeoverAttemptsInSession >= MAX_STOP_TAKEOVER_ATTEMPTS) {
            forceEndTakeoverSession("user_click_loop");
            return;
        }
        sendGuiTaskResponse(GuiTaskEnum.STOP_TAKEOVER);
        onAfterStopTakeoverSent();
        hideFloatingBall();
        appendMessage("系统", "已发送结束接管；若按钮再次出现将强制退出并完成本轮");
    }

    private void onAfterStopTakeoverSent() {
        stopTakeoverAttemptsInSession++;
        suppressFloatingBallUntilMs = System.currentTimeMillis() + TAKEOVER_BALL_SUPPRESS_MS;
        cancelFloatingBallCountdown();
        cancelAutoEndTakeover();
    }

    /**
     * 倒计时结束或 TestApp 强制：退出第三方界面并通知当前轮结束。
     */
    private void forceEndTakeoverSession(String reason) {
        cancelAutoEndTakeover();
        cancelFloatingBallCountdown();
        if (isForceEndingTakeover) {
            return;
        }
        isForceEndingTakeover = true;
        String msg = reason != null ? reason : "countdown";
        try {
            appendMessage("系统", "强制结束 GUI 接管: " + msg);
            if (currentSessionId != null && sessionApi != null) {
                try {
                    sendGuiTaskResponse(GuiTaskEnum.STOP_TAKEOVER);
                } catch (Exception e) {
                    Log.w(TAG, "forceEnd STOP_TAKEOVER failed: " + e.getMessage());
                }
            }
            stopTakeoverAttemptsInSession++;
            suppressFloatingBallUntilMs = System.currentTimeMillis() + TAKEOVER_BALL_SUPPRESS_MS;
            suppressedShowAttempts = 0;
            hideFloatingBall();
            bringAppToFront();
            handleStopGuiMessage();
            Context ctx = appContext != null ? appContext : getApplicationContext();
            TestReplySdk.publishGuiTakeoverForceComplete(ctx, "[强制结束] " + msg);
        } finally {
            mainHandler.postDelayed(() -> isForceEndingTakeover = false, 2000L);
        }
    }

    /**
     * 中间态回调（如进行中的 TASK_STATUS）不应切断流式 TEXT；
     * 仅在任务真正结束或弹出 GUI/对话框等“终端”事件时 finalize。
     */
    private static boolean shouldFinalizeStreamingForPayload(SessionResponseData data) {
        if (data == null) {
            return false;
        }
        OutputTypeEnum type = data.getOutputType();
        if (type == null || type == OutputTypeEnum.TEXT) {
            return false;
        }
        // START_GUI 只表示接管开始，此时 finalize 规划 TEXT 会让 TestApp 过早结束 wait-response
        if (type == OutputTypeEnum.GUI_TASK) {
            if (data.getGuiTaskInfo() != null
                    && data.getGuiTaskInfo().getGuiTaskType() == GuiTaskEnum.START_GUI) {
                return false;
            }
            return true; // STOP_GUI 等再收口
        }
        if (type == OutputTypeEnum.DIALOG) {
            return true;
        }
        if (type == OutputTypeEnum.TASK_STATUS && data.getTaskInfo() != null) {
            return data.getTaskInfo().isTaskCompleted();
        }
        // 其它非 TEXT：保守不 finalize，靠空闲超时收尾，避免截断多段回复
        return false;
    }

    /**
     * 流式 TEXT 空闲一段时间后自动结束本轮，并向 TestApp 广播回复。
     */
    private static void scheduleStreamingFinalize(String sid) {
        if (sid == null) {
            return;
        }
        cancelStreamingFinalize(sid);
        Runnable task = () -> {
            streamingFinalizeTasks.remove(sid);
            Log.i(TAG, "[TestReply] 流式空闲超时，自动 finalize: " + sid);
            finalizeStreaming(sid);
        };
        streamingFinalizeTasks.put(sid, task);
        mainHandler.postDelayed(task, STREAM_IDLE_FINALIZE_MS);
    }

    /**
     * 结束流式响应拼接，将累积的文本作为一条消息写入messageMap，并广播给 TestApp。
     */
    private static void finalizeStreaming(String sid) {
        if (sid == null || !streamingTextMap.containsKey(sid)) return;
        cancelStreamingFinalize(sid);
        StringBuilder accumulated = streamingTextMap.remove(sid);
        String timestamp = streamingTimestampMap.remove(sid);
        StringBuilder sessionSb = messageMap.get(sid);
        if (sessionSb != null && accumulated != null) {
            sessionSb.append("[").append(timestamp).append("] ")
                    .append("[服务端] ")
                    .append("text=").append(accumulated.toString())
                    .append("\n\n");
        }
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        Context replyCtx = appContext != null
                ? appContext
                : (activity != null ? activity.getApplicationContext() : null);
        if (replyCtx != null) {
            TestReplySdk.finalizeReply(replyCtx, sid, TestReplyContract.OUTPUT_TEXT);
            Log.i(TAG, "[TestReply] finalizeReply 已广播, session=" + sid
                    + ", len=" + (accumulated != null ? accumulated.length() : 0));
        } else {
            Log.w(TAG, "[TestReply] finalizeReply 跳过: 无 Context, session=" + sid);
        }
        // 刷新 UI，让流式预览落成最终消息
        if (activity != null && !activity.isFinishing() && sid.equals(currentSessionId)) {
            StringBuilder sb = messageMap.get(sid);
            if (sb != null) {
                activity.tvMessages.setText(sb.toString());
                activity.scrollView.post(() -> activity.scrollView.fullScroll(View.FOCUS_DOWN));
            }
        }
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

        // 无人值守：点「确认/确定」类按钮，而不是盲目点第一个
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        if (ConfigActivity.isAutoConfirmEnabled(prefs)) {
            DialogControl chosen = pickConfirmDialogControl(dialogInfo.getControlList());
            if (chosen == null) {
                appendMessage("警告", "DIALOG 未找到确认类按钮，仍展示弹窗待人工处理: "
                        + dialogInfo.getTitle());
            } else {
                String content = chosen.getContent();
                runOnUiThread(() -> {
                    appendMessage("系统", "DIALOG 自动点击确认按钮: "
                            + (dialogInfo.getTitle() != null ? dialogInfo.getTitle() : "")
                            + " → " + content);
                    sendDialogResponse(content);
                });
                return;
            }
        }

        // 检查 Activity 是否还活着
        if (isFinishing()) {
            appendMessage("错误", "Activity 正在销毁，无法显示弹窗");
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
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        boolean autoConfirm = ConfigActivity.isAutoConfirmEnabled(prefs);
        // START_GUI 和 STOP_GUI：根据配置决定是否弹窗确认
        boolean guiConfirmEnabled = ConfigActivity.isGuiConfirmEnabled(prefs);
        if (guiTaskType == GuiTaskEnum.START_GUI && (!guiConfirmEnabled || autoConfirm)) {
            runOnUiThread(() -> {
                appendMessage("系统", "START_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
            });
            return;
        }
        if (guiTaskType == GuiTaskEnum.STOP_GUI && (!guiConfirmEnabled || autoConfirm)) {
            runOnUiThread(() -> {
                appendMessage("系统", "STOP_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
                handleStopGuiMessage();
            });
            return;
        }
        // 无人值守：等价于自动点击弹窗上的「确认/确定」正向按钮
        if (autoConfirm) {
            final boolean isTakeover = guiTaskType == GuiTaskEnum.START_TAKEOVER;
            final boolean isPayment = guiTaskType == GuiTaskEnum.START_PAYMENT;
            final boolean isManual = guiTaskType == GuiTaskEnum.START_MANUAL;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    if (sessionApi != null) sessionApi.resumeMessageHandle();
                    return;
                }
                appendMessage("系统", "GUI_TASK 自动点击确认: " + guiTaskType);
                if (isPayment || isManual) {
                    confirmPaymentOrManualTakeover(true);
                } else if (isTakeover) {
                    sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                } else if (guiTaskType == GuiTaskEnum.STOP_GUI) {
                    handleStopGuiMessage();
                }
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
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isPayment || isManual) {
                    builder.setPositiveButton("确定", (d, which) -> {
                        confirmPaymentOrManualTakeover(false);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
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
                        sendGuiTaskResponse(GuiTaskEnum.CANCEL_TAKEOVER);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                } else if (isPayment || isManual) {
                    builder.setPositiveButton("确定", (d, which) -> {
                        confirmPaymentOrManualTakeover(false);
                        if (sessionApi != null) sessionApi.resumeMessageHandle();
                    });
                    builder.setNegativeButton("取消", (d, which) -> {
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

    /**
     * 发送 GUI_TASK 回包
     */
    private void sendGuiTaskResponse(GuiTaskEnum guiTask) {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送GUI响应");
            return;
        }
        Map<String, String> params = new HashMap<>();
        boolean success = sessionApi.sendMessage(currentSessionId, guiTask, params);
        if (success) {
            appendMessage("我", "[GUI响应] " + guiTask.getServerType());
        } else {
            appendMessage("错误", "GUI响应发送失败");
        }
    }

    /**
     * 从 DIALOG 按钮列表中选出「确认类」按钮（确认/确定/允许…），避开取消/拒绝。
     * 找不到确认类时返回 null（交还人工）。
     */
    private DialogControl pickConfirmDialogControl(List<DialogControl> controls) {
        if (controls == null || controls.isEmpty()) {
            return null;
        }
        for (DialogControl c : controls) {
            if (c == null) continue;
            if (isConfirmButtonLabel(c.getContent())) {
                return c;
            }
        }
        // 没有明确「确认」文案时：优先非取消类的第一个（常作为 Positive）
        for (DialogControl c : controls) {
            if (c == null) continue;
            if (!isCancelButtonLabel(c.getContent())) {
                return c;
            }
        }
        return null;
    }

    private static boolean isConfirmButtonLabel(String label) {
        if (label == null) return false;
        String t = label.trim().toLowerCase();
        if (t.isEmpty()) return false;
        String[] keys = {
                "确认", "确定", "允许", "同意", "继续", "是", "好的", "好", "立即开始", "开始",
                "ok", "yes", "allow", "confirm", "continue", "agree", "accept", "start"
        };
        for (String k : keys) {
            if (t.equals(k) || t.contains(k)) {
                // 排除「取消确认」这类歧义：若同时含取消关键词则不算确认
                if (isCancelButtonLabel(label) && !t.equals(k)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isCancelButtonLabel(String label) {
        if (label == null) return false;
        String t = label.trim().toLowerCase();
        if (t.isEmpty()) return false;
        String[] keys = {
                "取消", "拒绝", "否", "关闭", "不了", "以后再说", "跳过",
                "cancel", "deny", "reject", "no", "close", "skip", "dismiss"
        };
        for (String k : keys) {
            if (t.equals(k) || t.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发送弹窗按钮响应
     */
    private void sendDialogResponse(String content) {
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送响应");
            return;
        }

        SessionRequest request = new SessionRequest.Builder()
                .userInputText(content != null ? content : "")
                .build();
        boolean success = sessionApi.sendMessage(currentSessionId, request);

        if (success) {
            appendMessage("我", "[弹窗响应] " + content);
        } else {
            appendMessage("错误", "弹窗响应发送失败");
        }
    }

    /**
     * 显示悬浮球（"结束接管"按钮）
     * 点击后发送 STOPTAKEOVER 并关闭悬浮球
     */
    private void showFloatingBall() {
        if (!Settings.canDrawOverlays(this)) {
            return;
        }
        if (isFloatingBallSuppressed()) {
            return;
        }
        runOnUiThread(() -> {
            if (isFloatingBallShowing) {
                // 已在显示：不重置倒计时，避免平台反复回调导致永远到不了强制退出
                return;
            }
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
                ballButton.setOnClickListener(v -> onUserEndTakeoverClick());
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
                appendMessage("系统", "显示悬浮球: 结束接管 ("
                        + TAKEOVER_BALL_COUNTDOWN_SEC + "s 后强制退出)");
                startFloatingBallCountdown();
            } catch (Exception e) {
                appendMessage("错误", "显示悬浮球失败: " + e.getMessage());
            }
        });
    }

    /**
     * 隐藏并移除悬浮球
     */
    private void hideFloatingBall() {
        cancelFloatingBallCountdown();
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

    private void startFloatingBallCountdown() {
        cancelFloatingBallCountdown();
        floatingBallCountdownDeadlineMs = System.currentTimeMillis()
                + TAKEOVER_BALL_COUNTDOWN_SEC * 1000L;
        floatingBallCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFloatingBallShowing || floatingBallView == null) {
                    return;
                }
                long remainingMs = floatingBallCountdownDeadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    forceEndTakeoverSession("countdown");
                    return;
                }
                int sec = (int) Math.ceil(remainingMs / 1000.0);
                if (floatingBallView instanceof Button) {
                    ((Button) floatingBallView).setText("结束接管(" + sec + "s)");
                }
                mainHandler.postDelayed(this, 1000L);
            }
        };
        mainHandler.post(floatingBallCountdownRunnable);
    }

    private void cancelFloatingBallCountdown() {
        if (floatingBallCountdownRunnable == null) {
            return;
        }
        mainHandler.removeCallbacks(floatingBallCountdownRunnable);
        floatingBallCountdownRunnable = null;
    }

    /**
     * 无人值守：确认支付/人工接管后，延时自动发送 STOP_TAKEOVER（等价点击红色「结束接管」）。
     */
    private void scheduleAutoEndTakeover() {
        cancelAutoEndTakeover();
        autoEndTakeoverRunnable = () -> {
            autoEndTakeoverRunnable = null;
            if (isFinishing()) {
                return;
            }
            if (isFloatingBallSuppressed()) {
                return;
            }
            appendMessage("系统", "无人值守: 自动点击结束接管");
            sendGuiTaskResponse(GuiTaskEnum.STOP_TAKEOVER);
            onAfterStopTakeoverSent();
            hideFloatingBall();
        };
        mainHandler.postDelayed(autoEndTakeoverRunnable, AUTO_END_TAKEOVER_DELAY_MS);
        appendMessage("系统", "无人值守: "
                + (AUTO_END_TAKEOVER_DELAY_MS / 1000) + "s 后自动结束接管");
    }

    private void cancelAutoEndTakeover() {
        if (autoEndTakeoverRunnable == null) {
            return;
        }
        mainHandler.removeCallbacks(autoEndTakeoverRunnable);
        autoEndTakeoverRunnable = null;
    }

    /**
     * 处理 STOP_GUI 消息
     * 检测当前前台应用，如果不是自己的APP则拉起自己到前台
     */
    private void handleStopGuiMessage() {
        runOnUiThread(() -> {
            String currentPkg = getCurrentForegroundPackage();
            String myPkg = getPackageName();
            Log.i(TAG, "[STOP_GUI] 当前前台包名: " + currentPkg + ", 自己的包名: " + myPkg);

            if (!myPkg.equals(currentPkg)) {
                // 当前前台不是自己，拉起自己
                Log.i(TAG, "[STOP_GUI] APP不在前台，正在拉起...");
                bringAppToFront();
                appendMessage("系统", "STOP_GUI: APP已拉起到前台");
            } else {
                Log.i(TAG, "[STOP_GUI] APP已在前台，无需处理");
                appendMessage("系统", "STOP_GUI: APP已在前台");
            }
        });
    }

    /**
     * 获取当前前台应用的包名
     */
    private String getCurrentForegroundPackage() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return "";
        }
        // Android 5.0+ 使用 getRunningAppProcesses
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return process.processName;
                }
            }
        }
        return "";
    }

    /**
     * 拉起APP到前台
     */
    private void bringAppToFront() {
        Intent intent = new Intent(this, QAActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消注册广播接收器
        if (textInputReceiver != null) {
            unregisterReceiver(textInputReceiver);
            textInputReceiver = null;
            Log.i(TAG, "TextInputReceiver unregistered");
        }
        cancelAutoEndTakeover();
        cancelFloatingBallCountdown();
        // 销毁悬浮球
        hideFloatingBall();
        // 注意：这里不销毁会话，保持单例
    }
}
