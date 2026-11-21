/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.base;

import java.util.Arrays;

/**
 * TLS配置类
 * 用于配置SSL/TLS连接相关参数
 */
public class TLSConfig {

    /** CA证书类型，默认无CA证书（信任所有） */
    private CaCertType caCertType = CaCertType.NONE;

    /** CA证书文件路径 */
    private String caFilePath;

    /** 设备证书类型，默认无设备证书（单向认证） */
    private DeviceCertType deviceCertType = DeviceCertType.NONE;

    /** 设备证书文件路径（PEM格式为证书文件，PKCS12格式为证书库文件） */
    private String deviceCertPath;

    /** 设备证书内容（PEM格式字节数组），设置后优先从内容加载而非文件路径 */
    private byte[] deviceCertContent;

    /** 设备证书私钥内容（PEM格式字节数组） */
    private byte[] deviceKeyContent;

    /** 设备证书密码（PKCS12格式或加密私钥时使用） */
    private char[] deviceCertPassword;

    /**
     * 获取CA证书类型
     *
     * @return CA证书类型
     */
    public CaCertType getCaCertType() {
        return caCertType;
    }

    /**
     * 设置CA证书类型
     *
     * @param caCertType CA证书类型
     */
    public void setCaCertType(CaCertType caCertType) {
        this.caCertType = caCertType;
    }

    /**
     * 获取CA证书文件路径
     *
     * @return CA证书文件路径
     */
    public String getCaFilePath() {
        return caFilePath;
    }

    /**
     * 设置CA证书文件路径
     *
     * @param caFilePath CA证书文件路径
     */
    public void setCaFilePath(String caFilePath) {
        this.caFilePath = caFilePath;
    }

    /**
     * 获取设备证书类型
     *
     * @return 设备证书类型
     */
    public DeviceCertType getDeviceCertType() {
        return deviceCertType;
    }

    /**
     * 设置设备证书类型
     *
     * @param deviceCertType 设备证书类型
     */
    public void setDeviceCertType(DeviceCertType deviceCertType) {
        this.deviceCertType = deviceCertType;
    }

    /**
     * 获取设备证书文件路径
     *
     * @return 设备证书文件路径
     */
    public String getDeviceCertPath() {
        return deviceCertPath;
    }

    /**
     * 设置设备证书文件路径
     *
     * @param deviceCertPath 设备证书文件路径
     */
    public void setDeviceCertPath(String deviceCertPath) {
        this.deviceCertPath = deviceCertPath;
    }

    /**
     * 获取设备证书内容
     *
     * @return 设备证书内容（PEM格式字节数组），未设置时返回null
     */
    public byte[] getDeviceCertContent() {
        return deviceCertContent;
    }

    /**
     * 设置设备证书内容
     *
     * <p>App侧可以从assets、安全存储、网络下载等方式获取证书内容后传入。
     * 设置后优先从内容加载证书，无需提供deviceCertPath。</p>
     *
     * @param deviceCertContent 设备证书内容（PEM格式字节数组）
     */
    public void setDeviceCertContent(byte[] deviceCertContent) {
        this.deviceCertContent = deviceCertContent;
    }

    /**
     * 获取设备证书私钥内容
     *
     * @return 设备证书私钥内容（PEM格式字节数组），未设置时返回null
     */
    public byte[] getDeviceKeyContent() {
        return deviceKeyContent;
    }

    /**
     * 设置设备证书私钥内容
     *
     * <p>App侧可以从assets、安全存储、网络下载等方式获取私钥内容后传入。</p>
     *
     * @param deviceKeyContent 设备证书私钥内容（PEM格式字节数组）
     */
    public void setDeviceKeyContent(byte[] deviceKeyContent) {
        this.deviceKeyContent = deviceKeyContent;
    }

    /**
     * 获取设备证书密码
     *
     * @return 设备证书密码
     */
    public char[] getDeviceCertPassword() {
        return deviceCertPassword;
    }

    /**
     * 设置设备证书密码
     *
     * @param deviceCertPassword 设备证书密码
     */
    public void setDeviceCertPassword(char[] deviceCertPassword) {
        this.deviceCertPassword = deviceCertPassword;
    }

    /**
     * 清除敏感信息（口令、证书内容和私钥内容）
     *
     * <p>建议在不再使用TLSConfig时调用，将口令、证书内容和私钥内容从内存中安全清除，防止敏感数据残留</p>
     */
    public void clearSensitiveData() {
        if (deviceCertPassword != null) {
            Arrays.fill(deviceCertPassword, '\0');
            deviceCertPassword = null;
        }
        if (deviceCertContent != null) {
            Arrays.fill(deviceCertContent, (byte) 0);
            deviceCertContent = null;
        }
        if (deviceKeyContent != null) {
            Arrays.fill(deviceKeyContent, (byte) 0);
            deviceKeyContent = null;
        }
    }

    @Override
    public String toString() {
        return "TLSConfig{caCertType=" + caCertType + ", caFilePath='" + caFilePath + "', "
                + "deviceCertType=" + deviceCertType
                + ", deviceCertPath='" + deviceCertPath + "', hasCertContent="
                + (deviceCertContent != null && deviceCertContent.length > 0)
                + ", hasKeyContent=" + (deviceKeyContent != null && deviceKeyContent.length > 0)
                + ", hasPassword=" + (deviceCertPassword != null && deviceCertPassword.length > 0) + "}";
    }
}
