/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 技能管理 API 接口包
 *
 * <p>本包提供用户技能的查询、创建和删除能力，支持自定义技能管理。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.skill.SkillApi}：技能管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.skill.SkillInfo}：技能信息数据类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.skill.CreateSkillRequest}：创建技能请求</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.skill.DeleteSkillRequest}：删除技能请求</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取技能 API 实例
 *   SkillApi skillApi = SdkManager.getInstance().getSkillApi();
 *
 *   // 获取用户已创建的技能列表
 *   List&lt;SkillInfo&gt; skills = skillApi.getSkillList();
 *   for (SkillInfo skill : skills) {
 *       String skillId = skill.getSkillId();
 *       String skillName = skill.getSkillName();
 *       String description = skill.getDescription();
 *   }
 *
 *   // 创建新技能
 *   CreateSkillRequest request = new CreateSkillRequest();
 *   request.setSkillName("天气查询助手");
 *   request.setDescription("帮助用户查询各地天气情况");
 *   // ... 设置其他参数
 *   String newSkillId = skillApi.createSkill(request);
 *
 *   // 删除技能
 *   DeleteSkillRequest deleteRequest = new DeleteSkillRequest();
 *   deleteRequest.setSkillId("skill_id_123");
 *   boolean deleted = skillApi.deleteSkill(deleteRequest);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>获取技能列表前确保用户已登录</li>
 *   <li>创建技能时提供清晰的名称和描述，便于识别</li>
 *   <li>删除技能是不可逆操作，建议二次确认</li>
 *   <li>技能数据存储在服务端，需要网络连接</li>
 *   <li>频繁操作技能列表建议添加本地缓存</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.skill;
