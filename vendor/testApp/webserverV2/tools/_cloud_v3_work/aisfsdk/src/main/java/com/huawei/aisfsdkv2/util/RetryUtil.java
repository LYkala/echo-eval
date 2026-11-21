/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2020. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 重试工具类
 *
 * @author l00504561
 * @since 2020-01-13
 */
public class RetryUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("RetryUtil");

    /** 重试次数：3次 */
    public static final int RETRY_COUNT_3 = 3;

    /** 重试次数：5次 */
    public static final int RETRY_COUNT_5 = 5;

    /** 重试次数：10次 */
    public static final int RETRY_COUNT_10 = 10;

    /** 重试次数：无限次 */
    public static final int RETRY_COUNT_NA = Integer.MAX_VALUE;

    /** 重试间隔：50毫秒 */
    public static final int RETRY_BACKUP_50_MS = 50;

    /** 重试间隔：500毫秒 */
    public static final int RETRY_BACKUP_500_MS = 500;

    /** 重试间隔：1000毫秒（1秒） */
    public static final int RETRY_BACKUP_1000_MS = 1000;

    /** 重试间隔：5000毫秒（5秒） */
    public static final int RETRY_BACKUP_5000_MS = 5000;

    /**
     * 重试机制
     * 抛异常会认为一定需要重试，为了扩展允许用户自定义检查方法
     *
     * @param supplier      需要执行重试的方法
     * @param maxRetryCount 重试最大次数
     * @param delay         每一次重试间隔时间(单位毫秒)
     * @return 结果（返回为空表示重试最终失败）
     */
    public static <T> T retry(Supplier<T> supplier, Function<T, Boolean> check, int maxRetryCount, int delay) {
        for (int i = 0; i < maxRetryCount; i++) {
            try {
                T result = supplier.get();
                boolean status = check.apply(result);
                if (status) {
                    return result;
                }
            } catch (Throwable t) {
                LOGGER.error("RetryUtil.retry: retry execute error", t);
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                LOGGER.error("RetryUtil.retry: retry failure wait is interrupted", e);
            }
        }
        return null;
    }

    /**
     * 重试机制
     * 抛异常会认为一定需要重试，为了扩展允许用户自定义检查方法
     *
     * @param supplier      需要执行重试的方法
     * @param maxRetryCount 重试最大次数
     * @param delay         每一次重试间隔时间(单位毫秒)
     * @return 结果（返回结果为所调用方法的最终返回结果）
     */
    public static <T> T retryWithReturnObject(Supplier<T> supplier, Function<T, Boolean> check, int maxRetryCount, int delay) {
        T result = null;
        for (int i = 0; i < maxRetryCount; i++) {
            try {
                result = supplier.get();
                boolean status = check.apply(result);
                if (status) {
                    return result;
                }
            } catch (Throwable t) {
                LOGGER.error("RetryUtil.retry: retry execute error", t);
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                LOGGER.error("RetryUtil.retry: retry failure wait is interrupted", e);
            }
        }
        return result;
    }
}

