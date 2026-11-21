/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.apiimpl.memory;

import com.huawei.aisfsdkv2.api.memory.MemCategoryEnum;
import com.huawei.aisfsdkv2.api.memory.MemoryApi;
import com.huawei.aisfsdkv2.api.memory.MemoryInfo;
import com.huawei.aisfsdkv2.api.memory.MemoryListResult;
import com.huawei.aisfsdkv2.api.memory.MemoryPageInfo;
import com.huawei.aisfsdkv2.api.memory.PersonalClassifyMemoryInfo;
import com.huawei.aisfsdkv2.component.facade.MemoryFacade;
import com.huawei.aisfsdkv2.component.facade.model.memory.BaseMemInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryItem;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryException;
import com.huawei.aisfsdkv2.component.facade.model.memory.MemoryListInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PageInfo;
import com.huawei.aisfsdkv2.component.facade.model.memory.PersonalClassifyMemoryResult;
import com.huawei.aisfsdkv2.component.facade.model.memory.QueryMemoryListRequest;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆接口实现
 *
 * @since 2026-04-07
 */
public class MemoryApiImpl implements MemoryApi {

    private static final Logger LOGGER = LoggerFactory.getLogger("MemoryApiImpl");

    private static final int MEMORY_LIST_PAGE_SIZE = 50;

    private static class Holder {
        private static final MemoryApiImpl INSTANCE = new MemoryApiImpl();
    }

    private MemoryApiImpl() {
    }

    /**
     * 获取单例
     *
     * @return 单例实例
     */
    public static MemoryApiImpl getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 获取记忆列表（自动翻页，返回全量数据）
     *
     * <p>内部按页循环查询，直到 {@code hasNext} 为 false，将所有页数据合并后返回。
     *
     * @param category 记忆类型
     * @return 对应类型的全量记忆列表
     */
    @Override
    public List<MemoryInfo> getMemoryList(MemCategoryEnum category) {
        List<MemoryInfo> allMemories = new ArrayList<>();
        int page = 1;
        try {
            while (true) {
                QueryMemoryListRequest request = new QueryMemoryListRequest(category.getValue(), page, MEMORY_LIST_PAGE_SIZE);
                MemoryListInfo result = MemoryFacade.getInstance().queryMemoryList(request);
                allMemories.addAll(convertMemoryList(result));
                PageInfo pageInfo = result == null ? null : result.getPageInfo();
                if (pageInfo == null || !pageInfo.getHasNext()) {
                    break;
                }
                page++;
            }
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.getMemoryList: failed, code={}, msg={}", e.getCode(), e.getMessage());
        }
        return allMemories;
    }

    /**
     * 分页获取记忆列表
     *
     * @param category 记忆类型
     * @param page     页码
     * @param pageSize 每页大小
     * @return 记忆列表结果（含分页信息）
     */
    @Override
    public MemoryListResult getMemoryListWithPage(MemCategoryEnum category, int page, int pageSize) {
        try {
            QueryMemoryListRequest request = new QueryMemoryListRequest(category.getValue(), page, pageSize);
            MemoryListInfo result = MemoryFacade.getInstance().queryMemoryList(request);
            return convertMemoryListResult(result);
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.getMemoryListWithPage: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return new MemoryListResult();
        }
    }

    /**
     * 获取个人分类记忆
     *
     * @return 分类记忆，key为topic，value为该topic下的记忆列表
     */
    @Override
    public Map<String, List<PersonalClassifyMemoryInfo>> getPersonalClassifyMemory() {
        try {
            PersonalClassifyMemoryResult result = MemoryFacade.getInstance().queryPersonalClassifyMemory();
            return convertPersonalClassifyMemoryResult(result);
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.getPersonalClassifyMemory: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取记忆功能是否开启
     *
     * @return true表示已开启，false表示已关闭
     */
    @Override
    public boolean isMemoryEnabled() {
        try {
            return MemoryFacade.getInstance().queryMemoryEnabled();
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.isMemoryEnabled: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 设置记忆功能开关
     *
     * @param enabled true表示开启，false表示关闭
     * @return true表示设置成功，false表示设置失败
     */
    @Override
    public boolean setMemoryEnabled(boolean enabled) {
        try {
            MemoryFacade.getInstance().setMemoryEnabled(enabled);
            return true;
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.setMemoryEnabled: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 删除指定记忆
     *
     * @param category 记忆类型
     * @param memoryId   记忆ID
     * @return true表示删除成功，false表示删除失败
     */
    @Override
    public boolean deleteMemory(MemCategoryEnum category, String memoryId) {
        try {
            return MemoryFacade.getInstance().deleteMemory(category.getValue(), memoryId);
        } catch (MemoryException e) {
            LOGGER.error("MemoryApiImpl.deleteMemory: failed, code={}, msg={}", e.getCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 将 facade 层个人分类记忆结果转换为 API 层模型
     *
     * @param source facade 层个人分类记忆结果
     * @return API 层个人分类记忆信息
     */
    private Map<String, List<PersonalClassifyMemoryInfo>> convertPersonalClassifyMemoryResult(
            PersonalClassifyMemoryResult source) {
        Map<String, List<PersonalClassifyMemoryInfo>> result = new HashMap<>();
        if (source == null || source.getCategoryMap() == null) {
            return result;
        }
        for (Map.Entry<String, List<PersonalClassifyMemoryItem>> entry : source.getCategoryMap().entrySet()) {
            List<PersonalClassifyMemoryInfo> infoList = new ArrayList<>();
            for (PersonalClassifyMemoryItem item : entry.getValue()) {
                PersonalClassifyMemoryInfo info = new PersonalClassifyMemoryInfo();
                info.setId(item.getId());
                info.setMemType(item.getMemType());
                info.setTopic(item.getTopic());
                info.setContent(item.getContent());
                infoList.add(info);
            }
            result.put(entry.getKey(), infoList);
        }
        return result;
    }

    /**
     * 将 facade 层记忆列表转换为 API 层记忆列表
     *
     * @param source facade 层记忆列表信息
     * @return API 层记忆列表
     */
    private List<MemoryInfo> convertMemoryList(MemoryListInfo source) {
        List<MemoryInfo> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (BaseMemInfo item : source.getMemInfoList()) {
            MemoryInfo info = new MemoryInfo();
            info.setId(item.getId());
            info.setContent(item.getContent());
            info.setDetail(item.getDetail());
            info.setDate(item.getDate());
            info.setMemType(item.getMemType());
            result.add(info);
        }
        return result;
    }

    /**
     * 将 facade 层记忆列表结果转换为 API 层记忆列表结果（含分页）
     *
     * @param source facade 层记忆列表信息
     * @return API 层记忆列表结果
     */
    private MemoryListResult convertMemoryListResult(MemoryListInfo source) {
        MemoryListResult result = new MemoryListResult();
        if (source == null) {
            return result;
        }
        result.setMemoryList(convertMemoryList(source));
        PageInfo srcPage = source.getPageInfo();
        if (srcPage != null) {
            MemoryPageInfo pageInfo = new MemoryPageInfo();
            pageInfo.setPage(srcPage.getPage());
            pageInfo.setLimit(srcPage.getLimit());
            pageInfo.setTotalSize(srcPage.getTotalSize());
            pageInfo.setHasNext(srcPage.getHasNext());
            result.setPageInfo(pageInfo);
        }
        return result;
    }
}
