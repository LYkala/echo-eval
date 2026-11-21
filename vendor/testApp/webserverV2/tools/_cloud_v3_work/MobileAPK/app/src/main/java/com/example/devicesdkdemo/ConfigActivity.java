package com.example.devicesdkdemo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerEnvEnum;

/**
 * 配置页面 - 新版SDK简化配置
 * 使用 ServerEnvEnum 和 ServerAuthModeEnum 枚举
 */
public class ConfigActivity extends AppCompatActivity {

    static final String PREFS_NAME = "sdk_config";
    /** 从测试页再次进入配置时带上，保存后返回而非新开 Main。 */
    public static final String EXTRA_REEDIT = "reedit";
    static final String KEY_SERVER_ENV = "server_env";
    static final String KEY_AUTH_MODE = "auth_mode";
    static final String KEY_USERNAME = "username";
    static final String KEY_PASSWORD = "password";
    static final String KEY_DEVICE_TYPE = "device_type";
    static final String KEY_TICKET_ENABLED = "ticket_enabled";
    static final String KEY_TICKET_MODE = "ticket_mode";
    static final String KEY_SECURITY_FILTER_ENABLED = "security_filter_enabled";
    static final String KEY_LOG_ENABLE = "log_enable";
    static final String KEY_FOCUS_MODE_ENABLED = "focus_mode_enabled";
    static final String KEY_PURE_MODE_ENABLED = "pure_mode_enabled";
    static final String KEY_CA_CERT_ENABLED = "ca_cert_enabled";
    static final String KEY_DEVICE_CERT_ENABLED = "device_cert_enabled";
    static final String KEY_GUI_CONFIRM_ENABLED = "gui_confirm_enabled";
    static final String KEY_UNATTENDED_ENABLED = "unattended_enabled";
    // WebServer 直报 keys 与 WebServerReporter 共用字面量
    static final String KEY_WEBSERVER_URL = WebServerReporter.KEY_WEBSERVER_URL;
    static final String KEY_WEBSERVER_ENABLED = WebServerReporter.KEY_WEBSERVER_ENABLED;
    static final String DEFAULT_WEBSERVER_URL = "http://124.71.69.102:9526";
    private static final String KEY_RELAY_VIA_TESTAPP = "relay_via_testapp_v1";

    // 环境列表（与 ServerEnvEnum 枚举名一一对应，不含 CUSTOM_ENV）
    private static final String[] ENV_LIST = {
            "AISF02_INTERNAL", "AISF03_INTERNAL", "AISF04_INTERNAL", "AISF05_INTERNAL",
            "AISF06_INTERNAL", "AISF07_INTERNAL", "AISF08_INTERNAL", "AISF09_INTERNAL",
            "AISF10_INTERNAL", "AISF11_INTERNAL", "AISF14_INTERNAL",
            "AISF02_EXTERNAL", "AISF03_EXTERNAL", "AISF04_EXTERNAL", "AISF05_EXTERNAL",
            "AISF06_EXTERNAL", "AISF07_EXTERNAL", "AISF08_EXTERNAL", "AISF09_EXTERNAL",
            "AISF10_EXTERNAL", "AISF14_EXTERNAL"
    };

    // 认证模式列表
    private static final String[] AUTH_MODE_LIST = {"ACM", "APIGW"};

    // DeviceType 可选列表
    private static final String[] DEVICE_TYPE_LIST = {"CLOUD_PHONE", "PHY_PHONE", "COMPUTING_HOST", "CLOUD_PAD", "DX_PHY_PHONE"};

    // Ticket认证模式列表
    static final String[] TICKET_MODE_LIST = {"NONE", "mock: 成功", "mock: 失败", "mock: ticket-server"};
    static final String TICKET_MODE_NONE = "NONE";
    static final String TICKET_MODE_SUCCESS = "mock: 成功";
    static final String TICKET_MODE_FAIL = "mock: 失败";
    static final String TICKET_MODE_SERVER = "mock: ticket-server";

    private Spinner spinnerEnv;
    private Spinner spinnerAuthMode;
    private Spinner spinnerDeviceType;
    private Spinner spinnerTicket;
    private EditText etUsername, etPassword;
    private SwitchCompat switchCaCert;
    private SwitchCompat switchDeviceCert;
    private SwitchCompat switchGuiConfirm;
    private SwitchCompat switchUnattended;
    private SwitchCompat switchWebserverReport;
    private EditText etWebserverUrl;
    private LinearLayout contentServerConfig;
    private TextView titleServerConfig;
    private LinearLayout contentClientConfig;
    private TextView titleClientConfig;
    private LinearLayout contentLoginConfig;
    private TextView titleLoginConfig;
    private LinearLayout contentOtherConfig;
    private TextView titleOtherConfig;
    private SharedPreferences prefs;
    private boolean reeditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureWebServerDefaults(prefs);
        reeditMode = getIntent().getBooleanExtra(EXTRA_REEDIT, false);

        initViews();
        setupSpinners();
        setupCollapsibleSections();
        loadConfig();

        // 检查是否有崩溃日志（关掉即清除，避免每次进配置页反复弹）
        String crashLog = CrashHandler.readCrashLog(this);
        if (crashLog != null) {
            String displayLog = crashLog.length() > 3000 ? crashLog.substring(crashLog.length() - 3000) : crashLog;
            android.widget.EditText etCrash = new android.widget.EditText(this);
            etCrash.setText(displayLog);
            etCrash.setTextSize(11);
            etCrash.setTextColor(0xFFCC0000);
            etCrash.setBackgroundColor(0xFFF5F5F5);
            etCrash.setPadding(24, 24, 24, 24);
            etCrash.setFocusable(false);
            etCrash.setTextIsSelectable(true);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(etCrash);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("上次崩溃日志（长按可复制）")
                    .setView(sv)
                    .setPositiveButton("清除并关闭", (d, w) -> CrashHandler.clearCrashLog(this))
                    .setNegativeButton("关闭", (d, w) -> CrashHandler.clearCrashLog(this))
                    .setOnCancelListener(d -> CrashHandler.clearCrashLog(this))
                    .show();
        }

        // 配置页始终可再次编辑；从测试页进入时保存后返回
        Button btnConfirm = findViewById(R.id.btn_confirm);
        if (reeditMode) {
            btnConfirm.setText("保存配置");
        } else {
            boolean sdkReady = AISFSDKClient.getInstance().isInitialized();
            btnConfirm.setText(sdkReady ? "保存配置，进入测试页面" : "确认配置，进入测试页面");
        }
        setViewsEnabled(true);
        btnConfirm.setOnClickListener(v -> saveConfig());
    }

    /** 默认关闭 Demo 直连 WS；回复经 TestApp TestReply 中转。 */
    static void ensureWebServerDefaults(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        boolean changed = false;
        // 一次性迁移：关掉直连 WebServer，改走 TestApp 中转
        if (!prefs.getBoolean(KEY_RELAY_VIA_TESTAPP, false)) {
            ed.putBoolean(KEY_WEBSERVER_ENABLED, false);
            ed.putBoolean(KEY_RELAY_VIA_TESTAPP, true);
            changed = true;
        }
        if (!prefs.contains(KEY_WEBSERVER_ENABLED)) {
            ed.putBoolean(KEY_WEBSERVER_ENABLED, false);
            changed = true;
        }
        String url = prefs.getString(KEY_WEBSERVER_URL, "");
        if (url == null || url.trim().isEmpty()) {
            ed.putString(KEY_WEBSERVER_URL, DEFAULT_WEBSERVER_URL);
            changed = true;
        }
        if (changed) {
            ed.apply();
        }
    }

    static boolean isWebServerReportEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_WEBSERVER_ENABLED, false);
    }

    static String getWebServerUrl(SharedPreferences prefs) {
        String url = prefs.getString(KEY_WEBSERVER_URL, DEFAULT_WEBSERVER_URL);
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_WEBSERVER_URL;
        }
        return url.trim();
    }

    private void initViews() {
        spinnerEnv = findViewById(R.id.spinner_env);
        spinnerAuthMode = findViewById(R.id.spinner_auth_mode);
        spinnerDeviceType = findViewById(R.id.spinner_device_type);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        spinnerTicket = findViewById(R.id.spinner_ticket);
        switchCaCert = findViewById(R.id.switch_ca_cert);
        switchDeviceCert = findViewById(R.id.switch_device_cert);
        switchGuiConfirm = findViewById(R.id.switch_gui_confirm);
        switchUnattended = findViewById(R.id.switch_unattended);
        switchWebserverReport = findViewById(R.id.switch_webserver_report);
        etWebserverUrl = findViewById(R.id.et_webserver_url);

        // 密码可见切换按钮：按下显示，松开隐藏
        TextView btnShowPassword = findViewById(R.id.btn_show_password);
        btnShowPassword.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    break;
            }
            return true;
        });
        contentServerConfig = findViewById(R.id.content_server_config);
        titleServerConfig = findViewById(R.id.title_server_config);
        contentClientConfig = findViewById(R.id.content_client_config);
        titleClientConfig = findViewById(R.id.title_client_config);
        contentLoginConfig = findViewById(R.id.content_login_config);
        titleLoginConfig = findViewById(R.id.title_login_config);
        contentOtherConfig = findViewById(R.id.content_other_config);
        titleOtherConfig = findViewById(R.id.title_other_config);
    }

    private void setupSpinners() {
        // 环境选择
        ArrayAdapter<String> envAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, ENV_LIST);
        envAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEnv.setAdapter(envAdapter);

        // 认证模式选择
        ArrayAdapter<String> authAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, AUTH_MODE_LIST);
        authAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAuthMode.setAdapter(authAdapter);

        // 设备类型选择
        ArrayAdapter<String> deviceAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, DEVICE_TYPE_LIST);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceType.setAdapter(deviceAdapter);

        // Ticket认证模式选择
        ArrayAdapter<String> ticketAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, TICKET_MODE_LIST);
        ticketAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTicket.setAdapter(ticketAdapter);
    }

    private void loadConfig() {
        // 默认对齐实机常用配置：AISF05 + 双证书 + Ticket NONE + 关 GUI 确认
        String env = prefs.getString(KEY_SERVER_ENV, "AISF05_INTERNAL");
        String authMode = prefs.getString(KEY_AUTH_MODE, "APIGW");
        String username = prefs.getString(KEY_USERNAME, "test_user");
        String password = prefs.getString(KEY_PASSWORD, "test_password_12345");
        String deviceType = prefs.getString(KEY_DEVICE_TYPE, "CLOUD_PHONE");

        // 设置环境
        for (int i = 0; i < ENV_LIST.length; i++) {
            if (ENV_LIST[i].equals(env)) {
                spinnerEnv.setSelection(i);
                break;
            }
        }

        // 设置认证模式
        for (int i = 0; i < AUTH_MODE_LIST.length; i++) {
            if (AUTH_MODE_LIST[i].equals(authMode)) {
                spinnerAuthMode.setSelection(i);
                break;
            }
        }

        // 设置设备类型
        for (int i = 0; i < DEVICE_TYPE_LIST.length; i++) {
            if (DEVICE_TYPE_LIST[i].equals(deviceType)) {
                spinnerDeviceType.setSelection(i);
                break;
            }
        }

        // 设置Ticket认证模式
        String ticketMode = prefs.getString(KEY_TICKET_MODE, TICKET_MODE_NONE);
        for (int i = 0; i < TICKET_MODE_LIST.length; i++) {
            if (TICKET_MODE_LIST[i].equals(ticketMode)) {
                spinnerTicket.setSelection(i);
                break;
            }
        }

        etUsername.setText(username);
        etPassword.setText(password);
        switchCaCert.setChecked(prefs.getBoolean(KEY_CA_CERT_ENABLED, true));
        switchDeviceCert.setChecked(prefs.getBoolean(KEY_DEVICE_CERT_ENABLED, true));
        switchGuiConfirm.setChecked(prefs.getBoolean(KEY_GUI_CONFIRM_ENABLED, false));
        switchUnattended.setChecked(prefs.getBoolean(KEY_UNATTENDED_ENABLED, true));
        switchWebserverReport.setChecked(prefs.getBoolean(KEY_WEBSERVER_ENABLED, false));
        etWebserverUrl.setText(getWebServerUrl(prefs));
    }

    private void setupCollapsibleSections() {
        // 服务器配置折叠（默认展开）
        titleServerConfig.setOnClickListener(v -> toggleSection(contentServerConfig, titleServerConfig, "服务器配置"));

        // 客户端配置折叠（默认展开）
        titleClientConfig.setOnClickListener(v -> toggleSection(contentClientConfig, titleClientConfig, "客户端配置"));

        // 登录配置折叠（默认展开）
        titleLoginConfig.setOnClickListener(v -> toggleSection(contentLoginConfig, titleLoginConfig, "用户配置"));

        // 其他配置折叠（默认展开）
        titleOtherConfig.setOnClickListener(v -> toggleSection(contentOtherConfig, titleOtherConfig, "其他配置"));
    }

    private void toggleSection(LinearLayout contentView, TextView titleView, String title) {
        if (contentView.getVisibility() == View.VISIBLE) {
            contentView.setVisibility(View.GONE);
            titleView.setText("▶ " + title);
        } else {
            contentView.setVisibility(View.VISIBLE);
            titleView.setText("▼ " + title);
        }
    }

    /**
     * 设置所有输入控件的启用/禁用状态
     */
    private void setViewsEnabled(boolean enabled) {
        spinnerEnv.setEnabled(enabled);
        spinnerAuthMode.setEnabled(enabled);
        spinnerDeviceType.setEnabled(enabled);
        spinnerTicket.setEnabled(enabled);
        etUsername.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        switchCaCert.setEnabled(enabled);
        switchDeviceCert.setEnabled(enabled);
        switchGuiConfirm.setEnabled(enabled);
        switchUnattended.setEnabled(enabled);
        switchWebserverReport.setEnabled(enabled);
        etWebserverUrl.setEnabled(enabled);
    }

    private void saveConfig() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请填写用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        String env = spinnerEnv.getSelectedItem().toString();
        String authMode = spinnerAuthMode.getSelectedItem().toString();
        String deviceType = spinnerDeviceType.getSelectedItem().toString();
        String ticketMode = spinnerTicket.getSelectedItem().toString();

        prefs.edit()
                .putString(KEY_SERVER_ENV, env)
                .putString(KEY_AUTH_MODE, authMode)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_DEVICE_TYPE, deviceType)
                .putString(KEY_TICKET_MODE, ticketMode)
                .putBoolean(KEY_CA_CERT_ENABLED, switchCaCert.isChecked())
                .putBoolean(KEY_DEVICE_CERT_ENABLED, switchDeviceCert.isChecked())
                .putBoolean(KEY_GUI_CONFIRM_ENABLED, switchGuiConfirm.isChecked())
                .putBoolean(KEY_UNATTENDED_ENABLED, switchUnattended.isChecked())
                .putBoolean(KEY_WEBSERVER_ENABLED, switchWebserverReport.isChecked())
                .putString(KEY_WEBSERVER_URL, etWebserverUrl.getText().toString().trim())
                .apply();

        syncUnattendedToSdk(switchUnattended.isChecked());
        WebServerWsClient.refreshFromPrefs(this);

        Toast.makeText(this, "配置已保存 [" + env + "] 认证: " + authMode + " 设备: " + deviceType, Toast.LENGTH_SHORT).show();

        if (reeditMode) {
            // 从测试页再次编辑：保存后直接返回，保持可反复改
            finish();
            return;
        }
        // 冷启动确认：进入测试页
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // 供其他 Activity 获取配置
    public static ServerEnvEnum getServerEnv(SharedPreferences prefs) {
        String env = prefs.getString(KEY_SERVER_ENV, "AISF05_INTERNAL");
        try {
            return ServerEnvEnum.valueOf(env);
        } catch (IllegalArgumentException e) {
            return ServerEnvEnum.AISF05_INTERNAL;
        }
    }

    public static ServerAuthModeEnum getAuthMode(SharedPreferences prefs) {
        String mode = prefs.getString(KEY_AUTH_MODE, "APIGW");
        try {
            return ServerAuthModeEnum.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return ServerAuthModeEnum.APIGW;
        }
    }

    public static String getUsername(SharedPreferences prefs) {
        return prefs.getString(KEY_USERNAME, "test_user");
    }

    public static String getPassword(SharedPreferences prefs) {
        return prefs.getString(KEY_PASSWORD, "test_password_12345");
    }

    public static String getDeviceType(SharedPreferences prefs) {
        return prefs.getString(KEY_DEVICE_TYPE, "CLOUD_PHONE");
    }

    public static String getTicketMode(SharedPreferences prefs) {
        return prefs.getString(KEY_TICKET_MODE, TICKET_MODE_NONE);
    }

    public static boolean isSecurityFilterEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_SECURITY_FILTER_ENABLED, true);
    }

    public static boolean isLogEnable(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_LOG_ENABLE, false);
    }

    public static boolean isFocusModeEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_FOCUS_MODE_ENABLED, false);
    }

    /** 纯净版：聊天气泡样式，仅显示我与服务端及停止类系统消息。默认开启。 */
    public static boolean isPureModeEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_PURE_MODE_ENABLED, true);
    }

    public static boolean isCaCertEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_CA_CERT_ENABLED, true);
    }

    public static boolean isDeviceCertEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_DEVICE_CERT_ENABLED, true);
    }

    public static boolean isGuiConfirmEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_GUI_CONFIRM_ENABLED, false);
    }

    public static boolean isUnattendedEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_UNATTENDED_ENABLED, true);
    }

    /** 同步无人值守开关到 AISF SDK（拉起 App 确认） */
    public static void syncUnattendedToSdk(boolean enabled) {
        try {
            com.huawei.aisfsdkv2.component.androidfunc.app.impl.LaunchAppConfirmManager
                    .getInstance().setUnattendedMode(enabled);
        } catch (Throwable t) {
            // SDK 未初始化时忽略
        }
    }
}
