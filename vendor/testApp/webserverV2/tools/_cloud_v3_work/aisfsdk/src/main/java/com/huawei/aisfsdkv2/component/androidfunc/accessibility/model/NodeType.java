/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.model;

/**
 * 无障碍节点控件类型枚举
 *
 * <p>覆盖常用的Android控件类型，通过全限定类名映射到枚举值。</p>
 * <p>未识别的类型统一映射为 {@link #UNKNOWN}。</p>
 *
 * @since 2026-04-21
 */
public enum NodeType {

    /** 按钮 */
    BUTTON("android.widget.Button"),

    /** 文本视图 */
    TEXT_VIEW("android.widget.TextView"),

    /** 输入框 */
    EDIT_TEXT("android.widget.EditText"),

    /** 图片视图 */
    IMAGE_VIEW("android.widget.ImageView"),

    /** 图片按钮 */
    IMAGE_BUTTON("android.widget.ImageButton"),

    /** 复选框 */
    CHECK_BOX("android.widget.CheckBox"),

    /** 单选按钮 */
    RADIO_BUTTON("android.widget.RadioButton"),

    /** 开关 */
    SWITCH("android.widget.Switch"),

    /** 切换按钮 */
    TOGGLE_BUTTON("android.widget.ToggleButton"),

    /** 下拉选择框 */
    SPINNER("android.widget.Spinner"),

    /** 进度条 */
    PROGRESS_BAR("android.widget.ProgressBar"),

    /** 滑动条 */
    SEEK_BAR("android.widget.SeekBar"),

    /** 列表视图 */
    LIST_VIEW("android.widget.ListView"),

    /** 网格视图 */
    GRID_VIEW("android.widget.GridView"),

    /** 滚动视图 */
    SCROLL_VIEW("android.widget.ScrollView"),

    /** 横向滚动视图 */
    HORIZONTAL_SCROLL_VIEW("android.widget.HorizontalScrollView"),

    /** 容器：View */
    VIEW("android.view.View"),

    /** 容器：ViewGroup */
    VIEW_GROUP("android.view.ViewGroup"),

    /** 容器：FrameLayout */
    FRAME_LAYOUT("android.widget.FrameLayout"),

    /** 容器：LinearLayout */
    LINEAR_LAYOUT("android.widget.LinearLayout"),

    /** 容器：RelativeLayout */
    RELATIVE_LAYOUT("android.widget.RelativeLayout"),

    /** 容器：ConstraintLayout */
    CONSTRAINT_LAYOUT("androidx.constraintlayout.widget.ConstraintLayout"),

    /** 容器：RecyclerView */
    RECYCLER_VIEW("androidx.recyclerview.widget.RecyclerView"),

    /** 未识别的控件类型 */
    UNKNOWN("");

    private final String className;

    NodeType(String className) {
        this.className = className;
    }

    /**
     * 获取全限定类名
     *
     * @return 如 android.widget.Button
     */
    public String getClassName() {
        return className;
    }

    /**
     * 根据全限定类名查找对应的枚举值
     *
     * @param className 全限定类名
     * @return 对应的NodeType，未识别返回UNKNOWN
     */
    public static NodeType fromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return UNKNOWN;
        }
        for (NodeType type : values()) {
            if (type != UNKNOWN && type.className.equals(className)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
