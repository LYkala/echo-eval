/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.handler.GuiSubMsgHandler;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;

/**
 * 操控完成消息子处理器
 *
 * <p>处理 GUI 操控完成信号，表示当前 GUI 任务已结束</p>
 *
 * @author AI Assistant
 * @since 2026-04-04
 * @see GuiSubMsgHandler
 */
public class CompletedMsgSubHandler implements GuiSubMsgHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("CompletedMsgSubHandler");

    /**
     * 获取支持的命令类型
     *
     * @return {@link GuiCmdType#COMPLETED}
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.COMPLETED;
    }

    /**
     * 处理操控完成消息
     *
     * <p>收到此消息表示服务端已完成 GUI 操控任务</p>
     *
     * @param msgInfo GUI 消息信息
     * @return 处理结果
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        LOGGER.info("CompletedMsgSubHandler: GUI task completed, taskId={}", msgInfo.getTaskId());
        return GuiHandleResult.success();
    }
}
