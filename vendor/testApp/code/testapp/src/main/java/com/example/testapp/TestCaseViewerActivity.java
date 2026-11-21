package com.example.testapp;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 测试用例查看Activity
 */
public class TestCaseViewerActivity extends AppCompatActivity {

    private TextView tvContent;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testcase_viewer);

        // 显示返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvContent = findViewById(R.id.tvContent);
        scrollView = findViewById(R.id.scrollView);

        // 获取文件路径
        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");

        if (fileName != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        // 加载并显示文件内容
        if (filePath != null) {
            loadFileContent(filePath);
        }
    }

    /**
     * 加载文件内容
     */
    private void loadFileContent(String filePath) {
        try {
            File file = new File(filePath);
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // 格式化JSON
            String formattedJson = formatJson(content.toString());
            tvContent.setText(formattedJson);

        } catch (Exception e) {
            tvContent.setText("无法读取文件: " + e.getMessage());
        }
    }

    /**
     * 格式化JSON
     */
    private String formatJson(String json) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonElement element = JsonParser.parseString(json);
            return gson.toJson(element);
        } catch (Exception e) {
            // 如果解析失败，返回原始内容
            return json;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}