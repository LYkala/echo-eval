package com.example.testapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.testapp.util.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 日志查看界面 - LogViewerActivity
 *
 * 【功能说明】
 * 1. 显示应用运行日志
 * 2. 支持导出日志到下载目录
 * 3. 支持分享日志
 *
 * 【使用方式】
 * - 不带参数：显示当前内存中的日志
 * - 带testId参数：显示指定测试的日志文件
 */
public class LogViewerActivity extends AppCompatActivity {

    private static final String TAG = "LogViewerActivity";

    /** 日志内容显示控件 */
    private TextView tvLogContent;

    /** 清空日志按钮 */
    private Button btnClearLog;

    /** 导出日志按钮 */
    private Button btnExportLog;

    /** 分享日志按钮 */
    private Button btnShareLog;

    /** 滚动视图 */
    private ScrollView scrollView;

    /** 日志记录器实例 */
    private Logger logger;

    /** 测试ID（可选，用于加载特定测试的日志） */
    private String testId;

    /**
     * Activity创建回调
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_log_viewer);
        } catch (Exception e) {
            Logger.safeError(TAG, "加载布局失败", e);
            finish();
            return;
        }

        // 设置返回按钮
        try {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setTitle("日志查看");
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "设置ActionBar失败", e);
        }

        // 初始化Logger
        try {
            logger = Logger.getInstance(this);
        } catch (Exception e) {
            Logger.safeError(TAG, "Logger初始化失败", e);
        }

        // 获取传入的测试ID
        try {
            testId = getIntent() != null ? getIntent().getStringExtra("testId") : null;
        } catch (Exception e) {
            Logger.safeError(TAG, "获取Intent参数失败", e);
            testId = null;
        }

        // 初始化视图
        initViews();

        // 加载日志内容
        loadLog();
    }

    /**
     * 初始化视图控件
     */
    private void initViews() {
        try {
            tvLogContent = findViewById(R.id.tvLogContent);
            btnClearLog = findViewById(R.id.btnClearLog);
            btnExportLog = findViewById(R.id.btnExportLog);
            btnShareLog = findViewById(R.id.btnShareLog);
            scrollView = findViewById(R.id.scrollView);

            // 清空日志按钮
            if (btnClearLog != null) {
                btnClearLog.setOnClickListener(v -> {
                    try {
                        if (logger != null) {
                            logger.clear();
                        }
                        if (tvLogContent != null) {
                            tvLogContent.setText("日志已清空");
                        }
                        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Logger.safeError(TAG, "清空日志失败", e);
                    }
                });
            }

            // 导出日志按钮
            if (btnExportLog != null) {
                btnExportLog.setOnClickListener(v -> exportLogToDownload());
            }

            // 分享日志按钮
            if (btnShareLog != null) {
                btnShareLog.setOnClickListener(v -> shareLog());
            }

        } catch (Exception e) {
            Logger.safeError(TAG, "初始化视图失败", e);
        }
    }

    /**
     * 加载并显示日志内容
     */
    private void loadLog() {
        String logContent = "加载日志中...";

        try {
            if (testId != null && !testId.isEmpty()) {
                // 加载指定测试的日志文件
                if (logger != null) {
                    File logFile = logger.getLogFile(testId);
                    if (logFile != null && logFile.exists()) {
                        logContent = readFile(logFile);
                        // 安全地设置标题
                        try {
                            ActionBar actionBar = getSupportActionBar();
                            if (actionBar != null) {
                                actionBar.setTitle("日志: " + testId);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        logContent = "暂无日志文件: " + testId + "\n\n路径: " +
                                (logFile != null ? logFile.getAbsolutePath() : "null");
                    }
                } else {
                    logContent = "Logger未初始化";
                }
            } else {
                // 加载当前内存中的日志
                if (logger != null) {
                    logContent = logger.getLogContent();
                }
                if (logContent == null || logContent.isEmpty()) {
                    logContent = "暂无日志\n\n日志内容会在测试执行时自动记录。\n点击'导出到下载'可以保存日志文件。";
                }
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "加载日志失败", e);
            logContent = "加载日志失败: " + e.getMessage();
        }

        // 显示日志内容
        if (tvLogContent != null) {
            tvLogContent.setText(logContent);
        }

        // 滚动到底部
        if (scrollView != null) {
            scrollView.post(() -> {
                try {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                } catch (Exception ignored) {}
            });
        }
    }

    /**
     * 导出日志到下载目录
     */
    private void exportLogToDownload() {
        try {
            String id = testId != null ? testId : "current_" + System.currentTimeMillis();

            if (logger == null) {
                Toast.makeText(this, "Logger未初始化", Toast.LENGTH_SHORT).show();
                return;
            }

            File exportedFile = logger.exportToDownload(id);

            if (exportedFile != null) {
                String path = exportedFile.getAbsolutePath();
                Toast.makeText(this, "日志已导出到:\n" + path, Toast.LENGTH_LONG).show();

                // 刷新媒体库，使文件在文件管理器中可见
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaIntent.setData(android.net.Uri.fromFile(exportedFile));
                sendBroadcast(mediaIntent);
            } else {
                Toast.makeText(this, "导出日志失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "导出日志失败", e);
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 分享日志内容
     */
    private void shareLog() {
        try {
            String logContent = tvLogContent != null ? tvLogContent.getText().toString() : "";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "TestApp 日志");
            shareIntent.putExtra(Intent.EXTRA_TEXT, logContent);

            startActivity(Intent.createChooser(shareIntent, "分享日志"));
        } catch (Exception e) {
            Logger.safeError(TAG, "分享日志失败", e);
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 读取文件内容
     */
    private String readFile(File file) {
        StringBuilder content = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
        } catch (Exception e) {
            content.append("读取日志文件失败: ").append(e.getMessage());
        }
        return content.toString();
    }

    /**
     * 处理ActionBar菜单项点击
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理返回按钮
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 处理返回键
     */
    @Override
    public void onBackPressed() {
        finish();
    }
}