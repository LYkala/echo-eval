/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerEnvEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.AuthResult;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.httpclient.callback.HttpCallback;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.constants.HttpStatusCode;
import com.huawei.aisfsdkv2.login.auth.tokenrefresher.TokenResult;
import com.huawei.aisfsdkv2.util.FileUtil;
import com.huawei.aisfsdkv2.util.RandomUtil;
import com.huawei.aisfsdkv2.util.TimeUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * ACM 接口 Facade 实现
 *
 * <p>封装 ACM 服务 HTTP 接口调用，提供以下功能：
 * <ul>
 *   <li>ACM 认证：获取 Token 及有效期</li>
 *   <li>Ticket 认证：通过票据换取 Token</li>
 *   <li>文件上传：日志文件、图片文件上传</li>
 *   <li>文件下载：从 ACM 服务下载文件</li>
 *   <li>任务消息：发送任务执行消息</li>
 * </ul>
 *
 * <p>HTTP 客户端通过 {@link HttpClientManager} 获取：
 * <ul>
 *   <li>普通请求：使用 {@link HttpClientManager#getCommonHttpClient()}</li>
 *   <li>文件操作：使用 {@link HttpClientManager#getFileHttpClient()}</li>
 * </ul>
 *
 * @see HttpClientManager
 */
public class ACMFacadeImpl implements ACMFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger("ACMFacadeImpl");

    // ACM 接口路径
    private static final String AUTH_URL = "/acm/v1/auth";
    private static final String UPLOAD_URL = "/acm/v1/uploadfile";
    private static final String DOWNLOAD_URL = "/acm/v1/downloadfile";
    private static final String TASK_MESSAGE_URL = "/acm/v1/requestmsg";

    private static class Holder {
        private static final ACMFacadeImpl INSTANCE = new ACMFacadeImpl();
    }

    /**
     * 获取单例实例（供接口 {@link com.huawei.aisfsdkv2.component.facade.ACMFacade#getInstance()} 调用）
     *
     * @return ACMFacadeImpl 单例实例
     */
    public static ACMFacadeImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 私有构造方法
     */
    private ACMFacadeImpl() {
    }

    /**
     * ACM 认证，获取 Token 及有效期（同步）
     *
     * <p>请求格式：
     * <pre>POST /acm/v1/auth
     * Headers: AppName, DeviceId, DeviceCategory, UUID
     * Body: {"Account": username, "Passwd": password}</pre>
     *
     * <p>成功响应示例：
     * <pre>{"RetCode": 200, "Data": {"Token": "xxx", "ExpireTime": 3600}}</pre>
     *
     * @param identifyId 设备标识 ID，格式为 "1.{deviceType}.{username}_{uuid}@AISF.cu.gd.com.cn"
     * @param username   用户名，填充请求体 Account 字段
     * @param password   密码，填充请求体 Passwd 字段
     * @return 认证结果，包含 Token 字符串和有效期（秒）
     * @throws AcmException HTTP 失败、业务错误码非 200 或 JSON 解析异常时抛出
     */
    @Override
    public AuthResult auth(String identifyId, String username, String password) throws AcmException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("ACMFacadeImpl.auth: auth start, identifyId={}, username={}", identifyId, username);
        String url = FacadeHelper.formatUrl(buildRequestUrl(AUTH_URL));
        JSONObject json = new JSONObject();
        try {
            json.put("Account", username);
            json.put("Passwd", password);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.auth: auth build request body failed: {}", e.getMessage());
            throw new AcmException(-1, "Build request body failed: " + e.getMessage());
        }
        NettyHttpRequest request = NettyHttpRequest.builder()
                .url(url)
                .method("POST")
                .header(FacadeHelper.HEADER_USER_NAME, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, identifyId)
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .bodyString(json.toString())
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(request);
            AuthResult result = parseAuthResponse(response);
            LOGGER.info("ACMFacadeImpl.auth: auth success, expireTime={}, elapsed={}ms",
                    result.getExpireTime(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("ACMFacadeImpl.auth: auth failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new AcmException(-1, e.getMessage());
        }
    }

    /**
     * 通过 Ticket 换取 Token（同步）
     *
     * <p>请求格式：
     * <pre>POST /acm/v1/auth
     * Headers: DeviceCategory
     * Body: {"Ticket": "xxx"}</pre>
     *
     * <p>成功响应示例：
     * <pre>{"RetCode": 200, "Data": "token-string", "Version": "1.0"}</pre>
     *
     * <p>失败响应示例：
     * <pre>{"RetCode": 400, "RetMsg": "Invalid ticket: ...", "Version": "1.0"}</pre>
     *
     * @param ticket 票据，用于换取 Token
     * @return TokenResult 包含 Token 和过期时间，失败时返回 failure 结果
     */
    @Override
    public TokenResult authByTicket(String ticket) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("ACMFacadeImpl.authByTicket: authByTicket start");
        if (ticket == null || ticket.isEmpty()) {
            LOGGER.error("ACMFacadeImpl.authByTicket: ticket is null or empty");
            return TokenResult.failure("Ticket is null or empty");
        }
        String url = FacadeHelper.formatUrl(buildRequestUrl(AUTH_URL));
        JSONObject json = new JSONObject();
        try {
            json.put("ticket", ticket);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.authByTicket: build request body failed: {}", e.getMessage());
            return TokenResult.failure("Build request body failed: " + e.getMessage());
        }
        NettyHttpRequest request = NettyHttpRequest.builder()
                .url(url)
                .method("POST")
                .header(FacadeHelper.HEADER_USER_NAME, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .bodyString(json.toString())
                .build();
        LOGGER.info("AuthByticket request: url={}, method={}, headers={}, body={}",
                request.getUrl(), request.getMethod(), request.getHeaders(), json.toString());
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(request);
            TokenResult result = parseTicketAuthResponse(response);
            if (result.isSuccess()) {
                LOGGER.info("ACMFacadeImpl.authByTicket: authByTicket success, elapsed={}ms",
                        System.currentTimeMillis() - startTime);
            } else {
                LOGGER.error("ACMFacadeImpl.authByTicket: authByTicket failed, elapsed={}ms, msg={}",
                        System.currentTimeMillis() - startTime, result.getMessage());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("ACMFacadeImpl.authByTicket: authByTicket failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            return TokenResult.failure(e.getMessage());
        }
    }

    /**
     * 上传文件（File 形式，同步）
     *
     * <p>将 File 封装为 multipart/form-data，字段名为 {@code file}，
     * 服务端路径固定添加 {@code eventlog/} 前缀：{@code eventlog/{fileName}}。
     *
     * @param file 要上传的文件，不能为 null 且必须存在
     * @return 上传结果，包含服务端分配的 fileUUID
     * @throws AcmException 文件为 null、不存在或上传请求失败时抛出
     */
    @Override
    public UploadResult uploadLogFile(File file) throws AcmException {
        if (file == null || !file.exists()) {
            LOGGER.error("ACMFacadeImpl.uploadFile: uploadFile failed: file is null or not exists");
            throw new AcmException(-1, "File is null or does not exist");
        }
        LOGGER.info("ACMFacadeImpl.uploadFile: uploadFile start, file={}, size={}", file.getName(), file.length());
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            LOGGER.error("ACMFacadeImpl.uploadFile: read file bytes failed: {}", e.getMessage());
            throw new AcmException(-1, "Read file bytes failed: " + e.getMessage());
        }
        NettyHttpRequest.Builder requestBuilder = NettyHttpRequest.builder()
                .multipart()
                .addFilePart("file", fileBytes, file.getName(), "application/octet-stream")
                .header("UploadType", "log")
                .header("UserName", getLoginUsername());
        return doUpload(requestBuilder);
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名，未登录时返回空字符串
     */
    private String getLoginUsername() {
        try {
            if (AISFSDKClient.getLoginApi() != null
                    && AISFSDKClient.getLoginApi().getLoginInfo() != null
                    && AISFSDKClient.getLoginApi().getLoginInfo().getUsername() != null) {
                return AISFSDKClient.getLoginApi().getLoginInfo().getUsername();
            }
        } catch (Exception e) {
            LOGGER.warn("ACMFacadeImpl.getLoginUsername: failed to get username: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 上传文件（字节数组形式，同步）
     *
     * <p>将字节数组封装为 multipart/form-data，字段名为 {@code file}，
     * 文件名直接采用传入的 {@code name}（不会添加额外前缀）。
     *
     * <p>适用于内存中的临时数据（如日志内容），无需先写入磁盘。
     *
     * @param name  上传到服务端的文件名（不含路径）
     * @param bytes 文件字节内容，不能为 null
     * @return 上传结果，包含服务端分配的 fileUUID
     * @throws AcmException bytes 为 null 或上传请求失败时抛出
     */
    @Override
    public UploadResult uploadImageFile(String name, byte[] bytes) throws AcmException {
        return uploadImageFile(name, bytes, false);
    }

    /**
     * 上传图片文件（字节数组形式，同步，支持客户端主动关闭连接）
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
    @Override
    public UploadResult uploadImageFile(String name, byte[] bytes, boolean isClientClose) throws AcmException {
        if (bytes == null) {
            LOGGER.error("ACMFacadeImpl.uploadFile: uploadFile failed: bytes is null");
            throw new AcmException(-1, "Bytes is null");
        }
        LOGGER.info("ACMFacadeImpl.uploadFile: uploadFile start, name={}, size={}, isClientClose={}",
                name, bytes.length, isClientClose);
        NettyHttpRequest.Builder requestBuilder = NettyHttpRequest.builder()
                .multipart()
                .addFilePart("file", bytes, name, "application/octet-stream");
        return doUpload(requestBuilder, isClientClose);
    }

    /**
     * 从 ACM 服务下载文件并保存到本地（同步）
     *
     * <p>请求格式：
     * <pre>GET /acm/v1/downloadfile/{fileUrl}
     * Headers: AppName, DeviceId, DeviceCategory, UUID, Token, Connection=close</pre>
     *
     * <p>若父目录不存在，自动创建目录结构。
     *
     * <p><b>注意：</b>请求头中包含 {@code Connection: close}，表示使用短连接，
     * 请求完成后立即关闭连接，避免连接长时间占用。
     *
     * @param fileUrl  服务端文件标识，拼接到 URL 路径
     * @param savePath 本地保存路径（文件或目录）。若为目录，使用 fileUrl 作为文件名
     * @return 已保存的本地文件对象
     * @throws AcmException HTTP 失败、响应体为空或写入磁盘失败时抛出
     */
    @Override
    public File downloadFile(String fileUrl, String savePath) throws AcmException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("ACMFacadeImpl.downloadFile: downloadFile start, fileUrl={}, savePath={}", fileUrl, savePath);
        String url = FacadeHelper.formatUrl(buildRequestUrl(DOWNLOAD_URL + "/" + fileUrl));
        NettyHttpRequest request = NettyHttpRequest.builder()
                .url(url)
                .header("Connection", "close")
                .header(FacadeHelper.HEADER_USER_NAME, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getFileHttpClient().executeSync(request);
            if (!response.isSuccess()) {
                LOGGER.error("ACMFacadeImpl.downloadFile: downloadFile failed, httpCode={}", response.getStatusCode());
                throw new AcmException(response.getStatusCode(), "Response not successful");
            }
            byte[] body = response.getBody();
            if (body == null) {
                LOGGER.error("ACMFacadeImpl.downloadFile: downloadFile failed: response body is null");
                throw new AcmException(-1, "Response body is null");
            }
            File result = saveToFile(response.getBodyStream(), savePath);
            LOGGER.info("ACMFacadeImpl.downloadFile: downloadFile success, savePath={}, size={}, elapsed={}ms",
                    savePath, result.length(), System.currentTimeMillis() - startTime);
            return result;
        } catch (AcmException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("ACMFacadeImpl.downloadFile: downloadFile failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new AcmException(-1, e.getMessage());
        }
    }

    /**
     * 发送任务执行消息（同步）
     *
     * <p>将 userInput 封装为 TaskAssignment 消息体，通过 POST /acm/v1/requestmsg 发送到服务端。</p>
     *
     * @param sessionId 会话ID
     * @param userInput 用户输入文本，即 {@code ScheduleAction#body}
     * @throws AcmException 请求构建失败或 HTTP 请求失败时抛出
     */
    @Override
    public void sendTaskMessage(String sessionId, String userInput) throws AcmException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("ACMFacadeImpl.sendTaskMessage: start, sessionId={}, userInput={}", sessionId, userInput);
        String url = buildC2cRequestUrl(TASK_MESSAGE_URL);
        JSONObject requestData = new JSONObject();
        try {
            JSONObject data = new JSONObject();
            data.put("MessageType", "TaskAssignment");
            data.put("UserInputText", userInput);
            data.put("UserIntent", "");
            JSONObject header = buildHeader(sessionId);
            requestData.put("Data", data);
            requestData.put("Header", header);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.sendTaskMessage: build request body failed: {}", e.getMessage());
            throw new AcmException(-1, "Build request body failed: " + e.getMessage());
        }
        String requestBody = requestData.toString();
        LOGGER.info("ACMFacadeImpl.sendTaskMessage: requestBody={}", requestBody);
        NettyHttpRequest request = NettyHttpRequest.builder()
                .url(url)
                .method("POST")
                .bodyString(requestBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(request);
            if (!response.isSuccess()) {
                LOGGER.error("ACMFacadeImpl.sendTaskMessage: failed, elapsed={}ms, httpCode={}",
                        System.currentTimeMillis() - startTime, response.getStatusCode());
                throw new AcmException(response.getStatusCode(),
                        "sendTaskMessage failed, httpCode=" + response.getStatusCode());
            }
            String responseBody = response.getBodyAsString();
            LOGGER.info("ACMFacadeImpl.sendTaskMessage: success, elapsed={}ms, response={}",
                    System.currentTimeMillis() - startTime, responseBody);
        } catch (AcmException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("ACMFacadeImpl.sendTaskMessage: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new AcmException(-1, e.getMessage());
        }
    }

    // ============== 私有方法 ==============

    // ----- URL 构建 -----

    /**
     * 构建请求 URL
     *
     * @param path 接口路径（如 {@code /acm/v1/auth}）
     * @return 完整请求 URL
     */
    private String buildRequestUrl(String path) {
        if (path == null) {
            LOGGER.warn("ACMFacadeImpl.buildRequestUrl: path is null");
            return "";
        }
        ServerInfo serverInfo = AISFSDKClient.getBaseApi().getServerInfo();
        if (serverInfo == null) {
            LOGGER.error("ACMFacadeImpl.buildRequestUrl: serverInfo is null");
            throw new IllegalStateException("ServerInfo is not initialized");
        }
        ServerAuthModeEnum authMode = serverInfo.getServerAuthMode();
        int port = authMode == ServerAuthModeEnum.APIGW
                ? serverInfo.getApigwPort()
                : serverInfo.getRestfulPort();
        return schemeOf(serverInfo) + "://" + serverInfo.getServerIp() + ":" + port + path;
    }

    /**
     * 获取 REST 接口协议：AISF04 内/外网 ACM 服务未启用 TLS，只能走 HTTP；其余环境仍走 HTTPS。
     */
    private String schemeOf(ServerInfo serverInfo) {
        ServerEnvEnum env = serverInfo.getServerEnv();
        if (env == ServerEnvEnum.AISF04_INTERNAL || env == ServerEnvEnum.AISF04_EXTERNAL) {
            return "http";
        }
        return "https";
    }

    /**
     * 构建 C2C 请求 URL
     *
     * <p>使用云云对接（C2C）端口构建请求 URL，用于特定接口如任务消息发送。</p>
     *
     * @param path 接口路径（如 {@code /acm/v1/requestmsg}）
     * @return 完整请求 URL
     */
    private String buildC2cRequestUrl(String path) {
        if (path == null) {
            LOGGER.warn("ACMFacadeImpl.buildC2cRequestUrl: path is null");
            return "";
        }
        ServerInfo serverInfo = AISFSDKClient.getBaseApi().getServerInfo();
        if (serverInfo == null) {
            LOGGER.error("ACMFacadeImpl.buildC2cRequestUrl: serverInfo is null");
            throw new IllegalStateException("ServerInfo is not initialized");
        }
        int port = serverInfo.getC2cPort();
        return "https://" + serverInfo.getServerIp() + ":" + port + path;
    }

    // ----- 请求执行 -----

    /**
     * 执行文件上传请求
     *
     * <p><b>注意：</b>请求头中包含 {@code Connection: close}，表示使用短连接，
     * 请求完成后立即关闭连接，避免连接长时间占用。
     *
     * @param requestBuilder 已构建好的 multipart 请求 Builder（含文件部分）
     * @return 上传结果，包含 fileUUID
     * @throws AcmException 上传失败时抛出
     */
    private UploadResult doUpload(NettyHttpRequest.Builder requestBuilder) throws AcmException {
        return doUpload(requestBuilder, false);
    }

    /**
     * 执行文件上传请求（支持客户端主动关闭连接）
     *
     * <p>当 {@code isClientClose=false} 时，请求头为 {@code Connection: close}，使用短连接，
     * 请求完成后由服务端关闭连接。当 {@code isClientClose=true} 时，请求头为
     * {@code Connection: keep-alive}，但客户端在 finally 块中主动关闭连接，
     * 避免服务端先关闭连接导致 channelInactive 覆盖成功响应的竞态问题。
     *
     * @param requestBuilder 已构建好的 multipart 请求 Builder（含文件部分）
     * @param isClientClose  true 表示客户端主动关闭连接，false 表示遵循HTTP协议（默认行为）
     * @return 上传结果，包含 fileUUID
     * @throws AcmException 上传失败时抛出
     */
    private UploadResult doUpload(NettyHttpRequest.Builder requestBuilder, boolean isClientClose) throws AcmException {
        long startTime = System.currentTimeMillis();
        String url = FacadeHelper.formatUrl(buildRequestUrl(UPLOAD_URL));
        LOGGER.info("ACMFacadeImpl.doUpload: doUpload start, isClientClose={}", isClientClose);
        NettyHttpRequest request = requestBuilder
                .url(url)
                .header("Connection", isClientClose ? "keep-alive" : "close")
                .header(FacadeHelper.HEADER_USER_NAME, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .clientCloseConnection(isClientClose)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getFileHttpClient().executeSync(request);
            UploadResult result = parseUploadResponse(response);
            LOGGER.info("ACMFacadeImpl.doUpload: doUpload success, elapsed={}ms, fileUuid={}",
                    System.currentTimeMillis() - startTime, result.getFileUuid());
            return result;
        } catch (AcmException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("ACMFacadeImpl.doUpload: doUpload failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new AcmException(-1, e.getMessage());
        }
    }

    /**
     * 解析认证接口响应，提取 Token 和有效期
     *
     * @param response HTTP 响应
     * @return 认证结果
     * @throws AcmException 响应异常或业务错误码非 0 时抛出
     */
    private AuthResult parseAuthResponse(NettyHttpResponse response) throws AcmException {
        LOGGER.info("ACMFacadeImpl.parseAuthResponse: parseAuthResponse start, code={}", response.getStatusCode());
        if (!response.isSuccess()) {
            LOGGER.error("ACMFacadeImpl.parseAuthResponse: parseAuthResponse failed, httpCode={}",
                    response.getStatusCode());
            throw new AcmException(response.getStatusCode(), "Response not successful");
        }
        String responseBody = response.getBodyAsString();
        if (responseBody.isEmpty()) {
            LOGGER.error("ACMFacadeImpl.parseAuthResponse: parseAuthResponse failed: response body is null");
            throw new AcmException(-1, "Response body is null");
        }
        try {
            JSONObject jsonObj = new JSONObject(responseBody);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("ACMFacadeImpl.parseAuthResponse: parseAuthResponse failed, retCode={}, retMsg={}",
                        retCode, retMsg);
                throw new AcmException(retCode, retMsg);
            }
            JSONObject data = jsonObj.getJSONObject("Data");
            String token = data.getString("Token");
            int expireTime = data.getInt("ExpireTime");
            LOGGER.info("ACMFacadeImpl.parseAuthResponse: parseAuthResponse success, expireTime={}", expireTime);
            return new AuthResult(token, expireTime);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.parseAuthResponse: parseAuthResponse parse JSON failed: {}", e.getMessage());
            throw new AcmException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析 Ticket 认证响应，提取 Token 和过期时间
     *
     * <p>成功响应格式：{"RetCode": 200, "Data": {"Token": "xxx", "ExpireTime": "123456"}, "Version": "1.0"}</p>
     * <p>失败响应格式：{"RetCode": 400, "RetMsg": "xxx", "Version": "1.0"}</p>
     *
     * @param response HTTP 响应
     * @return TokenResult 包含 Token 和过期时间，以及Data中的所有字段作为extraData
     */
    private TokenResult parseTicketAuthResponse(NettyHttpResponse response) {
        LOGGER.info("ticket response: statusCode={}, statusText={}, headers={}, body={}",
                response.getStatusCode(), response.getStatusText(), response.getHeaders(), response.getBodyAsString());
        LOGGER.debug("ACMFacadeImpl.parseTicketAuthResponse: start, code={}", response.getStatusCode());
        if (!response.isSuccess()) {
            LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: failed, httpCode={}", response.getStatusCode());
            return TokenResult.failure("HTTP error: " + response.getStatusCode());
        }
        String responseBody = response.getBodyAsString();
        if (responseBody.isEmpty()) {
            LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: response body is null");
            return TokenResult.failure("Response body is null");
        }
        LOGGER.debug("ACMFacadeImpl.parseTicketAuthResponse: response received");
        try {
            JSONObject jsonObj = new JSONObject(responseBody);
            int retCode = jsonObj.optInt("RetCode", -1);
            if (retCode != HttpStatusCode.OK) {
                String retMsg = jsonObj.optString("RetMsg", "Unknown error");
                LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: failed, retCode={}, retMsg={}", retCode, retMsg);
                return TokenResult.failure(retMsg);
            }
            JSONObject data = jsonObj.optJSONObject("Data");
            if (data == null) {
                LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: Data field is null");
                return TokenResult.failure("Data field is null");
            }
            String token = data.optString("Token", null);
            if (token == null || token.isEmpty()) {
                LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: Token field is null or empty");
                return TokenResult.failure("Token field is null or empty");
            }
            int expireTime = data.optInt("ExpireTime", 0);
            
            // 将Data中的所有字段转换为Map<String, String>作为extraData
            Map<String, String> extraData = new HashMap<>();
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = data.opt(key);
                if (value != null) {
                    extraData.put(key, String.valueOf(value));
                }
            }
            
            LOGGER.debug("ACMFacadeImpl.parseTicketAuthResponse: success, extraData size={}", extraData.size());
            return TokenResult.success(token, expireTime, extraData);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.parseTicketAuthResponse: parse JSON failed: {}", e.getMessage());
            return TokenResult.failure("Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析文件上传接口响应，提取 fileUUID
     *
     * @param response HTTP 响应
     * @return 上传结果
     * @throws AcmException 响应异常或业务错误码非 0 时抛出
     */
    private UploadResult parseUploadResponse(NettyHttpResponse response) throws AcmException {
        if (!response.isSuccess()) {
            LOGGER.error("ACMFacadeImpl.parseUploadResponse: parseUploadResponse failed, httpCode={}",
                    response.getStatusCode());
            throw new AcmException(response.getStatusCode(), "Response not successful");
        }
        String responseBody = response.getBodyAsString();
        if (responseBody.isEmpty()) {
            LOGGER.error("ACMFacadeImpl.parseUploadResponse: parseUploadResponse failed: response body is null");
            throw new AcmException(-1, "Response body is null");
        }
        try {
            JSONObject jsonObj = new JSONObject(responseBody);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("ACMFacadeImpl.parseUploadResponse: parseUploadResponse failed, retCode={}, retMsg={}",
                        retCode, retMsg);
                throw new AcmException(retCode, retMsg);
            }
            JSONObject data = jsonObj.getJSONObject("Data");
            String fileUuid = data.getString("fileUUID");
            LOGGER.info("ACMFacadeImpl.parseUploadResponse: parseUploadResponse success, fileUuid={}", fileUuid);
            return new UploadResult(fileUuid);
        } catch (JSONException e) {
            LOGGER.error("ACMFacadeImpl.parseUploadResponse: parseUploadResponse parse JSON failed: {}",
                    e.getMessage());
            throw new AcmException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    // ----- 辅助方法 -----

    /**
     * 构建 HTTP 请求头信息
     *
     * <p>包含设备信息、用户信息、消息类型等字段，用于 TaskAssignment 消息发送。</p>
     *
     * @param sessionId 会话ID
     * @return 包含请求头字段的 JSONObject
     * @throws JSONException JSON 构建异常时抛出
     */
    private static JSONObject buildHeader(String sessionId) throws JSONException {
        JSONObject header = new JSONObject();
        header.put("ClientExField", "");
        header.put("ClientSessionId", sessionId);
        header.put("SeqNum", RandomUtil.getUUID());
        header.put("DeviceCategory", FacadeHelper.getDeviceCategory());
        header.put("DeviceCategoryStr", FacadeHelper.getDeviceCategoryStr());
        header.put("DeviceId", FacadeHelper.getDeviceId());
        header.put("IdentifyId", FacadeHelper.getDeviceId());
        header.put("EventTimeStamp", TimeUtil.getEventTimestamp());
        header.put("MessageType", "UserTaskManagement");
        header.put("Receiver", "AISF");
        header.put("Sender", "LTServer");
        header.put("UUID", AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceUuid());
        header.put("DeviceUUID", AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceUuid());
        header.put("UserName", FacadeHelper.getUserName());
        return header;
    }

    /**
     * 将响应体内容写入本地文件
     *
     * <p>自动创建父目录（如果不存在），使用 4KB 缓冲区进行流式写入，
     * 适用于大文件下载场景。</p>
     *
     * @param inputStream HTTP 响应体输入流，不能为 null
     * @param savePath     本地保存路径（完整文件路径）
     * @return 已写入的本地文件对象
     * @throws IOException 写入失败、磁盘空间不足或权限不足时抛出
     */
    private File saveToFile(InputStream inputStream, String savePath) throws IOException {
        LOGGER.info("ACMFacadeImpl.saveToFile: saveToFile start, savePath={}", savePath);
        File targetFile = new File(savePath);
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!FileUtil.mkdirs(parentDir)) {
                LOGGER.error("ACMFacadeImpl.saveToFile: failed to create parent directory, path={}",
                        FileUtil.getFilePath(parentDir));
                throw new IOException("Failed to create parent directory: " + FileUtil.getFilePath(parentDir));
            }
        }
        long totalBytes = 0L;
        try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.flush();
        }
        LOGGER.info("ACMFacadeImpl.saveToFile: saveToFile success, savePath={}, size={}bytes", savePath, totalBytes);
        return targetFile;
    }

    // ----- 异步上传方法 -----

    /**
     * 异步上传图片文件
     *
     * <p>使用异步HTTP请求，不阻塞调用线程，结果通过回调通知。</p>
     *
     * @param name 图片文件名
     * @param bytes 图片字节数组
     * @param callback 上传结果回调
     */
    @Override
    public void uploadImageFileAsync(String name, byte[] bytes, FileUploadCallback callback) {
        if (bytes == null) {
            LOGGER.error("ACMFacadeImpl.uploadImageFileAsync: bytes is null");
            if (callback != null) {
                callback.onFailure(name, -1, "Bytes is null");
            }
            return;
        }
        if (callback == null) {
            LOGGER.error("ACMFacadeImpl.uploadImageFileAsync: callback is null");
            return;
        }
        LOGGER.info("ACMFacadeImpl.uploadImageFileAsync: start async upload, name={}, size={}", name, bytes.length);

        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            try {
                UploadResult result = uploadImageFile(name, bytes);
                LOGGER.info("ACMFacadeImpl.uploadImageFileAsync: upload success, fileUuid={}", result.getFileUuid());
                callback.onSuccess(name, result.getFileUuid());
            } catch (AcmException e) {
                LOGGER.error("ACMFacadeImpl.uploadImageFileAsync: upload failed, code={}, msg={}",
                        e.getCode(), e.getMessage());
                callback.onFailure(name, e.getCode(), e.getMessage());
            } catch (Exception e) {
                LOGGER.error("ACMFacadeImpl.uploadImageFileAsync: unexpected error, msg={}", e.getMessage());
                callback.onFailure(name, -1, e.getMessage());
            }
        });
    }

    /**
     * 异步上传日志文件
     *
     * <p>使用异步HTTP请求，不阻塞调用线程，结果通过回调通知。</p>
     *
     * @param file 日志文件对象
     * @param callback 上传结果回调
     */
    @Override
    public void uploadLogFileAsync(File file, FileUploadCallback callback) {
        if (file == null || !file.exists()) {
            LOGGER.error("ACMFacadeImpl.uploadLogFileAsync: file is null or not exists");
            if (callback != null) {
                String fileName = file != null ? file.getName() : "unknown";
                callback.onFailure(fileName, -1, "File is null or does not exist");
            }
            return;
        }
        if (callback == null) {
            LOGGER.error("ACMFacadeImpl.uploadLogFileAsync: callback is null");
            return;
        }
        LOGGER.info("ACMFacadeImpl.uploadLogFileAsync: start async upload, file={}, size={}",
                file.getName(), file.length());

        ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
            try {
                UploadResult result = uploadLogFile(file);
                LOGGER.info("ACMFacadeImpl.uploadLogFileAsync: upload success, fileUuid={}", result.getFileUuid());
                callback.onSuccess(file.getName(), result.getFileUuid());
            } catch (AcmException e) {
                LOGGER.error("ACMFacadeImpl.uploadLogFileAsync: upload failed, code={}, msg={}",
                        e.getCode(), e.getMessage());
                callback.onFailure(file.getName(), e.getCode(), e.getMessage());
            } catch (Exception e) {
                LOGGER.error("ACMFacadeImpl.uploadLogFileAsync: unexpected error, msg={}", e.getMessage());
                callback.onFailure(file.getName(), -1, e.getMessage());
            }
        });
    }

}