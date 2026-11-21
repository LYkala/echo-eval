/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api;

import com.huawei.aisfsdkv2.api.base.BaseApi;
import com.huawei.aisfsdkv2.api.fileserver.FileServerApi;
import com.huawei.aisfsdkv2.api.location.LocationApi;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskApi;
import com.huawei.aisfsdkv2.api.log.LogApi;
import com.huawei.aisfsdkv2.api.login.LoginApi;
import com.huawei.aisfsdkv2.api.memory.MemoryApi;
import com.huawei.aisfsdkv2.api.session.SessionApi;
import com.huawei.aisfsdkv2.api.skill.SkillApi;
import com.huawei.aisfsdkv2.apiimpl.base.BaseApiImpl;
import com.huawei.aisfsdkv2.apiimpl.fileserver.FileServerApiImpl;
import com.huawei.aisfsdkv2.apiimpl.location.LocationApiImpl;
import com.huawei.aisfsdkv2.apiimpl.timertask.TimerTaskApiImpl;
import com.huawei.aisfsdkv2.apiimpl.log.LogApiImpl;
import com.huawei.aisfsdkv2.apiimpl.login.LoginApiImpl;
import com.huawei.aisfsdkv2.apiimpl.memory.MemoryApiImpl;
import com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl;
import com.huawei.aisfsdkv2.apiimpl.skill.SkillApiImpl;
import com.huawei.aisfsdkv2.component.location.LocationReporter;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.network.NetworkMonitor;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.timertask.TimerTaskHandler;
import com.huawei.aisfsdkv2.timertask.TimerTaskManager;

/**
 * AISF SDK V2 主入口类
 * <p>提供SDK初始化、配置和API访问入口</p>
 */
public class AISFSDKClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("AISFSDKClient");

    private static volatile AISFSDKClient instance;

    /** 初始化标志，防止重复初始化 */
    private volatile boolean initialized = false;

    private AISFSDKClient() {
    }

    /**
     * 获取单例实例
     *
     * @return AISFSDKClient 实例
     */
    public static AISFSDKClient getInstance() {
        if (instance == null) {
            synchronized (AISFSDKClient.class) {
                if (instance == null) {
                    instance = new AISFSDKClient();
                }
            }
        }
        return instance;
    }

    /**
     * 查询是否已初始化
     *
     * @return true 表示已初始化，false 表示未初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 初始化 SDK
     * <p>幂等操作，重复调用返回 true。初始化流程：</p>
     * <ol>
     *   <li>检查是否已初始化</li>
     *     <li>设置基础信息（AppContext、DeviceInfo、KeyFileInfo、ServerInfo）</li>
     *     <li>配置日志系统</li>
     *     <li>初始化位置上报器（触发单例初始化，启动定时任务）</li>
     *     <li>初始化网络监控器</li>
     *   </ol>
     *
     * @param aisfClientParam 初始化参数
     * @return 是否成功，已初始化时直接返回 true
     */
    public synchronized boolean init(AISFClientParam aisfClientParam) {
        LOGGER.info("AISFSDKClient.init: init called, param={}", aisfClientParam);
        if (initialized) {
            LOGGER.info("AISFSDKClient.init: AISFSDKClient already initialized, skip");
            return true;
        }
        BaseApi baseApi = getBaseApi();
        String sdkVersion = baseApi.getSdkVersion();
        LOGGER.info("AISFSDKClient.init: aisf sdk init start, sdkVersion={}", sdkVersion);
        baseApi.setAppContext(aisfClientParam.getAppContext());
        baseApi.setDeviceInfo(aisfClientParam.getDeviceInfo());
        baseApi.setKeyFileInfo(aisfClientParam.getKeyFileInfo());
        baseApi.setServerInfo(aisfClientParam.getServerInfo());
        baseApi.setSdkRoleName(aisfClientParam.getSdkRoleName());
        baseApi.setTLSConfig(aisfClientParam.getTlsConfig());
        getLogApi().setSdkLogConfig(aisfClientParam.getSdkLogConfig());
        TimerTaskHandler.init();
        TimerTaskManager.getInstance().start();
        LocationReporter.getInstance().init();
        NetworkMonitor.getInstance().init();
        MessageProcessor.getInstance().init();
        LOGGER.info("AISFSDKClient.init: aisf sdk init finish, sdk version = {}", sdkVersion);
        initialized = true;
        return true;
    }

    /**
     * 获取基础接口
     *
     * @return 基础接口
     */
    public static BaseApi getBaseApi() {
        return BaseApiImpl.getInstance();
    }

    /**
     * 获取日志接口
     *
     * @return 日志接口
     */
    public static LogApi getLogApi() {
        return LogApiImpl.getInstance();
    }

    /**
     * 获取登录接口
     *
     * @return 登录接口
     */
    public static LoginApi getLoginApi() {
        return LoginApiImpl.getInstance();
    }

    /**
     * 获取会话接口
     *
     * @return 会话接口
     */
    public static SessionApi getSessionApi() {
        return SessionApiImpl.getInstance();
    }

    /**
     * 获取记忆接口
     *
     * @return 记忆接口
     */
    public static MemoryApi getMemoryApi() {
        return MemoryApiImpl.getInstance();
    }

    /**
     * 获取任务接口
     *
     * @return 任务接口
     */
    public static TimerTaskApi getTimerTaskApi() {
        return TimerTaskApiImpl.getInstance();
    }

    /**
     * 获取技能接口
     *
     * @return 技能接口
     */
    public static SkillApi getSkillApi() {
        return SkillApiImpl.getInstance();
    }

    /**
     * 获取位置接口
     *
     * @return 位置接口
     */
    public static LocationApi getLocationApi() {
        return LocationApiImpl.getInstance();
    }

    /**
     * 获取文件服务接口
     *
     * @return 文件服务接口
     */
    public static FileServerApi getFileServerApi() {
        return FileServerApiImpl.getInstance();
    }
}
