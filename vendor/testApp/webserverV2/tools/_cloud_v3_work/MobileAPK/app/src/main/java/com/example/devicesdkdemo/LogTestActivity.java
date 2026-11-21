package com.example.devicesdkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogApi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Log接口测试页面
 */
public class LogTestActivity extends AppCompatActivity {

    private LogApi logApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentUploadLogTrigger;
    private TextView titleUploadLogTrigger;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_test);

        initLogApi();
        initViews();
    }

    private void initLogApi() {
        try {
            logApi = AISFSDKClient.getLogApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项: uploadLogTrigger
        titleUploadLogTrigger = findViewById(R.id.title_upload_log_trigger);
        contentUploadLogTrigger = findViewById(R.id.content_upload_log_trigger);
        Button btnTestUploadLogTrigger = findViewById(R.id.btn_test_upload_log_trigger);
        TextView tvOutputUploadLogTrigger = findViewById(R.id.tv_output_upload_log_trigger);

        titleUploadLogTrigger.setOnClickListener(v -> toggleItem(
                contentUploadLogTrigger, titleUploadLogTrigger, "测试 uploadLogTrigger"));
        btnTestUploadLogTrigger.setOnClickListener(v ->
                testUploadLogTrigger(tvOutputUploadLogTrigger));
    }

    /**
     * 切换折叠项展开/折叠状态
     */
    private void toggleItem(LinearLayout contentView, TextView titleView, String title) {
        if (contentView.getVisibility() == View.VISIBLE) {
            // 当前已展开，折叠它
            contentView.setVisibility(View.GONE);
            titleView.setText("▶ " + title);
            currentExpandedContent = null;
            currentExpandedTitle = null;
            currentExpandedText = "";
        } else {
            // 先折叠其他项
            if (currentExpandedContent != null) {
                currentExpandedContent.setVisibility(View.GONE);
                currentExpandedTitle.setText("▶ " + currentExpandedText);
            }
            // 展开当前项
            contentView.setVisibility(View.VISIBLE);
            titleView.setText("▼ " + title);
            currentExpandedContent = contentView;
            currentExpandedTitle = titleView;
            currentExpandedText = title;
        }
    }

    /**
     * 测试 uploadLogTrigger 接口
     */
    private void testUploadLogTrigger(TextView outputView) {
        if (logApi == null) {
            outputView.setText("错误: LogApi 未初始化");
            return;
        }

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // 调用 uploadLogTrigger 接口
                logApi.uploadLogTrigger();
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    sb.append("结果: 日志上传事件已触发\n");
                    sb.append("说明: SDK将异步上传日志到服务端\n");
                    sb.append("【v2.1变更】LogHook已移除，请查看服务端确认上传结果");
                    outputView.setText(sb.toString());
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
