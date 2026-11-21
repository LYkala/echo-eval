/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.api.skill;

import java.util.List;

/**
 * 技能接口
 *
 * @since 2026-04-07
 */
public interface SkillApi {

    /**
     * 获取用户已创建的技能列表
     * <p>
     * userId 从当前登录信息中自动获取
     *
     * @return 技能列表，查询失败时返回空列表
     */
    List<SkillInfo> getSkillList();

    /**
     * 创建技能
     *
     * @param request 创建技能请求
     * @return 创建成功返回技能ID，失败返回null
     */
    String createSkill(CreateSkillRequest request);

    /**
     * 删除技能
     *
     * @param request 删除技能请求
     * @return 删除成功返回true，失败返回false
     */
    boolean deleteSkill(DeleteSkillRequest request);
}
