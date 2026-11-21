/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model.gui;

/**
 * GUI消息处理结果
 *
 * <p>封装GUI消息处理的结果信息，包含处理状态、错误码和描述</p>
 */
public class GuiHandleResult {
    /** 默认错误码：内部错误 */
    public static final int DEFAULT_CODE = 101;
    /** 默认描述：Internal error */
    private static final String DEFAULT_DESCRIPTION = "Internal error";

    /** 处理结果：true=成功，false=失败 */
    private boolean result;
    /** 错误码，默认101 */
    private int code;
    /** 描述信息，默认"内部错误" */
    private String description;

    /**
     * 默认构造方法
     *
     * <p>初始化失败结果，使用默认错误码和描述</p>
     */
    public GuiHandleResult() {
        this(false, DEFAULT_CODE, DEFAULT_DESCRIPTION);
    }

    /**
     * 构造方法
     *
     * @param result 处理结果
     * @param code 错误码
     * @param description 描述信息
     */
    public GuiHandleResult(boolean result, int code, String description) {
        this.result = result;
        this.code = code;
        this.description = description;
    }

    /**
     * 创建成功结果
     *
     * @return 成功的处理结果对象
     */
    public static GuiHandleResult success() {
        return new GuiHandleResult(true, 0, "Success");
    }

    /**
     * 创建失败结果
     *
     * @param code 错误码
     * @param description 描述信息
     * @return 失败的处理结果对象
     */
    public static GuiHandleResult failure(int code, String description) {
        return new GuiHandleResult(false, code, description);
    }

    /**
     * 创建失败结果（使用默认错误码101）
     *
     * @param description 描述信息
     * @return 失败的处理结果对象
     */
    public static GuiHandleResult failure(String description) {
        return new GuiHandleResult(false, DEFAULT_CODE, description);
    }

    /**
     * 创建默认失败结果
     *
     * @return 使用默认错误码和描述的失败结果对象
     */
    public static GuiHandleResult failure() {
        return new GuiHandleResult(false, DEFAULT_CODE, DEFAULT_DESCRIPTION);
    }

    /**
     * 创建处理中结果（异步处理）
     *
     * <p>用于异步处理场景，表示任务已启动但尚未完成。</p>
     * <p>后续结果将通过回调或异步消息发送。</p>
     *
     * @return 处理中的结果对象（result=true, code=0, description="Processing"）
     */
    public static GuiHandleResult processing() {
        return new GuiHandleResult(true, 0, "Processing");
    }

    /**
     * 获取处理结果
     *
     * @return true=成功，false=失败
     */
    public boolean isResult() {
        return result;
    }

    /**
     * 设置处理结果
     *
     * @param result 处理结果
     */
    public void setResult(boolean result) {
        this.result = result;
    }

    /**
     * 获取错误码
     *
     * @return 错误码，默认101
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置错误码
     *
     * @param code 错误码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取描述信息
     *
     * @return 描述信息，默认"Internal error"
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述信息
     *
     * @param description 描述信息
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "GuiHandleResult{" +
                "result=" + result +
                ", code=" + code +
                ", description='" + description + '\'' +
                '}';
    }
}
