package com.example.devicesdkdemo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 接口测试页面
 * 包含记忆、技能、定时任务、Location、通通任务测试入口
 */
public class ApiTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_test);

        // 绑定按钮
        Button btnBack = findViewById(R.id.btn_back);
        Button btnLog = findViewById(R.id.btn_log);
        Button btnMemory = findViewById(R.id.btn_memory);
        Button btnSkill = findViewById(R.id.btn_skill);
        Button btnImitate = findViewById(R.id.btn_imitate);
        Button btnLocation = findViewById(R.id.btn_location);
        Button btnTongtongTask = findViewById(R.id.btn_tongtong_task);
        Button btnSession = findViewById(R.id.btn_session);

        // 返回主页面
        btnBack.setOnClickListener(v -> finish());

        // 日志测试
        btnLog.setOnClickListener(v -> startActivity(new Intent(this, LogTestActivity.class)));

        // 记忆测试
        btnMemory.setOnClickListener(v -> startActivity(new Intent(this, MemoryTestActivity.class)));

        // 技能测试
        btnSkill.setOnClickListener(v -> startActivity(new Intent(this, SkillTestActivity.class)));

        // 定时任务测试
        btnImitate.setOnClickListener(v -> startActivity(new Intent(this, ImitateTestActivity.class)));

        // Location测试
        btnLocation.setOnClickListener(v -> startActivity(new Intent(this, LocationTestActivity.class)));

        // 通通任务测试
        btnTongtongTask.setOnClickListener(v -> startActivity(new Intent(this, TongtongTaskTestActivity.class)));

        // 会话管理测试
        btnSession.setOnClickListener(v -> startActivity(new Intent(this, SessionTestActivity.class)));
    }
}
