/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.config;

/**
 * 超时配置类
 * <p>封装 HTTP 客户端的各类超时参数。
 * <p>提供预定义配置和自定义配置能力。
 * @since 2026-05-25
 */
public class TimeoutConfig {
    
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int writeTimeoutMs;
    private final int callTimeoutMs;
    
    /**
     * 私有构造函数，只能通过静态工厂方法创建
     */
    private TimeoutConfig(int connect, int read, int write, int call) {
        this.connectTimeoutMs = connect;
        this.readTimeoutMs = read;
        this.writeTimeoutMs = write;
        this.callTimeoutMs = call;
    }
    
    /**
     * 通用 HTTP 客户端默认超时配置
     * 
     * <p>对应原常量：
     * <ul>
     *   <li>COMMON_CONNECT_TIMEOUT_MS = 2000</li>
     *   <li>COMMON_READ_TIMEOUT_MS = 5000</li>
     *   <li>COMMON_WRITE_TIMEOUT_MS = 5000</li>
     *   <li>COMMON_CALL_TIMEOUT_MS = 10000</li>
     * </ul>
     *
     * @return 通用客户端超时配置
     */
    public static TimeoutConfig defaultCommon() {
        return new TimeoutConfig(2000, 5000, 5000, 10000);
    }
    
    /**
     * 文件 HTTP 客户端默认超时配置
     * 
     * <p>对应原常量：
     * <ul>
     *   <li>FILE_CONNECT_TIMEOUT_MS = 5000</li>
     *   <li>FILE_READ_TIMEOUT_MS = 120000</li>
     *   <li>FILE_WRITE_TIMEOUT_MS = 120000</li>
     *   <li>FILE_CALL_TIMEOUT_MS = 300000</li>
     * </ul>
     *
     * @return 文件客户端超时配置
     */
    public static TimeoutConfig defaultFile() {
        return new TimeoutConfig(5000, 120000, 120000, 300000);
    }
    
    /**
     * 自定义超时配置
     *
     * @param connect 连接超时（毫秒）
     * @param read 读超时（毫秒）
     * @param write 写超时（毫秒）
     * @param call 整体调用超时（毫秒）
     * @return 自定义超时配置实例
     */
    public static TimeoutConfig custom(int connect, int read, int write, int call) {
        return new TimeoutConfig(connect, read, write, call);
    }
    
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }
    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }
    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }
    public int getCallTimeoutMs() {
        return callTimeoutMs;
    }
    
    @Override
    public String toString() {
        return "TimeoutConfig{" +
            "connect=" + connectTimeoutMs + "ms, " +
            "read=" + readTimeoutMs + "ms, " +
            "write=" + writeTimeoutMs + "ms, " +
            "call=" + callTimeoutMs + "ms}";
    }
}