/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.acm;

/**
 * 文件上传结果模型类
 * <p>
 * 用于封装文件上传操作的返回结果，包含上传成功后生成的文件唯一标识符(UUID)。
 * </p>
 */
public class UploadResult {

    /** 上传文件的UUID标识符 */
    private final String fileUuid;

    /**
     * 构造方法，创建文件上传结果对象
     *
     * @param fileUuid 上传成功后生成的文件UUID标识符
     */
    public UploadResult(String fileUuid) {
        this.fileUuid = fileUuid;
    }

    /**
     * 获取上传文件的UUID标识符
     *
     * @return 文件UUID字符串
     */
    public String getFileUuid() {
        return fileUuid;
    }
}
