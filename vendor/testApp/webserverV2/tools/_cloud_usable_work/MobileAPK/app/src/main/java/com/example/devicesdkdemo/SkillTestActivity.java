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
import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.api.skill.SkillApi;
import com.huawei.aisfsdkv2.api.skill.SkillInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 技能接口测试页面 - 折叠式设计
 */
public class SkillTestActivity extends AppCompatActivity {

    private SkillApi skillApi;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 折叠项视图
    private LinearLayout contentGetSkillList;
    private TextView titleGetSkillList;
    private LinearLayout contentCreateSkill;
    private TextView titleCreateSkill;
    private LinearLayout contentDeleteSkill;
    private TextView titleDeleteSkill;

    // 当前展开的项
    private LinearLayout currentExpandedContent = null;
    private TextView currentExpandedTitle = null;
    private String currentExpandedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skill_test);

        initSkillApi();
        initViews();
    }

    private void initSkillApi() {
        try {
            skillApi = AISFSDKClient.getSkillApi();
        } catch (Exception e) {
            // 初始化失败
        }
    }

    private void initViews() {
        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 折叠项1: getSkillList
        titleGetSkillList = findViewById(R.id.title_get_skill_list);
        contentGetSkillList = findViewById(R.id.content_get_skill_list);
        Button btnTestGetSkillList = findViewById(R.id.btn_test_get_skill_list);
        TextView tvOutputGetSkillList = findViewById(R.id.tv_output_get_skill_list);

        titleGetSkillList.setOnClickListener(v -> toggleItem(
                contentGetSkillList, titleGetSkillList, "测试 getSkillList"));
        btnTestGetSkillList.setOnClickListener(v -> testGetSkillList(tvOutputGetSkillList));

        // 折叠项2: createSkill
        titleCreateSkill = findViewById(R.id.title_create_skill);
        contentCreateSkill = findViewById(R.id.content_create_skill);
        EditText etCreateSkillName = findViewById(R.id.et_create_skill_name);
        EditText etCreateSkillSummary = findViewById(R.id.et_create_skill_summary);
        EditText etCreateSkillApp = findViewById(R.id.et_create_skill_app);
        EditText etCreateSkillSteps = findViewById(R.id.et_create_skill_steps);
        EditText etCreateSkillVariables = findViewById(R.id.et_create_skill_variables);
        Button btnTestCreateSkill = findViewById(R.id.btn_test_create_skill);
        TextView tvOutputCreateSkill = findViewById(R.id.tv_output_create_skill);

        titleCreateSkill.setOnClickListener(v -> toggleItem(
                contentCreateSkill, titleCreateSkill, "测试 createSkill"));
        btnTestCreateSkill.setOnClickListener(v -> {
            String skillName = etCreateSkillName.getText().toString().trim();
            String summary = etCreateSkillSummary.getText().toString().trim();
            String app = etCreateSkillApp.getText().toString().trim();
            String steps = etCreateSkillSteps.getText().toString().trim();
            String variables = etCreateSkillVariables.getText().toString().trim();
            testCreateSkill(skillName, summary, app, steps, variables, tvOutputCreateSkill);
        });

        // 折叠项3: deleteSkill
        titleDeleteSkill = findViewById(R.id.title_delete_skill);
        contentDeleteSkill = findViewById(R.id.content_delete_skill);
        EditText etDeleteSkillId = findViewById(R.id.et_delete_skill_id);
        Button btnTestDeleteSkill = findViewById(R.id.btn_test_delete_skill);
        TextView tvOutputDeleteSkill = findViewById(R.id.tv_output_delete_skill);

        titleDeleteSkill.setOnClickListener(v -> toggleItem(
                contentDeleteSkill, titleDeleteSkill, "测试 deleteSkill"));
        btnTestDeleteSkill.setOnClickListener(v -> {
            String skillId = etDeleteSkillId.getText().toString().trim();
            testDeleteSkill(skillId, tvOutputDeleteSkill);
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
     * 测试 getSkillList 接口
     */
    private void testGetSkillList(TextView outputView) {
        if (skillApi == null) {
            outputView.setText("错误: SkillApi 未初始化");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                List<SkillInfo> result = skillApi.getSkillList();
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(getTimestamp()).append("] 测试成功\n");
                    sb.append("响应时间: ").append(duration).append("ms\n\n");
                    if (result == null || result.isEmpty()) {
                        sb.append("结果: 技能列表为空\n");
                    } else {
                        sb.append("结果: 共 ").append(result.size()).append(" 条技能\n\n");
                        for (int i = 0; i < result.size(); i++) {
                            SkillInfo info = result.get(i);
                            sb.append("[").append(i + 1).append("]\n");
                            sb.append("  SkillId: ").append(info.getSkillId()).append("\n");
                            sb.append("  SkillName: ").append(info.getSkillName()).append("\n");
                            sb.append("  Summary: ").append(info.getSummary()).append("\n");
                            sb.append("  App: ").append(info.getApp()).append("\n");
                            sb.append("  Steps: ").append(info.getSteps()).append("\n");
                            sb.append("  Variables: ").append(info.getVariables()).append("\n");
                            sb.append("  IsDefault: ").append(info.getIsDefault()).append("\n\n");
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
     * 测试 createSkill 接口
     */
    private void testCreateSkill(String skillName, String summary, String app, String steps, String variables, TextView outputView) {
        if (skillApi == null) {
            outputView.setText("错误: SkillApi 未初始化");
            return;
        }
        if (skillName.isEmpty()) {
            outputView.setText("错误: skillName 不能为空");
            return;
        }
        outputView.setText("测试中...");
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                CreateSkillRequest request = new CreateSkillRequest();
                request.setSkillName(skillName);
                request.setSummary(summary);
                request.setApp(app);
                request.setSteps(steps);
                request.setVariables(variables);
                String result = skillApi.createSkill(request);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String output = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "返回结果:\n" + result;
                    outputView.setText(output);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        outputView.setText("[" + getTimestamp() + "] 测试失败\n\n错误: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 测试 deleteSkill 接口
     */
    private void testDeleteSkill(String skillId, TextView outputView) {
        if (skillApi == null) {
            outputView.setText("错误: SkillApi 未初始化");
            return;
        }
        if (skillId.isEmpty()) {
            outputView.setText("错误: skillId 不能为空");
            return;
        }
        outputView.setText("测试中...");
        final String finalSkillId = skillId;
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                DeleteSkillRequest request = new DeleteSkillRequest(finalSkillId);
                boolean result = skillApi.deleteSkill(request);
                long duration = System.currentTimeMillis() - startTime;
                mainHandler.post(() -> {
                    String output = "[" + getTimestamp() + "] 测试成功\n"
                            + "响应时间: " + duration + "ms\n\n"
                            + "skillId = " + finalSkillId + "\n"
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
