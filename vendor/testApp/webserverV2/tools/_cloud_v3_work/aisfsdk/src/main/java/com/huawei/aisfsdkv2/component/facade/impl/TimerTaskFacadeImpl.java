/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.impl;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.timertask.CreateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.DeleteScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.timertask.ScheduleAction;
import com.huawei.aisfsdkv2.api.timertask.ScheduleInfo;
import com.huawei.aisfsdkv2.api.timertask.ScheduleTypeEnum;
import com.huawei.aisfsdkv2.api.timertask.TaskSchedule;
import com.huawei.aisfsdkv2.api.timertask.TimerTaskModel;
import com.huawei.aisfsdkv2.api.timertask.UpdateScheduleTaskRequest;
import com.huawei.aisfsdkv2.api.base.ServerAuthModeEnum;
import com.huawei.aisfsdkv2.api.base.ServerInfo;
import com.huawei.aisfsdkv2.component.facade.TimerTaskFacade;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityModel;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TaskAbilityResult;
import com.huawei.aisfsdkv2.component.facade.model.timertask.TimerTaskException;
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
 * 定时任务 Facade 实现（基于 HttpClient）
 * <p>封装定时任务相关 HTTP 接口调用（同步接口）。</p>
 */
public class TimerTaskFacadeImpl implements TimerTaskFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger("TimerTaskFacadeImpl");

    private static final String SCHEDULE_TASK_ABILITY_URL = "/acm/v1/memory/scheduleTaskAbility";
    private static final String SCHEDULE_TASK_URL = "/acm/v1/memory/scheduleTask";

    private static class Holder {
        private static final TimerTaskFacadeImpl INSTANCE = new TimerTaskFacadeImpl();
    }

    /**
     * 获取单例实例
     *
     * @return TimerTaskFacadeImpl 单例
     */
    public static TimerTaskFacadeImpl getInstance() {
        return Holder.INSTANCE;
    }

    private TimerTaskFacadeImpl() {
    }

    /**
     * 查询任务能力列表（同步）
     *
     * <p>对应接口: GET /acm/v1/memory/scheduleTaskAbility
     * <p>userId 由 {@link FacadeHelper#getUserName()} 从当前登录信息中自动获取。
     *
     * @return 任务能力查询结果，包含 Skill、Agents、Api 三类能力列表
     * @throws TimerTaskException 请求失败或响应解析失败时抛出
     */
    @Override
    public TaskAbilityResult queryTaskAbilityList() throws TimerTaskException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("TimerTaskFacadeImpl.queryTaskAbilityList: start");
        String url = FacadeHelper.formatUrl(buildRequestUrl(SCHEDULE_TASK_ABILITY_URL));
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
            TaskAbilityResult result = parseTaskAbilityResponse(response);
            LOGGER.info("TimerTaskFacadeImpl.queryTaskAbilityList: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("TimerTaskFacadeImpl.queryTaskAbilityList: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            throw new TimerTaskException(-1, e.getMessage());
        }
    }

    /**
     * 创建定时任务（同步）
     *
     * <p>对应接口: POST /acm/v1/memory/scheduleTask
     *
     * @param request 创建定时任务请求
     * @return 创建结果，成功时 data 为任务ID
     */
    @Override
    public TimerTaskModel<String> createScheduleTask(CreateScheduleTaskRequest request) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("TimerTaskFacadeImpl.createScheduleTask: start, request={}", request);
        if (request == null) {
            LOGGER.error("TimerTaskFacadeImpl.createScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        String url = FacadeHelper.formatUrl(buildRequestUrl(SCHEDULE_TASK_URL));
        String jsonBody = buildCreateScheduleTaskBody(request);
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
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            TimerTaskModel<String> result = parseCreateScheduleTaskResponse(response);
            LOGGER.info("TimerTaskFacadeImpl.createScheduleTask: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("TimerTaskFacadeImpl.createScheduleTask: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            return TimerTaskModel.error(-1, e.getMessage());
        }
    }

    /**
     * 删除定时任务（同步）
     *
     * <p>对应接口: DELETE /acm/v1/memory/scheduleTask
     *
     * @param request 删除定时任务请求
     * @return 删除结果
     */
    @Override
    public TimerTaskModel<Boolean> deleteScheduleTask(DeleteScheduleTaskRequest request) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("TimerTaskFacadeImpl.deleteScheduleTask: start, request={}", request);
        if (request == null) {
            LOGGER.error("TimerTaskFacadeImpl.deleteScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        String url = FacadeHelper.formatUrl(buildRequestUrl(SCHEDULE_TASK_URL));
        String jsonBody = buildDeleteScheduleTaskBody(request);
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .header("taskId", request.getTaskId())
                .method("DELETE")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            TimerTaskModel<Boolean> result = parseDeleteScheduleTaskResponse(response);
            LOGGER.info("TimerTaskFacadeImpl.deleteScheduleTask: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("TimerTaskFacadeImpl.deleteScheduleTask: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            return TimerTaskModel.error(-1, e.getMessage());
        }
    }

    /**
     * 构建删除定时任务请求体
     *
     * @param request 删除定时任务请求
     * @return JSON 字符串
     */
    private String buildDeleteScheduleTaskBody(DeleteScheduleTaskRequest request) {
        try {
            JSONObject json = new JSONObject();
            json.put("task_id", request.getTaskId() != null ? request.getTaskId() : "");
            return json.toString();
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.buildDeleteScheduleTaskBody: failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 解析删除定时任务响应
     *
     * @param response HTTP 响应对象
     * @return 删除结果
     */
    private TimerTaskModel<Boolean> parseDeleteScheduleTaskResponse(NettyHttpResponse response) {
        if (!response.isSuccess()) {
            LOGGER.error("TimerTaskFacadeImpl.parseDeleteScheduleTaskResponse: response failed, code={}",
                    response.getStatusCode());
            return TimerTaskModel.error(response.getStatusCode(), "Response not successful");
        }
        if (response.getBody() == null) {
            LOGGER.error("TimerTaskFacadeImpl.parseDeleteScheduleTaskResponse: response body is null");
            return TimerTaskModel.error(-1, "Response body is null");
        }
        try {
            String bodyStr = response.getBodyAsString();
            LOGGER.info("TimerTaskFacadeImpl.parseDeleteScheduleTaskResponse: body={}", bodyStr);
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("TimerTaskFacadeImpl.parseDeleteScheduleTaskResponse: error, code={}, msg={}",
                        retCode, retMsg);
                return TimerTaskModel.error(retCode, retMsg);
            }
            return new TimerTaskModel<>(retCode, retMsg, true);
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.parseDeleteScheduleTaskResponse: parse failed: {}", e.getMessage());
            return TimerTaskModel.error(-1, "Parse response failed: " + e.getMessage());
        }
    }

    /**
     * 更新定时任务（同步）
     *
     * <p>对应接口: PUT /acm/v1/memory/scheduleTask
     *
     * @param request 更新定时任务请求
     * @return 更新结果
     */
    @Override
    public TimerTaskModel<Boolean> updateScheduleTask(UpdateScheduleTaskRequest request) {
        long startTime = System.currentTimeMillis();
        LOGGER.info("TimerTaskFacadeImpl.updateScheduleTask: start, request={}", request);
        if (request == null) {
            LOGGER.error("TimerTaskFacadeImpl.updateScheduleTask: request is null");
            return TimerTaskModel.error(-1, "request is null");
        }
        String url = FacadeHelper.formatUrl(buildRequestUrl(SCHEDULE_TASK_URL));
        String jsonBody = buildUpdateScheduleTaskBody(request);
        LOGGER.info("TimerTaskFacadeImpl.updateScheduleTask: jsonBody={}", jsonBody);
        NettyHttpRequest httpRequest = NettyHttpRequest.builder()
                .url(url)
                .header(FacadeHelper.HEADER_APP_NAME, FacadeHelper.getAppName())
                .header(FacadeHelper.HEADER_DEVICE_ID, FacadeHelper.getDeviceId())
                .header(FacadeHelper.HEADER_DEVICE_CATEGORY, FacadeHelper.getDeviceCategory())
                .header(FacadeHelper.HEADER_UUID, FacadeHelper.getDeviceUuid())
                .header(FacadeHelper.HEADER_TOKEN, FacadeHelper.getAcmToken())
                .header(FacadeHelper.HEADER_USER_ID, FacadeHelper.getUserName())
                .header(FacadeHelper.HEADER_AUTH_VERSION, FacadeHelper.getAuthVersion())
                .header("taskId", request.getTaskId())
                .method("PUT")
                .bodyString(jsonBody)
                .build();
        try {
            NettyHttpResponse response = HttpClientManager.getInstance().getCommonHttpClient().executeSync(httpRequest);
            TimerTaskModel<Boolean> result = parseUpdateScheduleTaskResponse(response);
            LOGGER.info("TimerTaskFacadeImpl.updateScheduleTask: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("TimerTaskFacadeImpl.updateScheduleTask: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            return TimerTaskModel.error(-1, e.getMessage());
        }
    }

    /**
     * 构建更新定时任务请求体
     *
     * @param request 更新定时任务请求
     * @return JSON 字符串
     */
    private String buildUpdateScheduleTaskBody(UpdateScheduleTaskRequest request) {
        try {
            JSONObject json = new JSONObject();
            json.put("task_id", request.getTaskId() != null ? request.getTaskId() : "");
            json.put("enable", request.isEnable());
            // schedule
            ScheduleInfo schedule = request.getSchedule();
            if (schedule != null) {
                JSONObject scheduleJson = new JSONObject();
                scheduleJson.put("type", schedule.getType() != null ? schedule.getType().getValue() : "");
                scheduleJson.put("trigger_time", schedule.getTriggerTime() != null ? schedule.getTriggerTime() : "");
                scheduleJson.put("cron", schedule.getCron() != null ? schedule.getCron() : "");
                scheduleJson.put("description", schedule.getDescription() != null ? schedule.getDescription() : "");
                json.put("schedule", scheduleJson);
            }
            // action
            ScheduleAction action = request.getAction();
            if (action != null) {
                JSONObject actionJson = new JSONObject();
                actionJson.put("type", action.getType() != null ? action.getType() : "");
                actionJson.put("title", action.getTitle() != null ? action.getTitle() : "");
                actionJson.put("body", action.getBody() != null ? action.getBody() : "");
                json.put("action", actionJson);
            }
            return json.toString();
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.buildUpdateScheduleTaskBody: failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 解析更新定时任务响应
     *
     * @param response HTTP 响应对象
     * @return 更新结果
     */
    private TimerTaskModel<Boolean> parseUpdateScheduleTaskResponse(NettyHttpResponse response) {
        if (!response.isSuccess()) {
            LOGGER.error("TimerTaskFacadeImpl.parseUpdateScheduleTaskResponse: response failed, code={}",
                    response.getStatusCode());
            return TimerTaskModel.error(response.getStatusCode(), "Response not successful");
        }
        if (response.getBody() == null) {
            LOGGER.error("TimerTaskFacadeImpl.parseUpdateScheduleTaskResponse: response body is null");
            return TimerTaskModel.error(-1, "Response body is null");
        }
        try {
            String bodyStr = response.getBodyAsString();
            LOGGER.info("TimerTaskFacadeImpl.parseUpdateScheduleTaskResponse: body={}", bodyStr);
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("TimerTaskFacadeImpl.parseUpdateScheduleTaskResponse: error, code={}, msg={}",
                        retCode, retMsg);
                return TimerTaskModel.error(retCode, retMsg);
            }
            return new TimerTaskModel<>(retCode, retMsg, true);
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.parseUpdateScheduleTaskResponse: parse failed: {}", e.getMessage());
            return TimerTaskModel.error(-1, "Parse response failed: " + e.getMessage());
        }
    }

    /**
     * 查询定时任务列表（同步）
     *
     * <p>对应接口: GET /acm/v1/memory/scheduleTask
     *
     * @return 定时任务列表结果
     */
    @Override
    public TimerTaskModel<List<TaskSchedule>> queryScheduleTaskList() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("TimerTaskFacadeImpl.queryScheduleTaskList: start");
        String url = FacadeHelper.formatUrl(buildRequestUrl(SCHEDULE_TASK_URL));
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
            TimerTaskModel<List<TaskSchedule>> result = parseScheduleTaskListResponse(response);
            LOGGER.info("TimerTaskFacadeImpl.queryScheduleTaskList: success, elapsed={}ms",
                    System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("TimerTaskFacadeImpl.queryScheduleTaskList: failed, elapsed={}ms: {}",
                    System.currentTimeMillis() - startTime, e.getMessage());
            return TimerTaskModel.error(-1, e.getMessage());
        }
    }

    /**
     * 构建创建定时任务请求体
     *
     * @param request 创建定时任务请求
     * @return JSON 字符串
     */
    private String buildCreateScheduleTaskBody(CreateScheduleTaskRequest request) {
        try {
            JSONObject json = new JSONObject();
            json.put("enable", request.isEnable());
            // schedule
            ScheduleInfo schedule = request.getSchedule();
            if (schedule != null) {
                JSONObject scheduleJson = new JSONObject();
                scheduleJson.put("type", schedule.getType() != null ? schedule.getType().getValue() : "");
                scheduleJson.put("trigger_time", schedule.getTriggerTime() != null ? schedule.getTriggerTime() : "");
                scheduleJson.put("cron", schedule.getCron() != null ? schedule.getCron() : "");
                scheduleJson.put("description", schedule.getDescription() != null ? schedule.getDescription() : "");
                json.put("schedule", scheduleJson);
            }
            // action
            ScheduleAction action = request.getAction();
            if (action != null) {
                JSONObject actionJson = new JSONObject();
                actionJson.put("type", action.getType() != null ? action.getType() : "");
                actionJson.put("title", action.getTitle() != null ? action.getTitle() : "");
                actionJson.put("body", action.getBody() != null ? action.getBody() : "");
                json.put("action", actionJson);
            }
            return json.toString();
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.buildCreateScheduleTaskBody: failed: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 解析创建定时任务响应
     *
     * @param response HTTP 响应对象
     * @return 创建结果
     */
    private TimerTaskModel<String> parseCreateScheduleTaskResponse(NettyHttpResponse response) {
        if (!response.isSuccess()) {
            LOGGER.error("TimerTaskFacadeImpl.parseCreateScheduleTaskResponse: response failed, code={}",
                    response.getStatusCode());
            return TimerTaskModel.error(response.getStatusCode(), "Response not successful");
        }
        if (response.getBody() == null) {
            LOGGER.error("TimerTaskFacadeImpl.parseCreateScheduleTaskResponse: response body is null");
            return TimerTaskModel.error(-1, "Response body is null");
        }
        try {
            String bodyStr = response.getBodyAsString();
            LOGGER.info("TimerTaskFacadeImpl.parseCreateScheduleTaskResponse: body={}", bodyStr);
            JSONObject jsonObj = new JSONObject(bodyStr);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("TimerTaskFacadeImpl.parseCreateScheduleTaskResponse: error, code={}, msg={}",
                        retCode, retMsg);
                return TimerTaskModel.error(retCode, retMsg);
            }
            String taskId = jsonObj.optString("id", null);
            return new TimerTaskModel<>(retCode, retMsg, taskId);
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.parseCreateScheduleTaskResponse: parse failed: {}", e.getMessage());
            return TimerTaskModel.error(-1, "Parse response failed: " + e.getMessage());
        }
    }

    /**
     * 构建请求 URL
     *
     * @param path 接口路径
     * @return 完整请求 URL
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
     * 解析任务能力列表响应
     *
     * @param response HTTP 响应对象
     * @return 任务能力查询结果
     * @throws TimerTaskException 解析失败时抛出
     */
    private TaskAbilityResult parseTaskAbilityResponse(NettyHttpResponse response) throws TimerTaskException {
        if (!response.isSuccess()) {
            LOGGER.error("TimerTaskFacadeImpl.parseTaskAbilityResponse: response failed, code={}",
                    response.getStatusCode());
            throw new TimerTaskException(response.getStatusCode(), "Response not successful");
        }
        if (response.getBody() == null) {
            LOGGER.error("TimerTaskFacadeImpl.parseTaskAbilityResponse: response body is null");
            throw new TimerTaskException(-1, "Response body is null");
        }
        String bodyStr = response.getBodyAsString();
        LOGGER.info("TimerTaskFacadeImpl.parseTaskAbilityResponse: body={}", bodyStr);
        return doParseTaskAbilityResult(bodyStr);
    }

    /**
     * 解析任务能力 JSON 数据
     *
     * @param json JSON 字符串
     * @return 任务能力查询结果
     * @throws TimerTaskException 解析失败时抛出
     */
    private TaskAbilityResult doParseTaskAbilityResult(String json) throws TimerTaskException {
        try {
            JSONObject jsonObj = new JSONObject(json);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("TimerTaskFacadeImpl.doParseTaskAbilityResult: error, code={}, msg={}", retCode, retMsg);
                throw new TimerTaskException(retCode, retMsg);
            }
            JSONObject abilityObj = jsonObj.getJSONObject("Ability");
            Map<String, List<TaskAbilityModel>> map = new HashMap<>();
            Iterator<String> keys = abilityObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, parseAbilityArray(abilityObj.optJSONArray(key)));
            }
            TaskAbilityResult result = new TaskAbilityResult();
            result.setTaskAbilityModelMap(map);
            return result;
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.doParseTaskAbilityResult: JSON parse failed: {}", e.getMessage());
            throw new TimerTaskException(-1, "Parse JSON failed: " + e.getMessage());
        }
    }

    /**
     * 解析单类能力数组
     *
     * @param array JSON 数组，为 null 时返回空列表
     * @return 能力模型列表
     * @throws JSONException JSON 解析异常
     */
    private List<TaskAbilityModel> parseAbilityArray(JSONArray array) throws JSONException {
        List<TaskAbilityModel> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            TaskAbilityModel model = new TaskAbilityModel();
            model.setId(item.optString("id", ""));
            model.setShowLabel(item.optString("show_label", ""));
            model.setDescription(item.optString("description", ""));
            model.setDefault(item.optBoolean("is_default", false));
            list.add(model);
        }
        return list;
    }

    /**
     * 解析定时任务列表响应
     *
     * @param response HTTP 响应对象
     * @return 定时任务列表结果
     */
    private TimerTaskModel<List<TaskSchedule>> parseScheduleTaskListResponse(NettyHttpResponse response) {
        if (!response.isSuccess()) {
            LOGGER.error("TimerTaskFacadeImpl.parseScheduleTaskListResponse: response failed, code={}",
                    response.getStatusCode());
            return TimerTaskModel.error(response.getStatusCode(), "Response not successful");
        }
        if (response.getBody() == null) {
            LOGGER.error("TimerTaskFacadeImpl.parseScheduleTaskListResponse: response body is null");
            return TimerTaskModel.error(-1, "Response body is null");
        }
        String bodyStr = response.getBodyAsString();
        LOGGER.info("TimerTaskFacadeImpl.parseScheduleTaskListResponse: body={}", bodyStr);
        return doParseScheduleTaskList(bodyStr);
    }

    /**
     * 解析定时任务列表 JSON 数据
     *
     * @param json JSON 字符串
     * @return 定时任务列表结果
     */
    private TimerTaskModel<List<TaskSchedule>> doParseScheduleTaskList(String json) {
        List<TaskSchedule> result = new ArrayList<>();
        try {
            JSONObject jsonObj = new JSONObject(json);
            int retCode = jsonObj.getInt("RetCode");
            String retMsg = jsonObj.getString("RetMsg");
            if (retCode != HttpStatusCode.OK) {
                LOGGER.error("TimerTaskFacadeImpl.doParseScheduleTaskList: error, code={}, msg={}", retCode, retMsg);
                return TimerTaskModel.error(retCode, retMsg);
            }
            JSONArray tasksArray = jsonObj.optJSONArray("tasks");
            if (tasksArray != null) {
                for (int i = 0; i < tasksArray.length(); i++) {
                    JSONObject taskObj = tasksArray.getJSONObject(i);
                    TaskSchedule task = new TaskSchedule();
                    task.setTaskId(taskObj.optString("task_id", ""));
                    task.setEnable(taskObj.optBoolean("enable", false));
                    // schedule
                    JSONObject scheduleObj = taskObj.optJSONObject("schedule");
                    if (scheduleObj != null) {
                        ScheduleInfo schedule = new ScheduleInfo();
                        schedule.setType(ScheduleTypeEnum.fromValue(scheduleObj.optString("type", "")));
                        schedule.setTriggerTime(scheduleObj.optString("trigger_time", ""));
                        schedule.setCron(scheduleObj.optString("cron", ""));
                        schedule.setDescription(scheduleObj.optString("description", ""));
                        task.setSchedule(schedule);
                    }
                    // action
                    JSONObject actionObj = taskObj.optJSONObject("action");
                    if (actionObj != null) {
                        ScheduleAction action = new ScheduleAction();
                        action.setType(actionObj.optString("type", ""));
                        action.setTitle(actionObj.optString("title", ""));
                        action.setBody(actionObj.optString("body", ""));
                        task.setAction(action);
                    }
                    result.add(task);
                }
            }
            return new TimerTaskModel<>(retCode, retMsg, result);
        } catch (JSONException e) {
            LOGGER.error("TimerTaskFacadeImpl.doParseScheduleTaskList: JSON parse failed: {}", e.getMessage());
            return TimerTaskModel.error(-1, "Parse JSON failed: " + e.getMessage());
        }
    }
}
