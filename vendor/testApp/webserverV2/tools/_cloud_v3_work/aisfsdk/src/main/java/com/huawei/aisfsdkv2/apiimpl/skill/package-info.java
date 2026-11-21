/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 技能管理 API 实现包
 *
 * <p>本包提供 {@link com.huawei.aisfsdkv2.api.skill.SkillApi} 接口的实现，
 * 通过 ACM 服务 HTTP 接口实现技能的查询、创建和删除。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.skill.SkillApiImpl}：技能 API 实现类</li>
 * </ul>
 *
 * <h3>实现细节</h3>
 * <ul>
 *   <li>所有操作通过 ACM Facade 调用服务端接口</li>
 *   <li>用户 ID 从当前登录信息自动获取</li>
 *   <li>创建成功返回技能 ID，失败返回 null</li>
 *   <li>删除操作返回布尔值表示成功/失败</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>本包类仅供 SDK 内部使用，业务代码应通过 {@link com.huawei.aisfsdkv2.SdkManager#getSkillApi()} 访问。</p>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl.skill;
