/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade;

import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.facade.impl.ACMFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;

import java.io.File;
/**
 * ACM 接口 Facade
 * 封装 ACM 服务 HTTP 接口调用（认证、文件上传、文件下载）
 */
public interface ACMFacade {

    /**
     * 获取 ACM Facade 实例（单例）
     *
     * @return ACMFacade 实例
     */
    static ACMFacade getInstance() {
        return ACMFacadeImpl.getInstance();
    }

    /**
     * 认证获取 Token（同步）
     * 对应接口: POST /acm/v1/auth
     *
     * @param identifyId 设备标识ID，格式为"1.{deviceType}.{username}_{uuid}@AISF.cu.gd.com.cn"
     * @param username   用户名，用于认证账号
     * @param password   密码，用于认证
     * @return 认证结果，包含 Token 和过期时间
     * @throws AcmException 认证失败时抛出异常
     */
    AuthResult auth(String identifyId, String username, String password) throws AcmException;

    /**
     * 通过 Ticket 换取 Token（同步）
     * 对应接口: POST /acm/v1/auth
     *
     * @param ticket 票据，用于换取 Token
     * @return TokenResult 包含 Token 和过期时间，失败时返回 failure 结果
     */
    TokenResult authByTicket(String ticket);

    /**
     * 上传日志文件（同步）
     * 对应接口: POST /acm/v1/uploadfile
     *
     * @param file 要上传的日志文件对象
     * @return 上传结果，包含文件 UUID
     * @throws AcmException 上传失败时抛出异常
     */
    UploadResult uploadLogFile(File file) throws AcmException;

    /**
     * 上传图片文件（字节数组形式，同步）
     * 对应接口: POST /acm/v1/uploadfile
     *
     * @param name  图片文件名
     * @param bytes 图片文件字节数组
     * @return 上传结果，包含文件 UUID
     * @throws AcmException 上传失败时抛出异常
     */
    UploadResult uploadImageFile(String name, byte[] bytes) throws AcmException;

    /**
     * 上传图片文件（字节数组形式，同步，支持客户端主动关闭连接）
     * 对应接口: POST /acm/v1/uploadfile
     *
     * <p>当 {@code isClientClose=true} 时，请求头设置为 {@code Connection: keep-alive}，
     * 但客户端在请求完成后主动关闭连接，避免服务端先关闭连接导致 channelInactive
     * 覆盖成功响应的竞态问题。当 {@code isClientClose=false} 时，行为与
     * {@link #uploadImageFile(String, byte[])} 一致。</p>
     *
     * @param name          图片文件名
     * @param bytes         图片文件字节数组
     * @param isClientClose true 表示客户端主动关闭连接，false 表示遵循HTTP协议（默认行为）
     * @return 上传结果，包含文件 UUID
     * @throws AcmException 上传失败时抛出异常
     */
    UploadResult uploadImageFile(String name, byte[] bytes, boolean isClientClose) throws AcmException;

    /**
     * 下载文件（同步）
     * 对应接口: GET /acm/v1/downloadfile/{fileUrl}
     *
     * @param fileUrl  文件 URL（服务端文件标识）
     * @param savePath 本地保存路径
     * @return 保存的文件对象
     * @throws AcmException 下载失败时抛出异常
     */
    File downloadFile(String fileUrl, String savePath) throws AcmException;

    /**
     * 发送任务执行消息（同步）
     * 对应接口: POST /acm/v1/requestmsg
     *
     * <p>将 userInput 封装为 TaskAssignment 消息体发送到服务端，由服务端触发对应的 skill/agent/api 执行。</p>
     *
     * @param sessionId 会话ID
     * @param userInput 用户输入文本，即 {@code ScheduleAction#body}
     * @throws AcmException 发送失败时抛出异常
     */
    void sendTaskMessage(String sessionId, String userInput) throws AcmException;

    /**
     * 异步上传图片文件
     *
     * <p>使用异步HTTP请求上传图片，避免阻塞调用线程。</p>
     * <p>上传结果通过回调通知。</p>
     *
     * @param name 图片文件名
     * @param bytes 图片字节数组
     * @param callback 上传结果回调
     */
    void uploadImageFileAsync(String name, byte[] bytes, FileUploadCallback callback);

    /**
     * 异步上传日志文件
     *
     * <p>使用异步HTTP请求上传日志，避免阻塞调用线程。</p>
     * <p>上传结果通过回调通知。</p>
     *
     * @param file 日志文件对象
     * @param callback 上传结果回调
     */
    void uploadLogFileAsync(File file, FileUploadCallback callback);
}
