/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade.model.skill;

/**
 * 技能模型（Facade层）
 *
 * @since 2026-04-07
 */
public class SkillModel {

    /**
     * 技能ID
     */
    private String skillId;

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
     * 是否默认
     */
    private String isDefault;

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
    public SkillModel() {
    }

    /**
     * 获取技能ID
     *
     * @return 技能ID
     */
    public String getSkillId() {
        return skillId;
    }

    /**
     * 设置技能ID
     *
     * @param skillId 技能ID
     */
    public void setSkillId(String skillId) {
        this.skillId = skillId;
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
     * 获取是否默认
     *
     * @return 是否默认
     */
    public String getIsDefault() {
        return isDefault;
    }

    /**
     * 设置是否默认
     *
     * @param isDefault 是否默认
     */
    public void setIsDefault(String isDefault) {
        this.isDefault = isDefault;
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
        return "SkillModel{skillId='" + skillId + "', skillName='" + skillName
                + "', steps='" + steps + "', summary='" + summary
                + "', isDefault='" + isDefault + "', app='" + app
                + "', variables='" + variables + "'}";
    }
}
