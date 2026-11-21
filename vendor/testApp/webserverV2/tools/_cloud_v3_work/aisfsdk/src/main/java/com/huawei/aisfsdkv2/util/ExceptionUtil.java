/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常工具类
 *
 * @author l00504561
 * @since 2026-04-10
 */
public final class ExceptionUtil {
    private ExceptionUtil() {
    }

    /**
     * 获取异常堆栈字符串
     *
     * @param e 异常对象
     * @return 异常堆栈字符串
     */
    public static String getStackTrace(Exception e) {
        if (e == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
