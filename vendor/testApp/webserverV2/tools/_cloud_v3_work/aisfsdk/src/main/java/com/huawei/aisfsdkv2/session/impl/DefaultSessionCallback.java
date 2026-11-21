/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.api.session.SessionCallback;
import com.huawei.aisfsdkv2.api.session.SessionResponseData;

/**
 * 默认会话回调实现
 *
 * <p>所有方法均为空实现，用于不需要处理回调的场景。</p>
 *
 * @since 2026-04-15
 */
public class DefaultSessionCallback implements SessionCallback {
    @Override
    public void onReceived(int errorCode, String errorMessage, SessionResponseData data) {
    }
}
