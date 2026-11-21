/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.log;

/**
 * SDK日志配置
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class SdkLogConfig {

    /**
     * 本地日志配置
     */
    private SdkLocalLogConfig sdkLocalLogConfig;

    /**
     * 标准输出开关，默认 false
     */
    private boolean stdoutEnable = false;

    /**
     * 安全过滤开关，默认 true
     */
    private boolean securityFilterEnable = true;

    /**
     * 日志总开关，默认 true
     */
    private boolean logEnable = true;

    /**
     * 获取本地日志配置
     *
     * @return 本地日志配置
     */
    public SdkLocalLogConfig getSdkLocalLogConfig() {
        return sdkLocalLogConfig;
    }

    /**
     * 设置本地日志配置
     *
     * @param sdkLocalLogConfig 本地日志配置
     */
    public void setSdkLocalLogConfig(SdkLocalLogConfig sdkLocalLogConfig) {
        this.sdkLocalLogConfig = sdkLocalLogConfig;
    }

    /**
     * 是否启用标准输出
     *
     * @return true 启用，false 不启用
     */
    public boolean isStdoutEnable() {
        return stdoutEnable;
    }

    /**
     * 设置是否启用标准输出
     *
     * @param stdoutEnable true 启用，false 不启用
     */
    public void setStdoutEnable(boolean stdoutEnable) {
        this.stdoutEnable = stdoutEnable;
    }

    /**
     * 是否启用安全过滤
     *
     * @return true 启用，false 不启用
     */
    public boolean isSecurityFilterEnable() {
        return securityFilterEnable;
    }

    /**
     * 设置是否启用安全过滤
     *
     * @param securityFilterEnable true 启用，false 不启用
     */
    public void setSecurityFilterEnable(boolean securityFilterEnable) {
        this.securityFilterEnable = securityFilterEnable;
    }

    /**
     * 是否启用日志
     *
     * @return true 启用，false 不启用
     */
    public boolean isLogEnable() {
        return logEnable;
    }

    /**
     * 设置是否启用日志
     *
     * @param logEnable true 启用，false 不启用
     */
    public void setLogEnable(boolean logEnable) {
        this.logEnable = logEnable;
    }

    @Override
    public String toString() {
        return "SdkLogConfig{sdkLocalLogConfig=" + sdkLocalLogConfig + ", stdoutEnable=" + stdoutEnable + ", securityFilterEnable=" + securityFilterEnable + ", logEnable=" + logEnable + "}";
    }
}
