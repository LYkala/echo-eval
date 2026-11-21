/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import android.content.Context;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.util.FileUtil;

import java.io.File;

/**
 * 关键文件信息
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class KeyFileInfo {

    /**
     * SDK 数据目录路径
     * 用于存储日志、临时文件等 SDK 运行数据
     */
    private String sdkDataDir;

    /**
     * 获取 SDK 数据目录路径
     * 如果未设置，返回应用私有目录下的 aisfsdk 子目录
     *
     * @return SDK 数据目录绝对路径
     */
    public String getSdkDataDir() {
        if (sdkDataDir == null || sdkDataDir.isEmpty()) {
            Context context = AISFSDKClient.getBaseApi().getAppContext();
            if (context != null) {
                File dir = new File(context.getFilesDir(), "aisfsdk");
                if (!dir.exists()) {
                    FileUtil.mkdirs(dir);
                }
                return FileUtil.getFilePath(dir);
            }
            return "";
        }
        return sdkDataDir;
    }

    /**
     * 设置 SDK 数据目录路径
     *
     * @param sdkDataDir SDK 数据目录路径
     */
    public void setSdkDataDir(String sdkDataDir) {
        this.sdkDataDir = sdkDataDir;
    }

    @Override
    public String toString() {
        return "KeyFileInfo{sdkDataDir=" + sdkDataDir + "}";
    }
}
