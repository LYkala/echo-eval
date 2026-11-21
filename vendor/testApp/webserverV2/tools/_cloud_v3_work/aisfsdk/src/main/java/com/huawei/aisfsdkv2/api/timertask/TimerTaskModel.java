/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.timertask;

/**
 * 定时任务返回模型
 *
 * @param <T> 数据类型
 * @since 2026-04-09
 */
public class TimerTaskModel<T> {

    private final int retCode;
    private final String retMsg;
    private final T data;

    /**
     * 构造方法
     *
     * @param retCode 返回码
     * @param retMsg 返回消息
     * @param data 数据
     */
    public TimerTaskModel(int retCode, String retMsg, T data) {
        this.retCode = retCode;
        this.retMsg = retMsg != null ? retMsg : "";
        this.data = data;
    }

    /**
     * 创建成功结果
     *
     * @param data 数据
     * @param <T> 数据类型
     * @return TimerTaskModel
     */
    public static <T> TimerTaskModel<T> success(T data) {
        return new TimerTaskModel<>(200, "success", data);
    }

    /**
     * 创建成功结果（带自定义消息）
     *
     * @param data 数据
     * @param retMsg 返回消息
     * @param <T> 数据类型
     * @return TimerTaskModel
     */
    public static <T> TimerTaskModel<T> success(T data, String retMsg) {
        return new TimerTaskModel<>(200, retMsg, data);
    }

    /**
     * 创建失败结果
     *
     * @param retCode 返回码
     * @param retMsg 返回消息
     * @param <T> 数据类型
     * @return TimerTaskModel
     */
    public static <T> TimerTaskModel<T> error(int retCode, String retMsg) {
        return new TimerTaskModel<>(retCode, retMsg, null);
    }

    /**
     * 获取返回码
     *
     * @return 返回码
     */
    public int getRetCode() {
        return retCode;
    }

    /**
     * 获取返回消息
     *
     * @return 返回消息
     */
    public String getRetMsg() {
        return retMsg;
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public T getData() {
        return data;
    }

    /**
     * 判断是否成功
     *
     * @return true 成功，false 失败
     */
    public boolean isSuccess() {
        return retCode == 200;
    }
}
