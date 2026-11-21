/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.tempworkspace;

import com.huawei.aisfsdkv2.component.tempworkspace.impl.TempWorkspaceManagerImpl;

/**
 * 临时文件管理接口
 * 使用场景：申请一个临时目录，然后在目录下面做解压、文件拷贝、迁移等操作，完成之后直接删掉
 */
public interface TempWorkspaceManager {

    /**
     * 获取临时文件管理器实例（单例）
     * <p>获取实例后自动启动定时清理任务
     *
     * @return TempWorkspaceManager 实例
     */
    static TempWorkspaceManager getInstance() {
        return TempWorkspaceManagerImpl.getInstance();
    }

    /**
     * 申请临时工作空间
     *
     * @param tempFileExpire 临时文件过期时间（毫秒）
     * @return 临时目录路径，申请失败返回空字符串
     */
    String applyTempWorkspace(long tempFileExpire);

    /**
     * 清理临时工作空间
     *
     * @param tempWorkspace 临时工作空间路径
     */
    void cleanTempWorkspace(String tempWorkspace);
}
