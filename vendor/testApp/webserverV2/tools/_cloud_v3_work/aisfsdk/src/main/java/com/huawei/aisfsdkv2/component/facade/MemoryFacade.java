/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.facade;

import com.huawei.aisfsdkv2.component.facade.impl.MemoryFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult;
import com.huawei.aisfsdkv2.component.facade.model.memory.QueryMemoryListRequest;

/**
 * 记忆接口 Facade
 * 封装 ACM 记忆相关 HTTP 接口调用（同步接口）
 */
public interface MemoryFacade {

    /**
     * 获取 MemoryFacade 实例（单例）
     *
     * @return MemoryFacade 实例
     */
    static MemoryFacade getInstance() {
        return MemoryFacadeImpl.getInstance();
    }

    /**
     * 查询记忆列表（同步）
     * 对应接口: POST /acm/v1/memory/query
     *
     * @param request 查询请求参数，包含分类、分页等信息
     * @return 记忆列表信息，包含记忆列表和分页信息
     * @throws MemoryException 查询失败时抛出异常
     */
    MemoryListInfo queryMemoryList(QueryMemoryListRequest request) throws MemoryException;

    /**
     * 查询个人分类记忆/个人画像（同步）
     * 对应接口: GET /acm/v1/memory/persona/classify/query
     *
     * @return 个人分类记忆结果，按类别分组的记忆内容
     * @throws MemoryException 查询失败时抛出异常
     */
    PersonalClassifyMemoryResult queryPersonalClassifyMemory() throws MemoryException;

    /**
     * 查询记忆功能是否开启（同步）
     * 对应接口: POST /acm/v1/user/memory/user_configs_get
     *
     * @return true表示已开启，false表示已关闭
     * @throws MemoryException 查询失败时抛出异常
     */
    boolean queryMemoryEnabled() throws MemoryException;

    /**
     * 设置记忆功能开关（同步）
     * 对应接口: POST /acm/v1/user/memory/user_configs_add
     *
     * @param enabled true表示开启，false表示关闭
     * @throws MemoryException 设置失败时抛出异常
     */
    void setMemoryEnabled(boolean enabled) throws MemoryException;

    /**
     * 删除指定记忆（同步）
     * 对应接口: POST /acm/v1/user/memory/delete
     *
     * @param category 记忆类型
     * @param memoryId 记忆ID
     * @return true表示删除成功，false表示删除失败
     * @throws MemoryException 删除失败时抛出异常
     */
    boolean deleteMemory(String category, String memoryId) throws MemoryException;
}
