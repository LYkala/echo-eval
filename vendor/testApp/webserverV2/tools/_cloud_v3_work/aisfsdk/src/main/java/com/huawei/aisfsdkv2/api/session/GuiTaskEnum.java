/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * GUI任务类型枚举
 *
 * <p>定义GUI任务的类型，对应服务端 UserOutputObject.data.type 字段。</p>
 *
 * <p><b>类型说明：</b></p>
 * <ul>
 *   <li>START_GUI - 开始GUI操作任务（服务端type: STARTGUI）</li>
 *   <li>STOP_GUI - 结束GUI操作任务（服务端type: STOPGUI）</li>
 *   <li>START_MANUAL - 开始人工介入（服务端type: STARTMANUAL）</li>
 *   <li>START_TAKEOVER - 开始接管（服务端type: STARTTAKEOVER）</li>
 *   <li>STOP_TAKEOVER - 停止接管（服务端type: STOPTAKEOVER）</li>
 *   <li>CANCEL_TAKEOVER - 取消接管（服务端type: CANCELTAKEOVER）</li>
 *   <li>START_PAYMENT - 开始支付（服务端type: STARTPAYMENT）</li>
 * </ul>
 */
public enum GuiTaskEnum {
    /**
     * 开始GUI操作任务
     */
    START_GUI("START_GUI", "STARTGUI"),

    /**
     * 结束GUI操作任务
     */
    STOP_GUI("STOP_GUI", "STOPGUI"),

    /**
     * 开始支付
     */
    START_PAYMENT("START_PAYMENT", "STARTPAYMENT"),

    /**
     * 开始人工介入
     */
    START_MANUAL("START_MANUAL", "STARTMANUAL"),

    /**
     * 开始接管
     */
    START_TAKEOVER("START_TAKEOVER", "STARTTAKEOVER"),

    /**
     * 停止接管
     */
    STOP_TAKEOVER("STOP_TAKEOVER", "STOPTAKEOVER"),

    /**
     * 取消接管
     */
    CANCEL_TAKEOVER("CANCEL_TAKEOVER", "CANCELTAKEOVER");

    /** 枚举描述（内部使用，带下划线） */
    private final String description;

    /** 服务端type字段值（无下划线） */
    private final String serverType;

    GuiTaskEnum(String description, String serverType) {
        this.description = description;
        this.serverType = serverType;
    }

    /**
     * 获取描述信息
     *
     * @return 描述字符串
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取服务端type字段值
     *
     * @return 服务端type字符串
     */
    public String getServerType() {
        return serverType;
    }

    /**
     * 根据服务端type字符串获取对应的枚举值
     *
     * @param serverType 服务端type字符串（如 "STARTGUI", "STARTPAYMENT"）
     * @return 对应的枚举值，未找到返回null
     */
    public static GuiTaskEnum fromType(String serverType) {
        if (serverType == null || serverType.isEmpty()) {
            return null;
        }
        for (GuiTaskEnum value : values()) {
            if (value.serverType.equals(serverType)) {
                return value;
            }
        }
        return null;
    }
}
