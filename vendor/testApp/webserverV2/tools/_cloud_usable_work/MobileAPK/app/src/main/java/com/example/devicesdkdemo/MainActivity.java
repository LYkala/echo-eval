package com.example.devicesdkdemo;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// 自动生成的 BuildConfig，包含编译时间
import com.example.devicesdkdemo.BuildConfig;

// v2.1 新接口导入
import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.AISFClientParam;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.base.DeviceTypeEnum;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerEnvEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;      // 【补充】关键文件信息
import com.huawei.aisfsdkv2.api.base.TLSConfig;
import com.huawei.aisfsdkv2.api.base.CaCertType;
import com.huawei.aisfsdkv2.api.base.DeviceCertType;
import com.huawei.aisfsdkv2.api.log.SdkLogConfig;
import com.huawei.aisfsdkv2.api.log.SdkLocalLogConfig;
import com.huawei.aisfsdkv2.api.log.LogLevelEnum;

// Netty HTTP client for ticket-server
import com.example.devicesdkdemo.utils.NettyHttpClient;
import com.example.devicesdkdemo.utils.NettyHttpClient.HttpResponseResult;
import org.json.JSONObject;
// import com.huawei.aisfsdkv2.api.log.LogHook;       // 【v2.1移除】
import com.huawei.aisfsdkv2.api.login.LoginApi;
import com.huawei.aisfsdkv2.api.login.LoginCallback;
import com.huawei.aisfsdkv2.api.login.LoginParam;    // 【v2.1】登录参数
import com.huawei.aisfsdkv2.api.login.LoginInfo;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.api.login.LoginErrorEnum; // 【v2.1】登录错误枚举
import com.huawei.aisfsdkv2.api.login.TicketProvider;

import android.content.Intent;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.net.Uri;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

import java.util.List;
import java.util.UUID;

/**
 * DeviceSDK V2.1 (aisfsdkv2) 测试主界面
 * 测试 com.huawei.aisfsdkv2 包下的新接口
 *
 * v2.1 变更：
 * 1. 新增 ServerInfo 配置
 * 2. 移除 LogHook 日志回调
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvLog;
    private ScrollView scrollViewLog;
    private StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 显示编译时间
        TextView tvBuildTime = findViewById(R.id.tv_build_time);
        tvBuildTime.setText("编译时间: " + BuildConfig.BUILD_TIME);

        // 显示位置权限状态
        TextView tvLocationPermission = findViewById(R.id.tv_location_permission);
        updateLocationPermissionStatus(tvLocationPermission);
        tvLocationPermission.setOnClickListener(v -> {
            if (!isLocationPermissionGranted()) {
                requestLocationPermission();
            }
        });

        // 显示无障碍权限状态
        TextView tvAccessibilityPermission = findViewById(R.id.tv_accessibility_permission);
        updateAccessibilityPermissionStatus(tvAccessibilityPermission);
        tvAccessibilityPermission.setOnClickListener(v -> {
            // 如果无障碍权限未启用，跳转到系统设置页面
            if (!isAccessibilityEnabled()) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        });

        tvLog = findViewById(R.id.tv_log);
        scrollViewLog = findViewById(R.id.scroll_view_log);

        // 检查并请求悬浮窗权限，保证应用在前台
        if (!Settings.canDrawOverlays(this)) {
            appendLog("系统: 悬浮窗权限未开启，即将跳转设置页面");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            appendLog("系统: 悬浮窗权限已开启");
            // 启动前台服务 + 悬浮窗
            startService(new Intent(this, DemoForegroundService.class));
        }

        // 绑定测试按钮
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnLogout = findViewById(R.id.btn_logout);
        Button btnQueryLoginInfo = findViewById(R.id.btn_query_login_info);
        Button btnClearLog = findViewById(R.id.btn_clear_log);
        Button btnQa = findViewById(R.id.btn_qa);
        Button btnApiTest = findViewById(R.id.btn_api_test);
        Button btnConfig = findViewById(R.id.btn_config);

        btnLogin.setOnClickListener(v -> login());
        btnLogout.setOnClickListener(v -> logout());
        btnQueryLoginInfo.setOnClickListener(v -> queryLoginInfo());
        btnClearLog.setOnClickListener(v -> clearLog());
        btnQa.setOnClickListener(v -> {
            // 检查是否已登录
            if (!isLoggedIn()) {
                showLoginRequiredDialog();
                return;
            }
            if (!isAccessibilityEnabled()) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("无障碍权限未启用")
                        .setMessage("QA对话功能需要无障碍权限，请前往系统设置开启后再使用。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            startActivity(new Intent(this, QAActivity.class));
        });
        btnApiTest.setOnClickListener(v -> {
            // 检查是否已登录
            if (!isLoggedIn()) {
                showLoginRequiredDialog();
                return;
            }
            startActivity(new Intent(this, ApiTestActivity.class));
        });
        btnConfig.setOnClickListener(v -> startActivity(new Intent(this, ConfigActivity.class)));

        appendLog("DeviceSDK V2 Demo 已启动");
        appendLog("请点击按钮测试对应功能");
        appendLog("建议测试顺序: 登录(自动初始化SDK) -> 登出");

        // 打印当前配置信息
        printCurrentConfig();
    }

    /**
     * 打印当前配置信息
     */
    private void printCurrentConfig() {
        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);

        appendLog("\n========== 当前配置 ==========");
        appendLog("服务器环境: " + ConfigActivity.getServerEnv(prefs));
        appendLog("认证模式: " + ConfigActivity.getAuthMode(prefs));
        appendLog("用户名: " + ConfigActivity.getUsername(prefs));
        appendLog("密码: " + ConfigActivity.getPassword(prefs));
        appendLog("设备类型: " + ConfigActivity.getDeviceType(prefs));
        appendLog("==============================");
    }

    /**
     * 初始化SDK
     * 测试 AISFSDKClient.init() 接口
     *
     * v2.1 变更：
     * 1. 新增 ServerInfo 服务端信息配置
     * 2. 移除 LogHook 日志回调设置
     *
     * @return 初始化是否成功
     */
    private boolean initSDK() {
        appendLog("\n========== SDK初始化 (v2.1) ==========");

        try {
            SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);

            // 1. 创建设备信息（支持配置 deviceType）
            String deviceTypeStr = ConfigActivity.getDeviceType(prefs);
            DeviceTypeEnum deviceType = DeviceTypeEnum.CLOUD_PHONE; // 默认云手机
            try {
                deviceType = DeviceTypeEnum.valueOf(deviceTypeStr);
            } catch (IllegalArgumentException e) {
                appendLog("警告: 未知的设备类型 " + deviceTypeStr + ", 使用默认 CLOUD_PHONE");
            }

            DeviceInfo deviceInfo = new DeviceInfo();
            deviceInfo.setDeviceUuid(UUID.randomUUID().toString());
            deviceInfo.setDeviceType(deviceType);
            deviceInfo.setAppName(getPackageName());
            appendLog("设备类型: " + deviceType.getName());

            // 2. 创建日志配置
            SdkLogConfig logConfig = new SdkLogConfig();
            SdkLocalLogConfig localLogConfig = new SdkLocalLogConfig();
            localLogConfig.setLogLevel(LogLevelEnum.INFO);
            localLogConfig.setMaxLogFileSize(10 * 1024 * 1024); // 10MB
            localLogConfig.setMaxLogFileCount(5);
            logConfig.setSdkLocalLogConfig(localLogConfig);
            logConfig.setStdoutEnable(true);
            logConfig.setSecurityFilterEnable(ConfigActivity.isSecurityFilterEnabled(prefs));
            logConfig.setLogEnable(ConfigActivity.isLogEnable(prefs));

            // 3. 创建关键文件信息
            KeyFileInfo keyFileInfo = new KeyFileInfo();

            // 4. 组装客户端参数（通过 ServerInfo 设置环境和鉴权模式枚举）
            ServerEnvEnum serverEnv = ConfigActivity.getServerEnv(prefs);
            ServerAuthModeEnum authMode = ConfigActivity.getAuthMode(prefs);

            ServerInfo serverInfo = new ServerInfo();
            serverInfo.setServerEnv(serverEnv);
            serverInfo.setServerAuthMode(authMode);

            AISFClientParam param = new AISFClientParam();
            param.setAppContext(getApplicationContext());
            param.setDeviceInfo(deviceInfo);
            param.setServerInfo(serverInfo);
            param.setKeyFileInfo(keyFileInfo);
            param.setSdkLogConfig(logConfig);
            param.setSdkRoleName("AISFDEMO");

            // 5. 执行初始化
            boolean result = AISFSDKClient.getInstance().init(param);

            if (result) {
                String version = AISFSDKClient.getBaseApi().getSdkVersion();
                appendLog("✓ SDK初始化成功");
                appendLog("  SDK版本: " + version);
                appendLog("  设备UUID: " + deviceInfo.getDeviceUuid());
                appendLog("  设备类型: " + deviceInfo.getDeviceType().getName());
                appendLog("  服务器环境: " + serverEnv);
                appendLog("  鉴权模式: " + authMode);
                appendLog("  日志目录: " + AISFSDKClient.getLogApi().getLocalLogDirectory());

                // 设置TLS配置
                try {
                    TLSConfig tlsConfig = new TLSConfig();
                    if (ConfigActivity.isCaCertEnabled(prefs)) {
                        tlsConfig.setCaCertType(CaCertType.X509);
                        String caFilePath = copyAssetToCertDir("ca.crt");
                        tlsConfig.setCaFilePath(caFilePath);
                        appendLog("  TLS配置: caCertType=X509, caFilePath=" + caFilePath);
                    } else {
                        tlsConfig.setCaCertType(CaCertType.NONE);
                        appendLog("  TLS配置: caCertType=NONE");
                    }
                    if (ConfigActivity.isDeviceCertEnabled(prefs)) {
                        tlsConfig.setDeviceCertType(DeviceCertType.PEM);
                        String deviceCertPath = copyAssetToCertDir("tls.pem");
                        tlsConfig.setDeviceCertPath(deviceCertPath);
                        byte[] deviceKeyContent = readAssetAsBytes("tls.key");
                        String deviceKeyPwd = readAssetAsString("tls-key.pwd");
                        tlsConfig.setDeviceKeyContent(deviceKeyContent);
                        tlsConfig.setDeviceCertPassword(deviceKeyPwd.toCharArray());
                        appendLog("  TLS配置: deviceCertType=PEM (using deviceKeyContent)");
                        appendLog("    deviceCertPath=" + deviceCertPath);
                        appendLog("    deviceKeyContent=" + deviceKeyContent.length + " bytes");
                    } else {
                        tlsConfig.setDeviceCertType(DeviceCertType.NONE);
                        appendLog("  TLS配置: deviceCertType=NONE");
                    }
                    AISFSDKClient.getBaseApi().setTLSConfig(tlsConfig);
                } catch (Exception e) {
                    appendLog("  TLS配置设置失败: " + e.getMessage());
                }
            } else {
                appendLog("✗ SDK初始化失败 (返回false)");
            }

            return result;

        } catch (Exception e) {
            appendLog("✗ 初始化异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 功能: 登录
     * 测试 LoginApi.login() 接口（异步回调方式）
     * 自动检查并初始化SDK
     *
     * v2.1 变更：
     * 1. login() 返回 void，结果通过回调异步返回
     * 2. setLoginCb() 改为 addLoginCallback()
     * 3. LoginParam 使用 password 替代 token
     * 4. 【修复】使用 LoginParam.callback() 设置单次回调，避免多次点击导致回调堆积
     */
    private void login() {
        appendLog("\n========== 用户登录 (异步回调) ==========");

        try {
            // 步骤1: 确保SDK已初始化
            if (!ensureSDKInitialized()) {
                return;
            }

            // 步骤2: 获取登录配置
            SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
            String username = ConfigActivity.getUsername(prefs);
            String password = ConfigActivity.getPassword(prefs);
            String ticketMode = ConfigActivity.getTicketMode(prefs);

            // 步骤3: 查询并显示登录前状态
            LoginApi loginApi = AISFSDKClient.getLoginApi();
            LoginInfo infoBefore = loginApi.getLoginInfo();
            appendLog("登录前状态: " + infoBefore.getStatus());

            // 步骤4: 构建登录参数并执行登录
            appendLog("发送登录请求...");
            appendLog("  用户名: " + username);

            // 打印SDK配置信息
            appendLog("---------- SDK配置信息 ----------");
            appendLog("  服务器环境:   " + ConfigActivity.getServerEnv(prefs));
            appendLog("  鉴权模式:     " + ConfigActivity.getAuthMode(prefs));
            appendLog("  设备类型:     " + ConfigActivity.getDeviceType(prefs));
            appendLog("  Ticket认证:   " + ticketMode);
            appendLog("  CA认证:       " + (ConfigActivity.isCaCertEnabled(prefs) ? "已启用" : "未启用"));
            appendLog("  设备证书认证: " + (ConfigActivity.isDeviceCertEnabled(prefs) ? "已启用" : "未启用"));
            appendLog("  日志安全模式: " + (ConfigActivity.isSecurityFilterEnabled(prefs) ? "已启用" : "未启用"));
            appendLog("  SDK版本:      " + AISFSDKClient.getBaseApi().getSdkVersion());
            appendLog("----------------------------------");

            if (ConfigActivity.TICKET_MODE_SERVER.equals(ticketMode)) {
                // ticket-server模式: 子线程获取ticket后再登录
                appendLog("  Ticket认证: ticket-server模式，正在子线程获取ticket...");
                new Thread(() -> {
                    String ticket = fetchTicketFromServer();
                    runOnUiThread(() -> {
                        if (ticket != null && !ticket.isEmpty()) {
                            LoginParam loginParam = buildLoginParamWithTicket(username, password, ticket);
                            loginApi.login(loginParam);
                            appendLog("✓ 登录请求已发送（异步处理中）");
                        } else {
                            appendLog("✗ Ticket获取失败，登录取消");
                        }
                    });
                }).start();
            } else {
                // 其他模式: 直接在主线程构建参数并登录
                LoginParam loginParam = buildLoginParam(username, password);
                loginApi.login(loginParam);

                appendLog("✓ 登录请求已发送（异步处理中）");

                // 步骤5: 显示当前状态（异步处理中）
                LoginInfo infoAfter = loginApi.getLoginInfo();
                appendLog("当前登录状态: " + infoAfter.getStatus() + "（异步请求处理中）");
            }

        } catch (Exception e) {
            appendLog("✗ 登录异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 确保SDK已初始化
     * @return true-已初始化或初始化成功，false-初始化失败
     */
    private boolean ensureSDKInitialized() {
        // 检查SDK是否已初始化
        if (AISFSDKClient.getInstance().isInitialized()) {
            appendLog("SDK已初始化，跳过初始化步骤");
            return true;
        }

        // 未初始化，执行初始化
        appendLog("SDK未初始化，正在自动初始化...");
        boolean initResult = initSDK();

        if (initResult) {
            // 初始化后等待SDK内部状态就绪
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }

        return initResult;
    }

    /**
     * 构建登录参数
     */
    private LoginParam buildLoginParam(String username, String password) {
        LoginParam.Builder builder = new LoginParam.Builder()
                .username(username)
                .password(password)
                .timeoutMs(60000)
                .autoReconnect(true)
                .maxRetryCount(3)
                .callback(createLoginCallback());

        SharedPreferences prefs = getSharedPreferences(ConfigActivity.PREFS_NAME, MODE_PRIVATE);
        String ticketMode = ConfigActivity.getTicketMode(prefs);
        if (ConfigActivity.TICKET_MODE_SUCCESS.equals(ticketMode)) {
            String ticket = "111111" + username;
            builder.ticketProvider(() -> ticket);
            appendLog("  Ticket认证: mock成功模式 (ticket=" + ticket + ")");
        } else if (ConfigActivity.TICKET_MODE_FAIL.equals(ticketMode)) {
            builder.ticketProvider(() -> "12345678");
            appendLog("  Ticket认证: mock失败模式 (ticket=12345678)");
        } else {
            appendLog("  Ticket认证: 未启用");
        }

        return builder.build();
    }

    /**
     * 构建带ticket的登录参数（ticket-server模式专用）
     */
    private LoginParam buildLoginParamWithTicket(String username, String password, String ticket) {
        return new LoginParam.Builder()
                .username(username)
                .password(password)
                .timeoutMs(60000)
                .autoReconnect(true)
                .maxRetryCount(3)
                .callback(createLoginCallback())
                .ticketProvider(() -> ticket)
                .build();
    }

    /**
     * 创建登录回调
     */
    private LoginCallback createLoginCallback() {
        return new LoginCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> appendLog("✓ 登录成功回调"));
            }

            @Override
            public void onFailure(LoginErrorEnum error, String message) {
                runOnUiThread(() -> appendLog("✗ 登录失败回调[" + error.getCode() + "]: " + message));
            }

            @Override
            public void onConnectStatusChanged(LoginStatus oldStatus, LoginStatus newStatus) {
                runOnUiThread(() -> appendLog("  连接状态变化: " + oldStatus + " -> " + newStatus));
            }
        };
    }

    /**
     * 功能3: 登出
     * 测试 LoginApi.logout() 接口
     */
    private void logout() {
        appendLog("\n========== 功能3: 用户登出 ==========");

        try {
            LoginApi loginApi = AISFSDKClient.getLoginApi();

            // 1. 查询登出前状态
            LoginInfo infoBefore = loginApi.getLoginInfo();
            appendLog("登出前状态: " + infoBefore.getStatus());

            // 2. 执行登出
            loginApi.logout();

            appendLog("✓ 登出请求已发送");

            // 3. 查询登出后状态
            LoginInfo infoAfter = loginApi.getLoginInfo();
            appendLog("登出后状态: " + infoAfter.getStatus());

        } catch (Exception e) {
            appendLog("✗ 登出异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 功能4: 查询登录信息
     * 调用 LoginApi.getLoginInfo() 获取当前状态、用户名、设备标识
     */
    private void queryLoginInfo() {
        appendLog("\n========== 查询登录信息 ==========");

        try {
            // 检查SDK是否已初始化
            if (!AISFSDKClient.getInstance().isInitialized()) {
                appendLog("SDK未初始化，无法查询登录信息");
                return;
            }

            LoginApi loginApi = AISFSDKClient.getLoginApi();
            LoginInfo info = loginApi.getLoginInfo();

            appendLog("  登录状态: " + info.getStatus());
            appendLog("  用户名:   " + info.getUsername());
            appendLog("  设备标识: " + info.getIdentifyId());

        } catch (Exception e) {
            appendLog("✗ 查询登录信息异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从ticket-server获取ticket（使用Netty HTTP客户端）
     * @return ticket字符串，失败返回null
     */
    private String fetchTicketFromServer() {
        try {
            NettyHttpClient client = new NettyHttpClient(10, 10);

            String ticketUrl = "http://123.60.13.66:8011/aitt_test/customer/aisf/ticket";
            appendLog("  Ticket-server请求URL: " + ticketUrl);

            HttpResponseResult response = client.post(ticketUrl, "{}");

            int httpCode = response.getStatusCode();
            appendLog("  Ticket-server HTTP状态码: " + httpCode);

            String responseBody = response.getBody();
            if (responseBody != null && !responseBody.isEmpty()) {
                appendLog("  Ticket-server响应体: " + responseBody);
                JSONObject json = new JSONObject(responseBody);
                int code = json.optInt("code", -1);
                if (response.isSuccessful() && code == 200) {
                    JSONObject data = json.optJSONObject("data");
                    if (data != null) {
                        return data.optString("ticket", null);
                    }
                }
                appendLog("  Ticket-server返回错误: code=" + code);
            } else {
                appendLog("  Ticket-server响应体为空");
            }
        } catch (Exception e) {
            appendLog("  Ticket-server异常: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 将assets下的文件拷贝到应用持久化目录cert/下
     * @param assetName assets下的文件名
     * @return 拷贝后的文件绝对路径
     */
    private String copyAssetToCertDir(String assetName) throws Exception {
        java.io.File certDir = new java.io.File(getFilesDir(), "cert");
        if (!certDir.exists()) {
            certDir.mkdirs();
        }
        java.io.File outFile = new java.io.File(certDir, assetName);
        try (java.io.InputStream is = getAssets().open(assetName);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
        return outFile.getAbsolutePath();
    }

    /**
     * 读取assets下的文本文件内容
     * @param assetName assets下的文件名
     * @return 文件内容字符串
     */
    private String readAssetAsString(String assetName) throws Exception {
        try (java.io.InputStream is = getAssets().open(assetName);
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * 读取assets下的文件内容为字节数组
     *
     * <p>用于读取私钥等二进制文件内容，供SDK的TLSConfig.setDeviceKeyContent()直接使用，
     * 无需复制文件到持久化目录，减少磁盘IO和安全风险</p>
     *
     * @param assetName assets下的文件名
     * @return 文件内容字节数组
     */
    private byte[] readAssetAsBytes(String assetName) throws Exception {
        try (java.io.InputStream is = getAssets().open(assetName)) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /**
     * 日志输出工具方法
     */
    private void appendLog(String message) {
        logBuilder.append(message).append("\n");
        runOnUiThread(() -> {
            tvLog.setText(logBuilder.toString());
            scrollViewLog.post(() -> scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    /**
     * 清屏
     */
    private void clearLog() {
        logBuilder.setLength(0);
        logBuilder.append("日志输出:\n");
        tvLog.setText(logBuilder.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新权限状态显示
        TextView tvAccessibilityPermission = findViewById(R.id.tv_accessibility_permission);
        updateAccessibilityPermissionStatus(tvAccessibilityPermission);

        TextView tvLocationPermission = findViewById(R.id.tv_location_permission);
        updateLocationPermissionStatus(tvLocationPermission);
    }

    /**
     * 检查位置权限是否已授权（精确或粗略均视为已授权）
     */
    private boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static final int REQUEST_LOCATION_PERMISSION = 100;

    /**
     * 请求位置权限：
     * - 可弹窗时直接调系统弹窗
     * - 被永久拒绝时跳转应用设置页
     */
    private void requestLocationPermission() {
        boolean neverAskFine = !ActivityCompat.shouldShowRequestPermissionRationale(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        boolean neverAskCoarse = !ActivityCompat.shouldShowRequestPermissionRationale(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);

        // 首次请求时 shouldShowRequestPermissionRationale 也返回 false，
        // 通过检查是否曾经请求过来区分"首次"和"永久拒绝"
        SharedPreferences prefs = getSharedPreferences("permission_prefs", MODE_PRIVATE);
        boolean hasRequested = prefs.getBoolean("location_requested", false);

        if (hasRequested && neverAskFine && neverAskCoarse) {
            // 用户已永久拒绝，跳转应用设置页
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("位置权限被禁止")
                    .setMessage("位置权限已被永久拒绝，请前往应用设置手动开启。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            // 记录已请求过
            prefs.edit().putBoolean("location_requested", true).apply();
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            TextView tvLocationPermission = findViewById(R.id.tv_location_permission);
            updateLocationPermissionStatus(tvLocationPermission);
        }
    }

    /**
     * 更新位置权限状态显示
     */
    private void updateLocationPermissionStatus(TextView tv) {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFineLocation && hasCoarseLocation) {
            tv.setText("位置权限: 已授权 ✓");
            tv.setTextColor(0xFF4CAF50); // 绿色
        } else if (hasCoarseLocation) {
            tv.setText("位置权限: 仅粗略位置 ✓");
            tv.setTextColor(0xFFFF9800); // 橙色
        } else {
            tv.setText("位置权限: 未授权 ✗ (点击授权)");
            tv.setTextColor(0xFFF44336); // 红色
        }
    }

    /**
     * 更新无障碍权限状态显示
     */
    private void updateAccessibilityPermissionStatus(TextView tv) {
        boolean isEnabled = isAccessibilityEnabled();

        if (isEnabled) {
            tv.setText("无障碍权限: 已启用 ✓");
            tv.setTextColor(0xFF4CAF50); // 绿色
        } else {
            tv.setText("无障碍权限: 未启用 ✗ (点击跳转设置)");
            tv.setTextColor(0xFFF44336); // 红色
        }
    }

    // 录屏/MediaProjection 已禁用：TestApp 改用无障碍/screencap，Demo 不再申请录屏权限。

    /**
     * 检查本APP的无障碍服务是否启用
     */
    private boolean isAccessibilityEnabled() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibilityManager == null) return false;

        List<AccessibilityServiceInfo> enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices == null) return false;

        for (AccessibilityServiceInfo info : enabledServices) {
            if (info.getId().startsWith(getPackageName() + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查用户是否已登录
     * @return true-已登录（状态为CONNECTED），false-未登录
     */
    private boolean isLoggedIn() {
        try {
            // 检查SDK是否已初始化
            if (!AISFSDKClient.getInstance().isInitialized()) {
                return false;
            }
            LoginApi loginApi = AISFSDKClient.getLoginApi();
            LoginInfo info = loginApi.getLoginInfo();
            return info.getStatus() == LoginStatus.CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 显示需要登录的提示弹窗
     */
    private void showLoginRequiredDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("需要登录")
                .setMessage("该功能需要登录后才能使用，请先点击登录按钮完成登录。")
                .setPositiveButton("知道了", null)
                .show();
    }
}
