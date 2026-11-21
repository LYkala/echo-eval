/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹窗信息类
 *
 * <p>封装弹窗的详细信息，包括标题和控制按钮列表。</p>
 */
public class DialogInfo {

    /** 弹窗标题 */
    private String title;

    /** 控制按钮列表 */
    private List<DialogControl> controlList;

    /**
     * 默认构造方法
     */
    public DialogInfo() {
        this.title = "";
        this.controlList = new ArrayList<>();
    }

    /**
     * 获取弹窗标题
     *
     * @return 弹窗标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置弹窗标题
     *
     * @param title 弹窗标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取控制按钮列表
     *
     * @return 控制按钮列表
     */
    public List<DialogControl> getControlList() {
        return controlList;
    }

    /**
     * 设置控制按钮列表
     *
     * @param controlList 控制按钮列表
     */
    public void setControlList(List<DialogControl> controlList) {
        this.controlList = controlList != null ? controlList : new ArrayList<>();
    }

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"title\":\"").append(title != null ? title : "").append("\"");
        sb.append(",\"controlList\":");
        if (controlList != null) {
            sb.append("[");
            for (int i = 0; i < controlList.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(controlList.get(i).toString());
            }
            sb.append("]");
        } else {
            sb.append("[]");
        }
        sb.append("}");
        return sb.toString();
    }
}
