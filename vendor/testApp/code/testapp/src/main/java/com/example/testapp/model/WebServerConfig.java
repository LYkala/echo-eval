package com.example.testapp.model;

/**
 * WebServer配置模型
 * 存储Web服务器连接配置
 */
public class WebServerConfig {

    /** 服务器地址 */
    private String serverHost;

    /** 服务器端口 */
    private int serverPort;

    /** 连接间隔（毫秒） */
    private long connectionInterval;

    /** 是否启用自动同步 */
    private boolean autoSyncEnabled;

    /**
     * 默认构造函数
     */
    public WebServerConfig() {
        this.serverHost = "124.71.69.102"; // Web Server 地址
        this.serverPort = 9527;  // WebServer V2 端口
        this.connectionInterval = 30000; // 30秒
        this.autoSyncEnabled = false;
    }

    /**
     * 构造函数
     */
    public WebServerConfig(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.connectionInterval = 30000;
        this.autoSyncEnabled = false;
    }

    /**
     * 获取完整的服务器URL
     */
    public String getServerUrl() {
        return "http://" + serverHost + ":" + serverPort;
    }

    /**
     * 获取设备连接API URL
     */
    public String getDeviceConnectUrl() {
        return getServerUrl() + "/api/device/connect";
    }

    /**
     * 获取WebSocket URL
     */
    public String getWebSocketUrl() {
        return "ws://" + serverHost + ":" + serverPort + "/ws/device";
    }

    /**
     * 获取文件列表API URL
     */
    public String getFilesUrl() {
        return getServerUrl() + "/api/files";
    }

    // Getters and Setters

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public long getConnectionInterval() {
        return connectionInterval;
    }

    public void setConnectionInterval(long connectionInterval) {
        this.connectionInterval = connectionInterval;
    }

    public boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public void setAutoSyncEnabled(boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }
}