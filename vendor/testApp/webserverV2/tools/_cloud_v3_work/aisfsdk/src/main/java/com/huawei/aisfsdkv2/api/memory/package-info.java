/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 记忆管理 API 接口包
 *
 * <p>本包提供用户记忆数据的查询、管理和删除能力，支持分类记忆和分页查询。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory.MemoryApi}：记忆管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory.MemCategoryEnum}：记忆分类枚举</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory.MemoryInfo}：记忆信息数据类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory.MemoryListResult}：记忆列表查询结果</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.memory.PersonalClassifyMemoryInfo}：个人分类记忆信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取记忆 API 实例
 *   MemoryApi memoryApi = SdkManager.getInstance().getMemoryApi();
 *
 *   // 检查记忆功能是否开启
 *   if (memoryApi.isMemoryEnabled()) {
 *       // 获取特定类型的记忆列表
 *       List&lt;MemoryInfo&gt; memories = memoryApi.getMemoryList(MemCategoryEnum.PREFERENCE);
 *
 *       // 分页获取记忆
 *       MemoryListResult result = memoryApi.getMemoryListWithPage(
 *           MemCategoryEnum.HABIT, 1, 20);
 *       List&lt;MemoryInfo&gt; pageData = result.getList();
 *       int total = result.getTotal();
 *
 *       // 获取个人分类记忆
 *       Map&lt;String, List&lt;PersonalClassifyMemoryInfo&gt;&gt; classifyMemories =
 *           memoryApi.getPersonalClassifyMemory();
 *
 *       // 删除指定记忆
 *       boolean deleted = memoryApi.deleteMemory(MemCategoryEnum.PREFERENCE, "memory_id_123");
 *   }
 *
 *   // 开启/关闭记忆功能
 *   memoryApi.setMemoryEnabled(true);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>操作前先检查记忆功能是否开启</li>
 *   <li>分页查询适用于大量记忆数据的场景</li>
 *   <li>删除操作不可恢复，建议二次确认</li>
 *   <li>个人分类记忆按 topic 组织，便于分类展示</li>
 *   <li>记忆数据存储在服务端，需要网络连接</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.memory;
