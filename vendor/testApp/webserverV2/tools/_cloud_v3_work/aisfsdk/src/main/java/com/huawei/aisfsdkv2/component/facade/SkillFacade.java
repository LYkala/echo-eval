/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade;

import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.component.facade.impl.SkillFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillException;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel;

import java.util.List;

/**
 * 技能接口 Facade
 * 封装技能相关 HTTP 接口调用（同步接口）
 */
public interface SkillFacade {

    /**
     * 获取 SkillFacade 实例（单例）
     *
     * @return SkillFacade 实例
     */
    static SkillFacade getInstance() {
        return SkillFacadeImpl.getInstance();
    }

    /**
     * 查询技能列表（同步）
     * 对应接口: GET /acm/v1/memory/stepTeachSkill
     *
     * @return 技能列表
     * @throws SkillException 查询失败时抛出异常
     */
    List<SkillModel> querySkillList() throws SkillException;

    /**
     * 创建技能（同步）
     * 对应接口: POST /acm/v1/memory/stepTeachSkill
     *
     * @param request 创建技能请求
     * @return 创建成功返回技能ID
     * @throws SkillException 创建失败时抛出异常
     */
    String createSkill(CreateSkillRequest request) throws SkillException;

    /**
     * 删除技能（同步）
     * 对应接口: DELETE /acm/v1/memory/stepTeachSkill
     *
     * @param request 删除技能请求
     * @return 删除成功返回true，失败返回false
     * @throws SkillException 删除失败时抛出异常
     */
    boolean deleteSkill(DeleteSkillRequest request) throws SkillException;
}
