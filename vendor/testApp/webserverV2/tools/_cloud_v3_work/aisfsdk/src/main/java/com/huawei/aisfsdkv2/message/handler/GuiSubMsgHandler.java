/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler;

import com.huawei.aisfsdkv2.component.androidfunc.AndroidFuncManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;
import com.huawei.aisfsdkv2.message.model.GuiMsgInfo;
import com.huawei.aisfsdkv2.message.model.gui.GuiHandleResult;

/**
 * GUI子消息处理器接口
 *
 * <p>针对特定GUI命令类型的处理器，实现具体业务逻辑。</p>
 *
 * <p>实现类需要注册到 {@link com.huawei.aisfsdkv2.message.handler.impl.GuiMsgHandler} 中。</p>
 */
public interface GuiSubMsgHandler {

    /**
     * 获取支持的命令类型
     *
     * @return 该处理器支持的GUI命令类型
     */
    GuiCmdType getCmdType();

    /**
     * 处理GUI消息
     *
     * <p>处理接收到的GUI命令消息，执行相应的操作。</p>
     *
     * @param msgInfo GUI消息信息
     * @return 处理结果对象，包含成功/失败状态、错误码和描述
     */
    GuiHandleResult handle(GuiMsgInfo msgInfo);

    /**
     * 获取处理器名称
     *
     * @return 处理器名称，用于日志和调试
     */
    default String getHandlerName() {
        return getClass().getSimpleName();
    }

    /**
     * 获取无障碍管理器（动态获取）
     *
     * <p>每次调用都从 AndroidFuncManager 动态获取，确保服务断开重连后实例更新。</p>
     *
     * @return AccessibilityManager 实例，如果服务未连接则返回 null
     */
    default AccessibilityManager getAccessibilityManager() {
        return AndroidFuncManager.getInstance().getAccessibilityManager();
    }
}
