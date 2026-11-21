/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.location.impl;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.DeviceInfo;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.apiimpl.location.LocationApiImpl;
import com.huawei.aisfsdkv2.component.eventbus.Event;
import com.huawei.aisfsdkv2.component.eventbus.EventBusManager;
import com.huawei.aisfsdkv2.component.eventbus.EventSubscriber;
import com.huawei.aisfsdkv2.component.eventbus.event.ConnectionOpenedEvent;
import com.huawei.aisfsdkv2.component.location.LocationReporter;
import com.huawei.aisfsdkv2.component.location.model.Location;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.model.LocationMsgInfo;
import com.huawei.aisfsdkv2.util.CoordinateUtil;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 位置上报器实现
 *
 * <p>上报策略：定时上报（5分钟周期）+ 位置变化检测 + 失败重试</p>
 *
 * <p>设计特点：
 * <ul>
 *   <li>定时周期：每5分钟检查一次位置</li>
 *   <li>变化检测：位置变化超过20米才上报</li>
 *   <li>失败重试：获取位置或发送消息失败时重试5次，间隔5秒</li>
 *   <li>权限处理：无权限时静默跳过，不阻塞业务</li>
 *   <li>坐标转换：WGS84 → GCJ02（火星坐标系）</li>
 * </ul>
 */
public class LocationReporterImpl implements LocationReporter, EventSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger("LocationReporterImpl");
    private static final long INITIAL_DELAY_SECONDS = 2;
    private static final long REPORT_PERIOD_SECONDS = 300;
    private static final float MIN_REPORT_DISTANCE_METERS = 20.0f;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_INTERVAL_MS = 5000;
    private volatile Location lastReportedLocation = null;
    private volatile ScheduledFuture<?> scheduleFuture;
    private volatile boolean running = false;

    private static class Holder {
        private static final LocationReporterImpl INSTANCE = new LocationReporterImpl();
    }

    /**
     * 获取单例实例
     *
     * @return LocationReporterImpl 实例
     */
    public static LocationReporterImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 初始化（启动定时上报任务并订阅连接事件）
     */
    @Override
    public void init() {
        startScheduleTask();
        subscribeConnectionEvent();
    }

    /**
     * 私有构造方法（不自动启动，由init()触发）
     */
    private LocationReporterImpl() {
    }

    /**
     * 启动定时调度任务
     */
    private void startScheduleTask() {
        if (running) {
            LOGGER.warn("LocationReporterImpl.startScheduleTask: already running, skip");
            return;
        }
        scheduleFuture = ThreadPoolManager.getInstance().addScheduleTaskIndependentThreadPool(
                () -> reportLocationWithRetry(false),
                INITIAL_DELAY_SECONDS,
                REPORT_PERIOD_SECONDS,
                TimeUnit.SECONDS);
        running = true;
        LOGGER.info("LocationReporterImpl.startScheduleTask: Location schedule task started, " +
                "initialDelay={}s, period={}s", INITIAL_DELAY_SECONDS, REPORT_PERIOD_SECONDS);
    }

    /**
     * 停止定时调度任务
     */
    private void stopScheduleTask() {
        if (!running) {
            LOGGER.warn("LocationReporterImpl.stopScheduleTask: not running, skip");
            return;
        }
        if (scheduleFuture != null) {
            scheduleFuture.cancel(false);
            scheduleFuture = null;
        }
        running = false;
        LOGGER.info("LocationReporterImpl.stopScheduleTask: Location schedule task stopped");
    }

    /**
     * 取消连接事件订阅
     */
    private void unsubscribeConnectionEvent() {
        try {
            EventBusManager.getInstance().unsubscribeEvent(this);
            LOGGER.info("LocationReporterImpl.unsubscribeConnectionEvent: Unsubscribed from ConnectionOpenedEvent");
        } catch (Exception e) {
            LOGGER.error("LocationReporterImpl.unsubscribeConnectionEvent: Failed to unsubscribe", e);
        }
    }

    /**
     * 启动位置上报（定时任务+事件订阅）
     *
     * <p>启动定时上报任务和连接事件订阅。已运行时重复调用不会重复启动。</p>
     */
    @Override
    public void start() {
        LOGGER.info("LocationReporterImpl.start: starting location reporter");
        startScheduleTask();
        subscribeConnectionEvent();
    }

    /**
     * 停止位置上报（取消定时任务+取消事件订阅）
     *
     * <p>彻底停止定时上报任务和连接事件订阅，释放线程池资源。
     * 停止后可通过 {@link #start()} 重新启动。</p>
     */
    @Override
    public void stop() {
        LOGGER.info("LocationReporterImpl.stop: stopping location reporter");
        stopScheduleTask();
        unsubscribeConnectionEvent();
    }

    /**
     * 订阅连接打开事件
     *
     * <p>当 WebSocket 连接成功时，立即触发一次位置上报。</p>
     */
    private void subscribeConnectionEvent() {
        try {
            EventBusManager.getInstance().subscribeEvent(this, new ConnectionOpenedEvent());
            LOGGER.info("LocationReporterImpl: Subscribed to ConnectionOpenedEvent");
        } catch (Exception e) {
            LOGGER.error("LocationReporterImpl: Failed to subscribe ConnectionOpenedEvent", e);
        }
    }

    /**
     * 处理优先级（低优先级，确保登录流程先完成）
     *
     * @return 优先级值，越小越优先
     */
    @Override
    public int order() {
        return 1000;
    }

    /**
     * 处理事件
     *
     * <p>EventBus 使用 ASYNC_PARALLEL 策略，此方法已在独立线程中执行。</p>
     *
     * @param event 事件对象
     */
    @Override
    public void handler(Event event) {
        if (event instanceof ConnectionOpenedEvent) {
            LOGGER.info("LocationReporterImpl: Received ConnectionOpenedEvent, trigger immediate location report");
            reportLocationWithRetry(true);
        }
    }

    /**
     * 带重试的位置上报（定时任务 + 连接后触发共用）
     *
     * <p>处理流程：
     * <ol>
     *   <li>根据 force 参数选择上报策略</li>
     *   <li>执行位置上报（最多5次尝试）</li>
     *   <li>失败时等待5秒后重试</li>
     * </ol>
     *
     * <p>策略差异：
     * <ul>
     *   <li>force=false：使用缓存位置，检查距离变化</li>
     *   <li>force=true：获取实时位置，跳过距离检查</li>
     * </ul>
     *
     * @param force 是否强制上报（true=连接后上报，false=定时任务）
     */
    private void reportLocationWithRetry(boolean force) {
        if (!LocationApiImpl.getInstance().getReportLocationEnable()) {
            LOGGER.info("LocationReporterImpl.reportLocationWithRetry: Location reporting is disabled, skip");
            return;
        }
        String lastFailureReason = "unknown";
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                LOGGER.info("LocationReporterImpl.reportLocationWithRetry: Attempt {} starting, force={}", attempt, force);
                boolean success = reportLocation(force);
                if (success) {
                    LOGGER.info("LocationReporterImpl.reportLocationWithRetry: Location reported successfully");
                    return;
                }
                lastFailureReason = "reportLocation returned false";
                LOGGER.warn("LocationReporterImpl.reportLocationWithRetry: Attempt {} failed", attempt);
            } catch (Exception e) {
                lastFailureReason = e.getMessage();
                LOGGER.error("LocationReporterImpl.reportLocationWithRetry: Attempt {} error", attempt, e);
            }
            if (attempt < MAX_RETRY_COUNT) {
                ThreadUtil.sleepNoExcept(RETRY_INTERVAL_MS);
            }
        }
        LOGGER.error("LocationReporterImpl.reportLocationWithRetry: All {} attempts failed, lastReason={}", MAX_RETRY_COUNT, lastFailureReason);
    }

    /**
     * 上报位置
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查登录状态</li>
     *   <li>检查位置权限</li>
     *   <li>根据force参数选择位置获取策略</li>
     *   <li>发送位置消息</li>
     * </ol>
     *
     * <p>策略差异：
     * <ul>
     *   <li>force=false：使用缓存位置，检查距离变化</li>
     *   <li>force=true：获取实时位置，跳过距离检查</li>
     * </ul>
     *
     * @param force 是否强制上报（true=连接后上报，false=定时任务）
     * @return 是否上报成功
     */
    private boolean reportLocation(boolean force) {
        if (AISFSDKClient.getLoginApi().getLoginInfo().getStatus() != LoginStatus.CONNECTED) {
            LOGGER.error("LocationReporterImpl.reportLocation: Login status is not CONNECTED, skip report");
            return false;
        }
        if (!hasLocationPermission()) {
            LOGGER.error("LocationReporterImpl.reportLocation: No location permission, skip report");
            return false;
        }
        Location location;
        if (force) {
            android.location.Location freshLocation = getFreshLocation(30000);
            if (freshLocation == null) {
                LOGGER.error("LocationReporterImpl.reportLocation: Failed to get fresh location");
                return false;
            }
            location = new Location();
            location.setLatitude(freshLocation.getLatitude());
            location.setLongitude(freshLocation.getLongitude());
        } else {
            location = getLocationInfo();
            if (location == null) {
                LOGGER.error("LocationReporterImpl.reportLocation: Get location failed");
                return false;
            }
            if (!shouldReportLocation(location)) {
                LOGGER.debug("LocationReporterImpl.reportLocation: Location not changed enough, skip report");
                return true;
            }
        }
        boolean sent = sendLocationMessage(location);
        if (sent) {
            lastReportedLocation = location;
            LOGGER.info("LocationReporterImpl.reportLocation: Location reported successfully: [{}, {}]",
                    location.getLatitude(), location.getLongitude());
        }
        return sent;
    }

    /**
     * 获取实时位置（带超时）
     *
     * <p>处理流程：
     * <ol>
     *   <li>获取Context和LocationManager</li>
     *   <li>获取最佳位置提供者</li>
     *   <li>检查60秒内的缓存位置</li>
     *   <li>如缓存过期则请求新的GPS定位</li>
     * </ol>
     *
     * @param timeoutMs 等待新位置的最大时间（毫秒）
     * @return 位置对象，失败返回 null
     */
    private android.location.Location getFreshLocation(long timeoutMs) {
        Context context = AISFSDKClient.getBaseApi().getAppContext();
        if (context == null) {
            LOGGER.error("LocationReporterImpl.getFreshLocation: Context is null");
            return null;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            LOGGER.error("LocationReporterImpl.getFreshLocation: LocationManager is null");
            return null;
        }
        try {
            String bestProvider = locationManager.getBestProvider(new Criteria(), true);
            if (bestProvider == null) {
                bestProvider = LocationManager.GPS_PROVIDER;
            }
            android.location.Location lastKnown = locationManager.getLastKnownLocation(bestProvider);
            if (lastKnown != null) {
                long age = System.currentTimeMillis() - lastKnown.getTime();
                if (age < 60000) {
                    LOGGER.debug("LocationReporterImpl.getFreshLocation: Using fresh last known location (age={}ms)", age);
                    return lastKnown;
                }
            }
            LOGGER.info("LocationReporterImpl.getFreshLocation: Requesting fresh location from {}", bestProvider);
            return requestSingleLocationUpdate(locationManager, bestProvider, timeoutMs);
        } catch (SecurityException e) {
            LOGGER.error("LocationReporterImpl.getFreshLocation: SecurityException", e);
            return null;
        }
    }

    /**
     * 请求单次位置更新（带超时）
     *
     * <p>使用 CountDownLatch 等待位置更新或超时。</p>
     *
     * @param locationManager LocationManager 实例
     * @param provider 位置提供者
     * @param timeoutMs 超时时间
     * @return 位置对象，超时返回 null
     */
    private android.location.Location requestSingleLocationUpdate(
            LocationManager locationManager,
            String provider,
            long timeoutMs) {
        final android.location.Location[] result = new android.location.Location[1];
        final CountDownLatch latch = new CountDownLatch(1);
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                result[0] = location;
                latch.countDown();
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        };
        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            boolean received = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (received && result[0] != null) {
                LOGGER.debug("LocationReporterImpl.requestSingleLocationUpdate: Received fresh location");
                return result[0];
            } else {
                LOGGER.warn("LocationReporterImpl.requestSingleLocationUpdate: Location request timeout ({}ms)", timeoutMs);
                return null;
            }
        } catch (SecurityException e) {
            LOGGER.error("LocationReporterImpl.requestSingleLocationUpdate: SecurityException", e);
            return null;
        } catch (Exception e) {
            LOGGER.error("LocationReporterImpl.requestSingleLocationUpdate: Error", e);
            return null;
        }
    }

    /**
     * 检查是否有位置权限
     *
     * @return 是否有权限
     */
    private boolean hasLocationPermission() {
        Context context = AISFSDKClient.getBaseApi().getAppContext();
        if (context == null) {
            return false;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 判断是否应上报位置
     *
     * <p>与上次上报位置对比，变化超过20米才上报。</p>
     *
     * @param location 当前位置
     * @return 是否应该上报
     */
    private boolean shouldReportLocation(com.huawei.aisfsdkv2.component.location.model.Location location) {
        if (lastReportedLocation == null) {
            return true;
        }
        double distance = calculateDistance(
                lastReportedLocation.getLatitude(), lastReportedLocation.getLongitude(),
                location.getLatitude(), location.getLongitude());
        return distance >= MIN_REPORT_DISTANCE_METERS;
    }

    /**
     * 计算两点间距离（米）
     *
     * <p>使用 Haversine 公式计算地球表面两点距离。</p>
     *
     * @param lat1 点1纬度
     * @param lng1 点1经度
     * @param lat2 点2纬度
     * @param lng2 点2经度
     * @return 距离（米）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 获取位置信息
     *
     * <p>处理流程：
     * <ol>
     *   <li>获取Context和LocationManager</li>
     *   <li>获取最佳位置提供者</li>
     *   <li>获取最后已知位置</li>
     *   <li>转换为SDK Location对象</li>
     * </ol>
     *
     * @return 位置信息，获取失败返回 null
     */
    private Location getLocationInfo() {
        Context context = AISFSDKClient.getBaseApi().getAppContext();
        if (context == null) {
            LOGGER.error("LocationReporterImpl.getLocationInfo: Context is null");
            return null;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            LOGGER.error("LocationReporterImpl.getLocationInfo: LocationManager is null");
            return null;
        }
        try {
            Criteria criteria = new Criteria();
            String bestProvider = locationManager.getBestProvider(criteria, true);
            if (bestProvider == null) {
                bestProvider = LocationManager.GPS_PROVIDER;
            }
            android.location.Location lastLocation = locationManager.getLastKnownLocation(bestProvider);
            if (lastLocation != null) {
                com.huawei.aisfsdkv2.component.location.model.Location location = new com.huawei.aisfsdkv2.component.location.model.Location();
                location.setLatitude(lastLocation.getLatitude());
                location.setLongitude(lastLocation.getLongitude());
                LOGGER.debug("LocationReporterImpl.getLocationInfo: Get location from {}", bestProvider);
                return location;
            }
            LOGGER.warn("LocationReporterImpl.getLocationInfo: No last known location available");
            return null;
        } catch (SecurityException e) {
            LOGGER.error("LocationReporterImpl.getLocationInfo: SecurityException", e);
            return null;
        }
    }

    /**
     * 发送位置消息
     *
     * <p>处理流程：
     * <ol>
     *   <li>坐标转换：WGS84 → GCJ02</li>
     *   <li>构造位置消息对象</li>
     *   <li>发送消息</li>
     * </ol>
     *
     * @param location 位置信息
     * @return 是否发送成功
     */
    private boolean sendLocationMessage(com.huawei.aisfsdkv2.component.location.model.Location location) {
        try {
            double[] gcj02 = CoordinateUtil.wgs84ToGcj02(location.getLongitude(), location.getLatitude());
            LocationMsgInfo msgInfo = new LocationMsgInfo();
            msgInfo.getData().setLongitude(String.format(Locale.ENGLISH, "%.6f", gcj02[0]));
            msgInfo.getData().setLatitude(String.format(Locale.ENGLISH, "%.6f", gcj02[1]));
            DeviceInfo deviceInfo = AISFSDKClient.getBaseApi().getDeviceInfo();
            if (deviceInfo != null) {
                String instanceId = deviceInfo.getCloudPhoneInstanceId();
                if (instanceId != null && !instanceId.isEmpty()) {
                    msgInfo.getData().setCloudPhoneInstanceId(instanceId);
                    LOGGER.debug("LocationReporterImpl.sendLocationMessage: Set cloudPhoneInstanceId={}", instanceId);
                }
            }
            boolean result = MessageProcessor.getInstance().send(msgInfo);
            if (!result) {
                LOGGER.error("LocationReporterImpl.sendLocationMessage: MessageProcessor.send() returned false");
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("LocationReporterImpl.sendLocationMessage: Error sending location", e);
            return false;
        }
    }

}
