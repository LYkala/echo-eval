/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

/**
 * 服务端连接信息
 * <p>通过 {@link ServerEnvEnum} 和 {@link ServerAuthModeEnum} 指定目标环境与鉴权模式，
 * SDK 内部从枚举中读取 IP、端口、TLS 等参数，用户无需手动填写连接细节。</p>
 *
 * <p>若使用 {@link ServerEnvEnum#CUSTOM_ENV}，用户需自行设置 serverIp、端口等参数。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 *     ServerInfo serverInfo = new ServerInfo();
 *     serverInfo.setServerEnv(ServerEnvEnum.AISF02_INTERNAL);
 *     serverInfo.setServerAuthMode(ServerAuthModeEnum.ACM);
 * </pre>
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class ServerInfo {

    /**
     * 服务器环境枚举，默认 {@link ServerEnvEnum#CUSTOM_ENV}
     */
    private ServerEnvEnum serverEnv = ServerEnvEnum.CUSTOM_ENV;

    /**
     * 服务器鉴权模式枚举，默认 {@link ServerAuthModeEnum#ACM}
     */
    private ServerAuthModeEnum serverAuthMode = ServerAuthModeEnum.ACM;

    /**
     * 自定义服务端 IP
     */
    private String customServerIp;

    /**
     * 自定义 WebSocket 端口
     */
    private int customWebsocketPort;

    /**
     * 自定义 RESTful 端口
     */
    private int customRestfulPort;

    /**
     * 自定义云云对接端口
     */
    private int customC2cPort;

    /**
     * 自定义 APIGW 端口
     */
    private int customApigwPort;

    /**
     * 获取服务器环境枚举
     *
     * @return 服务器环境枚举
     */
    public ServerEnvEnum getServerEnv() {
        return serverEnv;
    }

    /**
     * 设置服务器环境枚举
     *
     * @param serverEnv 服务器环境枚举
     */
    public void setServerEnv(ServerEnvEnum serverEnv) {
        this.serverEnv = serverEnv;
    }

    /**
     * 获取服务器鉴权模式枚举
     *
     * @return 服务器鉴权模式枚举
     */
    public ServerAuthModeEnum getServerAuthMode() {
        return serverAuthMode;
    }

    /**
     * 设置服务器鉴权模式枚举
     *
     * @param serverAuthMode 服务器鉴权模式枚举
     */
    public void setServerAuthMode(ServerAuthModeEnum serverAuthMode) {
        this.serverAuthMode = serverAuthMode;
    }

    /**
     * 获取自定义服务端 IP
     *
     * @return 自定义服务端 IP
     */
    public String getServerIp() {
        if (serverEnv == ServerEnvEnum.CUSTOM_ENV) {
            return customServerIp;
        }
        return serverEnv.getIp();
    }

    /**
     * 设置自定义服务端 IP
     *
     * @param serverIp 自定义服务端 IP
     */
    public void setCustomServerIp(String serverIp) {
        this.customServerIp = serverIp;
    }

    /**
     * 获取自定义 WebSocket 端口
     *
     * @return 自定义 WebSocket 端口
     */
    public int getWebsocketPort() {
        if (serverEnv == ServerEnvEnum.CUSTOM_ENV) {
            return customWebsocketPort;
        }
        return serverEnv.getWebsocketPort();
    }

    /**
     * 设置自定义 WebSocket 端口
     *
     * @param websocketPort 自定义 WebSocket 端口
     */
    public void setWebsocketPort(int websocketPort) {
        this.customWebsocketPort = websocketPort;
    }

    /**
     * 获取自定义 RESTful 端口
     *
     * @return 自定义 RESTful 端口
     */
    public int getRestfulPort() {
        if (serverEnv == ServerEnvEnum.CUSTOM_ENV) {
            return customRestfulPort;
        }
        return serverEnv.getRestfulPort();
    }

    /**
     * 设置自定义 RESTful 端口
     *
     * @param restfulPort 自定义 RESTful 端口
     */
    public void setRestfulPort(int restfulPort) {
        this.customRestfulPort = restfulPort;
    }

    /**
     * 获取自定义云云对接端口
     *
     * @return 自定义云云对接端口
     */
    public int getC2cPort() {
        if (serverEnv == ServerEnvEnum.CUSTOM_ENV) {
            return customC2cPort;
        }
        return serverEnv.getC2cPort();
    }

    /**
     * 设置自定义云云对接端口
     *
     * @param c2cPort 自定义云云对接端口
     */
    public void setC2cPort(int c2cPort) {
        this.customC2cPort = c2cPort;
    }

    /**
     * 获取自定义 APIGW 端口
     *
     * @return 自定义 APIGW 端口
     */
    public int getApigwPort() {
        if (serverEnv == ServerEnvEnum.CUSTOM_ENV) {
            return customApigwPort;
        }
        return serverEnv.getApigwPort();
    }

    /**
     * 设置自定义 APIGW 端口
     *
     * @param apigwPort 自定义 APIGW 端口
     */
    public void setApigwPort(int apigwPort) {
        this.customApigwPort = apigwPort;
    }

    @Override
    public String toString() {
        return "ServerInfo{serverEnv=" + serverEnv
                + ", serverAuthMode=" + serverAuthMode
                + ", serverIp=" + customServerIp
                + ", websocketPort=" + customWebsocketPort
                + ", restfulPort=" + customRestfulPort
                + ", c2cPort=" + customC2cPort
                + ", apigwPort=" + customApigwPort + "}";
    }
}
