/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger;

import android.util.Log;

import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.component.logger.local.LocalFileLogger;
import com.huawei.aisfsdkv2.component.logger.model.LogEvent;
import com.huawei.aisfsdkv2.constants.NumberConstants;
import com.huawei.aisfsdkv2.component.trace.TraceManager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 日志接口实现类
 *
 * <p>支持双写输出：</p>
 * <ol>
 *   <li>Android Log 控制台输出（Logcat）</li>
 *   <li>本地文件写入（异步队列）</li>
 * </ol>
 *
 * @author l00504561
 * @since 2026-03-27
 */
public class LoggerImpl implements Logger {

    /** SDK 日志统一前缀，便于 Logcat 过滤。使用方式: adb logcat -s AISFSDK:D */
    private static final String LOG_TAG_PREFIX = "AISFSDK";

    /** 敏感字段列表（忽略大小写），默认空集合，预埋功能 */
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>();

    static {
        SENSITIVE_FIELDS.add("sessionId");
        SENSITIVE_FIELDS.add("ClientSessionId");
        SENSITIVE_FIELDS.add("token");
        SENSITIVE_FIELDS.add("IdentifyId");
        SENSITIVE_FIELDS.add("DeviceId");
    }

    /** 标准输出开关，默认 false */
    private static volatile boolean stdoutEnable = false;

    /** 安全过滤开关，默认 true */
    private static volatile boolean securityFilterEnable = true;

    /** 日志总开关，默认 false */
    private static volatile boolean logEnable = false;

    private final String tag;

    /**
     * 构造方法
     *
     * <p>使用传入的标签构建完整 tag，格式为 {@code AISFSDK_标签名}。
     * 使用字符串标签避免 Android 混淆后类名变成 a、b、c 的问题。</p>
     *
     * @param tag 日志标签，如 "MessageProcessorImpl"
     */
    public LoggerImpl(String tag) {
        this.tag = LOG_TAG_PREFIX + "_" + tag;
    }

    /**
     * 打印 DEBUG 级别日志
     *
     * @param format 格式字符串，使用 {} 作为占位符
     * @param args   参数列表
     */
    @Override
    public void debug(String format, Object... args) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, args);
        Log.d(tag, message);
        writeToFile(LogLevelEnum.DEBUG, message);
        writeToStdout(LogLevelEnum.DEBUG, message);
    }

    /**
     * 打印 DEBUG 级别日志（带异常）
     *
     * @param format 消息
     * @param t      异常
     */
    @Override
    public void debug(String format, Throwable t) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, t);
        Log.d(tag, message);
        writeToFile(LogLevelEnum.DEBUG, message);
        writeToStdout(LogLevelEnum.DEBUG, message);
    }

    /**
     * 打印 INFO 级别日志
     *
     * @param format 格式字符串，使用 {} 作为占位符
     * @param args   参数列表
     */
    @Override
    public void info(String format, Object... args) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, args);
        Log.i(tag, message);
        writeToFile(LogLevelEnum.INFO, message);
        writeToStdout(LogLevelEnum.INFO, message);
    }

    /**
     * 打印 INFO 级别日志（带异常）
     *
     * @param format 消息
     * @param t      异常
     */
    @Override
    public void info(String format, Throwable t) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, t);
        Log.i(tag, message);
        writeToFile(LogLevelEnum.INFO, message);
        writeToStdout(LogLevelEnum.INFO, message);
    }

    /**
     * 打印 WARN 级别日志
     *
     * @param format 格式字符串，使用 {} 作为占位符
     * @param args   参数列表
     */
    @Override
    public void warn(String format, Object... args) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, args);
        Log.w(tag, message);
        writeToFile(LogLevelEnum.WARN, message);
        writeToStdout(LogLevelEnum.WARN, message);
    }

    /**
     * 打印 WARN 级别日志（带异常）
     *
     * @param format 消息
     * @param t      异常
     */
    @Override
    public void warn(String format, Throwable t) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, t);
        Log.w(tag, message);
        writeToFile(LogLevelEnum.WARN, message);
        writeToStdout(LogLevelEnum.WARN, message);
    }

    /**
     * 打印 ERROR 级别日志
     *
     * @param format 格式字符串，使用 {} 作为占位符
     * @param args   参数列表
     */
    @Override
    public void error(String format, Object... args) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, args);
        Log.e(tag, message);
        writeToFile(LogLevelEnum.ERROR, message);
        writeToStdout(LogLevelEnum.ERROR, message);
    }

    /**
     * 打印 ERROR 级别日志（带异常）
     *
     * @param format 消息
     * @param t      异常
     */
    @Override
    public void error(String format, Throwable t) {
        if (!logEnable) {
            return;
        }
        String message = buildLoggerLine(format, t);
        Log.e(tag, message);
        writeToFile(LogLevelEnum.ERROR, message);
        writeToStdout(LogLevelEnum.ERROR, message);
    }

    /**
     * 写入日志到文件
     *
     * <p>仅当 LocalFileLogger 已启动时才写入。
     * 自动从 {@link TraceManager} 获取当前线程的 taskId 填入日志事件，不存在时为空字符串。</p>
     *
     * @param level   日志级别
     * @param message 日志内容
     */
    private void writeToFile(LogLevelEnum level, String message) {
        LocalFileLogger fileLogger = LocalFileLogger.getInstance();
        if (fileLogger.isStarted()) {
            String taskId = TraceManager.getInstance().getCurrentTaskId();
            LogEvent logEvent = new LogEvent(level, tag, message, taskId);
            fileLogger.writeLogFile(logEvent);
        }
    }

    /**
     * 更新日志配置
     *
     * @param stdoutEnable 标准输出开关
     * @param securityFilterEnable 安全过滤开关
     * @param logEnable 日志总开关
     */
    public static void updateConfig(boolean stdoutEnable, boolean securityFilterEnable, boolean logEnable) {
        LoggerImpl.stdoutEnable = stdoutEnable;
        LoggerImpl.securityFilterEnable = securityFilterEnable;
        LoggerImpl.logEnable = logEnable;
    }

    /**
     * 输出日志到标准输出
     *
     * <p>仅当 stdoutEnable 开关打开时才输出。</p>
     *
     * @param level   日志级别
     * @param message 日志内容
     */
    private void writeToStdout(LogLevelEnum level, String message) {
        if (stdoutEnable) {
            System.out.println("[" + level + "][" + tag + "] " + message);
        }
    }

    /**
     * 构建日志内容（格式化参数）
     *
     * <p>使用 {} 作为占位符，逐个替换为参数值。</p>
     *
     * @param format    格式字符串
     * @param arguments 参数列表
     * @return 格式化后的日志内容
     */
    private String buildLoggerLine(String format, Object... arguments) {
        if (arguments == null || arguments.length == 0) {
            return desensitize(format);
        }
        StringBuilder result = new StringBuilder(format);
        for (Object argument : arguments) {
            int placeholderIndex = result.indexOf("{}");
            if (placeholderIndex < 0) {
                break;
            }
            String itemString = argument != null ? argument.toString() : "null";
            result.replace(placeholderIndex, placeholderIndex + NumberConstants.NUMBER_2, itemString);
        }
        return desensitize(result.toString());
    }

    /**
     * 构建日志内容（带异常堆栈）
     *
     * @param msg 消息
     * @param t   异常
     * @return 包含异常堆栈的日志内容
     */
    private String buildLoggerLine(String msg, Throwable t) {
        StringBuilder result = new StringBuilder(desensitize(msg));
        result.append(System.lineSeparator());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream,
                StandardCharsets.UTF_8))) {
            t.printStackTrace(printWriter);
        }
        String stackTraceString;
        try {
            stackTraceString = byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        result.append(stackTraceString);
        return result.toString();
    }

    /** 敏感信息最小长度，小于此长度不脱敏 */
    private static final int MIN_SENSITIVE_LENGTH = 6;

    /**
     * 脱敏处理
     *
     * <p>遍历敏感字段列表，对每个字段在消息中查找并脱敏。
     * 支持格式：field=value、field":value、field":"value" 等。
     * 从字段名后第3个字符开始查找值，到引号/逗号/句号/空格/右括号结束。</p>
     *
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    private String desensitize(String message) {
        if (!securityFilterEnable || message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        for (String field : SENSITIVE_FIELDS) {
            result = desensitizeField(result, field);
        }
        return result;
    }

    /**
     * 对单个字段进行脱敏处理
     *
     * <p>循环查找字段名所有出现位置，对每个匹配到的位置：</p>
     * <ol>
     *   <li>智能识别分隔符（支持field=value、field":value、field":"value格式）</li>
     *   <li>向后查找值的结束位置（引号、逗号、空格、右括号、右花括号）</li>
     *   <li>如果值长度>=MIN_SENSITIVE_LENGTH，则替换为******</li>
     * </ol>
     *
     * @param message 原始消息
     * @param field   字段名（忽略大小写匹配）
     * @return 脱敏后的消息
     */
    private String desensitizeField(String message, String field) {
        if (message == null || message.isEmpty() || field == null || field.isEmpty()) {
            return message;
        }
        StringBuilder result = new StringBuilder(message);
        String lowerMessage = message.toLowerCase();
        String lowerField = field.toLowerCase();
        int fieldLen = field.length();
        int searchPos = 0;
        while (true) {
            int fieldPos = lowerMessage.indexOf(lowerField, searchPos);
            if (fieldPos < 0) {
                break;
            }
            int valueStart = findValueStart(result.toString(), fieldPos + fieldLen);
            if (valueStart < 0 || valueStart >= result.length()) {
                searchPos = fieldPos + fieldLen;
                continue;
            }
            int valueEnd = findValueEnd(result.toString(), valueStart);
            if (valueEnd > valueStart) {
                String value = result.substring(valueStart, valueEnd);
                if (value.length() >= MIN_SENSITIVE_LENGTH) {
                    result.replace(valueStart, valueEnd, "******");
                    lowerMessage = result.toString().toLowerCase();
                }
            }
            searchPos = valueStart + 1;
        }
        return result.toString();
    }

    /**
     * 查找值的起始位置
     *
     * <p>从字段名结尾位置开始，智能识别分隔符格式：</p>
     * <ul>
     *   <li>field=value：跳过=</li>
     *   <li>field":value 或 field":"value：跳过"和:组合</li>
     *   <li>转义JSON格式（\"代替"）：如 field\\":value 或 field\\"":"value</li>
     * </ul>
     *
     * @param message     消息字符串
     * @param afterField  字段名结尾位置
     * @return 值起始位置，如果无法识别分隔符则返回-1
     */
    private int findValueStart(String message, int afterField) {
        int len = message.length();
        int pos = skipSpaces(message, afterField, len);
        if (pos >= len) {
            return -1;
        }
        char c = message.charAt(pos);
        if (c == '\\' && pos + 1 < len) {
            pos++;
            c = message.charAt(pos);
        }
        return resolveValueStart(message, pos, len, c);
    }

    /**
     * 跳过空白字符
     *
     * @param message 消息字符串
     * @param pos     起始位置
     * @param len     字符串长度
     * @return 第一个非空白字符位置
     */
    private int skipSpaces(String message, int pos, int len) {
        while (pos < len && message.charAt(pos) == ' ') {
            pos++;
        }
        return pos;
    }

    /**
     * 根据分隔符解析值起始位置
     *
     * @param message   消息字符串
     * @param pos       分隔符位置
     * @param len       字符串长度
     * @param delimiter 分隔符字符
     * @return 值起始位置
     */
    private int resolveValueStart(String message, int pos, int len, char delimiter) {
        if (delimiter == '=') {
            return pos + 1;
        }
        if (delimiter == '"') {
            return handleQuoteFormat(message, pos + 1, len);
        }
        if (delimiter == ':') {
            return handleColonFormat(message, pos + 1, len);
        }
        return -1;
    }

    /**
     * 处理引号分隔符格式（field":value 或 field":"value）
     *
     * @param message 消息字符串
     * @param pos     引号后的位置
     * @param len     字符串长度
     * @return 值起始位置
     */
    private int handleQuoteFormat(String message, int pos, int len) {
        pos = skipBackslash(message, pos, len);
        if (pos >= len || message.charAt(pos) != ':') {
            return -1;
        }
        pos = skipBackslash(message, pos + 1, len);
        if (pos >= len) {
            return pos;
        }
        if (message.charAt(pos) == '"') {
            return pos + 1;
        }
        return pos;
    }

    /**
     * 处理冒号分隔符格式（field:value）
     *
     * @param message 消息字符串
     * @param pos     冒号后的位置
     * @param len     字符串长度
     * @return 值起始位置
     */
    private int handleColonFormat(String message, int pos, int len) {
        pos = skipBackslash(message, pos, len);
        if (pos >= len) {
            return pos;
        }
        if (message.charAt(pos) == '"') {
            return pos + 1;
        }
        return pos;
    }

    /**
     * 跳过反斜杠转义字符
     *
     * @param message 消息字符串
     * @param pos     当前位置
     * @param len     字符串长度
     * @return 反斜杠后的位置（若当前不是反斜杠则返回原位置）
     */
    private int skipBackslash(String message, int pos, int len) {
        if (pos < len && message.charAt(pos) == '\\') {
            return pos + 1;
        }
        return pos;
    }

    /**
     * 查找值的结束位置
     *
     * <p>从指定位置开始，查找以下分隔符作为值结束（支持转义字符）：</p>
     * <ul>
     *   <li>" (双引号，但跳过 \\")</li>
     *   <li>, (逗号)</li>
     *   <li> (空格)</li>
     *   <li>) (右括号)</li>
     *   <li>} (右花括号)</li>
     *   <li>] (右方括号)</li>
     *   <li>\n (换行)</li>
     *   <li>\r (回车)</li>
     * </ul>
     *
     * @param message 消息字符串
     * @param start   值起始位置
     * @return 值结束位置（分隔符位置），如果未找到则返回字符串末尾
     */
    private int findValueEnd(String message, int start) {
        int len = message.length();
        for (int i = start; i < len; i++) {
            char c = message.charAt(i);
            if (c == '\\' && i + 1 < len && message.charAt(i + 1) == '"') {
                i++;
                continue;
            }
            if (c == '"' || c == ',' || c == ' ' || c == ')' || c == '}' || c == ']' || c == '\n' || c == '\r') {
                return i;
            }
        }
        return len;
    }
}
