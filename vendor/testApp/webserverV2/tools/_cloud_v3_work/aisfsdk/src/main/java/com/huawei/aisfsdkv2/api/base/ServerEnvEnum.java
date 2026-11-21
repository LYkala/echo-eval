/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import com.huawei.aisfsdkv2.util.Base64Util;

/**
 * 服务器环境枚举
 * <p>每个枚举值内置对应环境的完整服务器配置，用户无需手动填写 IP/端口细节。</p>
 * <p>IP 地址采用 Base64 编码存储，通过 {@link #getIp()} 解码获取原始值。</p>
 *
 * @author l00504561
 * @since 2026-04-08
 */
public enum ServerEnvEnum {

    /**
     * AISF02 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40038</li>
     *   <li>RESTful 端口: 40039</li>
     *   <li>云云对接端口: 40040</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF02_INTERNAL("MTE1LjEyMC4yNDkuNDU=", 40038, 40039, 40040, 40052),

    /**
     * AISF03 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40050</li>
     *   <li>RESTful 端口: 40051</li>
     *   <li>云云对接端口: 40052</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF03_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40050, 40051, 40052, 40050),

    /**
     * AISF04 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40066</li>
     *   <li>RESTful 端口: 40067</li>
     *   <li>云云对接端口: 40068</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF04_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40066, 40067, 40068, 10000),

    /**
     * AISF05 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40058</li>
     *   <li>RESTful 端口: 40059</li>
     *   <li>云云对接端口: 40060</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF05_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40058, 40059, 40036, 40098),

    /**
     * AISF06 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40041</li>
     *   <li>RESTful 端口: 40042</li>
     *   <li>云云对接端口: 40043</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF06_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40041, 40042, 40043, 10000),

    /**
     * AISF07 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40071</li>
     *   <li>RESTful 端口: 40072</li>
     *   <li>云云对接端口: 40073</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF07_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40071, 40072, 40073, 10000),

    /**
     * AISF08 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40074</li>
     *   <li>RESTful 端口: 40075</li>
     *   <li>云云对接端口: 40076</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF08_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40074, 40075, 40076, 40088),

    /**
     * AISF09 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40077</li>
     *   <li>RESTful 端口: 40078</li>
     *   <li>云云对接端口: 40079</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF09_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40077, 40078, 40079, 40071),

    /**
     * AISF10 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40077</li>
     *   <li>RESTful 端口: 40078</li>
     *   <li>云云对接端口: 40079</li>
     *   <li>APIGW 端口: 40094</li>
     * </ul>
     */
    AISF10_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40077, 40078, 40079, 40094),

    /**
     * AISF11 内部环境
     * <ul>
     *   <li>WebSocket 端口: 暂未配置 (-1)</li>
     *   <li>RESTful 端口: 暂未配置 (-1)</li>
     *   <li>云云对接端口: 暂未配置 (-1)</li>
     *   <li>APIGW 端口: 40006</li>
     * </ul>
     */
    AISF11_INTERNAL("MTIxLjM3LjUzLjIwMQ==", -1, -1, -1, 40006),

    /**
     * AISF14 内部环境
     * <ul>
     *   <li>WebSocket 端口: 40021</li>
     *   <li>RESTful 端口: 40021</li>
     *   <li>云云对接端口: 40021</li>
     *   <li>APIGW 端口: 40021</li>
     * </ul>
     */
    AISF14_INTERNAL("MTIxLjM3LjUzLjIwMQ==", 40021, 40021, 40021, 40021),

    /**
     * AISF02 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40038</li>
     *   <li>RESTful 端口: 40039</li>
     *   <li>云云对接端口: 40040</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF02_EXTERNAL("MTI0LjIyMi41LjI0MA==", 40038, 40039, 40040, 40052),

    /**
     * AISF03 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40050</li>
     *   <li>RESTful 端口: 40051</li>
     *   <li>云云对接端口: 40052</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF03_EXTERNAL("MTI0LjIyMi41LjI0MA==", 40050, 40051, 40052, 40050),

    /**
     * AISF04 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40066</li>
     *   <li>RESTful 端口: 40067</li>
     *   <li>云云对接端口: 40068</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF04_EXTERNAL("MTI0LjIyMi41LjI0MA==", 40066, 40067, 40068, 10000),

    /**
     * AISF05 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40058</li>
     *   <li>RESTful 端口: 40059</li>
     *   <li>云云对接端口: 40036</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF05_EXTERNAL("MTE1LjEyMC4yNDkuNDU=", 40058, 40059, 40036, 40098),

    /**
     * AISF06 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40041</li>
     *   <li>RESTful 端口: 40042</li>
     *   <li>云云对接端口: 40043</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF06_EXTERNAL("MTI0LjIyMi41LjI0MA==", 40041, 40042, 40043, 10000),

    /**
     * AISF07 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40071</li>
     *   <li>RESTful 端口: 40072</li>
     *   <li>云云对接端口: 40073</li>
     *   <li>APIGW 端口: 10000</li>
     * </ul>
     */
    AISF07_EXTERNAL("MTIxLjM3LjUzLjIwMQ==", 40071, 40072, 40073, 40095),

    /**
     * AISF08 外部环境【5.30曹学良】
     * <ul>
     *   <li>WebSocket 端口: 40074</li>
     *   <li>RESTful 端口: 40075</li>
     *   <li>云云对接端口: 40076</li>
     *   <li>APIGW 端口: 40007</li>
     * </ul>
     */
    AISF08_EXTERNAL("MTIxLjM3LjUzLjIwMQ==", 40074, 40075, 40076, 40007),

    /**
     * AISF09 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40077</li>
     *   <li>RESTful 端口: 40078</li>
     *   <li>云云对接端口: 40079</li>
     *   <li>APIGW 端口: 40071</li>
     * </ul>
     */
    AISF09_EXTERNAL("MTE1LjEyMC4yNDkuNDU=", 40077, 40078, 40079, 40071),

    /**
     * AISF10 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40077</li>
     *   <li>RESTful 端口: 40078</li>
     *   <li>云云对接端口: 40079</li>
     *   <li>APIGW 端口: 40094</li>
     * </ul>
     */
    AISF10_EXTERNAL("MTIxLjM3LjUzLjIwMQ==", -1, -1, -1, 40054),

    /**
     * AISF14 外部环境
     * <ul>
     *   <li>WebSocket 端口: 40021</li>
     *   <li>RESTful 端口: 40021</li>
     *   <li>云云对接端口: 40021</li>
     *   <li>APIGW 端口: 40021</li>
     * </ul>
     */
    AISF14_EXTERNAL("MTI0LjIyMC4yMzMuODc=", 40021, 40021, 40021, 40021),

    AISF16_EXTERNAL("MTI0LjIyMi41LjI0MA==", -1, -1, -1, 40002),

    /**
     * 自定义环境
     * <p>用户自行设置 IP 和端口。</p>
     */
    CUSTOM_ENV(null, 0, 0, 0, 0);

    private final String encodedIp;
    private final int websocketPort;
    private final int restfulPort;
    private final int c2cPort;
    private final int apigwPort;

    ServerEnvEnum(String encodedIp, int websocketPort, int restfulPort, int c2cPort, int apigwPort) {
        this.encodedIp = encodedIp;
        this.websocketPort = websocketPort;
        this.restfulPort = restfulPort;
        this.c2cPort = c2cPort;
        this.apigwPort = apigwPort;
    }

    /**
     * 获取服务端 IP
     *
     * @return 服务端 IP
     */
    public String getIp() {
        return Base64Util.decode(encodedIp);
    }

    /**
     * 获取 WebSocket 端口
     *
     * @return WebSocket 端口
     */
    public int getWebsocketPort() {
        return websocketPort;
    }

    /**
     * 获取 RESTful API 端口
     *
     * @return RESTful API 端口
     */
    public int getRestfulPort() {
        return restfulPort;
    }

    /**
     * 获取云云对接端口
     *
     * @return 云云对接端口
     */
    public int getC2cPort() {
        return c2cPort;
    }

    /**
     * 获取 APIGW 端口
     *
     * @return APIGW 端口
     */
    public int getApigwPort() {
        return apigwPort;
    }
}
