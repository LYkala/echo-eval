/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.tempworkspace.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.KeyFileInfo;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.tempworkspace.TempWorkspaceManager;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.constants.NumberConstants;
import com.huawei.aisfsdkv2.util.FileUtil;
import com.huawei.aisfsdkv2.util.RandomUtil;
import com.huawei.aisfsdkv2.util.StringUtil;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * 临时文件管理实现
 * 使用场景：申请一个临时目录，然后在目录下面做解压、文件拷贝、迁移等操作，完成之后直接删掉
 */
public class TempWorkspaceManagerImpl implements TempWorkspaceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("TempWorkspaceManagerImpl");

    private static final TempWorkspaceManagerImpl INSTANCE = new TempWorkspaceManagerImpl();

    /**
     * 临时目录名
     */
    private static final String TEMP_DIR = "AISF_temp";

    /**
     * 定时任务周期（分钟）
     */
    private static final int CLEAN_TASK_DELAY_MINUTE = 5;

    /**
     * 临时工作空间根目录
     */
    private final String tempWorkspace;

    /**
     * 获取单例实例（供接口调用）
     *
     * @return TempWorkspaceManagerImpl 实例
     */
    public static TempWorkspaceManagerImpl getInstance() {
        return INSTANCE;
    }

    private TempWorkspaceManagerImpl() {
        KeyFileInfo keyFileInfo = AISFSDKClient.getBaseApi().getKeyFileInfo();
        this.tempWorkspace = FileUtil.getFilePath(new File(keyFileInfo.getSdkDataDir(), TEMP_DIR));
        ThreadPoolManager.getInstance().addScheduleTaskIndependentThreadPool(
                this::cleanExpiredWorkspaces, 
                0, 
                CLEAN_TASK_DELAY_MINUTE, 
                TimeUnit.MINUTES
        );
    }

    @Override
    public String applyTempWorkspace(long tempFileExpire) {
        long expireTime = System.currentTimeMillis() + tempFileExpire;
        String uuid = RandomUtil.getUUID();
        String tempWorkspaceName = expireTime + "_" + uuid;

        File tempWorkspaceFile = new File(tempWorkspace, tempWorkspaceName);
        boolean success = FileUtil.mkdirs(tempWorkspaceFile);
        if (!success) {
            LOGGER.error("TempWorkspaceManagerImpl.applyTempWorkspace: applyTempWorkspace failure = {}", 
                    FileUtil.getSecurityFilePath(tempWorkspaceFile));
            return StringUtil.EMPTY;
        }
        String result = FileUtil.getFilePath(tempWorkspaceFile);
        LOGGER.info("TempWorkspaceManagerImpl.applyTempWorkspace: applyTempWorkspace success = {}", result);
        return result;
    }

    @Override
    public void cleanTempWorkspace(String tempWorkspace) {
        FileUtil.deleteDirectory(new File(tempWorkspace));
        LOGGER.info("TempWorkspaceManagerImpl.cleanTempWorkspace: cleanTempWorkspace = {}", tempWorkspace);
    }

    /**
     * 清理过期的临时工作空间（定时任务）
     */
    private void cleanExpiredWorkspaces() {
        File tempWorkspaceDir = new File(this.tempWorkspace);
        File[] fileList = tempWorkspaceDir.listFiles();
        if (fileList == null) {
            LOGGER.debug("TempWorkspaceManagerImpl.cleanExpiredWorkspaces: no files to clean");
            return;
        }
        
        int cleanedCount = 0;
        for (File file : fileList) {
            String fileName = file.getName();
            String[] splits = fileName.split("_");
            if (splits.length != NumberConstants.NUMBER_2) {
                LOGGER.warn("TempWorkspaceManagerImpl.cleanExpiredWorkspaces: invalid fileName = {}", fileName);
                continue;
            }
            
            long expireTime;
            try {
                expireTime = Long.parseLong(splits[0]);
            } catch (NumberFormatException e) {
                LOGGER.warn("TempWorkspaceManagerImpl.cleanExpiredWorkspaces: parse expireTime failed, fileName = {}", fileName);
                continue;
            }
            
            if (System.currentTimeMillis() > expireTime) {
                FileUtil.deleteDirectory(file);
                cleanedCount++;
                LOGGER.info("TempWorkspaceManagerImpl.cleanExpiredWorkspaces: cleaned expired workspace = {}", fileName);
            }
        }
        
        LOGGER.debug("TempWorkspaceManagerImpl.cleanExpiredWorkspaces: cleaned {} expired workspaces", cleanedCount);
    }
}
