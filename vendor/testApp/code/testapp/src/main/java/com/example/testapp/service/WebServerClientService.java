package com.example.testapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.testapp.MainActivity;
import com.example.testapp.R;
import com.example.testapp.model.WebServerConfig;
import com.example.testapp.util.Logger;
import com.example.testapp.util.WebServerClient;

import java.io.File;
import java.util.List;

/**
 * WebServer客户端服务
 * 后台服务，使用WebSocket连接WebServer接收测试文件
 */
public class WebServerClientService extends Service {

    private static final String TAG = "WebServerClientService";

    /** 通知渠道ID */
    private static final String CHANNEL_ID = "webserver_client_channel";

    /** 通知ID */
    private static final int NOTIFICATION_ID = 2001;

    /** 配置文件名 */
    private static final String PREFS_NAME = "webserver_config";

    /** 广播Action */
    public static final String ACTION_CONNECTION_STATUS = "com.example.testapp.CONNECTION_STATUS";
    public static final String ACTION_FILES_RECEIVED = "com.example.testapp.FILES_RECEIVED";
    public static final String ACTION_FILE_DELETED = "com.example.testapp.FILE_DELETED";
    public static final String ACTION_FILES_REFRESH = "com.example.testapp.FILES_REFRESH";
    public static final String ACTION_EXECUTE_TEST = "com.example.testapp.EXECUTE_TEST";

    /** Extra keys */
    public static final String EXTRA_CONNECTED = "connected";
    public static final String EXTRA_FILE_COUNT = "fileCount";
    public static final String EXTRA_SERVER_URL = "serverUrl";
    public static final String EXTRA_FILENAME = "filename";
    public static final String EXTRA_SUCCESS = "success";

    /** 服务Action */
    public static final String ACTION_START = "com.example.testapp.START_WEBSERVER_CLIENT";
    public static final String ACTION_STOP = "com.example.testapp.STOP_WEBSERVER_CLIENT";
    public static final String ACTION_RECONNECT = "com.example.testapp.RECONNECT_WEBSERVER_CLIENT";

    /** 日志记录器 */
    private Logger logger;

    /** WebServer配置 */
    private WebServerConfig config;

    /** WebServer客户端 */
    private WebServerClient client;

    /** 心跳后台线程 */
    private HandlerThread heartbeatThread;

    /** 心跳Handler */
    private Handler heartbeatHandler;

    /** 心跳Runnable */
    private Runnable heartbeatRunnable;

    /** 重连Handler */
    private Handler reconnectHandler;

    /** 重连Runnable */
    private Runnable reconnectRunnable;

    /** 是否已连接 */
    private boolean isConnected = false;

    /** 服务是否运行 */
    private boolean isRunning = false;

    /** 重连次数 */
    private int reconnectCount = 0;

    /** 最大重连次数 */
    private static final int MAX_RECONNECT_COUNT = -1; // -1 表示无限重连

    /** 重连间隔（毫秒） */
    private static final long RECONNECT_INTERVAL = 2000; // 2秒

    /** 心跳间隔（毫秒） */
    private static final long HEARTBEAT_INTERVAL = 10000; // 10秒

    @Override
    public void onCreate() {
        super.onCreate();
        logger = Logger.getInstance(this);
        logger.info(TAG, "Service onCreate");

        // 初始化配置
        loadConfig();

        // 创建通知渠道
        createNotificationChannel();

        reconnectHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START:
                    startClient();
                    break;
                case ACTION_STOP:
                    stopClient();
                    stopSelf();
                    break;
                case ACTION_RECONNECT:
                    reconnectClient();
                    break;
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.info(TAG, "Service onDestroy");
        stopClient();
    }

    /**
     * 启动客户端
     */
    private void startClient() {
        if (isRunning) {
            logger.info(TAG, "Client already running");
            if (client != null && client.isConnected()) {
                isConnected = true;
                updateConnectionStatus(true);
                updateNotification("Connected to " + config.getServerUrl());
            }
            return;
        }

        logger.info(TAG, "Starting WebServer client with WebSocket");
        logger.info(TAG, "Server URL: " + config.getWebSocketUrl());
        isRunning = true;
        reconnectCount = 0;

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification("Connecting to " + config.getServerUrl()));

        // 初始化客户端
        client = WebServerClient.getInstance(config);
        client.setCallback(new WebServerClient.ConnectionCallback() {
            @Override
            public void onConnected() {
                isConnected = true;
                reconnectCount = 0;
                updateConnectionStatus(true);
                updateNotification("Connected to " + config.getServerUrl());
                logger.info(TAG, "WebSocket connected and registered");

                // 发送文件列表更新
                client.sendFileListUpdate(getFilesDir());
            }

            @Override
            public void onDisconnected() {
                isConnected = false;
                updateConnectionStatus(false);
                updateNotification("Disconnected - Retrying...");
                logger.warn(TAG, "WebSocket disconnected");

                // 尝试重连（无限重试）
                if (isRunning) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onFilesReceived(List<WebServerClient.FileData> files) {
                handleReceivedFiles(files);
            }

            @Override
            public void onDeleteFile(String filename) {
                handleDeleteFile(filename);
            }

            @Override
            public void onRefreshFiles() {
                handleRefreshFiles();
            }

            @Override
            public void onExecuteTest(String filename) {
                handleExecuteTest(filename);
            }

            @Override
            public void onStopTest(String taskId, String reason) {
                handleStopTest(taskId, reason);
            }

            @Override
            public void onError(String message) {
                logger.error(TAG, "WebSocket error: " + message);
                updateNotification("Error: " + message);
            }
        });

        // 连接WebSocket
        client.connect();

        // 启动心跳
        startHeartbeat();
    }

    /**
     * 停止客户端
     */
    private void stopClient() {
        logger.info(TAG, "Stopping WebServer client");
        isRunning = false;
        stopHeartbeat();
        cancelReconnect();

        if (client != null) {
            client.disconnect();
        }

        updateConnectionStatus(false);
    }

    /**
     * 重连客户端
     */
    private void reconnectClient() {
        if (!isRunning) {
            return;
        }

        reconnectCount++;
        logger.info(TAG, "Attempting to reconnect (" + reconnectCount + ")");

        if (client != null) {
            client.disconnect();
            client.connect();
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        cancelReconnect();

        reconnectRunnable = () -> {
            Intent intent = new Intent(WebServerClientService.this, WebServerClientService.class);
            intent.setAction(ACTION_RECONNECT);
            startService(intent);
        };

        reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL);
        logger.info(TAG, "Scheduled reconnect in " + (RECONNECT_INTERVAL/1000) + " seconds");
    }

    /**
     * 取消重连
     */
    private void cancelReconnect() {
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        if (heartbeatThread == null) {
            heartbeatThread = new HandlerThread("WebServerHeartbeat");
            heartbeatThread.start();
            heartbeatHandler = new Handler(heartbeatThread.getLooper());
        }

        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning || heartbeatHandler == null) {
                    return;
                }

                if (client != null && client.isConnected()) {
                    client.sendHeartbeat(getFilesDir());
                }
                // WS 在跨 App 时常被冻结；HTTP 短超时心跳尽量刷新服务端 last_seen
                if (client != null) {
                    client.sendHttpHeartbeat();
                }

                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };

        heartbeatHandler.postDelayed(heartbeatRunnable, 5000);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
        if (heartbeatThread != null) {
            heartbeatThread.quitSafely();
            heartbeatThread = null;
            heartbeatHandler = null;
        }
    }

    /**
     * 处理接收到的文件
     */
    private void handleReceivedFiles(List<WebServerClient.FileData> files) {
        logger.info(TAG, "Received " + files.size() + " files");

        int savedCount = 0;
        File filesDir = getFilesDir();

        for (WebServerClient.FileData fileInfo : files) {
            if (fileInfo.content != null) {
                boolean saved = client.saveTestFile(fileInfo.name, fileInfo.content, filesDir);
                if (saved) {
                    savedCount++;
                    logger.info(TAG, "Saved file: " + fileInfo.name);
                }
            }
        }

        // 发送广播通知
        Intent intent = new Intent(ACTION_FILES_RECEIVED);
        intent.putExtra(EXTRA_FILE_COUNT, savedCount);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        // 更新通知
        if (savedCount > 0) {
            updateNotification("Received " + savedCount + " file(s)");
        }

        // 发送文件列表更新到服务器
        if (client != null && client.isConnected()) {
            client.sendFileListUpdate(filesDir);
        }
    }

    /**
     * 处理删除文件命令
     */
    private void handleDeleteFile(String filename) {
        logger.info(TAG, "Handling delete file command: " + filename);

        boolean success = false;
        if (client != null) {
            success = client.deleteLocalFile(filename, getFilesDir());
        }

        // 发送删除确认到服务器
        if (client != null) {
            client.sendFileDeletedConfirm(filename, success);
        }

        // 发送广播通知UI刷新
        Intent intent = new Intent(ACTION_FILE_DELETED);
        intent.putExtra(EXTRA_FILENAME, filename);
        intent.putExtra(EXTRA_SUCCESS, success);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        // 更新文件列表
        if (success && client != null && client.isConnected()) {
            client.sendFileListUpdate(getFilesDir());
        }
    }

    /**
     * 处理刷新文件列表命令
     */
    private void handleRefreshFiles() {
        logger.info(TAG, "Handling refresh files command");

        // 发送文件列表到服务器
        if (client != null && client.isConnected()) {
            client.sendFileListUpdate(getFilesDir());
        }

        // 发送广播通知UI刷新
        Intent intent = new Intent(ACTION_FILES_REFRESH);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * 处理执行测试命令
     */
    private void handleExecuteTest(String filename) {
        logger.info(TAG, "Handling execute test command: " + filename);

        Intent serviceIntent = new Intent(this, TestExecuteService.class);
        serviceIntent.setAction(TestExecuteService.ACTION_START_TEST);
        serviceIntent.putExtra(TestExecuteService.EXTRA_TEST_FILE, filename);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // 通知 MainActivity 刷新 UI（Activity 不在前台时仍可执行）
        Intent intent = new Intent(ACTION_EXECUTE_TEST);
        intent.putExtra(EXTRA_FILENAME, filename);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        updateNotification("Executing test: " + filename);
    }

    /**
     * 处理停止测试命令
     */
    private void handleStopTest(String taskId, String reason) {
        logger.info(TAG, "Handling stop test command, taskId=" + taskId + ", reason=" + reason);

        Intent intent = new Intent(TestExecuteService.ACTION_STOP_TEST);
        intent.setPackage(getPackageName());
        if (reason != null && !reason.isEmpty()) {
            intent.putExtra("reason", reason);
        }
        sendBroadcast(intent);

        updateNotification("Stopping test: " + reason);
    }

    /**
     * 更新连接状态
     */
    private void updateConnectionStatus(boolean connected) {
        Intent intent = new Intent(ACTION_CONNECTION_STATUS);
        intent.putExtra(EXTRA_CONNECTED, connected);
        intent.putExtra(EXTRA_SERVER_URL, connected ? config.getServerUrl() : "");
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        config = new WebServerConfig();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedHost = prefs.getString("server_host", null);
        if (savedHost != null) {
            config.setServerHost(savedHost);
        }

        int savedPort = prefs.getInt("server_port", -1);
        if (savedPort != -1) {
            config.setServerPort(savedPort);
        }

        config.setConnectionInterval(prefs.getLong("connection_interval", 30000));
        config.setAutoSyncEnabled(prefs.getBoolean("auto_sync", false));

        logger.info(TAG, "Loaded config: " + config.getServerUrl());
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebServer Client Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WebServer connection status");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification(String status) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TestApp WebServer")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    /**
     * 更新通知
     */
    private void updateNotification(String status) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(status));
        }
    }
}