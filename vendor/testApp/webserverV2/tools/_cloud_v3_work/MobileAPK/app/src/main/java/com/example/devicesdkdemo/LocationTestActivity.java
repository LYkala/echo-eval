package com.example.devicesdkdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.location.LocationApi;
import com.huawei.aisfsdkv2.api.location.LocationOprResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Location接口测试页面
 */
public class LocationTestActivity extends AppCompatActivity {

    private LocationApi locationApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentDeleteUserLocationInfo;
    private TextView titleDeleteUserLocationInfo;

    private LinearLayout contentGetReportLocationEnable;
    private TextView titleGetReportLocationEnable;

    private LinearLayout contentUpdateReportLocationEnable;
    private TextView titleUpdateReportLocationEnable;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_test);

        initLocationApi();
        initViews();
    }

    private void initLocationApi() {
        try {
            locationApi = AISFSDKClient.getLocationApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项: deleteUserLocationInfo
        titleDeleteUserLocationInfo = findViewById(R.id.title_delete_user_location_info);
        contentDeleteUserLocationInfo = findViewById(R.id.content_delete_user_location_info);
        Button btnTestDeleteUserLocationInfo = findViewById(R.id.btn_test_delete_user_location_info);
        TextView tvOutputDeleteUserLocationInfo = findViewById(R.id.tv_output_delete_user_location_info);

        titleDeleteUserLocationInfo.setOnClickListener(v -> toggleItem(
                contentDeleteUserLocationInfo, titleDeleteUserLocationInfo, "测试 deleteUserLocationInfo"));
        btnTestDeleteUserLocationInfo.setOnClickListener(v ->
                testDeleteUserLocationInfo(tvOutputDeleteUserLocationInfo));

        // 折叠项: getReportLocationEnable
        titleGetReportLocationEnable = findViewById(R.id.title_get_report_location_enable);
        contentGetReportLocationEnable = findViewById(R.id.content_get_report_location_enable);
        Button btnTestGetReportLocationEnable = findViewById(R.id.btn_test_get_report_location_enable);
        TextView tvOutputGetReportLocationEnable = findViewById(R.id.tv_output_get_report_location_enable);

        titleGetReportLocationEnable.setOnClickListener(v -> toggleItem(
                contentGetReportLocationEnable, titleGetReportLocationEnable, "测试 getReportLocationEnable"));
        btnTestGetReportLocationEnable.setOnClickListener(v ->
                testGetReportLocationEnable(tvOutputGetReportLocationEnable));

        // 折叠项: updateReportLocationEnable
        titleUpdateReportLocationEnable = findViewById(R.id.title_update_report_location_enable);
        contentUpdateReportLocationEnable = findViewById(R.id.content_update_report_location_enable);
        Button btnTestUpdateReportLocationEnable = findViewById(R.id.btn_test_update_report_location_enable);
        TextView tvOutputUpdateReportLocationEnable = findViewById(R.id.tv_output_update_report_location_enable);
        Spinner spinnerEnable = findViewById(R.id.spinner_enable);

        titleUpdateReportLocationEnable.setOnClickListener(v -> toggleItem(
                contentUpdateReportLocationEnable, titleUpdateReportLocationEnable, "测试 updateReportLocationEnable"));
        btnTestUpdateReportLocationEnable.setOnClickListener(v -> {
            boolean enable = spinnerEnable.getSelectedItemPosition() == 0;
            testUpdateReportLocationEnable(enable, tvOutputUpdateReportLocationEnable);
        });
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
     * 测试 deleteUserLocationInfo 接口
     */
    private void testDeleteUserLocationInfo(TextView outputView) {
        if (locationApi == null) {
            outputView.setText("错误: LocationApi 未初始化");
            return;
        }

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                LocationOprResult result = locationApi.deleteUserLocationInfo();
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    if (result != null) {
                        sb.append("结果: ").append(result.isResult() ? "成功" : "失败").append("\n");
                        sb.append("消息: ").append(result.getMessage());
                    } else {
                        sb.append("结果: 返回值为 null");
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
     * 测试 getReportLocationEnable 接口
     */
    private void testGetReportLocationEnable(TextView outputView) {
        if (locationApi == null) {
            outputView.setText("错误: LocationApi 未初始化");
            return;
        }

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                boolean result = locationApi.getReportLocationEnable();
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    sb.append("结果: ").append(result);
                    outputView.setText(sb.toString());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 updateReportLocationEnable 接口
     */
    private void testUpdateReportLocationEnable(boolean enable, TextView outputView) {
        if (locationApi == null) {
            outputView.setText("错误: LocationApi 未初始化");
            return;
        }

        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                locationApi.updateReportLocationEnable(enable);
                long duration = System.currentTimeMillis() - startTime;

                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    sb.append("参数: enable=").append(enable).append("\n");
                    sb.append("调用完成（无返回值）");
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
