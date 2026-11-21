/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.skill;

/**
 * 创建技能请求
 *
 * @since 2026-04-07
 */
public class CreateSkillRequest {

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能步骤
     */
    private String steps;

    /**
     * 技能摘要
     */
    private String summary;

    /**
     * 关联应用
     */
    private String app;

    /**
     * 技能变量
     */
    private String variables;

    /**
     * 默认构造方法
     */
    public CreateSkillRequest() {
    }

    /**
     * 带参数的构造方法
     *
     * @param skillName 技能名称
     * @param steps 技能步骤
     * @param summary 技能摘要
     * @param app 关联应用
     * @param variables 技能变量
     */
    public CreateSkillRequest(String skillName, String steps, String summary, String app, String variables) {
        this.skillName = skillName;
        this.steps = steps;
        this.summary = summary;
        this.app = app;
        this.variables = variables;
    }

    /**
     * 获取技能名称
     *
     * @return 技能名称
     */
    public String getSkillName() {
        return skillName;
    }

    /**
     * 设置技能名称
     *
     * @param skillName 技能名称
     */
    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    /**
     * 获取技能步骤
     *
     * @return 技能步骤
     */
    public String getSteps() {
        return steps;
    }

    /**
     * 设置技能步骤
     *
     * @param steps 技能步骤
     */
    public void setSteps(String steps) {
        this.steps = steps;
    }

    /**
     * 获取技能摘要
     *
     * @return 技能摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 设置技能摘要
     *
     * @param summary 技能摘要
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * 获取关联应用
     *
     * @return 关联应用
     */
    public String getApp() {
        return app;
    }

    /**
     * 设置关联应用
     *
     * @param app 关联应用
     */
    public void setApp(String app) {
        this.app = app;
    }

    /**
     * 获取技能变量
     *
     * @return 技能变量
     */
    public String getVariables() {
        return variables;
    }

    /**
     * 设置技能变量
     *
     * @param variables 技能变量
     */
    public void setVariables(String variables) {
        this.variables = variables;
    }

    @Override
    public String toString() {
        return "CreateSkillRequest{skillName='" + skillName + "', steps='" + steps
                + "', summary='" + summary + "', app='" + app + "', variables='" + variables + "'}";
    }
}
