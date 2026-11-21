/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.component.facade.MemoryFacade;
import com.huawei.aisfsdkv2.component.facade.model.memory.BaseMemInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryItem;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult;
import com.huawei.aisfsdkv2.component.facade.model.memory.QueryMemoryListRequest;
import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.constants.HttpStatusCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 记忆接口 Facade 实现
 *
 * <p>封装 ACM 记忆相关 HTTP 接口调用（同步接口）
 *
 * <p>HTTP 客户端通过 {@link HttpClientManager} 获取：
 * <ul>
 *   <li>普通请求：使用 {@link HttpClientManager#getCommonHttpClient()}</li>
 * </ul>
 *
 * @see HttpClientManager
 */
public class MemoryFacadeImpl implements MemoryFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger("MemoryFacadeImpl");

    // ACM 接口路径
    private static final String QUERY_MEM_LIST_URL = "/acm/v1/memory/query";
    private static final String QUERY_CLASSIFY_MEM_URL = "/acm/v1/memory/persona/classify/query";
    private static final String QUERY_USER_CONFIGS_URL = "/acm/v1/user/memory/user_configs_get";
    private static final String SET_USER_CONFIGS_URL = "/acm/v1/user/memory/user_configs_add";
    private static final String MEMORY_DELETE_URL = "/acm/v1/user/memory/delete";

    private static class Holder {
        private static final MemoryFacadeImpl INSTANCE = new MemoryFacadeImpl();
    }

    /**
     * 获取单例实例（供接口调用）
     */
    public static MemoryFacadeImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 私有构造方法
     */
    private MemoryFacadeImpl() {
    }

    /**
     * 查询记忆列表（同步）
     *
     * <p>对应接口: POST /acm/v1/memory/query
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @param request 查询请求参数，包含记忆类型、分页等信息
     * @return 记忆列表信息，包含记忆列表和分页信息
     * @throws MemoryException 请求失败或响应解析失败时抛出
     */
    @Override
    public MemoryListInfo queryMemoryList(QueryMemoryListRequest request) throws MemoryException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("MemoryFacadeImpl.queryMemoryList: start, request={}", request);
        String url = FacadeHelper.formatUrl(buildRequestUrl(QUERY_MEM_LIST_URL));
        String jsonBody = request.toJson();
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .method("POST")
                .header("Content-Type", "application/json; charset=utf-8")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            MemoryListInfo result = parseMemoryListResponse(response);
            LOGGER.info("MemoryFacadeImpl.queryMemoryList: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("MemoryFacadeImpl.queryMemoryList: queryMemoryList failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new MemoryException(-1, e.getMessage());
        }
    }

    /**
     * 查询分类记忆/个人画像（同步）
     *
     * <p>对应接口: GET /acm/v1/memory/persona/classify/query
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @return 分类记忆信息，按类别分组的记忆内容
     * @throws MemoryException 请求失败或响应解析失败时抛出
     */
    @Override
    public PersonalClassifyMemoryResult queryPersonalClassifyMemory() throws MemoryException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("MemoryFacadeImpl.queryPersonalClassifyMemory: queryPersonalClassifyMemory start");
        String url = FacadeHelper.formatUrl(buildRequestUrl(QUERY_CLASSIFY_MEM_URL));
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            PersonalClassifyMemoryResult result = parseClassifyMemoryResponse(response);
            LOGGER.info("MemoryFacadeImpl.queryPersonalClassifyMemory: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("MemoryFacadeImpl.queryPersonalClassifyMemory: "
                    + "queryPersonalClassifyMemory failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new MemoryException(-1, e.getMessage());
        }
    }

    /**
     * 查询记忆功能是否开启（同步）
     *
     * <p>对应接口: POST /acm/v1/user/memory/user_configs_get
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @return true 表示已开启，false 表示已关闭
     * @throws MemoryException 请求失败或响应解析失败时抛出
     */
    @Override
    public boolean queryMemoryEnabled() throws MemoryException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("MemoryFacadeImpl.queryMemoryEnabled: start");
        String url = FacadeHelper.formatUrl(buildRequestUrl(QUERY_USER_CONFIGS_URL));
        String jsonBody = "{\"keys\":[\"memory_enabled\"]}";
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .method("POST")
                .header("Content-Type", "application/json; charset=utf-8")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            boolean result = parseMemoryEnabledResponse(response);
            LOGGER.info("MemoryFacadeImpl.queryMemoryEnabled: success, result={}, elapsed={}ms",
                    result, System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("MemoryFacadeImpl.queryMemoryEnabled: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new MemoryException(-1, e.getMessage());
        }
    }

    /**
     * 设置记忆功能开关（同步）
     *
     * <p>对应接口: POST /acm/v1/user/memory/user_configs_add
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @param enabled true 表示开启，false 表示关闭
     * @throws MemoryException 请求失败时抛出
     */
    @Override
    public void setMemoryEnabled(boolean enabled) throws MemoryException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("MemoryFacadeImpl.setMemoryEnabled: start, enabled={}", enabled);
        String url = FacadeHelper.formatUrl(buildRequestUrl(SET_USER_CONFIGS_URL));
        String jsonBody = "{\"configs\":{\"memory_enabled\":" + enabled + "}}";
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .method("POST")
                .header("Content-Type", "application/json; charset=utf-8")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            checkResponse(response, "setMemoryEnabled");
            LOGGER.info("MemoryFacadeImpl.setMemoryEnabled: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOGGER.error("MemoryFacadeImpl.setMemoryEnabled: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new MemoryException(-1, e.getMessage());
        }
    }

    /**
     * 删除指定记忆（同步）
     *
     * <p>对应接口: POST /acm/v1/user/memory/delete
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @param category 记忆类型
     * @param memoryId 记忆 ID
     * @return true 表示删除成功
     * @throws MemoryException 请求失败时抛出
     */
    @Override
    public boolean deleteMemory(String category, String memoryId) throws MemoryException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("MemoryFacadeImpl.deleteMemory: start, category={}, memoryId={}", category, memoryId);
        String url = FacadeHelper.formatUrl(buildRequestUrl(MEMORY_DELETE_URL));
        String jsonBody;
        try {
            JSONObject body = new JSONObject();
            body.put("id", memoryId);
            body.put("type", category);
            jsonBody = body.toString();
        } catch (JSONException e) {
            throw new MemoryException(-1, "Build request body failed: " + e.getMessage());
        }
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .method("POST")
                .header("Content-Type", "application/json; charset=utf-8")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            checkResponse(response, "deleteMemory");
            LOGGER.info("MemoryFacadeImpl.deleteMemory: success, elapsed={}ms", System.currentTimeMillis() - startTime);
            return true;
        } catch (Exception e) {
            LOGGER.error("MemoryFacadeImpl.deleteMemory: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new MemoryException(-1, e.getMessage());
        }
    }

    // ============== 私有方法 ==============

    /**
     * 构建请求 URL
     *
     * <p>使用云云对接端口（c2cPort）拼接完整 URL。
     *
     * @param path 接口路径
     * @return 完整的请求 URL
     */
    private String buildRequestUrl(String path) {
        ServerInfo serverInfo = AISFSDKClient.getBaseApi().getServerInfo();
        ServerAuthModeEnum authMode = serverInfo.getServerAuthMode();
        String address = serverInfo.getServerIp();
        int port = authMode == ServerAuthModeEnum.APIGW
                ? serverInfo.getApigwPort()
                : serverInfo.getC2cPort();
        return "https://" + address + ":" + port + path;
    }

    /**
     * 解析记忆列表响应
     *
     * @param response HTTP 响应对象
     * @return 记忆列表信息
     * @throws MemoryException 解析失败时抛出异常
     */
    private MemoryListInfo parseMemoryListResponse(NettyHttpResponse response) throws MemoryException {
        if (!response.isSuccess()) {
            LOGGER.error("MemoryFacadeImpl.parseMemoryListResponse: queryMemoryList response failed, code: {}",
                    response.getStatusCode());
            throw new MemoryException(response.getStatusCode(), "Response not successful");
        }
        String bodyStr = response.getBodyAsString();
        if (bodyStr.isEmpty()) {
            LOGGER.error("MemoryFacadeImpl.parseMemoryListResponse: queryMemoryList response body is null");
            throw new MemoryException(-1, "Response body is null");
        }
        LOGGER.info("MemoryFacadeImpl.parseMemoryListResponse: body={}", bodyStr);
        return doParseMemoryList(bodyStr);
    }

    /**
     * 解析记忆列表 JSON 数据
     *
     * @param json JSON 字符串
     * @return 记忆列表信息对象
     * @throws MemoryException 解析失败时抛出异常
     */
    private MemoryListInfo doParseMemoryList(String json) throws MemoryException {
        try {
            JSONObject jsonObj = new JSONObject(json);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("MemoryFacadeImpl.doParseMemoryList: parseMemoryList error, code: {}, msg: {}",
                        retCode, retMsg);
                throw new MemoryException(retCode, retMsg);
            }
            MemoryListInfo result = new MemoryListInfo();
            // 解析 data 数组
            JSONArray dataArray = jsonObj.getJSONArray("data");
            List<BaseMemInfo> memInfos = new ArrayList<>();
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                BaseMemInfo info = new BaseMemInfo();
                info.setId(item.optString("id", ""));
                info.setContent(item.optString("content", ""));
                info.setDetail(item.optString("detail", ""));
                info.setDate(item.optString("date", ""));
                info.setMemType(item.optString("memType", ""));
                memInfos.add(info);
            }
            result.setMemInfoList(memInfos);
            // 解析 pageInfo
            JSONObject pageObj = jsonObj.getJSONObject("pageInfo");
            PageInfo pageInfo = new PageInfo();
            pageInfo.setPage(pageObj.getInt("page"));
            pageInfo.setLimit(pageObj.getInt("limit"));
            pageInfo.setTotalSize(pageObj.getInt("totalSize"));
            pageInfo.setHasNext(pageObj.getInt("hasNext") != 0);
            result.setPageInfo(pageInfo);
            return result;
        } catch (JSONException e) {
            LOGGER.error("MemoryFacadeImpl.doParseMemoryList: parseMemoryList JSON failed: {}", e.getMessage());
            throw new MemoryException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析分类记忆响应
     *
     * @param response HTTP 响应对象
     * @return 分类记忆信息
     * @throws MemoryException 解析失败时抛出异常
     */
    private PersonalClassifyMemoryResult parseClassifyMemoryResponse(NettyHttpResponse response)
            throws MemoryException {
        if (!response.isSuccess()) {
            LOGGER.error("MemoryFacadeImpl.parseClassifyMemoryResponse: "
                    + "queryPersonalClassifyMemory response failed, code: {}",
                    response.getStatusCode());
            throw new MemoryException(response.getStatusCode(), "Response not successful");
        }
        String bodyStr = response.getBodyAsString();
        if (bodyStr.isEmpty()) {
            LOGGER.error("MemoryFacadeImpl.parseClassifyMemoryResponse: "
                    + "queryPersonalClassifyMemory response body is null");
            throw new MemoryException(-1, "Response body is null");
        }
        LOGGER.info("MemoryFacadeImpl.parseClassifyMemoryResponse: body={}", bodyStr);
        return doParseClassifyMemory(bodyStr);
    }

    /**
     * 解析分类记忆 JSON 数据
     *
     * @param json JSON 字符串
     * @return 分类记忆信息对象
     * @throws MemoryException 解析失败时抛出异常
     */
    private PersonalClassifyMemoryResult doParseClassifyMemory(String json) throws MemoryException {
        try {
            JSONObject jsonObj = new JSONObject(json);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("MemoryFacadeImpl.doParseClassifyMemory: parseClassifyMemory error, code: {}, msg: {}",
                        retCode, retMsg);
                throw new MemoryException(retCode, retMsg);
            }
            PersonalClassifyMemoryResult result = new PersonalClassifyMemoryResult();
            Map<String, List<PersonalClassifyMemoryItem>> categoryMap = new HashMap<>();
            JSONObject dataObj = jsonObj.getJSONObject("data");
            Iterator<String> keys = dataObj.keys();
            while (keys.hasNext()) {
                String category = keys.next();
                JSONArray items = dataObj.getJSONArray(category);
                List<PersonalClassifyMemoryItem> memList = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    PersonalClassifyMemoryItem mem = new PersonalClassifyMemoryItem();
                    mem.setId(item.optString("id", ""));
                    mem.setMemType(item.optString("memType", ""));
                    mem.setTopic(item.optString("topic", ""));
                    mem.setContent(item.optString("content", ""));
                    memList.add(mem);
                }
                categoryMap.put(category, memList);
            }
            result.setCategoryMap(categoryMap);
            return result;
        } catch (JSONException e) {
            LOGGER.error("MemoryFacadeImpl.doParseClassifyMemory: parseClassifyMemory JSON failed: {}", e.getMessage());
            throw new MemoryException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析记忆开关查询响应
     *
     * @param response HTTP 响应对象
     * @return true表示已开启，false表示已关闭
     * @throws MemoryException 解析失败时抛出异常
     */
    private boolean parseMemoryEnabledResponse(NettyHttpResponse response) throws MemoryException {
        checkResponse(response, "queryMemoryEnabled");
        String bodyStr = response.getBodyAsString();
        if (bodyStr.isEmpty()) {
            throw new MemoryException(-1, "Response body is null");
        }
        try {
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("MemoryFacadeImpl.parseMemoryEnabledResponse: error, code: {}, msg: {}", retCode, retMsg);
                throw new MemoryException(retCode, retMsg);
            }
            JSONObject dataObj = jsonObj.getJSONObject("data");
            return dataObj.optBoolean("memory_enabled", false);
        } catch (JSONException e) {
            LOGGER.error("MemoryFacadeImpl.parseMemoryEnabledResponse: parse failed: {}", e.getMessage());
            throw new MemoryException(-1, "Parse response failed: " + e.getMessage());
        }
    }

    /**
     * 检查 HTTP 响应是否成功
     *
     * @param response HTTP 响应对象
     * @param method   调用方法名（用于日志）
     * @throws MemoryException 响应失败时抛出异常
     */
    private void checkResponse(NettyHttpResponse response, String method) throws MemoryException {
        if (!response.isSuccess()) {
            LOGGER.error("MemoryFacadeImpl.{}: response failed, code: {}", method, response.getStatusCode());
            throw new MemoryException(response.getStatusCode(), "Response not successful");
        }
    }
}
