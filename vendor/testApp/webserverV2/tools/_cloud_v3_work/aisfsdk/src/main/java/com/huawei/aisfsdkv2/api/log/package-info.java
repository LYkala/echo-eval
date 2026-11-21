/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 日志 API 接口包
 *
 * <p>本包提供 SDK 日志配置和管理能力，支持日志级别设置、本地日志配置和日志上传触发。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.log.LogApi}：日志接口，提供日志配置和上传能力</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.log.SdkLogConfig}：SDK 日志配置类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.log.LogLevelEnum}：日志级别枚举</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.log.SdkLocalLogConfig}：本地日志配置类</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取日志 API 实例
 *   LogApi logApi = SdkManager.getInstance().getLogApi();
 *
 *   // 配置日志级别
 *   SdkLogConfig config = new SdkLogConfig();
 *   config.setLogLevel(LogLevelEnum.DEBUG);
 *   logApi.setSdkLogConfig(config);
 *
 *   // 配置本地日志
 *   SdkLocalLogConfig localConfig = new SdkLocalLogConfig();
 *   localConfig.setMaxFileSize(10 * 1024 * 1024); // 10MB
 *   localConfig.setMaxFileCount(5);
 *   config.setLocalLogConfig(localConfig);
 *
 *   // 触发日志上传
 *   logApi.uploadLogTrigger();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>Release 环境建议使用 WARN 或 ERROR 级别，减少性能开销</li>
 *   <li>Debug 环境可使用 DEBUG 级别，便于问题排查</li>
 *   <li>本地日志文件大小和数量需合理设置，避免占用过多存储</li>
 *   <li>日志上传建议在 WiFi 环境下触发，节省用户流量</li>
 *   <li>敏感信息不应输出到日志中</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.log;
