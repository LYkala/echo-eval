package com.example.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testapp.adapter.TestFileAdapter;
import com.example.testapp.model.WebServerConfig;
import com.example.testapp.service.MediaCaptureService;
import com.example.testapp.service.TestAccessibilityService;
import com.example.testapp.service.TestExecuteService;
import com.example.testapp.service.WebServerClientService;
import com.example.testapp.util.Logger;
import com.example.testapp.util.WebServerClient;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 主界面 - MainActivity
 */
public class MainActivity extends AppCompatActivity implements TestFileAdapter.OnFileClickListener {

    private static final String TAG = "MainActivity";

    /** 权限请求码 */
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_ACCESSIBILITY = 1002;

    /** 配置文件名 */
    private static final String PREFS_NAME = "webserver_config";

    /** UI控件 */
    private Button btnStartTest;
    private Button btnStopTest;
    private Button btnFileManager;
    private Button btnViewLogs;
    private Button btnConnect;
    private Button btnSettings;
    private TextView tvTestStatus;
    private TextView tvCurrentStep;
    private TextView tvTestResult;
    private TextView tvAccessibilityStatus;
    private TextView tvMediaProjectionStatus;
    private TextView tvServerStatus;
    private TextView tvDeviceIp;
    private RecyclerView rvTestFiles;

    /** 测试文件列表适配器 */
    private TestFileAdapter testFileAdapter;

    /** 测试文件名列表 */
    private List<String> testFiles = new ArrayList<>();

    /** 当前选中的测试文件 */
    private String selectedTestFile;

    /** 日志记录器 */
    private Logger logger;

    /** MainActivity 是否处于 resumed 状态（用于退出流程判断是否真的回到主界面） */
    private static volatile boolean activityVisible = false;

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    /** WebServer配置 */
    private WebServerConfig webServerConfig;

    /**
     * 测试完成广播接收器
     */
    private BroadcastReceiver testFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.safeInfo(TAG, "收到测试完成广播");

            boolean success = intent.getBooleanExtra("success", false);
            String testId = intent.getStringExtra("testId");
            String message = intent.getStringExtra("message");

            updateTestStatus("completed");
            tvTestResult.setText("测试结果: " + (success ? "成功" : "失败") + " - " + message);

            btnStartTest.setEnabled(true);
            btnStopTest.setEnabled(false);

            Toast.makeText(MainActivity.this,
                    "测试" + (success ? "成功" : "失败") + ": " + testId,
                    Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * WebServer连接状态广播接收器
     */
    private BroadcastReceiver serverStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connected = intent.getBooleanExtra(WebServerClientService.EXTRA_CONNECTED, false);
            String serverUrl = intent.getStringExtra(WebServerClientService.EXTRA_SERVER_URL);
            updateServerStatus(connected, serverUrl);
        }
    };

    /**
     * 文件接收广播接收器
     */
    private BroadcastReceiver filesReceivedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int fileCount = intent.getIntExtra(WebServerClientService.EXTRA_FILE_COUNT, 0);
            if (fileCount > 0) {
                Toast.makeText(MainActivity.this,
                        "收到 " + fileCount + " 个测试文件",
                        Toast.LENGTH_SHORT).show();
                loadTestFiles();
            }
        }
    };

    /**
     * 文件删除广播接收器
     */
    private BroadcastReceiver fileDeletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String filename = intent.getStringExtra(WebServerClientService.EXTRA_FILENAME);
            boolean success = intent.getBooleanExtra(WebServerClientService.EXTRA_SUCCESS, false);
            if (success) {
                Toast.makeText(MainActivity.this,
                        "文件已删除: " + filename,
                        Toast.LENGTH_SHORT).show();
                loadTestFiles();
            } else {
                Toast.makeText(MainActivity.this,
                        "删除文件失败: " + filename,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * 文件刷新广播接收器
     */
    private BroadcastReceiver filesRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadTestFiles();
        }
    };

    /**
     * 执行测试命令广播接收器
     */
    private BroadcastReceiver executeTestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String filename = intent.getStringExtra(WebServerClientService.EXTRA_FILENAME);
            logger.info("MainActivity", "Received execute test command: " + filename);
            if (filename != null) {
                selectedTestFile = filename;
                loadTestFiles();
                updateTestStatus("running");
                tvTestResult.setText("测试结果: -");
                btnStartTest.setEnabled(false);
                btnStopTest.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logger = Logger.getInstance(this);
        logger.info("MainActivity", "=== 应用启动 ===");
        logger.info("MainActivity", "设备: " + Build.MANUFACTURER + " " + Build.MODEL);
        logger.info("MainActivity", "Android版本: " + Build.VERSION.RELEASE);

        // 加载配置
        loadWebServerConfig();

        // 初始化界面
        initViews();

        // 显示设备IP
        displayDeviceIp();

        // 加载测试文件列表
        loadTestFiles();

        // 检查权限
        checkPermissions();

        // 自动连接服务器
        autoConnectToServer();

        IntentFilter executeTestFilter = new IntentFilter(WebServerClientService.ACTION_EXECUTE_TEST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(executeTestReceiver, executeTestFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(executeTestReceiver, executeTestFilter);
        }
    }

    /**
     * 初始化视图控件
     */
    private void initViews() {
        btnStartTest = findViewById(R.id.btnStartTest);
        btnStopTest = findViewById(R.id.btnStopTest);
        btnFileManager = findViewById(R.id.btnFileManager);
        btnViewLogs = findViewById(R.id.btnViewLogs);
        btnConnect = findViewById(R.id.btnConnect);
        btnSettings = findViewById(R.id.btnSettings);
        tvTestStatus = findViewById(R.id.tvTestStatus);
        tvCurrentStep = findViewById(R.id.tvCurrentStep);
        tvTestResult = findViewById(R.id.tvTestResult);
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvMediaProjectionStatus = findViewById(R.id.tvMediaProjectionStatus);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvDeviceIp = findViewById(R.id.tvDeviceIp);
        rvTestFiles = findViewById(R.id.rvTestFiles);

        // 初始化测试文件列表
        testFileAdapter = new TestFileAdapter(testFiles, this);
        rvTestFiles.setLayoutManager(new LinearLayoutManager(this));
        rvTestFiles.setAdapter(testFileAdapter);

        // 启动测试按钮
        btnStartTest.setOnClickListener(v -> {
            logger.info("MainActivity", "点击启动测试按钮");

            if (selectedTestFile == null) {
                Toast.makeText(this, "请先选择测试文件", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!checkPermissions()) {
                return;
            }

            startTest();
        });

        // 停止测试按钮
        btnStopTest.setOnClickListener(v -> {
            logger.info("MainActivity", "点击停止测试按钮");

            Intent intent = new Intent(TestExecuteService.ACTION_STOP_TEST);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            btnStartTest.setEnabled(true);
            btnStopTest.setEnabled(false);
            updateTestStatus("stopped");
            tvTestResult.setText("测试结果: 用户停止");

            Toast.makeText(this, "正在停止测试...", Toast.LENGTH_SHORT).show();
        });

        // 文件管理按钮
        btnFileManager.setOnClickListener(v -> {
            logger.info("MainActivity", "点击文件管理按钮");
            startActivity(new Intent(this, FileManagerActivity.class));
        });

        // 查看日志按钮
        btnViewLogs.setOnClickListener(v -> {
            logger.info("MainActivity", "点击查看日志按钮");
            startActivity(new Intent(this, LogViewerActivity.class));
        });

        // 服务器地址设置按钮
        btnSettings.setOnClickListener(v -> {
            logger.info("MainActivity", "点击服务器地址设置按钮");
            showServerConfigDialog();
        });

        // 连接服务器按钮
        btnConnect.setOnClickListener(v -> {
            logger.info("MainActivity", "点击连接服务器按钮");
            reconnectToServer();
        });

        if (tvMediaProjectionStatus != null) {
            tvMediaProjectionStatus.setOnClickListener(v -> onMediaProjectionStatusClick());
        }
        if (tvAccessibilityStatus != null) {
            tvAccessibilityStatus.setOnClickListener(v -> openAccessibilitySettings());
        }

        // 显示当前服务器配置
        updateServerConfigDisplay();
    }

    /**
     * 加载WebServer配置
     */
    private void loadWebServerConfig() {
        webServerConfig = new WebServerConfig();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedHost = prefs.getString("server_host", null);
        if (savedHost != null) {
            webServerConfig.setServerHost(savedHost);
        }

        int savedPort = prefs.getInt("server_port", -1);
        if (savedPort != -1) {
            webServerConfig.setServerPort(savedPort);
        }

        logger.info("MainActivity", "加载配置: " + webServerConfig.getServerUrl());
    }

    /**
     * 保存WebServer配置
     */
    private void saveWebServerConfig(String host, int port) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("server_host", host)
                .putInt("server_port", port)
                .apply();

        webServerConfig.setServerHost(host);
        webServerConfig.setServerPort(port);

        logger.info("MainActivity", "保存配置: " + webServerConfig.getServerUrl());
    }

    /**
     * 显示服务器配置对话框
     */
    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("服务器地址设置");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_server_config, null);
        EditText etHost = view.findViewById(R.id.etServerHost);
        EditText etPort = view.findViewById(R.id.etServerPort);

        etHost.setText(webServerConfig.getServerHost());
        etPort.setText(String.valueOf(webServerConfig.getServerPort()));

        builder.setView(view);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String host = etHost.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();

            if (TextUtils.isEmpty(host)) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }

            int port = 9527;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "端口格式错误，使用默认端口9527", Toast.LENGTH_SHORT).show();
            }

            // 保存配置
            saveWebServerConfig(host, port);

            // 更新显示
            updateServerConfigDisplay();

            Toast.makeText(this, "配置已保存，请点击\"连接服务器\"按钮重新连接", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 更新服务器配置显示
     */
    private void updateServerConfigDisplay() {
        String host = webServerConfig.getServerHost();
        int port = webServerConfig.getServerPort();
        tvServerStatus.setText("服务器: " + host + ":" + port + " (未连接)");
        tvServerStatus.setTextColor(0xFF9E9E9E);
    }

    /**
     * 自动连接服务器（启动时调用）
     */
    private void autoConnectToServer() {
        logger.info("MainActivity", "自动连接服务器: " + webServerConfig.getServerUrl());
        updateServerConfigDisplay();
        connectToServer();
    }

    /**
     * 连接到服务器
     */
    private void connectToServer() {
        Intent intent = new Intent(this, WebServerClientService.class);
        intent.setAction(WebServerClientService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        tvServerStatus.setText("服务器: " + webServerConfig.getServerUrl() + " (连接中...)");
        tvServerStatus.setTextColor(0xFFFF9800);

        logger.info("MainActivity", "正在连接服务器...");
    }

    /**
     * 重新连接到服务器
     */
    private void reconnectToServer() {
        // 先停止当前服务
        Intent stopIntent = new Intent(this, WebServerClientService.class);
        stopIntent.setAction(WebServerClientService.ACTION_STOP);
        startService(stopIntent);

        // 延迟后重新连接
        new android.os.Handler().postDelayed(() -> {
            connectToServer();
        }, 500);
    }

    /**
     * 获取设备IP地址
     */
    private String getDeviceIpAddress() {
        try {
            // 先尝试获取WiFi IP
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return formatIpAddress(ipAddress);
                }
            }

            // 如果WiFi没有IP，尝试其他网络接口
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "获取IP地址失败", e);
        }
        return "无法获取";
    }

    /**
     * 格式化IP地址
     */
    private String formatIpAddress(int ipAddress) {
        return (ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                ((ipAddress >> 24) & 0xFF);
    }

    /**
     * 显示设备IP
     */
    private void displayDeviceIp() {
        String ip = getDeviceIpAddress();
        tvDeviceIp.setText("本机IP: " + ip);
        logger.info("MainActivity", "设备IP: " + ip);
    }

    /**
     * 加载测试文件列表
     * 从assets和filesDir两个位置加载
     */
    private void loadTestFiles() {
        testFiles.clear();

        // 1. 从assets目录加载
        try {
            String[] files = getAssets().list("testflows");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".json") && !testFiles.contains(file)) {
                        testFiles.add(file);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("MainActivity", "加载assets测试文件失败", e);
        }

        // 2. 从filesDir/testflows目录加载（从WebServer接收的文件）
        File testflowsDir = new File(getFilesDir(), "testflows");
        if (testflowsDir.exists() && testflowsDir.isDirectory()) {
            File[] receivedFiles = testflowsDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (receivedFiles != null) {
                for (File file : receivedFiles) {
                    String fileName = file.getName();
                    if (!testFiles.contains(fileName)) {
                        testFiles.add(fileName);
                    }
                }
            }
        }

        // 排序
        java.util.Collections.sort(testFiles);

        logger.info("MainActivity", "加载测试文件: " + testFiles.size() + " 个");

        if (testFileAdapter != null) {
            testFileAdapter.notifyDataSetChanged();
        }
    }

    private static final String PREFS_ACCESSIBILITY = "accessibility_first_launch";

    /**
     * 检查必要的权限
     * 只在首次启动时显示无障碍权限弹窗引导用户授权
     */
    private boolean checkPermissions() {
        logger.method("MainActivity", "checkPermissions");

        // 检查是否是首次启动
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean(PREFS_ACCESSIBILITY, true);

        // 使用 checkServiceReallyEnabled() 获取真实状态用于显示
        boolean reallyEnabled = TestAccessibilityService.checkServiceReallyEnabled();
        updateAccessibilityStatus(reallyEnabled);

        // 首次启动时，如果权限未开启，弹出提示框引导用户
        if (isFirstLaunch && !reallyEnabled) {
            showAccessibilityFirstLaunchDialog();

            // 更新标记为非首次启动
            prefs.edit().putBoolean(PREFS_ACCESSIBILITY, false).apply();
        }

        logger.info("MainActivity", "权限检查完成");
        return true;
    }

    /**
     * 首次启动时的无障碍权限引导对话框
     */
    private void showAccessibilityFirstLaunchDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要开启无障碍服务")
            .setMessage("TestApp 需要无障碍服务权限来执行自动化测试。\n\n请开启：无障碍服务 → TestApp → 开启")
            .setPositiveButton("去开启", (dialog, which) -> {
                // 跳转到无障碍服务设置
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("稍后再说", null)
            .show();
    }

    /**
     * 更新无障碍服务状态显示
     */
    private void updateAccessibilityStatus(boolean enabled) {
        if (tvAccessibilityStatus != null) {
            if (enabled) {
                tvAccessibilityStatus.setText("无障碍服务: 已启用 ✓ (点击设置)");
                tvAccessibilityStatus.setTextColor(0xFF4CAF50);
            } else {
                tvAccessibilityStatus.setText("无障碍服务: 未启用 ✗ (点击设置)");
                tvAccessibilityStatus.setTextColor(0xFFF44336);
            }
        }
    }

    private void openAccessibilitySettings() {
        logger.info("MainActivity", "点击无障碍状态，打开系统设置");
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
            logger.error("MainActivity", "打开无障碍设置失败", e);
        }
    }

    /** 更新录屏（MediaProjection）权限状态。 */
    private void updateMediaProjectionStatus() {
        if (tvMediaProjectionStatus == null) {
            return;
        }
        if (MediaCaptureService.isReady()) {
            tvMediaProjectionStatus.setText("录屏权限: 服务就绪 ✓");
            tvMediaProjectionStatus.setTextColor(0xFF4CAF50);
            return;
        }
        if (MediaCaptureService.hasUsableProjection()) {
            tvMediaProjectionStatus.setText("录屏权限: 已授权，点击恢复服务");
            tvMediaProjectionStatus.setTextColor(0xFFFF9800);
            return;
        }
        if (MediaCaptureService.isPermissionRecorded(this)) {
            tvMediaProjectionStatus.setText("录屏权限: 曾授权，点击重新激活");
            tvMediaProjectionStatus.setTextColor(0xFFFF9800);
            return;
        }
        tvMediaProjectionStatus.setText("录屏权限: 未授权 ✗ (点击授权)");
        tvMediaProjectionStatus.setTextColor(0xFFF44336);
    }

    private void onMediaProjectionStatusClick() {
        if (MediaCaptureService.isReady()) {
            Toast.makeText(this, "录屏截图已就绪", Toast.LENGTH_SHORT).show();
            updateMediaProjectionStatus();
            return;
        }
        if (MediaCaptureService.hasUsableProjection()
                && MediaCaptureService.ensureRunning(this)) {
            Toast.makeText(this, "录屏服务已恢复", Toast.LENGTH_SHORT).show();
            updateMediaProjectionStatus();
            return;
        }
        tvMediaProjectionStatus.setText("录屏权限: 等待授权…");
        tvMediaProjectionStatus.setTextColor(0xFFFF9800);
        MediaCaptureService.requestPermission(this, granted -> runOnUiThread(() -> {
            updateMediaProjectionStatus();
            if (granted) {
                Toast.makeText(MainActivity.this, "录屏授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "录屏授权失败，请重试", Toast.LENGTH_LONG).show();
            }
        }));
    }

    /**
     * 更新WebServer状态显示
     */
    private void updateServerStatus(boolean connected, String serverUrl) {
        if (tvServerStatus != null) {
            if (connected) {
                tvServerStatus.setText("服务器: " + serverUrl + " (已连接)");
                tvServerStatus.setTextColor(0xFF4CAF50);
            } else {
                tvServerStatus.setText("服务器: " + webServerConfig.getServerUrl() + " (未连接)");
                tvServerStatus.setTextColor(0xFF9E9E9E);
            }
        }
    }

    /**
     * 与 WebServerClient 实际连接状态对齐，避免页面停留在「连接中」。
     */
    private void syncServerConnectionStatus() {
        WebServerClient client = WebServerClient.peekInstance();
        if (client != null && client.isConnected()) {
            WebServerConfig config = client.getConfig();
            String serverUrl = config != null ? config.getServerUrl() : webServerConfig.getServerUrl();
            updateServerStatus(true, serverUrl);
        }
    }

    /**
     * 显示权限请求对话框
     */
    private void showPermissionDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("去开启", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 启动测试
     */
    private void startTest() {
        logger.info("MainActivity", "开始测试: " + selectedTestFile);

        updateTestStatus("running");
        tvTestResult.setText("测试结果: -");

        Intent intent = new Intent(this, TestExecuteService.class);
        intent.setAction(TestExecuteService.ACTION_START_TEST);
        intent.putExtra(TestExecuteService.EXTRA_TEST_FILE, selectedTestFile);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        btnStartTest.setEnabled(false);
        btnStopTest.setEnabled(true);
    }

    /**
     * 使用指定文件启动测试（从webserver命令触发）
     */
    private void startTestWithFile(String filename) {
        logger.info("MainActivity", "从webserver启动测试: " + filename);

        // 设置选中的测试文件
        selectedTestFile = filename;

        // 刷新文件列表显示选中状态
        loadTestFiles();

        // 启动测试
        startTest();
    }

    /**
     * 更新测试状态显示
     */
    private void updateTestStatus(String status) {
        String statusText;
        switch (status) {
            case "waiting": statusText = "待执行"; break;
            case "running": statusText = "执行中"; break;
            case "paused": statusText = "已暂停"; break;
            case "completed": statusText = "已完成"; break;
            case "failed": statusText = "失败"; break;
            case "stopped": statusText = "已停止"; break;
            default: statusText = status;
        }
        tvTestStatus.setText("测试状态: " + statusText);
    }

    @Override
    public void onFileClick(String fileName, int position) {
        selectedTestFile = fileName;
        testFileAdapter.setSelectedPosition(position);
        logger.info("MainActivity", "选择测试文件: " + fileName);
        Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        logger.method("MainActivity", "onResume");

        IntentFilter testFilter = new IntentFilter(TestExecuteService.ACTION_TEST_FINISHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testFinishedReceiver, testFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(testFinishedReceiver, testFilter);
        }

        IntentFilter serverFilter = new IntentFilter(WebServerClientService.ACTION_CONNECTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serverStatusReceiver, serverFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serverStatusReceiver, serverFilter);
        }

        IntentFilter filesFilter = new IntentFilter(WebServerClientService.ACTION_FILES_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(filesReceivedReceiver, filesFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(filesReceivedReceiver, filesFilter);
        }

        IntentFilter fileDeletedFilter = new IntentFilter(WebServerClientService.ACTION_FILE_DELETED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fileDeletedReceiver, fileDeletedFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(fileDeletedReceiver, fileDeletedFilter);
        }

        IntentFilter filesRefreshFilter = new IntentFilter(WebServerClientService.ACTION_FILES_REFRESH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(filesRefreshReceiver, filesRefreshFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(filesRefreshReceiver, filesRefreshFilter);
        }

        updateAccessibilityStatus(TestAccessibilityService.checkServiceReallyEnabled());
        updateMediaProjectionStatus();
        syncServerConnectionStatus();

        // 刷新设备IP
        displayDeviceIp();

        // 检查测试运行状态，更新按钮状态
        updateButtonStateByTestStatus();
    }

    /**
     * 根据测试运行状态更新按钮
     * 解决退出流程后按钮状态不一致的问题
     */
    private void updateButtonStateByTestStatus() {
        boolean isTestRunning = TestExecuteService.isRunning();
        Logger.safeInfo(TAG, "检查测试运行状态: " + (isTestRunning ? "运行中" : "已停止"));

        if (isTestRunning) {
            btnStartTest.setEnabled(false);
            btnStopTest.setEnabled(true);
            updateTestStatus("running");
        } else {
            btnStartTest.setEnabled(true);
            btnStopTest.setEnabled(false);
            updateTestStatus("idle");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityVisible = false;
        try { unregisterReceiver(testFinishedReceiver); } catch (Exception e) {}
        try { unregisterReceiver(serverStatusReceiver); } catch (Exception e) {}
        try { unregisterReceiver(filesReceivedReceiver); } catch (Exception e) {}
        try { unregisterReceiver(fileDeletedReceiver); } catch (Exception e) {}
        try { unregisterReceiver(filesRefreshReceiver); } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        activityVisible = false;
        try { unregisterReceiver(executeTestReceiver); } catch (Exception e) {}
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
                logger.info("MainActivity", "悬浮窗权限已开启");
            } else {
                Toast.makeText(this, "悬浮窗权限未开启", Toast.LENGTH_SHORT).show();
                logger.warn("MainActivity", "悬浮窗权限未开启");
            }
        } else if (requestCode == REQUEST_ACCESSIBILITY) {
            boolean enabled = TestAccessibilityService.checkServiceReallyEnabled();
            updateAccessibilityStatus(enabled);
            if (enabled) {
                Toast.makeText(this, "无障碍服务已开启", Toast.LENGTH_SHORT).show();
                logger.info("MainActivity", "无障碍服务已开启");
            } else {
                Toast.makeText(this, "无障碍服务未开启", Toast.LENGTH_SHORT).show();
                logger.warn("MainActivity", "无障碍服务未开启");
            }
        }
    }
}