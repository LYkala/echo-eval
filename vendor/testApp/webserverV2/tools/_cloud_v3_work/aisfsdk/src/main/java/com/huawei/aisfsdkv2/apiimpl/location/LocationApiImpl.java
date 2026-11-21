/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.api.location.LocationApi;
import com.huawei.aisfsdkv2.api.location.LocationOprResult;
import com.huawei.aisfsdkv2.component.location.LocationReporter;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.model.LocationMsgInfo;
import com.huawei.aisfsdkv2.util.FileUtil;

/**
 * 位置接口实现
 *
 * @since 2026-04-16
 */
public class LocationApiImpl implements LocationApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("LocationApiImpl");

    /** 配置目录名 */
    private static final String CONFIG_DIR_NAME = "location";

    /** 配置文件名 */
    private static final String CONFIG_FILE_NAME = "location-config.json";

    /** JSON中地理位置上报启用字段名 */
    private static final String FIELD_REPORT_LOCATION_ENABLE = "ReportLocationEnable";

    /** 配置文件读写锁 */
    private final Object configLock = new Object();

    /** 配置文件绝对路径（懒加载，双重检查锁定） */
    private volatile String configFilePath;

    /** 地理位置上报启用状态缓存（null 表示未初始化） */
    private volatile Boolean reportLocationEnableCache;

    private static class Holder {
        private static final LocationApiImpl INSTANCE = new LocationApiImpl();
    }

    private LocationApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 单例实例
     */
    public static LocationApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 删除用户位置信息
     *
     * <p>通过 WebSocket 消息通道向服务端发送删除位置信息请求。</p>
     *
     * @return 操作结果，包含是否成功及描述信息
     */
    @Override
    public LocationOprResult deleteUserLocationInfo() {
        try {
            LocationMsgInfo msgInfo = new LocationMsgInfo();
            msgInfo.getData().setDeleteLocationInfo(true);
            boolean sent = MessageProcessor.getInstance().sendImmediately(msgInfo);
            if (sent) {
                LOGGER.info("LocationApiImpl.deleteUserLocationInfo: message sent successfully");
                return new LocationOprResult(true, "success");
            } else {
                LOGGER.error("LocationApiImpl.deleteUserLocationInfo: sendImmediately returned false");
                return new LocationOprResult(false, "send failed");
            }
        } catch (Exception e) {
            LOGGER.error("LocationApiImpl.deleteUserLocationInfo: failed, {}", e.getMessage());
            return new LocationOprResult(false, e.getMessage());
        }
    }

    /**
     * 查询地理位置上报是否启用
     *
     * <p>读取本地配置文件，文件不存在时默认返回 true（支持上报）。</p>
     *
     * @return true 启用，false 禁用
     */
    @Override
    public boolean getReportLocationEnable() {
        synchronized (configLock) {
            if (reportLocationEnableCache != null) {
                return reportLocationEnableCache;
            }
            String filePath = getConfigFilePath();
            if (filePath == null || filePath.isEmpty()) {
                LOGGER.warn("getReportLocationEnable: config file path is empty, return default true");
                reportLocationEnableCache = true;
                return true;
            }
            File file = new File(filePath);
            if (!file.exists()) {
                LOGGER.info("getReportLocationEnable: config file not exists, return default true");
                reportLocationEnableCache = true;
                return true;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int readLen = fis.read(data);
                if (readLen <= 0) {
                    LOGGER.warn("getReportLocationEnable: config file is empty, return default true");
                    reportLocationEnableCache = true;
                    return true;
                }
                String jsonStr = new String(data, 0, readLen, StandardCharsets.UTF_8);
                JSONObject jsonObj = new JSONObject(jsonStr);
                boolean value = jsonObj.optBoolean(FIELD_REPORT_LOCATION_ENABLE, true);
                reportLocationEnableCache = value;
                return value;
            } catch (Exception e) {
                LOGGER.error("getReportLocationEnable: failed to read config, error={}, " +
                        "return default true", e.getMessage());
                reportLocationEnableCache = true;
                return true;
            }
        }
    }

    /**
     * 更新地理位置上报配置
     *
     * <p>将配置持久化到 location/location-config.json。</p>
     *
     * @param enable true 启用，false 禁用
     */
    @Override
    public void updateReportLocationEnable(boolean enable) {
        synchronized (configLock) {
            String filePath = getConfigFilePath();
            if (filePath == null || filePath.isEmpty()) {
                LOGGER.warn("updateReportLocationEnable: config file path is empty");
                return;
            }
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                FileUtil.mkdirs(parentDir);
            }
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put(FIELD_REPORT_LOCATION_ENABLE, enable);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(jsonObj.toString().getBytes(StandardCharsets.UTF_8));
                }
                reportLocationEnableCache = enable;
                LOGGER.info("updateReportLocationEnable: saved {}", enable);
            } catch (Exception e) {
                LOGGER.error("updateReportLocationEnable: failed to save config, error={}", e.getMessage());
            }
        }
    }

    /**
     * 停止地理位置上报定时任务
     *
     * <p>彻底停止定时上报任务和连接事件订阅，释放线程池资源。</p>
     */
    @Override
    public void stopLocationReport() {
        LOGGER.info("LocationApiImpl.stopLocationReport: stopping location reporter");
        LocationReporter.getInstance().stop();
    }

    /**
     * 启动地理位置上报定时任务
     *
     * <p>重新启动定时上报任务和连接事件订阅。</p>
     */
    @Override
    public void startLocationReport() {
        LOGGER.info("LocationApiImpl.startLocationReport: starting location reporter");
        LocationReporter.getInstance().start();
    }

    /**
     * 获取配置文件绝对路径（双重检查锁定）
     *
     * @return 配置文件路径
     */
    private String getConfigFilePath() {
        if (configFilePath == null) {
            synchronized (configLock) {
                if (configFilePath == null) {
                    KeyFileInfo keyFileInfo = AISFSDKClient.getBaseApi().getKeyFileInfo();
                    if (keyFileInfo == null) {
                        LOGGER.warn("getConfigFilePath: KeyFileInfo is null");
                        return "";
                    }
                    String sdkDataDir = keyFileInfo.getSdkDataDir();
                    if (sdkDataDir == null || sdkDataDir.isEmpty()) {
                        LOGGER.warn("getConfigFilePath: sdkDataDir is empty");
                        return "";
                    }
                    File configDir = new File(sdkDataDir, CONFIG_DIR_NAME);
                    File configFile = new File(configDir, CONFIG_FILE_NAME);
                    try {
                        configFilePath = configFile.getCanonicalPath();
                    } catch (IOException e) {
                        LOGGER.error("getConfigFilePath: failed to get canonical path, error={}", e.getMessage());
                        return "";
                    }
                }
            }
        }
        return configFilePath;
    }
}
