/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.skill;

import com.huawei.aisfsdkv2.api.skill.CreateSkillRequest;
import com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest;
import com.huawei.aisfsdkv2.api.skill.SkillApi;
import com.huawei.aisfsdkv2.api.skill.SkillInfo;
import com.huawei.aisfsdkv2.component.facade.SkillFacade;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillException;
import com.huawei.aisfsdkv2.component.facade.model.skill.SkillModel;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能接口实现
 *
 * @since 2026-04-07
 */
public class SkillApiImpl implements SkillApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("SkillApiImpl");

    private static class Holder {
        private static final SkillApiImpl INSTANCE = new SkillApiImpl();
    }

    private SkillApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 单例实例
     */
    public static SkillApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 获取用户已创建的技能列表
     * <p>
     * userId 从当前登录信息中自动获取
     *
     * @return 技能列表，查询失败时返回空列表
     */
    @Override
    public List<SkillInfo> getSkillList() {
        try {
            List<SkillModel> facadeList = SkillFacade.getInstance().querySkillList();
            return convertSkillList(facadeList);
        } catch (SkillException e) {
            LOGGER.error("SkillApiImpl.getSkillList: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 创建技能
     *
     * @param request 创建技能请求
     * @return 创建成功返回技能ID，失败返回null
     */
    @Override
    public String createSkill(CreateSkillRequest request) {
        if (request == null) {
            LOGGER.warn("SkillApiImpl.createSkill: request is null");
            return null;
        }
        try {
            return SkillFacade.getInstance().createSkill(request);
        } catch (SkillException e) {
            LOGGER.error("SkillApiImpl.createSkill: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return null;
        }
    }

    /**
     * 删除技能
     *
     * @param request 删除技能请求
     * @return 删除成功返回true，失败返回false
     */
    @Override
    public boolean deleteSkill(DeleteSkillRequest request) {
        if (request == null) {
            LOGGER.warn("SkillApiImpl.deleteSkill: request is null");
            return false;
        }
        try {
            return SkillFacade.getInstance().deleteSkill(request);
        } catch (SkillException e) {
            LOGGER.error("SkillApiImpl.deleteSkill: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 将 Facade 层技能列表转换为 API 层技能列表
     *
     * @param source Facade 层技能列表
     * @return API 层技能列表
     */
    private List<SkillInfo> convertSkillList(List<SkillModel> source) {
        List<SkillInfo> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (SkillModel item : source) {
            SkillInfo info = new SkillInfo();
            info.setSkillId(item.getSkillId());
            info.setSkillName(item.getSkillName());
            info.setSteps(item.getSteps());
            info.setSummary(item.getSummary());
            info.setIsDefault(item.getIsDefault());
            info.setApp(item.getApp());
            info.setVariables(item.getVariables());
            result.add(info);
        }
        return result;
    }
}
