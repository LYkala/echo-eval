package com.example.testapp.util;

import com.example.testapp.model.Params;
import com.example.testapp.model.Target;
import com.example.testapp.model.TestFlow;
import com.example.testapp.model.TestStep;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSON 解析器
 */
public class JsonParser {

    private static final String TAG = "JsonParser";

    /**
     * 解析测试流程 JSON
     */
    public TestFlow parse(String jsonContent) throws JSONException {
        Logger logger = Logger.getInstance();

        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            if (logger != null) {
                logger.error(TAG, "JSON内容为空", new Exception("Empty JSON"));
            }
            return null;
        }

        if (logger != null) {
            logger.info(TAG, "=== 开始解析JSON ===");
            logger.debug(TAG, "JSON长度: " + jsonContent.length());
        }

        try {
            JSONObject json = new JSONObject(jsonContent);
            if (logger != null) logger.debug(TAG, "JSON对象创建成功");

            TestFlow flow = new TestFlow();

            // 解析基本字段
            flow.setVersion(json.getString("version"));
            flow.setTestId(json.getString("testId"));
            flow.setTestName(json.optString("testName", ""));
            flow.setDescription(json.optString("description", ""));
            flow.setCaseType(json.optString("caseType", "GUI"));
            flow.setTimeout(json.optInt("timeout", 60000));
            flow.setExitBackCount(json.optInt("exitBackCount", 2));

            if (logger != null) {
                logger.debug(TAG, "testId: " + flow.getTestId());
                logger.debug(TAG, "version: " + flow.getVersion());
            }

            // 解析 targetApp
            if (json.has("targetApp")) {
                JSONObject targetApp = json.getJSONObject("targetApp");
                flow.setTargetPackage(targetApp.getString("packageName"));
                flow.setTargetActivity(targetApp.optString("mainActivity", null));
                if (logger != null) {
                    logger.debug(TAG, "targetApp: " + flow.getTargetPackage() + "/" + flow.getTargetActivity());
                }
            } else {
                if (logger != null) logger.warn(TAG, "JSON中缺少targetApp字段");
            }

            // 解析 steps
            if (json.has("steps")) {
                JSONArray steps = json.getJSONArray("steps");
                if (logger != null) logger.info(TAG, "找到 " + steps.length() + " 个步骤");

                for (int i = 0; i < steps.length(); i++) {
                    JSONObject stepJson = steps.getJSONObject(i);
                    TestStep step = parseStep(stepJson, i);
                    flow.addStep(step);
                }
            } else {
                if (logger != null) logger.warn(TAG, "JSON中缺少steps字段");
            }

            if (logger != null) {
                logger.info(TAG, "JSON解析完成: " + flow.getTestId() + ", " + flow.getSteps().size() + " 个步骤");
            }
            return flow;

        } catch (JSONException e) {
            if (logger != null) logger.error(TAG, "JSON解析失败", e);
            throw e;
        }
    }

    /**
     * 解析单个步骤
     */
    private TestStep parseStep(JSONObject json, int index) throws JSONException {
        Logger logger = Logger.getInstance();

        TestStep step = new TestStep();
        step.setStepId(json.optString("stepId", "step_" + index));
        step.setDescription(json.optString("description", ""));
        step.setAction(json.getString("action"));

        if (logger != null) {
            logger.debug(TAG, "步骤" + index + ": " + step.getStepId() + ", action=" + step.getAction());
        }

        if (json.has("target")) {
            step.setTarget(parseTarget(json.getJSONObject("target")));
        }

        if (json.has("params")) {
            step.setParams(parseParams(json.getJSONObject("params")));
        }

        return step;
    }

    /**
     * 解析元素定位目标
     */
    private Target parseTarget(JSONObject json) throws JSONException {
        Target target = new Target();
        target.setStrategy(json.getString("strategy"));
        target.setValue(json.getString("value"));
        target.setIndex(json.optInt("index", 0));
        return target;
    }

    /**
     * 解析动作参数
     */
    private Params parseParams(JSONObject json) throws JSONException {
        Params params = new Params();
        params.setText(json.optString("text", null));
        params.setDirection(json.optString("direction", null));
        params.setDistance(json.optInt("distance", 0));
        params.setDuration(json.optInt("duration", 300));
        params.setTimeout(json.optInt("timeout", 5000));
        params.setX(json.optInt("x", 0));
        params.setY(json.optInt("y", 0));
        params.setStartX(json.optInt("startX", 0));
        params.setStartY(json.optInt("startY", 0));
        params.setEndX(json.optInt("endX", 0));
        params.setEndY(json.optInt("endY", 0));
        params.setClearFirst(json.optBoolean("clearFirst", false));
        params.setSaveName(json.optString("saveName", null));
        params.setMaxScrolls(json.optInt("maxScrolls", 0));
        params.setClarifyMessage(json.optString("clarifyMessage", null));
        params.setBroadcastAction(json.optString("action", null));
        params.setComponent(json.optString("component", null));
        return params;
    }
}