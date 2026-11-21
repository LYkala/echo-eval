/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.session;

/**
 * 用户文本输出类
 *
 * <p>封装用户文本输出内容及相关状态信息。</p>
 */
public class TextInfo {
    /** 用户输出文本内容 */
    private String text;

    /** 流式完成标志，true 表示流式输出已完成 */
    private boolean streamFinishFlag;

    /** 是否语音播报，true 表示需要语音播报 */
    private boolean isVoiceBroadcast;

    /**
     * 返回对象的JSON格式字符串表示
     *
     * @return JSON格式的字符串
     */
    @Override
    public String toString() {
        return "{\"text\":\"" + (text != null ? text : "") + "\",\"streamFinishFlag\":" + streamFinishFlag + ",\"isVoiceBroadcast\":" + isVoiceBroadcast + "}";
    }

    /**
     * 获取用户输出文本
     *
     * @return 用户输出文本
     */
    public String getText() {
        return text;
    }

    /**
     * 设置用户输出文本
     *
     * @param text 用户输出文本
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取流式完成标志
     *
     * @return true 表示流式输出已完成
     */
    public boolean isStreamFinishFlag() {
        return streamFinishFlag;
    }

    /**
     * 设置流式完成标志
     *
     * @param streamFinishFlag 流式完成标志
     */
    public void setStreamFinishFlag(boolean streamFinishFlag) {
        this.streamFinishFlag = streamFinishFlag;
    }

    /**
     * 获取是否语音播报
     *
     * @return true 表示需要语音播报
     */
    public boolean isVoiceBroadcast() {
        return isVoiceBroadcast;
    }

    /**
     * 设置是否语音播报
     *
     * @param voiceBroadcast 是否语音播报
     */
    public void setVoiceBroadcast(boolean voiceBroadcast) {
        isVoiceBroadcast = voiceBroadcast;
    }
}
