/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.location;

/**
 * 位置操作结果
 */
public class LocationOprResult {

    /**
     * 操作是否成功
     */
    private final boolean result;

    /**
     * 操作结果描述信息
     */
    private final String message;

    /**
     * 构造方法
     *
     * @param result  操作是否成功
     * @param message 操作结果描述信息
     */
    public LocationOprResult(boolean result, String message) {
        this.result = result;
        this.message = message;
    }

    /**
     * 获取操作是否成功
     *
     * @return true 表示成功，false 表示失败
     */
    public boolean isResult() {
        return result;
    }

    /**
     * 获取操作结果描述信息
     *
     * @return 描述信息
     */
    public String getMessage() {
        return message;
    }
}
