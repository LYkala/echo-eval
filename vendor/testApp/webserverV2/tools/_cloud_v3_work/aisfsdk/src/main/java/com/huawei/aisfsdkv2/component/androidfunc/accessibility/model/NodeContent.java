/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.model;

/**
 * 无障碍节点内容信息
 *
 * <p>封装坐标点命中控件的内容，包含控件类型枚举和文本/描述。</p>
 *
 * @since 2026-04-21
 */
public class NodeContent {

    /** 控件类型枚举 */
    private final NodeType nodeType;

    /** 控件文本或内容描述 */
    private final String text;

    /**
     * 构造节点内容信息
     *
     * @param nodeType 控件类型枚举
     * @param text     控件文本或内容描述
     */
    public NodeContent(NodeType nodeType, String text) {
        this.nodeType = nodeType != null ? nodeType : NodeType.UNKNOWN;
        this.text = text != null ? text : "";
    }

    /**
     * 获取控件类型枚举
     *
     * @return NodeType枚举值
     */
    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * 获取控件文本或内容描述
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return nodeType.name() + ":" + text;
    }
}
