package com.example.devicesdkdemo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.session.SessionApi;
import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionRequest;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;
import com.huawei.aisfsdkv2.api.session.OutputTypeEnum;
import com.huawei.aisfsdkv2.api.session.TextInfo;
import com.huawei.aisfsdkv2.api.session.TaskInfo;
import com.huawei.aisfsdkv2.api.session.SendPreHandler;
import com.huawei.aisfsdkv2.api.session.SessionAgingConfig;
import com.huawei.aisfsdkv2.api.skill.SkillApi;
import com.huawei.aisfsdkv2.api.skill.SkillInfo;
import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.session.Session;
import com.huawei.aisfsdkv2.session.SessionManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通通任务测试页面
 */
public class TongtongTaskTestActivity extends AppCompatActivity {

    private static final String TAG = "TongtongTaskTest";

    private SessionApi sessionApi;
    private SkillApi skillApi;
    private String currentSessionId = null;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvOutput;
    private ScrollView scrollView;
    private LinearLayout skillListContainer;
    private Button btnStartRecording;
    private StringBuilder outputBuilder = new StringBuilder();

    // 老化配置
    private androidx.appcompat.widget.SwitchCompat switchAgingEnabled;
    private android.widget.EditText etAgingMinutes;

    // 系统悬浮窗
    private Button floatingStopButton;
    private boolean isFloatingButtonAdded = false;
    private android.view.WindowManager.LayoutParams floatingParams;
    private int floatingLastX, floatingLastY;
    private boolean floatingIsDragging = false;
    private static final int FLOATING_MOVE_THRESHOLD = 10;

    // 等待服务端响应弹窗
    private androidx.appcompat.app.AlertDialog waitingDialog;
    // 标记新增技能弹窗是否已弹出，防止重复创建
    private boolean isSkillDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tongtong_task_test);

        // 初始化 SessionApi
        try {
            sessionApi = AISFSDKClient.getSessionApi();
        } catch (Exception e) {
            appendOutput("错误: SessionApi 初始化失败 - " + e.getMessage());
        }

        // 初始化 SkillApi
        try {
            skillApi = AISFSDKClient.getSkillApi();
        } catch (Exception e) {
            appendOutput("错误: SkillApi 初始化失败 - " + e.getMessage());
        }

        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 开始录制按钮
        btnStartRecording = findViewById(R.id.btn_create_tongtong_task);
        btnStartRecording.setOnClickListener(v -> startRecording());

        // 老化配置控件
        switchAgingEnabled = findViewById(R.id.switch_aging_enabled);
        etAgingMinutes = findViewById(R.id.et_aging_minutes);

        // 输出框
        tvOutput = findViewById(R.id.tv_output);
        scrollView = (ScrollView) tvOutput.getParent();

        // 清屏按钮
        Button btnClearOutput = findViewById(R.id.btn_clear_output);
        btnClearOutput.setOnClickListener(v -> {
            outputBuilder.setLength(0);
            tvOutput.setText("");
        });

        // 技能列表
        skillListContainer = findViewById(R.id.skill_list_container);
        Button btnRefreshSkills = findViewById(R.id.btn_refresh_skills);
        btnRefreshSkills.setOnClickListener(v -> refreshSkillList());
        refreshSkillList();

        // 结束录制悬浮窗按钮（通过 WindowManager 添加，默认不显示）
        initFloatingButton();

        appendOutput("页面已加载，点击按钮创建通通任务");
    }

    /**
     * 从 SkillApi 获取技能列表并刷新
     */
    private void refreshSkillList() {
        skillListContainer.removeAllViews();
        appendOutput("[" + getTimestamp() + "] 正在获取技能列表...");
        new Thread(() -> {
            try {
                List<SkillInfo> skills = skillApi != null ? skillApi.getSkillList() : null;
                mainHandler.post(() -> {
                    skillListContainer.removeAllViews();
                    if (skills == null || skills.isEmpty()) {
                        TextView tvEmpty = new TextView(this);
                        tvEmpty.setText("暂无技能");
                        tvEmpty.setTextSize(13f);
                        tvEmpty.setPadding(16, 12, 16, 12);
                        skillListContainer.addView(tvEmpty);
                        appendOutput("[" + getTimestamp() + "] 技能列表已刷新，共 0 条");
                    } else {
                        for (SkillInfo skill : skills) {
                            skillListContainer.addView(buildSkillRow(skill));
                        }
                        appendOutput("[" + getTimestamp() + "] 技能列表已刷新，共 " + skills.size() + " 条");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        appendOutput("[" + getTimestamp() + "] 获取技能列表失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 构建技能列表中的一行
     */
    private LinearLayout buildSkillRow(SkillInfo skill) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 12, 16, 12);

        // 交替背景色
        int index = skillListContainer.getChildCount();
        row.setBackgroundColor(index % 2 == 0 ? 0xFFFFFFFF : 0xFFF9F9F9);

        // 技能名
        TextView tvName = new TextView(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameParams);
        tvName.setText(skill.getSkillName());
        tvName.setTextSize(14f);
        tvName.setGravity(Gravity.CENTER_VERTICAL);

        // 执行按钮
        Button btnExecute = new Button(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                (int) (64 * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(4, 0, 4, 0);
        btnExecute.setLayoutParams(btnParams);
        btnExecute.setText("执行");
        btnExecute.setTextSize(12f);
        btnExecute.setPadding(0, 0, 0, 0);
        btnExecute.setOnClickListener(v -> {
            executeSkill(skill.getSkillName());
        });

        // 删除按钮
        Button btnDelete = new Button(this);
        btnDelete.setLayoutParams(btnParams);
        btnDelete.setTextSize(12f);
        btnDelete.setPadding(0, 0, 0, 0);
        boolean isDefault = "1".equals(skill.getIsDefault()) || "true".equalsIgnoreCase(skill.getIsDefault());
        if (isDefault) {
            btnDelete.setText("默认");
            btnDelete.setEnabled(false);
            btnDelete.setAlpha(0.5f);
        } else {
            btnDelete.setText("删除");
            btnDelete.setOnClickListener(v -> {
                String skillId = skill.getSkillId();
                appendOutput("[" + getTimestamp() + "] 正在删除技能: " + skill.getSkillName() + " (id=" + skillId + ")");
                btnDelete.setEnabled(false);
                new Thread(() -> {
                    try {
                        DeleteSkillRequest request = new DeleteSkillRequest();
                        request.setSkillId(skillId);
                        boolean success = skillApi.deleteSkill(request);
                        mainHandler.post(() -> {
                            if (success) {
                                appendOutput("[" + getTimestamp() + "] 技能删除成功");
                            } else {
                                appendOutput("[" + getTimestamp() + "] 技能删除失败");
                                btnDelete.setEnabled(true);
                            }
                            refreshSkillList();
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            appendOutput("[" + getTimestamp() + "] 技能删除异常: " + e.getMessage());
                            btnDelete.setEnabled(true);
                        });
                    }
                }).start();
            });
        }

        row.addView(tvName);
        row.addView(btnExecute);
        row.addView(btnDelete);
        return row;
    }

    /**
     * 执行技能：创建会话并发送"通通帮我+技能名"
     */
    private void executeSkill(String skillName) {
        if (sessionApi == null) {
            appendOutput("\n错误: SessionApi 未初始化，请先登录");
            return;
        }

        String message = "通通帮我" + skillName;
        appendOutput("\n========== 执行技能: " + skillName + " ==========");
        appendOutput("[" + getTimestamp() + "] 正在创建会话...");

        String sessionId = sessionApi.createSession(new SessionCallback() {
            @Override
            public void onReceived(int errorCode, String errorMessage, SessionResponseData data) {
                mainHandler.post(() -> {
                    // 测试聚焦模式：如果消息发送成功（errorCode=0 且 errorMsg为空），跳过调试信息
                    SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
                    boolean focusMode = ConfigActivity.isFocusModeEnabled(prefs);
                    boolean shouldSkipDebug = focusMode && errorCode == 0 
                            && (errorMessage == null || errorMessage.isEmpty());
                    
                    if (shouldSkipDebug) {
                        // 不显示调试信息，跳过
                    } else if (data != null && data.getNotifyInfo() != null) {
                        appendOutput("[" + getTimestamp() + "] 收到通知: code=" + data.getNotifyInfo().getCode()
                                + ", outputType=" + data.getOutputType());
                    } else if (errorCode == 0) {
                        appendOutput("[" + getTimestamp() + "] 收到消息:");
                        appendOutput("  outputType: " + (data != null ? data.getOutputType() : "null"));
                        if (data != null) {
                            if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                                appendOutput("  对话内容: " + data.getDialogInfo().getTitle());
                            } else if (data.getOutputType() == OutputTypeEnum.GUI_TASK && data.getGuiTaskInfo() != null) {
                                appendOutput("  GUI任务: " + data.getGuiTaskInfo().getGuiTaskType());
                                if (sessionApi != null) {
                                    sessionApi.stopMessageHandle();
                                }
                            } else if (data.getOutputType() == OutputTypeEnum.TASK_STATUS && data.getTaskInfo() != null) {
                                // 测试聚焦模式：如果 taskCompleted=true 且 taskStatusCode=200，跳过显示（使用前面已定义的 focusMode）
                                boolean shouldSkip = focusMode && data.getTaskInfo().isTaskCompleted() 
                                        && data.getTaskInfo().getTaskStatusCode() == 200;
                                if (shouldSkip) {
                                    // 不显示，跳过
                                } else {
                                    appendOutput("  任务状态: completed=" + data.getTaskInfo().isTaskCompleted()
                                            + ", statusCode=" + data.getTaskInfo().getTaskStatusCode());
                                }
                            } else {
                                // 测试聚焦模式过滤逻辑（TEXT 类型，使用前面已定义的 focusMode）
                                if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                    appendOutput("  text: " + data.getTextInfo().getText());
                                } else {
                                    appendOutput("  data: " + data.toString());
                                }
                            }
                        }
                    } else {
                        appendOutput("[" + getTimestamp() + "] 错误: code=" + errorCode + ", msg=" + errorMessage);
                    }
                });
            }

            @Override
            public SendPreHandler getSendPreHandler() {
                return null;
            }
        });

        if (sessionId != null) {
            appendOutput("[" + getTimestamp() + "] 会话创建成功，sessionId: " + shortId(sessionId));

            // 应用老化配置
            applySessionAgingConfig(sessionId);

            appendOutput("[" + getTimestamp() + "] 发送消息: " + message);
            SessionRequest request = new SessionRequest.Builder()
                    .userInputText(message)
                    .build();
            boolean success = sessionApi.sendMessage(sessionId, request);
            if (!success) {
                appendOutput("[" + getTimestamp() + "] 发送消息失败");
            }
        } else {
            appendOutput("[" + getTimestamp() + "] 创建会话失败");
        }
    }

    /**
     * 开始录制
     */
    private void startRecording() {
        if (sessionApi == null) {
            appendOutput("\n错误: SessionApi 未初始化，请先登录");
            return;
        }

        if (currentSessionId != null) {
            appendOutput("\n[" + getTimestamp() + "] 当前已在录制中，sessionId: " + shortId(currentSessionId));
            Toast.makeText(this, "已在录制中，请先结束当前录制", Toast.LENGTH_SHORT).show();
            return;
        }

        appendOutput("\n========== 开始录制 ==========");

        // 打印当前会话数量
        try {
            java.util.List<String> sessionIds = sessionApi.getAllSessionIds();
            int count = sessionIds != null ? sessionIds.size() : 0;
            appendOutput("[" + getTimestamp() + "] 当前会话数量: " + count);
        } catch (Exception e) {
            appendOutput("[" + getTimestamp() + "] 获取会话数量失败: " + e.getMessage());
        }

        appendOutput("[" + getTimestamp() + "] 正在创建会话...");

        // 立即置灰按钮
        btnStartRecording.setEnabled(false);
        btnStartRecording.setAlpha(0.5f);
        isSkillDialogShown = false;

        // 创建会话
        String sessionId = sessionApi.createSession(new SessionCallback() {
            @Override
            public void onReceived(int errorCode, String errorMessage, SessionResponseData data) {
                mainHandler.post(() -> {
                    // 测试聚焦模式：如果任务正常，跳过调试信息
                    SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
                    boolean focusMode = ConfigActivity.isFocusModeEnabled(prefs);
                    boolean shouldSkipDebug = focusMode && errorCode == 0 
                            && (errorMessage == null || errorMessage.isEmpty());
                    
                    // 统一打印 NotifyInfo（NOTIFY 和 TASK_STATUS 都可能携带）
                    if (!shouldSkipDebug && data != null && data.getNotifyInfo() != null) {
                        int notifyCode = data.getNotifyInfo().getCode();
                        appendOutput("[" + getTimestamp() + "] 收到通知: outputType=" + data.getOutputType()
                                + ", code=" + notifyCode
                                + ", userOutput=" + data.getNotifyInfo().getUserOutputObjectRawString()
                                + ", toString=" + data.getNotifyInfo().toString());
                        if (notifyCode == 210001) {
                            appendOutput("  收到录制开始通知，显示结束录制按钮");
                            showFloatingButton();
                        } else if (notifyCode == 210003) {
                            appendOutput("  收到录制完成通知，等待任务完成...");
                        } else if (notifyCode == 210004) {
                            appendOutput("  收到任务完成通知");
                            // 解析 UserOutputObjectRawString
                            String app = "", steps = "", summary = "", variables = "";
                            try {
                                String raw = data.getNotifyInfo().getUserOutputObjectRawString();
                                if (raw != null && !raw.isEmpty()) {
                                    JSONObject json = new JSONObject(raw);
                                    if (json.has("data")) {
                                        JSONObject dataObj = json.getJSONObject("data");
                                        app = dataObj.optString("app", "");
                                        steps = dataObj.optString("steps", "");
                                        summary = dataObj.optString("summary", "");
                                        variables = dataObj.optString("variables", "");
                                    }
                                }
                            } catch (Exception e) {
                                appendOutput("  解析任务数据失败: " + e.getMessage());
                            }
                            appendOutput("  应用: " + app);
                            appendOutput("  步骤: " + steps);
                            appendOutput("  摘要: " + summary);
                            appendOutput("  变量: " + variables);
                            removeFloatingButton();
                            showAddSkillDialog(app, steps, summary, variables);
                        } else if (notifyCode == 210006) {
                            appendOutput("  收到录制失败通知");
                            dismissCurrentDialog();
                            removeFloatingButton();
                            finishSession();
                            waitingDialog = new androidx.appcompat.app.AlertDialog.Builder(TongtongTaskTestActivity.this)
                                    .setTitle("录制失败")
                                    .setMessage("录制失败，请重试")
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                        return;
                    }

                    // TASK_STATUS 类型处理
                    if (data != null && data.getOutputType() == OutputTypeEnum.TASK_STATUS && data.getTaskInfo() != null) {
                        appendOutput("[" + getTimestamp() + "] 收到任务状态: taskCompleted=" + data.getTaskInfo().isTaskCompleted()
                                + ", statusCode=" + data.getTaskInfo().getTaskStatusCode()
                                + ", taskId=" + data.getTaskInfo().getTaskId()
                                + ", guiTaskRunning=" + data.getTaskInfo().isGuiTaskRunning()
                                + ", toString=" + data.getTaskInfo().toString());
                        // 无论任务是否完成，都打印 NotifyInfo（如果不为空）
                        if (data.getNotifyInfo() != null) {
                            appendOutput("  notifyInfo: code=" + data.getNotifyInfo().getCode()
                                    + ", userOutput=" + data.getNotifyInfo().getUserOutputObjectRawString()
                                    + ", toString=" + data.getNotifyInfo().toString());
                        }
                        if (data.getTaskInfo().isTaskCompleted()) {
                            appendOutput("  任务已完成");
                            // 尝试从 NotifyInfo 解析任务数据
                            String app = "", steps = "", summary = "", variables = "";
                            if (data.getNotifyInfo() != null) {
                                try {
                                    String raw = data.getNotifyInfo().getUserOutputObjectRawString();
                                    if (raw != null && !raw.isEmpty()) {
                                        JSONObject json = new JSONObject(raw);
                                        if (json.has("data")) {
                                            JSONObject dataObj = json.getJSONObject("data");
                                            app = dataObj.optString("app", "");
                                            steps = dataObj.optString("steps", "");
                                            summary = dataObj.optString("summary", "");
                                            variables = dataObj.optString("variables", "");
                                        }
                                    }
                                } catch (Exception e) {
                                    appendOutput("  解析任务数据失败: " + e.getMessage());
                                }
                            }
                            if (!app.isEmpty()) {
                                appendOutput("  应用: " + app);
                                appendOutput("  步骤: " + steps);
                                appendOutput("  摘要: " + summary);
                                appendOutput("  变量: " + variables);
                            }
                            removeFloatingButton();
                            showAddSkillDialog(app, steps, summary, variables);
                        }
                        return;
                    }

if (!shouldSkipDebug && errorCode == 0) {
                        appendOutput("[" + getTimestamp() + "] 收到消息:");
                        appendOutput("  outputType: " + (data != null ? data.getOutputType() : "null"));
                        if (data != null) {
                            if (data.getOutputType() == OutputTypeEnum.DIALOG && data.getDialogInfo() != null) {
                                appendOutput("  对话内容: " + data.getDialogInfo().getTitle());
                            } else if (data.getOutputType() == OutputTypeEnum.GUI_TASK && data.getGuiTaskInfo() != null) {
                                appendOutput("  GUI任务: " + data.getGuiTaskInfo().getGuiTaskType());
                                // GUI_TASK 类型停止消息处理
                                if (sessionApi != null) {
                                    sessionApi.stopMessageHandle();
                                }
                            } else {
                                // 测试聚焦模式过滤逻辑（使用前面已定义的 focusMode）
                                
                                // TASK_STATUS 类型：如果 taskCompleted=true 且 taskStatusCode=200，跳过显示
                                boolean shouldSkipTaskStatus = focusMode && data.getOutputType() == OutputTypeEnum.TASK_STATUS 
                                        && data.getTaskInfo() != null 
                                        && data.getTaskInfo().isTaskCompleted() 
                                        && data.getTaskInfo().getTaskStatusCode() == 200;
                                
                                if (shouldSkipTaskStatus) {
                                    // 不显示，跳过
                                } else if (focusMode && data.getOutputType() == OutputTypeEnum.TEXT && data.getTextInfo() != null) {
                                    appendOutput("  text: " + data.getTextInfo().getText());
                                } else {
                                    appendOutput("  data: " + data.toString());
                                }
                            }
                        }
                    } else {
                        appendOutput("[" + getTimestamp() + "] 错误: code=" + errorCode + ", msg=" + errorMessage);
                    }
                });
            }

            @Override
            public SendPreHandler getSendPreHandler() {
                return new SendPreHandler() {
                    @Override
                    public JSONObject handle(JSONObject msg, java.util.Map<String, String> params) {
                        try {
                            if (msg != null && params != null && params.containsKey("START_SKILL")) {
                                // 修改 Header
                                if (msg.has("Header")) {
                                    JSONObject header = msg.getJSONObject("Header");
                                    header.put("Receiver", "SystemAgent");
                                }
                                // 在 Data 中添加字段
                                if (msg.has("Data")) {
                                    JSONObject data = msg.getJSONObject("Data");
                                    data.put("UserSpecifiedAgent", "4");
                                    data.put("UserIntent", "2");
                                }
                                appendOutput("[" + getTimestamp() + "] SendPreHandler: 消息已修改");
                                appendOutput("  Header: Receiver=SystemAgent");
                                appendOutput("  Data: UserSpecifiedAgent=4, UserIntent=2");
                            }
                        } catch (Exception e) {
                            appendOutput("[" + getTimestamp() + "] SendPreHandler错误: " + e.getMessage());
                        }
                        return msg;
                    }
                };
            }
        });

        if (sessionId != null) {
            currentSessionId = sessionId;
            appendOutput("[" + getTimestamp() + "] 会话创建成功");
            appendOutput("  sessionId: " + shortId(sessionId));

            // 应用老化配置
            applySessionAgingConfig(sessionId);

            // 发送"开始录制"消息
            appendOutput("[" + getTimestamp() + "] 发送消息: 开始录制");
            Map<String, String> startParams = new HashMap<>();
            startParams.put("START_SKILL", "START_SKILL");
            SessionRequest request = new SessionRequest.Builder()
                    .userInputText("开始录制")
                    .params(startParams)
                    .build();
            boolean success = sessionApi.sendMessage(sessionId, request);
            if (!success) {
                appendOutput("[" + getTimestamp() + "] 发送消息失败");
            }
        } else {
            appendOutput("[" + getTimestamp() + "] 创建会话失败，请检查SDK是否已初始化并登录");
            Toast.makeText(this, "创建会话失败，请先登录", Toast.LENGTH_SHORT).show();
            // 创建失败，恢复按钮
            btnStartRecording.setEnabled(true);
            btnStartRecording.setAlpha(1.0f);
        }
    }

    private void appendOutput(String message) {
        mainHandler.post(() -> {
            outputBuilder.append(message).append("\n");
            tvOutput.setText(outputBuilder.toString());
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }

    private String shortId(String sid) {
        if (sid == null) return "null";
        return sid.substring(0, Math.min(8, sid.length())) + "...";
    }

    /**
     * 为指定 session 应用老化配置
     */
    private void applySessionAgingConfig(String sessionId) {
        try {
            boolean agingEnabled = switchAgingEnabled != null && switchAgingEnabled.isChecked();
            int minutes = 30;
            try {
                String text = etAgingMinutes != null ? etAgingMinutes.getText().toString().trim() : "";
                if (!text.isEmpty()) {
                    minutes = Integer.parseInt(text);
                }
            } catch (NumberFormatException ignored) {}

            if (!agingEnabled) {
                appendOutput("[" + getTimestamp() + "] 自动老化: 未启用");
                return;
            }

            Session session = SessionManager.getInstance().getSession(sessionId);
            if (session == null) {
                appendOutput("[" + getTimestamp() + "] 自动老化: 获取 Session 对象失败");
                return;
            }

            SessionAgingConfig config = new SessionAgingConfig().setMaxIdleTimeMinutes(minutes);
            session.setAgingConfig(config);
            appendOutput("[" + getTimestamp() + "] 自动老化: 已启用，空闲 " + minutes + " 分钟后自动销毁");
        } catch (Exception e) {
            appendOutput("[" + getTimestamp() + "] 自动老化配置异常: " + e.getMessage());
        }
    }

    /**
     * 初始化悬浮窗按钮（不添加到屏幕）
     */
    @SuppressLint("SetTextI18n")
    private void initFloatingButton() {
        floatingStopButton = new Button(this);
        floatingStopButton.setText("结束录制");
        floatingStopButton.setTextSize(14f);
        floatingStopButton.setTextColor(0xFFFFFFFF);
        floatingStopButton.setBackgroundColor(0xCCFF4444);
        floatingStopButton.setPadding(24, 8, 24, 8);

        floatingStopButton.setOnTouchListener((v, event) -> {
            android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    floatingLastX = (int) event.getRawX();
                    floatingLastY = (int) event.getRawY();
                    floatingIsDragging = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - floatingLastX;
                    int dy = (int) event.getRawY() - floatingLastY;
                    if (Math.abs(dx) > FLOATING_MOVE_THRESHOLD || Math.abs(dy) > FLOATING_MOVE_THRESHOLD) {
                        floatingIsDragging = true;
                    }
                    if (floatingIsDragging && floatingParams != null) {
                        floatingParams.x -= dx;
                        floatingParams.y += dy;
                        windowManager.updateViewLayout(floatingStopButton, floatingParams);
                        floatingLastX = (int) event.getRawX();
                        floatingLastY = (int) event.getRawY();
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    if (!floatingIsDragging) {
                        // 点击事件：结束录制
                        removeFloatingButton();
                        // 先拉起APP页面，确保等待弹窗可见
                        Intent intent = new Intent(TongtongTaskTestActivity.this, TongtongTaskTestActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        if (currentSessionId != null && sessionApi != null) {
                            appendOutput("[" + getTimestamp() + "] 发送消息: 结束录制");
                            Map<String, String> stopParams = new HashMap<>();
                            stopParams.put("STOP_SKILL", "STOP_SKILL");
                            SessionRequest request = new SessionRequest.Builder()
                                    .userInputText("结束录制")
                                    .userInputObjectRawString("{\"code\":300001,\"data\":{\"type\":\"\"}}")
                                    .params(stopParams)
                                    .build();
                            boolean success = sessionApi.sendMessage(currentSessionId, request);
                            if (!success) {
                                appendOutput("[" + getTimestamp() + "] 发送消息失败");
                                btnStartRecording.setEnabled(true);
                                btnStartRecording.setAlpha(1.0f);
                                return true;
                            }
                        }
                        // 显示等待弹窗
                        showWaitingDialog();
                    }
                    break;
            }
            return true;
        });
    }

    /**
     * 显示系统级悬浮按钮
     */
    private void showFloatingButton() {
        if (isFloatingButtonAdded) return;

        // 检查悬浮窗权限
        if (!android.provider.Settings.canDrawOverlays(this)) {
            appendOutput("[" + getTimestamp() + "] 无悬浮窗权限，请授权后重试");
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        floatingParams = new android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        floatingParams.gravity = Gravity.TOP | Gravity.END;
        floatingParams.x = 16;
        floatingParams.y = 16;

        android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingStopButton, floatingParams);
        isFloatingButtonAdded = true;
    }

    /**
     * 移除悬浮窗按钮
     */
    private void removeFloatingButton() {
        if (isFloatingButtonAdded && floatingStopButton != null) {
            android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.removeView(floatingStopButton);
            isFloatingButtonAdded = false;
        }
    }

    /**
     * 显示等待服务端响应弹窗
     */
    private void showWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) return;
        waitingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("结束录制")
                .setMessage("稍等一会，服务端正在处理")
                .setCancelable(false)
                .show();
    }

    /**
     * 关闭当前弹窗（如果有）
     */
    private void dismissCurrentDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
    }

    /**
     * 弹出新增技能表单（防重复调用）
     */
    private void showAddSkillDialog(String app, String steps, String summary, String variables) {
        // 已经弹出过技能弹窗，不重复创建
        if (isSkillDialogShown) {
            return;
        }
        isSkillDialogShown = true;

        // 先保存旧弹窗引用，新弹窗 show 后再关闭旧的，避免闪烁
        androidx.appcompat.app.AlertDialog oldDialog = waitingDialog;

        // 构建新增技能表单布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 16);

        // 技能名称输入框
        android.widget.EditText etSkillName = new android.widget.EditText(this);
        etSkillName.setHint("请输入技能名称");
        if (!app.isEmpty()) {
            etSkillName.setText(app.trim());
            etSkillName.setSelection(etSkillName.getText().length());
        }
        layout.addView(etSkillName);

        // 步骤
        if (!steps.isEmpty()) {
            TextView tvLabel = new TextView(this);
            tvLabel.setText("步骤:");
            tvLabel.setTextSize(13f);
            tvLabel.setTextColor(0xFF666666);
            tvLabel.setPadding(0, 16, 0, 4);
            layout.addView(tvLabel);
            TextView tvContent = new TextView(this);
            tvContent.setText(steps);
            tvContent.setTextSize(13f);
            tvContent.setMaxLines(6);
            layout.addView(tvContent);
        }

        // 摘要
        if (!summary.isEmpty()) {
            TextView tvLabel = new TextView(this);
            tvLabel.setText("摘要:");
            tvLabel.setTextSize(13f);
            tvLabel.setTextColor(0xFF666666);
            tvLabel.setPadding(0, 12, 0, 4);
            layout.addView(tvLabel);
            TextView tvContent = new TextView(this);
            tvContent.setText(summary);
            tvContent.setTextSize(13f);
            tvContent.setMaxLines(4);
            layout.addView(tvContent);
        }

        // 变量
        if (!variables.isEmpty()) {
            TextView tvLabel = new TextView(this);
            tvLabel.setText("变量:");
            tvLabel.setTextSize(13f);
            tvLabel.setTextColor(0xFF666666);
            tvLabel.setPadding(0, 12, 0, 4);
            layout.addView(tvLabel);
            TextView tvContent = new TextView(this);
            tvContent.setText(variables);
            tvContent.setTextSize(13f);
            tvContent.setMaxLines(4);
            layout.addView(tvContent);
        }

        // 统一使用 waitingDialog 引用
        waitingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("新增技能")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("确认", null)
                .setNegativeButton("取消", null)
                .show();

        // 新弹窗已显示，关闭旧弹窗（新盖旧，无闪烁）
        if (oldDialog != null && oldDialog.isShowing()) {
            oldDialog.dismiss();
        }

        // 覆盖确认按钮：名称为空不关闭
        waitingDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String skillName = etSkillName.getText().toString().trim();
                    if (skillName.isEmpty()) {
                        etSkillName.setError("技能名称不能为空");
                        return;
                    }
                    waitingDialog.dismiss();
                    waitingDialog = null;
                    appendOutput("[" + getTimestamp() + "] 正在创建技能: " + skillName);
                    new Thread(() -> {
                        try {
                            CreateSkillRequest request = new CreateSkillRequest();
                            request.setSkillName(skillName);
                            request.setApp(app);
                            request.setSteps(steps);
                            request.setSummary(summary);
                            request.setVariables(variables);
                            String skillId = skillApi.createSkill(request);
                            mainHandler.post(() -> {
                                if (skillId != null && !skillId.isEmpty()) {
                                    appendOutput("[" + getTimestamp() + "] 技能创建成功，skillId: " + skillId);
                                    refreshSkillList();
                                } else {
                                    appendOutput("[" + getTimestamp() + "] 技能创建失败，返回为空");
                                }
                                finishSession();
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                appendOutput("[" + getTimestamp() + "] 技能创建异常: " + e.getMessage());
                                finishSession();
                            });
                        }
                    }).start();
                });

        // 覆盖取消按钮
        waitingDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(v -> {
                    waitingDialog.dismiss();
                    waitingDialog = null;
                    appendOutput("[" + getTimestamp() + "] 取消新增技能");
                    finishSession();
                });
    }

    /**
     * 结束会话，销毁session并恢复按钮
     */
    private void finishSession() {
        if (currentSessionId != null && sessionApi != null) {
            sessionApi.destroySession(currentSessionId);
            appendOutput("  已销毁会话: " + shortId(currentSessionId));
        }
        currentSessionId = null;
        btnStartRecording.setEnabled(true);
        btnStartRecording.setAlpha(1.0f);
        appendOutput("[" + getTimestamp() + "] 已结束录制");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissCurrentDialog();
        removeFloatingButton();
    }
}
