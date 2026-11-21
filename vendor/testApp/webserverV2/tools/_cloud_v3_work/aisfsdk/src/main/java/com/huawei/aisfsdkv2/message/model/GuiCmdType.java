/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.model;

/**
 * GUI命令类型枚举
 *
 * <p>定义所有支持的GUI操作命令类型。
 * 每个枚举值对应一个唯一的命令代码和描述信息。</p>
 *
 * <p><b>命令分类:</b></p>
 * <ul>
 *   <li>截图相关: SCREEN_SHOT</li>
 *   <li>点击操作: CLICK, LONG_CLICK</li>
 *   <li>滑动操作: SWIPE</li>
 *   <li>文本输入: INPUT_TEXT</li>
 *   <li>系统按键: HOME, BACK, ENTER</li>
 *   <li>任务控制: COMPLETED</li>
 * </ul>
 */
public enum GuiCmdType {
    /** 未知命令 */
    UNKNOWN(-1, "未知命令"),

    /** 拉起APP */
    LAUNCH_APP(1003, "拉起APP"),

    /** 截图 */
    SCREEN_SHOT(1004, "截图"),

    /** 上传截图 */
    UPLOAD_SCREEN_SHOT(1005, "上传截图"),

    /** 点击 */
    CLICK(1006, "点击"),

    /** 滑动 */
    SWIPE(1007, "滑动"),

    /** 长按 */
    LONG_CLICK(1008, "长按"),

    /** 输入文本 */
    INPUT_TEXT(1009, "输入文本"),

    /** 操控完成 */
    COMPLETED(1010, "操控完成"),

    /** 操控异常 */
    EXCEPTION(1011, "操控异常"),

    /** Home键 */
    HOME(1012, "Home键"),

    /** Back键 */
    BACK(1013, "Back键"),

    /** Enter键 */
    ENTER(1014, "Enter键"),

    /** 检查应用是否安装 */
    CHECK_APP_INSTALLED(1018, "检查应用是否安装"),

    /** 应用安装状态响应 */
    APP_INSTALL_STATUS(1019, "应用安装状态响应");

    private final int code;
    private final String desc;

    /**
     * 构造方法
     *
     * @param code 命令代码
     * @param desc 命令描述
     */
    GuiCmdType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取命令代码
     *
     * @return 命令代码值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取命令描述
     *
     * @return 命令描述文本
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举类型
     *
     * @param code 命令代码
     * @return 对应的枚举类型，如果找不到返回 UNKNOWN
     */
    public static GuiCmdType fromCode(int code) {
        for (GuiCmdType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
