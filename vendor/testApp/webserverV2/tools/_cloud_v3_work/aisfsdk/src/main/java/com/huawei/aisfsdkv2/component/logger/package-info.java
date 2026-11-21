/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 日志组件包
 *
 * <p>本包提供 SDK 日志记录功能，支持多种日志级别、本地文件存储和远程上传。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.Logger}：日志接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.LoggerFactory}：日志工厂类</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.config}：日志配置</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.local}：本地日志存储</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.manager}：日志管理器</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.logger.model}：日志数据模型</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取日志记录器
 *   private static final Logger LOGGER = LoggerFactory.getLogger("MyClass");
 *
 *   // 记录不同级别的日志
 *   LOGGER.debug("Debug message: {}", value);
 *   LOGGER.info("Info message: {}", value);
 *   LOGGER.warn("Warning message: {}", value);
 *   LOGGER.error("Error message: {}", value, exception);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>每个类定义独立的 LOGGER 常量</li>
 *   <li>使用占位符 {} 避免字符串拼接开销</li>
 *   <li>DEBUG 级别日志只在开发环境启用</li>
 *   <li>异常信息使用 error 级别并传入异常对象</li>
 *   <li>敏感信息（密码、Token）不应输出到日志</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.logger;
