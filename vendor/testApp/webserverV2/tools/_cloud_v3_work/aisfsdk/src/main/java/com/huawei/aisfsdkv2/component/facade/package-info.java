/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * ACM 服务 Facade 组件包
 *
 * <p>本包提供 ACM（AI Service Framework）后端服务的 HTTP 接口封装，
 * 包括认证、文件上传下载、消息发送等功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.facade.AcmFacade}：ACM 服务 Facade 接口</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.component.facade.impl}：Facade 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.component.facade.model}：数据模型</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取 ACM Facade 实例
 *   ACMFacade acm = ACMFacade.getInstance();
 *
 *   // 认证获取 Token
 *   AuthResult result = acm.auth(identifyId, username, password);
 *   String token = result.getToken();
 *
 *   // 通过 Ticket 换取 Token
 *   String token = acm.authByTicket(ticket);
 *
 *   // 上传日志文件
 *   UploadResult upload = acm.uploadLogFile(logFile);
 *   String fileUuid = upload.getFileUuid();
 *
 *   // 上传图片
 *   UploadResult upload = acm.uploadImageFile("screenshot.png", imageBytes);
 *
 *   // 下载文件
 *   File file = acm.downloadFile(fileUrl, savePath);
 *
 *   // 发送任务消息
 *   acm.sendTaskMessage("执行天气查询");
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>认证操作是同步阻塞的，需在后台线程调用</li>
 *   <li>Token 过期后自动刷新，业务层无需处理</li>
 *   <li>大文件上传建议使用独立线程</li>
 *   <li>处理 AcmException 异常，获取详细错误信息</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.component.facade;
