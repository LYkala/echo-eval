/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.network.impl;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.network.NetworkChangeCallback;
import com.huawei.aisfsdkv2.component.network.NetworkMonitor;
import com.huawei.aisfsdkv2.component.network.NetworkStatusInfo;
import com.huawei.aisfsdkv2.component.network.NetworkType;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 网络监控组件实现
 *
 * <p>用于实时监控 Android 设备的网络连接状态。
 * 采用单例模式，提供网络状态订阅功能。</p>
 *
 * <p><b>初始化流程：</b></p>
 * <ol>
 *   <li>获取 ConnectivityManager</li>
 *   <li>注册系统网络回调</li>
 *   <li>检测初始网络状态</li>
 * </ol>
 */
public class NetworkMonitorImpl implements NetworkMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("NetworkMonitorImpl");

    private static volatile NetworkMonitorImpl instance;

    private ConnectivityManager connectivityManager;

    private volatile boolean initialized = false;
    private volatile NetworkStatusInfo currentStatus = NetworkStatusInfo.DISCONNECTED;

    private final CopyOnWriteArrayList<NetworkChangeCallback> callbacks = new CopyOnWriteArrayList<>();

    /**
     * 获取单例实例
     *
     * @return NetworkMonitorImpl 实例
     */
    public static NetworkMonitorImpl getInstance() {
        if (instance == null) {
            synchronized (NetworkMonitorImpl.class) {
                if (instance == null) {
                    instance = new NetworkMonitorImpl();
                }
            }
        }
        return instance;
    }

    /**
     * 私有构造方法
     *
     * <p>禁止外部实例化，使用 getInstance() 获取单例。</p>
     */
    private NetworkMonitorImpl() {
    }

    /**
     * 初始化网络监控
     *
     * <p>获取 ConnectivityManager 并注册系统网络回调，检测初始网络状态。
     * 幂等操作，重复调用会记录日志并返回。</p>
     *
     * <p><b>初始化流程：</b></p>
     * <ol>
     *   <li>检查是否已初始化</li>
     *   <li>获取应用 Context</li>
     *   <li>获取 ConnectivityManager</li>
     *   <li>注册网络回调</li>
     *   <li>检测初始网络状态</li>
     * </ol>
     */
    @Override
    public void init() {
        if (initialized) {
            LOGGER.info("NetworkMonitorImpl.init: NetworkMonitor already initialized, skip");
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            Context appContext = AISFSDKClient.getBaseApi().getAppContext();
            if (appContext == null) {
                LOGGER.error("NetworkMonitorImpl.init: Failed to get application context from BaseApi");
                return;
            }
            connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                LOGGER.error("NetworkMonitorImpl.init: Failed to get ConnectivityManager");
                return;
            }
            registerNetworkCallback();
            currentStatus = detectCurrentStatus();
            LOGGER.info("NetworkMonitorImpl.init: NetworkMonitor init success, initial status: {}", currentStatus);
            initialized = true;
        }
    }

    /**
     * 订阅网络状态变更
     *
     * <p>订阅后，当网络状态发生变化时会触发回调。
     * 新订阅者会立即收到当前网络状态的回调。</p>
     *
     * <p><b>注意事项：</b></p>
     * <ul>
     *   <li>如果 NetworkMonitor 未初始化，会记录警告但正常注册</li>
     *   <li>不能订阅 null 回调</li>
     *   <li>订阅持续到应用生命周期结束，无法取消订阅</li>
     * </ul>
     *
     * @param callback 网络变更回调接口，不能为 null
     */
    @Override
    public void subscribe(NetworkChangeCallback callback) {
        if (callback == null) {
            LOGGER.warn("NetworkMonitorImpl.subscribe: Cannot subscribe null callback");
            return;
        }
        if (!initialized) {
            LOGGER.warn("NetworkMonitorImpl.subscribe: NetworkMonitor not initialized, callback will be registered after init");
        }
        callbacks.add(callback);
        LOGGER.info("NetworkMonitorImpl.subscribe: NetworkChangeCallback subscribed, count={}", callbacks.size());
        callback.onNetworkChanged(NetworkStatusInfo.DISCONNECTED, currentStatus);
    }

    /**
     * 获取当前网络状态
     *
     * <p>返回最近一次检测到的网络状态。</p>
     *
     * @return 当前网络状态信息，未初始化时返回 DISCONNECTED
     */
    @Override
    public NetworkStatusInfo getNetworkStatus() {
        return currentStatus;
    }

    /**
     * 注册系统网络回调
     *
     * <p>注册网络可用、丢失和能力变化回调。</p>
     */
    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                LOGGER.info("NetworkMonitorImpl.onAvailable: network available");
                notifyNetworkChanged(detectCurrentStatus());
            }
            @Override
            public void onLost(Network network) {
                LOGGER.info("NetworkMonitorImpl.onLost: network lost");
                notifyNetworkChanged(NetworkStatusInfo.DISCONNECTED);
            }
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                LOGGER.debug("NetworkMonitorImpl.onCapabilitiesChanged: capabilities changed");
                notifyNetworkChanged(detectStatusFromCapabilities(capabilities));
            }
        };
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            LOGGER.info("NetworkMonitorImpl.registerNetworkCallback: Network callback registered");
        } catch (Exception e) {
            LOGGER.error("NetworkMonitorImpl.registerNetworkCallback: Failed to register network callback: {}", e.getMessage());
        }
    }

    /**
     * 通知所有订阅者网络状态变化
     *
     * <p>只有当状态真正发生变化时才通知订阅者。</p>
     *
     * @param newStatus 新状态
     */
    private void notifyNetworkChanged(NetworkStatusInfo newStatus) {
        NetworkStatusInfo oldStatus = currentStatus;
        if (oldStatus.equals(newStatus)) {
            LOGGER.debug("NetworkMonitorImpl.notifyNetworkChanged: Network status unchanged: {}", currentStatus);
            return;
        }
        currentStatus = newStatus;
        LOGGER.info("NetworkMonitorImpl.notifyNetworkChanged: Network changed: {} -> {}", oldStatus, newStatus);
        for (NetworkChangeCallback callback : callbacks) {
            try {
                callback.onNetworkChanged(oldStatus, newStatus);
            } catch (Exception e) {
                LOGGER.error("NetworkMonitorImpl.notifyNetworkChanged: Error notifying network change: {}", e.getMessage());
            }
        }
    }

    /**
     * 检测当前网络状态
     *
     * @return 网络状态信息
     */
    private NetworkStatusInfo detectCurrentStatus() {
        try {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return NetworkStatusInfo.DISCONNECTED;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (capabilities == null) {
                return NetworkStatusInfo.DISCONNECTED;
            }
            return detectStatusFromCapabilities(capabilities);
        } catch (Exception e) {
            LOGGER.error("NetworkMonitorImpl.detectCurrentStatus: Error detecting network status: {}", e.getMessage());
            return NetworkStatusInfo.DISCONNECTED;
        }
    }

    /**
     * 从网络能力检测状态
     *
     * <p><b>检测逻辑：</b></p>
     * <ol>
     *   <li>无网络能力 → DISCONNECTED</li>
     *   <li>Wi-Fi 优先</li>
     *   <li>蜂窝网络次之</li>
     *   <li>其他 → DISCONNECTED</li>
     * </ol>
     *
     * @param capabilities 网络能力
     * @return 网络状态信息
     */
    private NetworkStatusInfo detectStatusFromCapabilities(NetworkCapabilities capabilities) {
        if (capabilities == null) {
            return NetworkStatusInfo.DISCONNECTED;
        }
        boolean hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (!hasInternet) {
            return NetworkStatusInfo.DISCONNECTED;
        }
        if (hasWifi) {
            return new NetworkStatusInfo(true, NetworkType.WIFI);
        }
        if (hasCellular) {
            return new NetworkStatusInfo(true, NetworkType.CELLULAR);
        }
        return NetworkStatusInfo.DISCONNECTED;
    }
}
