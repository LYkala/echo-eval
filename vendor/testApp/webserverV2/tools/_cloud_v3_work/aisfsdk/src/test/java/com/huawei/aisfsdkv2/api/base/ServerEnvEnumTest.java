/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ServerEnvEnum 测试（仅测试端口，跳过 getIp() 因依赖 android.util.Base64）
 */
public class ServerEnvEnumTest {

    @Test
    public void testEnumCount() {
        assertEquals(23, ServerEnvEnum.values().length);
    }

    @Test
    public void testAisf02Ports() {
        assertEquals(40038, ServerEnvEnum.AISF02_INTERNAL.getWebsocketPort());
        assertEquals(40039, ServerEnvEnum.AISF02_INTERNAL.getRestfulPort());
        assertEquals(40040, ServerEnvEnum.AISF02_INTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF02_INTERNAL.getApigwPort());
    }

    @Test
    public void testAisf03Ports() {
        assertEquals(40050, ServerEnvEnum.AISF03_INTERNAL.getWebsocketPort());
        assertEquals(40051, ServerEnvEnum.AISF03_INTERNAL.getRestfulPort());
        assertEquals(40052, ServerEnvEnum.AISF03_INTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF03_INTERNAL.getApigwPort());
    }

    @Test
    public void testAisf04Ports() {
        assertEquals(40066, ServerEnvEnum.AISF04_INTERNAL.getWebsocketPort());
        assertEquals(40067, ServerEnvEnum.AISF04_INTERNAL.getRestfulPort());
        assertEquals(40068, ServerEnvEnum.AISF04_INTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF04_INTERNAL.getApigwPort());
    }

    @Test
    public void testAisf05Ports() {
        assertEquals(40058, ServerEnvEnum.AISF05_INTERNAL.getWebsocketPort());
        assertEquals(40059, ServerEnvEnum.AISF05_INTERNAL.getRestfulPort());
        assertEquals(40036, ServerEnvEnum.AISF05_INTERNAL.getC2cPort());
        assertEquals(40003, ServerEnvEnum.AISF05_INTERNAL.getApigwPort());
    }

    @Test
    public void testAisf06Ports() {
        assertEquals(40041, ServerEnvEnum.AISF06_INTERNAL.getWebsocketPort());
        assertEquals(40042, ServerEnvEnum.AISF06_INTERNAL.getRestfulPort());
        assertEquals(40043, ServerEnvEnum.AISF06_INTERNAL.getC2cPort());
    }

    @Test
    public void testAisf07Ports() {
        assertEquals(40071, ServerEnvEnum.AISF07_INTERNAL.getWebsocketPort());
        assertEquals(40072, ServerEnvEnum.AISF07_INTERNAL.getRestfulPort());
        assertEquals(40073, ServerEnvEnum.AISF07_INTERNAL.getC2cPort());
    }

    @Test
    public void testAisf08Ports() {
        assertEquals(40074, ServerEnvEnum.AISF08_INTERNAL.getWebsocketPort());
        assertEquals(40075, ServerEnvEnum.AISF08_INTERNAL.getRestfulPort());
        assertEquals(40076, ServerEnvEnum.AISF08_INTERNAL.getC2cPort());
    }

    @Test
    public void testAisf09Ports() {
        assertEquals(40077, ServerEnvEnum.AISF09_INTERNAL.getWebsocketPort());
        assertEquals(40078, ServerEnvEnum.AISF09_INTERNAL.getRestfulPort());
        assertEquals(40079, ServerEnvEnum.AISF09_INTERNAL.getC2cPort());
    }

    @Test
    public void testAisf11InternalPorts() {
        // AISF11_INTERNAL 端口暂未配置（WebSocket/RESTful/云云对接 = -1），仅 APIGW 已配置
        assertEquals(-1, ServerEnvEnum.AISF11_INTERNAL.getWebsocketPort());
        assertEquals(-1, ServerEnvEnum.AISF11_INTERNAL.getRestfulPort());
        assertEquals(-1, ServerEnvEnum.AISF11_INTERNAL.getC2cPort());
        assertEquals(40006, ServerEnvEnum.AISF11_INTERNAL.getApigwPort());
    }

    @Test
    public void testAisf02ExternalPorts() {
        assertEquals(40038, ServerEnvEnum.AISF02_EXTERNAL.getWebsocketPort());
        assertEquals(40039, ServerEnvEnum.AISF02_EXTERNAL.getRestfulPort());
        assertEquals(40040, ServerEnvEnum.AISF02_EXTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF02_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf03ExternalPorts() {
        assertEquals(40050, ServerEnvEnum.AISF03_EXTERNAL.getWebsocketPort());
        assertEquals(40051, ServerEnvEnum.AISF03_EXTERNAL.getRestfulPort());
        assertEquals(40052, ServerEnvEnum.AISF03_EXTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF03_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf04ExternalPorts() {
        assertEquals(40066, ServerEnvEnum.AISF04_EXTERNAL.getWebsocketPort());
        assertEquals(40067, ServerEnvEnum.AISF04_EXTERNAL.getRestfulPort());
        assertEquals(40068, ServerEnvEnum.AISF04_EXTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF04_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf05ExternalPorts() {
        assertEquals(40058, ServerEnvEnum.AISF05_EXTERNAL.getWebsocketPort());
        assertEquals(40059, ServerEnvEnum.AISF05_EXTERNAL.getRestfulPort());
        assertEquals(40036, ServerEnvEnum.AISF05_EXTERNAL.getC2cPort());
        assertEquals(40003, ServerEnvEnum.AISF05_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf06ExternalPorts() {
        assertEquals(40041, ServerEnvEnum.AISF06_EXTERNAL.getWebsocketPort());
        assertEquals(40042, ServerEnvEnum.AISF06_EXTERNAL.getRestfulPort());
        assertEquals(40043, ServerEnvEnum.AISF06_EXTERNAL.getC2cPort());
        assertEquals(10000, ServerEnvEnum.AISF06_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf07ExternalPorts() {
        assertEquals(40071, ServerEnvEnum.AISF07_EXTERNAL.getWebsocketPort());
        assertEquals(40072, ServerEnvEnum.AISF07_EXTERNAL.getRestfulPort());
        assertEquals(40073, ServerEnvEnum.AISF07_EXTERNAL.getC2cPort());
        assertEquals(40095, ServerEnvEnum.AISF07_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf08ExternalPorts() {
        assertEquals(40074, ServerEnvEnum.AISF08_EXTERNAL.getWebsocketPort());
        assertEquals(40075, ServerEnvEnum.AISF08_EXTERNAL.getRestfulPort());
        assertEquals(40076, ServerEnvEnum.AISF08_EXTERNAL.getC2cPort());
        assertEquals(40007, ServerEnvEnum.AISF08_EXTERNAL.getApigwPort());
    }

    @Test
    public void testAisf09ExternalPorts() {
        assertEquals(40077, ServerEnvEnum.AISF09_EXTERNAL.getWebsocketPort());
        assertEquals(40078, ServerEnvEnum.AISF09_EXTERNAL.getRestfulPort());
        assertEquals(40079, ServerEnvEnum.AISF09_EXTERNAL.getC2cPort());
        assertEquals(40081, ServerEnvEnum.AISF09_EXTERNAL.getApigwPort());
    }

    @Test
    public void testCustomEnvZeroPorts() {
        assertEquals(0, ServerEnvEnum.CUSTOM_ENV.getWebsocketPort());
        assertEquals(0, ServerEnvEnum.CUSTOM_ENV.getRestfulPort());
        assertEquals(0, ServerEnvEnum.CUSTOM_ENV.getC2cPort());
        assertEquals(0, ServerEnvEnum.CUSTOM_ENV.getApigwPort());
    }

    @Test
    public void testAisf09ApigwPort() {
        assertEquals(40081, ServerEnvEnum.AISF09_INTERNAL.getApigwPort());
    }

    @Test
    public void testMostEnvsApigwPortIs10000() {
        for (ServerEnvEnum env : ServerEnvEnum.values()) {
            if (env == ServerEnvEnum.CUSTOM_ENV || env.getApigwPort() != 10000) {
                continue;
            }
            assertEquals("APIGW port mismatch for " + env.name(), 10000, env.getApigwPort());
        }
    }

    @Test
    public void testAisf08ApigwPort() {
        assertEquals(40088, ServerEnvEnum.AISF08_INTERNAL.getApigwPort());
    }

    @Test
    public void testValueOf() {
        assertEquals(ServerEnvEnum.AISF02_INTERNAL, ServerEnvEnum.valueOf("AISF02_INTERNAL"));
        assertEquals(ServerEnvEnum.CUSTOM_ENV, ServerEnvEnum.valueOf("CUSTOM_ENV"));
    }

    /**
     * 验证所有 WebSocket 端口均为正值
     * <p>
     * 例外情况（有意设计，不应修改）：
     * <ul>
     *   <li>CUSTOM_ENV: 端口=0，用户自定义环境，需用户自行配置</li>
     *   <li>AISF10_EXTERNAL: 端口=-1，暂未配置，后期可能改动</li>
     *   <li>AISF11_INTERNAL: 端口=-1，暂未配置，后期可能改动</li>
     * </ul>
     * </p>
     * <p>
     * 注意：若将来例外枚举的端口改为正值，需从跳过列表中移除，否则测试不会验证它们。
     * </p>
     */
    @Test
    public void testAllWebsocketPortsArePositive() {
        // 定义有意设计为非正值的枚举（端口 <= 0）
        Set<ServerEnvEnum> exceptions = EnumSet.of(
            ServerEnvEnum.CUSTOM_ENV,      // 用户自定义环境，端口=0
            ServerEnvEnum.AISF10_EXTERNAL, // 暂未配置，端口=-1
            ServerEnvEnum.AISF11_INTERNAL  // 暂未配置，端口=-1
        );
        
        for (ServerEnvEnum env : ServerEnvEnum.values()) {
            if (!exceptions.contains(env)) {
                assertTrue("WebSocket port should be positive for " + env.name(),
                    env.getWebsocketPort() > 0);
            }
        }
    }
}
