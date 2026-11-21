/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.gui;

import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;

/**
 * Home键消息子处理器
 *
 * <p>处理 Android Home键操作，支持截图上传</p>
 *
 * @author AI Assistant
 * @since 2026-04-04
 */
public class HomeMsgSubHandler extends AbstractSystemKeyMsgSubHandler {

    /**
     * 获取支持的命令类型
     *
     * @return {@link GuiCmdType#HOME}
     */
    @Override
    public GuiCmdType getCmdType() {
        return GuiCmdType.HOME;
    }

    /**
     * 处理Home键消息
     *
     * @param msgInfo GUI 消息信息
     * @return 处理结果
     */
    @Override
    public GuiHandleResult handle(GuiMsgInfo msgInfo) {
        return handleSystemKey(msgInfo, "Home", getAccessibilityManager()::goHome);
    }
}
