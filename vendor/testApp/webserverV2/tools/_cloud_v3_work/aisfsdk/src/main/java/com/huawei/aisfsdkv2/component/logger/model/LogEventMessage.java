/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.model;

/**
 * 日志事件消息对象，封装了日志事件的详细内容。
 * <p>
 * 包含服务名称、事件名称、详细描述和执行状态等信息。
 * 用于构建标准化的日志消息格式，便于日志的解析和分析。
 * </p>
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class LogEventMessage {
    /** 服务名称，默认为"SDK" */
    private String serviceName = "SDK";

    /** 事件名称，用于标识具体的事件类型 */
    private String eventName;

    /** 详细描述，包含事件的具体信息 */
    private String detail;

    /** 执行状态，默认为"success" */
    private String status = "success";

    /**
     * 构造日志事件消息对象。
     * <p>
     * 服务名称默认为"SDK"，状态默认为"success"。
     * </p>
     *
     * @param eventName 事件名称，用于标识事件类型
     * @param detail 详细的事件描述信息
     */
    public LogEventMessage(String eventName, String detail) {
        this.eventName = eventName;
        this.detail = detail;
    }

    /**
     * 获取服务名称。
     *
     * @return 服务名称字符串
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 设置服务名称。
     *
     * @param serviceName 服务名称字符串
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * 获取事件名称。
     *
     * @return 事件名称字符串
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * 设置事件名称。
     *
     * @param eventName 事件名称字符串
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * 获取执行状态。
     *
     * @return 状态字符串，如"success"、"failed"等
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置执行状态。
     *
     * @param status 状态字符串
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取详细描述。
     *
     * @return 详细描述字符串
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 设置详细描述。
     *
     * @param detail 详细描述字符串
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }
}
