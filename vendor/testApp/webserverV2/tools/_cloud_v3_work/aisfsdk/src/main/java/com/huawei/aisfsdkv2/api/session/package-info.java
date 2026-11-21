/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 会话管理 API 接口包
 *
 * <p>本包提供会话生命周期管理和对话控制能力，支持多会话并行、流式响应和提醒功能。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.SessionApi}：会话管理接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.SessionCallback}：会话回调接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.SessionResponseData}：会话响应数据</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.DialogInfo}：对话信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.DialogControl}：对话控制参数</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.TaskInfo}：任务信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.TextInfo}：文本信息</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.session.NotifyInfo}：通知信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取会话 API 实例
 *   SessionApi sessionApi = SdkManager.getInstance().getSessionApi();
 *
 *   // 创建会话并设置回调
 *   String sessionId = sessionApi.createSession(new SessionCallback() {
 *       &#64;Override
 *       public void onReceived(int errorCode, String errorMessage,
 *                            SessionResponseData data) {
 *           if (errorCode == 0) {
 *               // 处理响应数据
 *               String text = data.getText();
 *               boolean isStreamEnd = data.isStreamEnd();
 *           }
 *       }
 *   });
 *
 *   // 发送对话
 *   DialogInfo dialogInfo = new DialogInfo();
 *   dialogInfo.setText("你好，请帮我查天气");
 *   sessionApi.talk(sessionId, dialogInfo);
 *
 *   // 控制对话（如停止生成）
 *   DialogControl control = new DialogControl();
 *   control.setStopGeneration(true);
 *   sessionApi.control(sessionId, control);
 *
 *   // 结束会话
 *   sessionApi.finish(sessionId);
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>每个业务场景建议创建独立会话，避免相互干扰</li>
 *   <li>会话结束或页面销毁时及时调用 finish 释放资源</li>
 *   <li>流式响应时关注 isStreamEnd 标志判断结束</li>
 *   <li>长对话建议分页或分会话处理，避免上下文过长</li>
 *   <li>网络异常时会自动重连，业务层无需额外处理</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.session;
