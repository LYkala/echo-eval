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
    static final String KEY_CA_CERT_ENABLED = "ca_cert_enabled";
    static final String KEY_DEVICE_CERT_ENABLED = "device_cert_enabled";
    static final String KEY_GUI_CONFIRM_ENABLED = "gui_confirm_enabled";
    /** 无人值守：自动确认 GUI_TASK / AISF DIALOG，避免人工点「确认」 */
    static final String KEY_AUTO_CONFIRM_ENABLED = "auto_confirm_enabled";
    static final String KEY_WS_URL = "ws_url";
    static final String KEY_AUTH_URL = "auth_url";
    static final String DEFAULT_WS_URL = "wss://121.37.53.201:40098/ws/acm/v1/ai";
    /** 鉴权路径是 /acm/v1/auth（不要带 /ws/） */
    static final String DEFAULT_AUTH_URL = "https://121.37.53.201:40098/acm/v1/auth";

    // 环境列表：CUSTOM_ENV 置顶，默认选中
    private static final String[] ENV_LIST = {
            "CUSTOM_ENV",
            "AISF02_INTERNAL", "AISF03_INTERNAL", "AISF04_INTERNAL", "AISF05_INTERNAL",
            "AISF06_INTERNAL", "AISF07_INTERNAL", "AISF08_INTERNAL", "AISF09_INTERNAL",
            "AISF10_INTERNAL", "AISF11_INTERNAL",
            "AISF02_EXTERNAL", "AISF03_EXTERNAL", "AISF04_EXTERNAL", "AISF05_EXTERNAL",
            "AISF06_EXTERNAL", "AISF07_EXTERNAL", "AISF08_EXTERNAL", "AISF09_EXTERNAL",
            "AISF10_EXTERNAL"
    };

    // 认证模式列表
    private static final String[] AUTH_MODE_LIST = {"ACM", "APIGW"};

    // DeviceType 可选列表
    /** 模拟器/本机无障碍 GUI 用 PHY_PHONE；云手机远程操控才用 CLOUD_PHONE */
    private static final String[] DEVICE_TYPE_LIST = {"PHY_PHONE", "CLOUD_PHONE", "COMPUTING_HOST", "CLOUD_PAD", "DX_PHY_PHONE"};

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
    private EditText etWsUrl, etAuthUrl;
    private SwitchCompat switchCaCert;
    private SwitchCompat switchDeviceCert;
    private SwitchCompat switchGuiConfirm;
    private SwitchCompat switchAutoConfirm;
    private LinearLayout contentServerConfig;
    private TextView titleServerConfig;
    private LinearLayout contentClientConfig;
    private TextView titleClientConfig;
    private LinearLayout contentLoginConfig;
    private TextView titleLoginConfig;
    private LinearLayout contentOtherConfig;
    private TextView titleOtherConfig;
    private SharedPreferences prefs;
    private boolean isConfigured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ensureDefaultPrefs(prefs);

        initViews();
        setupSpinners();
        setupCollapsibleSections();
        loadConfig();

        // 检查是否有崩溃日志
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
                    .setNegativeButton("关闭", null)
                    .show();
        }

        // SDK 已初始化时仍允许改配置并保存（以前会锁死控件，只能第一次配）
        isConfigured = AISFSDKClient.getInstance().isInitialized();
        Button btnConfirm = findViewById(R.id.btn_confirm);
        setViewsEnabled(true);
        btnConfirm.setText(isConfigured ? "保存配置，进到测试页面" : "确认配置，进到测试页面");
        btnConfirm.setOnClickListener(v -> saveConfig());
    }

    private void initViews() {
        spinnerEnv = findViewById(R.id.spinner_env);
        spinnerAuthMode = findViewById(R.id.spinner_auth_mode);
        spinnerDeviceType = findViewById(R.id.spinner_device_type);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etWsUrl = findViewById(R.id.et_ws_url);
        etAuthUrl = findViewById(R.id.et_auth_url);
        spinnerTicket = findViewById(R.id.spinner_ticket);
        switchCaCert = findViewById(R.id.switch_ca_cert);
        switchDeviceCert = findViewById(R.id.switch_device_cert);
        switchGuiConfirm = findViewById(R.id.switch_gui_confirm);
        switchAutoConfirm = findViewById(R.id.switch_auto_confirm);

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
        // 加载保存的配置；默认 CUSTOM_ENV + ACM
        String env = prefs.getString(KEY_SERVER_ENV, "CUSTOM_ENV");
        String authMode = prefs.getString(KEY_AUTH_MODE, "ACM");
        String username = prefs.getString(KEY_USERNAME, "test_user");
        String password = prefs.getString(KEY_PASSWORD, "test_password_12345");
        String deviceType = prefs.getString(KEY_DEVICE_TYPE, "PHY_PHONE");

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
        String ticketMode = prefs.getString(KEY_TICKET_MODE, TICKET_MODE_SUCCESS);
        for (int i = 0; i < TICKET_MODE_LIST.length; i++) {
            if (TICKET_MODE_LIST[i].equals(ticketMode)) {
                spinnerTicket.setSelection(i);
                break;
            }
        }

        etUsername.setText(username);
        etPassword.setText(password);
        if (etWsUrl != null) {
            etWsUrl.setText(getWsUrl(prefs));
        }
        if (etAuthUrl != null) {
            etAuthUrl.setText(getAuthUrl(prefs));
        }
        switchCaCert.setChecked(prefs.getBoolean(KEY_CA_CERT_ENABLED, true));
        switchDeviceCert.setChecked(prefs.getBoolean(KEY_DEVICE_CERT_ENABLED, true));
        switchGuiConfirm.setChecked(prefs.getBoolean(KEY_GUI_CONFIRM_ENABLED, false));
        switchAutoConfirm.setChecked(prefs.getBoolean(KEY_AUTO_CONFIRM_ENABLED, true));
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
        if (etWsUrl != null) {
            etWsUrl.setEnabled(enabled);
        }
        if (etAuthUrl != null) {
            etAuthUrl.setEnabled(enabled);
        }
        switchCaCert.setEnabled(enabled);
        switchDeviceCert.setEnabled(enabled);
        switchGuiConfirm.setEnabled(enabled);
        if (switchAutoConfirm != null) {
            switchAutoConfirm.setEnabled(enabled);
        }
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

        String wsUrl = etWsUrl != null ? etWsUrl.getText().toString().trim() : "";
        String authUrl = etAuthUrl != null ? etAuthUrl.getText().toString().trim() : "";
        if ("CUSTOM_ENV".equals(env) && (TextUtils.isEmpty(wsUrl) || TextUtils.isEmpty(authUrl))) {
            Toast.makeText(this, "CUSTOM_ENV 请填写 WS URL 和鉴权 URL", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString(KEY_SERVER_ENV, env)
                .putString(KEY_AUTH_MODE, authMode)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_DEVICE_TYPE, deviceType)
                .putString(KEY_TICKET_MODE, ticketMode)
                .putString(KEY_WS_URL, wsUrl)
                .putString(KEY_AUTH_URL, authUrl)
                .putBoolean(KEY_CA_CERT_ENABLED, switchCaCert.isChecked())
                .putBoolean(KEY_DEVICE_CERT_ENABLED, switchDeviceCert.isChecked())
                .putBoolean(KEY_GUI_CONFIRM_ENABLED, switchGuiConfirm.isChecked())
                .putBoolean(KEY_AUTO_CONFIRM_ENABLED,
                        switchAutoConfirm != null && switchAutoConfirm.isChecked())
                .apply();

        Toast.makeText(this, "配置已保存 [" + env + "] 认证: " + authMode + " 设备: " + deviceType, Toast.LENGTH_SHORT).show();

        // 跳转到测试页面
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // 供其他 Activity 获取配置
    public static ServerEnvEnum getServerEnv(SharedPreferences prefs) {
        String env = prefs.getString(KEY_SERVER_ENV, "CUSTOM_ENV");
        try {
            return ServerEnvEnum.valueOf(env);
        } catch (IllegalArgumentException e) {
            // CUSTOM_ENV 等非枚举值：调用方走自定义 URL
            return ServerEnvEnum.AISF02_INTERNAL;
        }
    }

    public static ServerAuthModeEnum getAuthMode(SharedPreferences prefs) {
        String mode = prefs.getString(KEY_AUTH_MODE, "ACM");
        try {
            return ServerAuthModeEnum.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return ServerAuthModeEnum.ACM;
        }
    }

    public static String getUsername(SharedPreferences prefs) {
        return prefs.getString(KEY_USERNAME, "test_user");
    }

    public static String getPassword(SharedPreferences prefs) {
        return prefs.getString(KEY_PASSWORD, "test_password_12345");
    }

    public static String getWsUrl(SharedPreferences prefs) {
        return prefs.getString(KEY_WS_URL, DEFAULT_WS_URL);
    }

    public static String getAuthUrl(SharedPreferences prefs) {
        return prefs.getString(KEY_AUTH_URL, DEFAULT_AUTH_URL);
    }

    public static String getDeviceType(SharedPreferences prefs) {
        return prefs.getString(KEY_DEVICE_TYPE, "PHY_PHONE");
    }

    public static String getTicketMode(SharedPreferences prefs) {
        return prefs.getString(KEY_TICKET_MODE, TICKET_MODE_SUCCESS);
    }

    /**
     * 首次安装写入内置默认项：CUSTOM_ENV + ACM + 实验室 WS/鉴权 URL。
     */
    public static void ensureDefaultPrefs(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!prefs.contains(KEY_FOCUS_MODE_ENABLED)) {
            editor = prefs.edit();
            editor.putBoolean(KEY_FOCUS_MODE_ENABLED, true);
        }
        if (!prefs.contains(KEY_GUI_CONFIRM_ENABLED)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(KEY_GUI_CONFIRM_ENABLED, false);
        }
        if (!prefs.contains(KEY_DEVICE_TYPE)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(KEY_DEVICE_TYPE, "PHY_PHONE");
        }
        if (!prefs.contains(KEY_SERVER_ENV)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(KEY_SERVER_ENV, "CUSTOM_ENV");
        }
        if (!prefs.contains(KEY_AUTH_MODE)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(KEY_AUTH_MODE, "ACM");
        }
        if (!prefs.contains(KEY_WS_URL)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(KEY_WS_URL, DEFAULT_WS_URL);
        }
        if (!prefs.contains(KEY_AUTH_URL)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(KEY_AUTH_URL, DEFAULT_AUTH_URL);
        }
        if (!prefs.contains(KEY_DEVICE_CERT_ENABLED)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(KEY_DEVICE_CERT_ENABLED, true);
        }
        if (!prefs.contains(KEY_CA_CERT_ENABLED)) {
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putBoolean(KEY_CA_CERT_ENABLED, true);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    public static boolean isSecurityFilterEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_SECURITY_FILTER_ENABLED, true);
    }

    public static boolean isLogEnable(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_LOG_ENABLE, false);
    }

    public static boolean isFocusModeEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_FOCUS_MODE_ENABLED, true);
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

    public static boolean isAutoConfirmEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_AUTO_CONFIRM_ENABLED, true);
    }
}
