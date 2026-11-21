/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.component.facade.SkillFacade;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillException;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel;
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
import java.util.List;

/**
 * 技能接口 Facade 实现
 * 封装技能相关 HTTP 接口调用（同步接口）
 *
 * <p>HTTP 客户端通过 {@link HttpClientManager} 获取：
 * <ul>
 *   <li>普通请求：使用 {@link HttpClientManager#getCommonHttpClient()}</li>
 * </ul>
 *
 * @see HttpClientManager
 */
public class SkillFacadeImpl implements SkillFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger("SkillFacadeImpl");

    private static final String SKILL_URL = "/acm/v1/memory/stepTeachSkill";

    private static class Holder {
        private static final SkillFacadeImpl INSTANCE = new SkillFacadeImpl();
    }

    /**
     * 获取单例实例（供接口调用）
     */
    public static SkillFacadeImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 私有构造方法
     */
    private SkillFacadeImpl() {
    }

    /**
     * 查询技能列表（同步）
     *
     * <p>对应接口: GET /acm/v1/memory/stepTeachSkill
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @return 技能列表
     * @throws SkillException 请求失败或响应解析失败时抛出
     */
    @Override
    public List<SkillModel> querySkillList() throws SkillException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("SkillFacadeImpl.querySkillList: start");
        String url = FacadeHelper.formatUrl(buildRequestUrl(SKILL_URL));
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .method("GET")
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            List<SkillModel> result = parseSkillListResponse(response);
            LOGGER.info("SkillFacadeImpl.querySkillList: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("SkillFacadeImpl.querySkillList: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new SkillException(-1, e.getMessage());
        }
    }

    /**
     * 创建技能（同步）
     *
     * <p>对应接口: POST /acm/v1/memory/stepTeachSkill
     *
     * @param request 创建技能请求
     * @return 创建成功返回技能ID
     * @throws SkillException 请求失败或响应解析失败时抛出
     */
    @Override
    public String createSkill(CreateSkillRequest request) throws SkillException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("SkillFacadeImpl.createSkill: start, request={}", request);
        String url = FacadeHelper.formatUrl(buildRequestUrl(SKILL_URL));
        String jsonBody = buildCreateSkillBody(request);
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .header("Content-Type", "application/json; charset=utf-8")
                .method("POST")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            String result = parseCreateSkillResponse(response);
            LOGGER.info("SkillFacadeImpl.createSkill: success, elapsed={}ms", System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("SkillFacadeImpl.createSkill: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new SkillException(-1, e.getMessage());
        }
    }

    /**
     * 构建创建技能请求体
     *
     * @param request 创建技能请求
     * @return JSON 字符串
     */
    private String buildCreateSkillBody(CreateSkillRequest request) {
        try {
            JSONObject json = new JSONObject();
            json.put("skill_name", request.getSkillName() != null ? request.getSkillName() : "");
            json.put("steps", request.getSteps() != null ? request.getSteps() : "");
            json.put("summary", request.getSummary() != null ? request.getSummary() : "");
            json.put("app", request.getApp() != null ? request.getApp() : "");
            json.put("variables", request.getVariables() != null ? request.getVariables() : "");
            return json.toString();
        } catch (JSONException e) {
            LOGGER.error("SkillFacadeImpl.buildCreateSkillBody: failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 删除技能（同步）
     *
     * <p>对应接口: DELETE /acm/v1/memory/stepTeachSkill
     *
     * @param request 删除技能请求
     * @return 删除成功返回true，失败返回false
     * @throws SkillException 请求失败或响应解析失败时抛出
     */
    @Override
    public boolean deleteSkill(DeleteSkillRequest request) throws SkillException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("SkillFacadeImpl.deleteSkill: start, request={}", request);
        String url = FacadeHelper.formatUrl(buildRequestUrl(SKILL_URL));
        String jsonBody = buildDeleteSkillBody(request);
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .header("Content-Type", "application/json; charset=utf-8")
                .method("DELETE")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            boolean result = parseDeleteSkillResponse(response);
            LOGGER.info("SkillFacadeImpl.deleteSkill: success, elapsed={}ms", System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("SkillFacadeImpl.deleteSkill: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new SkillException(-1, e.getMessage());
        }
    }

    /**
     * 构建删除技能请求体
     *
     * @param request 删除技能请求
     * @return JSON 字符串
     */
    private String buildDeleteSkillBody(DeleteSkillRequest request) {
        try {
            JSONObject json = new JSONObject();
            json.put("skill_id", request.getSkillId() != null ? request.getSkillId() : "");
            return json.toString();
        } catch (JSONException e) {
            LOGGER.error("SkillFacadeImpl.buildDeleteSkillBody: failed: {}", e.getMessage());
            return "{}";
        }
    }

    // ============== 私有方法 ==============

    /**
     * 构建请求 URL
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
     * 解析技能列表响应
     *
     * @param response HTTP 响应对象
     * @return 技能列表
     * @throws SkillException 解析失败时抛出异常
     */
    private List<SkillModel> parseSkillListResponse(NettyHttpResponse response) throws SkillException {
        if (!response.isSuccess()) {
            LOGGER.error("SkillFacadeImpl.parseSkillListResponse: response failed, code: {}", response.getStatusCode());
            throw new SkillException(response.getStatusCode(), "Response not successful");
        }
        byte[] bodyBytes = response.getBody();
        if (bodyBytes == null) {
            LOGGER.error("SkillFacadeImpl.parseSkillListResponse: response body is null");
            throw new SkillException(-1, "Response body is null");
        }
        String bodyStr = response.getBodyAsString();
        LOGGER.info("SkillFacadeImpl.parseSkillListResponse: body={}", bodyStr);
        return doParseSkillList(bodyStr);
    }

    /**
     * 解析技能列表 JSON 数据
     *
     * @param json JSON 字符串
     * @return 技能列表
     * @throws SkillException 解析失败时抛出异常
     */
    private List<SkillModel> doParseSkillList(String json) throws SkillException {
        List<SkillModel> result = new ArrayList<>();
        try {
            JSONObject jsonObj = new JSONObject(json);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("SkillFacadeImpl.doParseSkillList: error, code: {}, msg: {}", retCode, retMsg);
                throw new SkillException(retCode, retMsg);
            }
            JSONArray skillsArray = jsonObj.getJSONArray("skills");
            for (int i = 0; i < skillsArray.length(); i++) {
                JSONObject item = skillsArray.getJSONObject(i);
                SkillModel info = new SkillModel();
                info.setSkillId(item.optString("skill_id", ""));
                info.setSkillName(item.optString("skill_name", ""));
                info.setSteps(item.optString("steps", ""));
                info.setSummary(item.optString("summary", ""));
                info.setIsDefault(item.optString("is_default", ""));
                info.setApp(item.optString("app", ""));
                info.setVariables(item.optString("variables", ""));
                result.add(info);
            }
            return result;
        } catch (JSONException e) {
            LOGGER.error("SkillFacadeImpl.doParseSkillList: JSON parse failed: {}", e.getMessage());
            throw new SkillException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析创建技能响应
     *
     * @param response HTTP 响应对象
     * @return 技能ID
     * @throws SkillException 解析失败时抛出异常
     */
    private String parseCreateSkillResponse(NettyHttpResponse response) throws SkillException {
        if (!response.isSuccess()) {
            LOGGER.error("SkillFacadeImpl.parseCreateSkillResponse: response failed, code: {}",
                    response.getStatusCode());
            throw new SkillException(response.getStatusCode(), "Response not successful");
        }
        byte[] bodyBytes = response.getBody();
        if (bodyBytes == null) {
            LOGGER.error("SkillFacadeImpl.parseCreateSkillResponse: response body is null");
            throw new SkillException(-1, "Response body is null");
        }
        try {
            String bodyStr = response.getBodyAsString();
            LOGGER.info("SkillFacadeImpl.parseCreateSkillResponse: body={}", bodyStr);
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("SkillFacadeImpl.parseCreateSkillResponse: error, code: {}, msg: {}", retCode, retMsg);
                throw new SkillException(retCode, retMsg);
            }
            JSONObject dataObj = jsonObj.getJSONObject("data");
            return dataObj.optString("skill_id", null);
        } catch (JSONException e) {
            LOGGER.error("SkillFacadeImpl.parseCreateSkillResponse: parse failed: {}", e.getMessage());
            throw new SkillException(-1, "Parse response failed: " + e.getMessage());
        }
    }

    /**
     * 解析删除技能响应
     *
     * @param response HTTP 响应对象
     * @return 删除成功返回true，失败返回false
     * @throws SkillException 解析失败时抛出异常
     */
    private boolean parseDeleteSkillResponse(NettyHttpResponse response) throws SkillException {
        if (!response.isSuccess()) {
            LOGGER.error("SkillFacadeImpl.parseDeleteSkillResponse: response failed, code: {}",
                    response.getStatusCode());
            throw new SkillException(response.getStatusCode(), "Response not successful");
        }
        byte[] bodyBytes = response.getBody();
        if (bodyBytes == null) {
            LOGGER.error("SkillFacadeImpl.parseDeleteSkillResponse: response body is null");
            throw new SkillException(-1, "Response body is null");
        }
        try {
            String bodyStr = response.getBodyAsString();
            LOGGER.info("SkillFacadeImpl.parseDeleteSkillResponse: body={}", bodyStr);
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("SkillFacadeImpl.parseDeleteSkillResponse: error, code: {}, msg: {}", retCode, retMsg);
                return false;
            }
            return true;
        } catch (JSONException e) {
            LOGGER.error("SkillFacadeImpl.parseDeleteSkillResponse: parse failed: {}", e.getMessage());
            throw new SkillException(-1, "Parse response failed: " + e.getMessage());
        }
    }
}
