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

import com.example.testreply.TestReplyContract;
import com.example.testreply.TestReplySdk;
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

    // 当前Activity实例的弱引用，供静态回调使用
    private static WeakReference<QAActivity> currentActivityRef = null;
    private static Context appContext;

    // 动态注册的广播接收器
    private BroadcastReceiver textInputReceiver;
    
    // 悬浮球相关
    private View floatingBallView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams floatingBallParams;
    private boolean isFloatingBallShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qa);

        // 保存当前Activity引用
        currentActivityRef = new WeakReference<>(this);
        appContext = getApplicationContext();

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
        switchLogEnable.setChecked(ConfigActivity.isLogEnable(prefs));
        switchSecurityFilter.setChecked(prefs.getBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, true));
        switchFocusMode.setChecked(ConfigActivity.isFocusModeEnabled(prefs));

        // 进入 QA 时按 prefs 热更新一次，避免仅写 prefs 需重登才生效
        applySdkLogConfigFromPrefs(prefs);

        switchLogEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences p = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
            p.edit().putBoolean(ConfigActivity.KEY_LOG_ENABLE, isChecked).apply();
            applySdkLogConfigFromPrefs(p);
            Toast.makeText(this, isChecked ? "SDK 日志已开启" : "SDK 日志已关闭", Toast.LENGTH_SHORT).show();
        });
        switchSecurityFilter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences p = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
            p.edit().putBoolean(ConfigActivity.KEY_SECURITY_FILTER_ENABLED, isChecked).apply();
            applySdkLogConfigFromPrefs(p);
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
                    boolean shouldSkipDebug = focusMode && errorCode == 0 
                            && (errorMessage == null || errorMessage.isEmpty());
                    
                    if (!shouldSkipDebug) {
                        // 更新消息到静态map（不依赖Activity）
                        appendMessageToSession(sid, "调试", logMsg);
                    }

                    // 流式响应拼接：非TEXT类型或错误到达时，结束流式拼接
                    if (errorCode != 0 || data == null || data.getOutputType() != OutputTypeEnum.TEXT) {
                        finalizeStreaming(sid);
                    }

                    if (errorCode == 0 && data != null) {
                        if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                            ReplyMetrics.onFirstSignal(sid, "DIALOG",
                                    data.getDialogInfo().getTitle() != null
                                            ? data.getDialogInfo().getTitle() : "dialog");
                            String dialogTitle = data.getDialogInfo().getTitle() != null
                                    ? data.getDialogInfo().getTitle() : "dialog";
                            Context replyCtx = activity != null ? activity : appContext;
                            if (replyCtx != null) {
                                TestReplySdk.onDialogReceived(replyCtx, sid, dialogTitle);
                            }
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
                            
                            // TASK_STATUS：完成时收口时延
                            if (data.getOutputType() == OutputTypeEnum.TASK_STATUS
                                    && data.getTaskInfo() != null
                                    && data.getTaskInfo().isTaskCompleted()) {
                                ReplyMetrics.onTaskCompleted(sid);
                                Context replyCtx = activity != null ? activity : appContext;
                                if (replyCtx != null) {
                                    TestReplySdk.onTaskCompleted(
                                            replyCtx, sid, data.getTaskInfo().getTaskStatusCode());
                                }
                            }

                            // TASK_STATUS 类型：如果 taskCompleted=true 且 taskStatusCode=200，跳过显示
                            boolean shouldSkip = focusMode && data.getOutputType() == OutputTypeEnum.TASK_STATUS 
                                    && data.getTaskInfo() != null 
                                    && data.getTaskInfo().isTaskCompleted() 
                                    && data.getTaskInfo().getTaskStatusCode() == 200;
                            
                            if (shouldSkip) {
                                // 不显示，跳过
                            } else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 聚焦模式：流式响应拼接，将多个TEXT chunk合并为一条显示
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                TestReplySdk.onReplyChunk(sid, textContent != null ? textContent : "");
                                if (streamingTextMap.containsKey(sid)) {
                                    // 继续拼接：追加到已有的流式文本
                                    streamingTextMap.get(sid).append(textContent);
                                } else {
                                    // 新的流式响应开始
                                    String streamTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                                    streamingTimestampMap.put(sid, streamTimestamp);
                                    streamingTextMap.put(sid, new StringBuilder(textContent));
                                }
                                // 流式文本在UI刷新时单独拼接显示，不调用 appendMessageToSession
                            } else if (!focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                // 非聚焦模式：逐条显示每个TEXT chunk
                                String textContent = data.getTextInfo().getText();
                                ReplyMetrics.onTextChunk(sid, textContent != null ? textContent : "");
                                Context replyCtx = activity != null ? activity : appContext;
                                if (replyCtx != null) {
                                    TestReplySdk.publishTextReply(replyCtx, sid, textContent);
                                }
                                appendMessageToSession(sid, "服务端", "text=" + textContent);
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

        if (!success) {
            appendMessage("错误", "发送失败，会话可能已断开");
        } else {
            ReplyMetrics.onUserSend(currentSessionId, message);
            TestReplySdk.onUserMessageSent(currentSessionId, message);
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

    /**
     * 结束流式响应拼接，将累积的文本作为一条消息写入messageMap
     */
    private static void finalizeStreaming(String sid) {
        if (sid == null || !streamingTextMap.containsKey(sid)) return;
        StringBuilder accumulated = streamingTextMap.remove(sid);
        String timestamp = streamingTimestampMap.remove(sid);
        StringBuilder sessionSb = messageMap.get(sid);
        if (sessionSb != null && accumulated != null) {
            sessionSb.append("[").append(timestamp).append("] ")
                    .append("[服务端] ")
                    .append("text=").append(accumulated.toString())
                    .append("\n\n");
        }
        ReplyMetrics.finalizeStream(sid);
        Context replyCtx = appContext;
        QAActivity act = currentActivityRef != null ? currentActivityRef.get() : null;
        if (act != null) {
            replyCtx = act;
        }
        if (replyCtx != null) {
            TestReplySdk.finalizeReply(replyCtx, sid, TestReplyContract.OUTPUT_TEXT);
            Log.i(TAG, "[TestReply] finalizeReply 已广播, session=" + sid
                    + ", len=" + (accumulated != null ? accumulated.length() : 0));
        }
    }

    /**
     * 取 sessionId 前8位用于显示
     */
    private String shortId(String sid) {
        if (sid == null) return "null";
        return sid.substring(0, Math.min(8, sid.length())) + "...";
    }


    /** TestApp 新用例开始：清流式缓冲。 */
    public static void onAutomationCaseStart(String caseId) {
        String sid = currentSessionId;
        if (sid != null) {
            streamingTextMap.remove(sid);
            streamingTimestampMap.remove(sid);
        }
        Log.i(TAG, "[TestReply] automation case start caseId=" + caseId);
    }

    /** TestApp 澄清发送。 */
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
            Log.w(TAG, "澄清无法发送：会话未就绪");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder().userInputText(message).build();
        if (sessionApi.sendMessage(currentSessionId, request)) {
            TestReplySdk.sendClarify(currentSessionId, message);
        }
    }

    private void sendClarifyMessage(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (currentSessionId == null || sessionApi == null) {
            appendMessage("错误", "会话未创建，无法发送澄清消息");
            return;
        }
        String message = text.trim();
        SessionRequest request = new SessionRequest.Builder().userInputText(message).build();
        if (sessionApi.sendMessage(currentSessionId, request)) {
            TestReplySdk.sendClarify(currentSessionId, message);
            appendMessage("我", "[澄清] " + message);
            if (etInput != null) {
                etInput.setText("");
            }
        } else {
            appendMessage("错误", "澄清消息发送失败");
        }
    }

    /** TestApp 强制结束 GUI 接管。 */
    public static void forceEndTakeoverFromAutomation() {
        QAActivity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> {
                Map<String, String> params = new HashMap<>();
                if (sessionApi != null && currentSessionId != null) {
                    sessionApi.sendMessage(currentSessionId, GuiTaskEnum.STOP_TAKEOVER, params);
                }
                activity.hideFloatingBall();
                Context ctx = activity.getApplicationContext();
                TestReplySdk.publishGuiTakeoverForceComplete(ctx, "testapp_force");
            });
            return;
        }
        if (currentSessionId != null && sessionApi != null) {
            Map<String, String> params = new HashMap<>();
            sessionApi.sendMessage(currentSessionId, GuiTaskEnum.STOP_TAKEOVER, params);
        }
        if (appContext != null) {
            TestReplySdk.publishGuiTakeoverForceComplete(appContext, "testapp_force_no_activity");
        }
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
        // START_GUI 和 STOP_GUI：根据配置决定是否弹窗确认
        boolean guiConfirmEnabled = ConfigActivity.isGuiConfirmEnabled(
                getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE));
        if (guiTaskType == GuiTaskEnum.START_GUI && !guiConfirmEnabled) {
            runOnUiThread(() -> {
                appendMessage("系统", "START_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
            });
            return;
        }
        if (guiTaskType == GuiTaskEnum.STOP_GUI && !guiConfirmEnabled) {
            runOnUiThread(() -> {
                appendMessage("系统", "STOP_GUI: 自动确认");
                if (sessionApi != null) sessionApi.resumeMessageHandle();
                handleStopGuiMessage();
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
                        // 先发送 START_TAKEOVER，然后显示红色悬浮球
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        showFloatingBall();
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
                        // 先发送 START_TAKEOVER，然后显示红色悬浮球
                        sendGuiTaskResponse(GuiTaskEnum.START_TAKEOVER);
                        showFloatingBall();
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
        // 销毁悬浮球
        hideFloatingBall();
        // 注意：这里不销毁会话，保持单例
    }
}
