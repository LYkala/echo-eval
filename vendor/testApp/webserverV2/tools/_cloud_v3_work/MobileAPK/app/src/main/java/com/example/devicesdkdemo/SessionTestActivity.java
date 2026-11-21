package com.example.devicesdkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.session.SessionApi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 会话管理测试页面 - 折叠式设计
 */
public class SessionTestActivity extends AppCompatActivity {

    private SessionApi sessionApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentGetAllSessionIds;
    private TextView titleGetAllSessionIds;
    private LinearLayout contentDestroyAllSessions;
    private TextView titleDestroyAllSessions;
    private LinearLayout contentDestroySession;
    private TextView titleDestroySession;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_test);

        initSessionApi();
        initViews();
    }

    private void initSessionApi() {
        try {
            sessionApi = AISFSDKClient.getSessionApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项1: getAllSessionIds
        titleGetAllSessionIds = findViewById(R.id.title_get_all_session_ids);
        contentGetAllSessionIds = findViewById(R.id.content_get_all_session_ids);
        Button btnTestGetAllSessionIds = findViewById(R.id.btn_test_get_all_session_ids);
        TextView tvOutputGetAllSessionIds = findViewById(R.id.tv_output_get_all_session_ids);

        titleGetAllSessionIds.setOnClickListener(v -> toggleItem(
                contentGetAllSessionIds, titleGetAllSessionIds, "测试 getAllSessionIds"));
        btnTestGetAllSessionIds.setOnClickListener(v -> testGetAllSessionIds(tvOutputGetAllSessionIds));

        // 折叠项2: destroyAllSessions
        titleDestroyAllSessions = findViewById(R.id.title_destroy_all_sessions);
        contentDestroyAllSessions = findViewById(R.id.content_destroy_all_sessions);
        Button btnTestDestroyAllSessions = findViewById(R.id.btn_test_destroy_all_sessions);
        TextView tvOutputDestroyAllSessions = findViewById(R.id.tv_output_destroy_all_sessions);

        titleDestroyAllSessions.setOnClickListener(v -> toggleItem(
                contentDestroyAllSessions, titleDestroyAllSessions, "测试 destroyAllSessions"));
        btnTestDestroyAllSessions.setOnClickListener(v -> testDestroyAllSessions(tvOutputDestroyAllSessions));

        // 折叠项3: destroySession
        titleDestroySession = findViewById(R.id.title_destroy_session);
        contentDestroySession = findViewById(R.id.content_destroy_session);
        EditText etDestroySessionId = findViewById(R.id.et_destroy_session_id);
        Button btnTestDestroySession = findViewById(R.id.btn_test_destroy_session);
        TextView tvOutputDestroySession = findViewById(R.id.tv_output_destroy_session);

        titleDestroySession.setOnClickListener(v -> toggleItem(
                contentDestroySession, titleDestroySession, "测试 destroySession"));
        btnTestDestroySession.setOnClickListener(v -> {
            String sessionId = etDestroySessionId.getText().toString().trim();
            testDestroySession(sessionId, tvOutputDestroySession);
        });
    }

    /**
     * 切换折叠项展开/折叠状态
     */
    private void toggleItem(LinearLayout contentView, TextView titleView, String title) {
        if (contentView.getVisibility() == View.VISIBLE) {
            contentView.setVisibility(View.GONE);
            titleView.setText("▶ " + title);
            currentExpandedContent = null;
            currentExpandedTitle = null;
            currentExpandedText = "";
        } else {
            if (currentExpandedContent != null) {
                currentExpandedContent.setVisibility(View.GONE);
                currentExpandedTitle.setText("▶ " + currentExpandedText);
            }
            contentView.setVisibility(View.VISIBLE);
            titleView.setText("▼ " + title);
            currentExpandedContent = contentView;
            currentExpandedTitle = titleView;
            currentExpandedText = title;
        }
    }

    /**
     * 测试 getAllSessionIds 接口
     */
    private void testGetAllSessionIds(TextView outputView) {
        if (sessionApi == null) {
            outputView.setText("错误: SessionApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                List<String> result = sessionApi.getAllSessionIds();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    if (result == null || result.isEmpty()) {
                        sb.append("结果: 会话列表为空\n");
                    } else {
                        sb.append("结果: 共 ").append(result.size()).append(" 个会话\n\n");
                        for (int i = 0; i < result.size(); i++) {
                            sb.append("[").append(i + 1).append("] SessionId: ").append(result.get(i)).append("\n");
                        }
                    }
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 destroyAllSessions 接口
     */
    private void testDestroyAllSessions(TextView outputView) {
        if (sessionApi == null) {
            outputView.setText("错误: SessionApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                sessionApi.destroyAllSessions();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String output = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "所有会话已销毁";
                    outputView.setText(output);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 destroySession 接口
     */
    private void testDestroySession(String sessionId, TextView outputView) {
        if (sessionApi == null) {
            outputView.setText("错误: SessionApi 未初始化");
            return;
        }
        if (sessionId.isEmpty()) {
            outputView.setText("错误: sessionId 不能为空");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                boolean result = sessionApi.destroySession(sessionId);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String output = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "sessionId = " + sessionId + "\n"
                            + "返回结果: " + result;
                    outputView.setText(output);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    private String getTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }
}
