/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

/**
 * 登录认证 API 接口包
 *
 * <p>本包提供用户登录、登出和登录状态管理能力，采用异步回调模式处理登录结果。</p>
 *
 * <h3>核心类说明</h3>
 * <ul>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginApi}：登录接口，提供登录/登出能力</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginCallback}：登录回调接口</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginErrorEnum}：登录错误码枚举</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginInfo}：登录信息数据类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginParam}：登录参数类</li>
 *   <li>{@link com.huawei.aisfsdkv2.api.login.LoginStatus}：登录状态枚举</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 *   // 获取登录 API 实例
 *   LoginApi loginApi = SdkManager.getInstance().getLoginApi();
 *
 *   // 添加全局登录回调（可选）
 *   loginApi.addLoginCallback(new LoginCallback() {
 *       &#64;Override
 *       public void onSuccess() {
 *           // 登录成功处理
 *       }
 *
 *       &#64;Override
 *       public void onFailure(LoginErrorEnum error, String message) {
 *           // 登录失败处理
 *       }
 *   });
 *
 *   // 执行登录
 *   LoginParam param = new LoginParam();
 *   param.setUsername("user123");
 *   param.setPassword("password");
 *   loginApi.login(param);
 *
 *   // 获取登录信息
 *   LoginInfo info = loginApi.getLoginInfo();
 *   if (info.getStatus() == LoginStatus.LOGGED_IN) {
 *       String username = info.getUsername();
 *   }
 *
 *   // 执行登出
 *   loginApi.logout();
 * </pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li>登录操作是异步的，结果通过回调返回，不会抛出异常</li>
 *   <li>支持多回调监听，可用于状态栏、日志等全局监听场景</li>
 *   <li>登录前确保已配置服务器地址和设备信息</li>
 *   <li>登出后会自动清理 Token 和会话数据</li>
 *   <li>应用退出时建议主动调用登出，确保资源释放</li>
 * </ul>
 *
 * @since 2026-04-10
 */
package com.huawei.aisfsdkv2.api.login;
