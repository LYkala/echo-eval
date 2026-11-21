/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.manager;

import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.logger.local.LocalFileLogger;
import com.huawei.aisfsdkv2.component.logger.model.LogEvent;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 捕获crash异常，并写入日志文件
 *
 * @author l00504561
 * @since 2026-02-08
 */
public class CrashCatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("CrashCatcher");

    private static final CrashCatcher crashCatcher = new CrashCatcher();

    private final AtomicBoolean initFlag = new AtomicBoolean(Boolean.FALSE);

    private CrashCatcher() {
    }

    public static CrashCatcher getInstance() {
        return crashCatcher;
    }

    /**
     * 初始化
     */
    public synchronized void init() {
        if (initFlag.get()) {
            return;
        }
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            // 异步写日志
            LOGGER.error("CrashCatcher.init: " + e.getMessage(), e);
            // 同步写日志
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream,
                    StandardCharsets.UTF_8))) {
                e.printStackTrace(printWriter);
            }
            String stackTraceString;
            try {
                stackTraceString = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e1) {
                throw new RuntimeException(e1);
            }
            LogEvent logEvent = new LogEvent(LogLevelEnum.ERROR, CrashCatcher.class.getName(), stackTraceString);
            LocalFileLogger.getInstance().writeCrashLogFile(logEvent);
            // 其他
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e);
            }
        });
        initFlag.set(Boolean.TRUE);
    }
}
