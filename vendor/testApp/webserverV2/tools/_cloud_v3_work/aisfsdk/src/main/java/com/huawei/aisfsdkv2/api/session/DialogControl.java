/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 弹窗控制项类
 *
 * <p>封装弹窗中的单个控制项（如按钮）。</p>
 */
public class DialogControl {

    /** 显示文本 */
    private String content;

    /** 控件类型 */
    private String type;

    /**
     * 默认构造方法
     */
    public DialogControl() {
        this.content = "";
        this.type = "";
    }

    /**
     * 获取显示文本
     *
     * @return 显示文本
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置显示文本
     *
     * @param content 显示文本
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取控件类型
     *
     * @return 控件类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置控件类型
     *
     * @param type 控件类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        return "{"
                + "\"content\":\"" + (content != null ? content : "") + "\""
                + ",\"type\":\"" + (type != null ? type : "") + "\""
                + "}";
    }
}
