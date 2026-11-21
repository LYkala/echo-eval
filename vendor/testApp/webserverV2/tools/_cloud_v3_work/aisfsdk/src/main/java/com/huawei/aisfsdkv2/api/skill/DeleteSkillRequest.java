/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.skill;

/**
 * 删除技能请求
 *
 * @since 2026-04-08
 */
public class DeleteSkillRequest {

    /**
     * 技能ID
     */
    private String skillId;

    /**
     * 默认构造方法
     */
    public DeleteSkillRequest() {
    }

    /**
     * 构造方法
     *
     * @param skillId 技能ID
     */
    public DeleteSkillRequest(String skillId) {
        this.skillId = skillId;
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

    @Override
    public String toString() {
        return "DeleteSkillRequest{" +
                "skillId='" + skillId + '\'' +
                '}';
    }
}
