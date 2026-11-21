/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * AISF SDK V2 API 实现层
 *
 * <p>本包包含所有公共 API 接口的实现类，采用单例模式和依赖注入设计，
 * 对外隐藏实现细节，通过 {@link com.huawei.aisfsdkv2.SdkManager} 统一暴露。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口隔离</b>：实现类仅依赖接口，不直接依赖其他实现</li>
 *   <li><b>单例模式</b>：所有实现类均为单例，通过静态方法获取实例</li>
 *   <li><b>线程安全</b>：实现类内部处理线程同步，对外提供线程安全的方法</li>
 *   <li><b>异常处理</b>：内部捕获异常，通过回调或返回值通知调用方</li>
 * </ul>
 *
 * <h3>子包说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.base}：基础 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.log}：日志 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.login}：登录认证 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.memory}：记忆管理 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.session}：会话管理 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.skill}：技能管理 API 实现</li>
 *   <li>{@link com.huawei.aisfsdkv2.apiimpl.timertask}：定时任务 API 实现</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <p>业务代码不应直接访问本包中的类，应通过 {@code api} 包的接口使用 SDK 功能。</p>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>实现类修改需保持接口契约不变</li>
 *   <li>新增功能优先在接口层定义，再提供实现</li>
 *   <li>实现类内部使用组件层服务完成具体功能</li>
 *   <li>避免实现类之间直接依赖，通过接口或事件总线解耦</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.apiimpl;
