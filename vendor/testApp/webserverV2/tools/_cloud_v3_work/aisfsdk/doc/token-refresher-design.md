# TokenRefresher 架构设计文档

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    LoginCoordinator                          │
│  (协调登录流程，调用TokenManager获取Token)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │ fetchToken(identifyId, username, password)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    TokenManager (单例)                       │
│  - 管理Token生命周期（获取、存储、刷新监控）                    │
│  - 持有 TokenRefresher 策略接口                               │
└──────────────────────┬──────────────────────────────────────┘
                       │ refresher.fetch(identifyId, username, password)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              TokenRefresher (策略接口)                        │
│         ┌─────────────────────────────┐                     │
│         │   AcmTokenRefresher (实现)   │                     │
│         │   调用 ACMFacade 获取真实Token │                     │
│         └─────────────────────────────┘                     │
└──────────────────────┬──────────────────────────────────────┘
                       │ auth(identifyId, username, password)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    ACMFacade (单例)                          │
│  - HTTP 通信层                                               │
│  - 调用 ACM 服务 /acm/v1/auth                                │
│  - 返回 AuthResult (token, expireTime)                       │
└─────────────────────────────────────────────────────────────┘
```

## 核心流程

### 1. Token 获取流程

```
LoginCoordinator.startLoginFlow(username, password, devInfo, callback)
    │
    ├── String identifyId = buildIdentifyId(username, devInfo)
    │   格式: "1.{deviceType}.{username}_{uuid}@AISF.cu.gd.com.cn"
    │
    ├── String acmToken = TokenManager.getInstance()
    │                      .fetchToken(identifyId, username, password)
    │       │
    │       └── TokenRefresher.fetch(identifyId, username, password)
    │               │
    │               └── ACMFacade.getInstance()
    │                      .auth(identifyId, username, password)
    │                       │
    │                       └── HTTP POST /acm/v1/auth
    │                           Header: UUID=identifyId
    │                           Body: { "Account": username, "Passwd": password }
    │                           │
    │                           └── 返回 AuthResult
    │                               │
    │                               └── TokenResult.success(token, expireTime)
    │
    └── callback.onTokenAcquired(acmToken, identifyId)
```

### 2. Token 刷新流程

```
TokenManager.startRefreshMonitor()
    │
    ├── 定时检查 Token 是否即将过期（expireTime - 当前时间 < 3小时）
    │
    └── 触发刷新: refreshToken()
            │
            ├── TokenRefresher.refresh(currentToken)
            │       │
            │       └── ACMFacade (使用当前Token换取新Token)
            │           或复用 auth() 接口
            │
            └── 更新 acmToken, expireTime
```

## 关键类设计

### AcmTokenRefresher

```java
public class AcmTokenRefresher implements TokenRefresher {
    
    @Override
    public TokenResult fetch(String identifyId, String username, String password) {
        // 调用 ACMFacade 获取真实 Token
        try {
            AuthResult authResult = ACMFacade.getInstance()
                    .auth(identifyId, username, password);
            return TokenResult.success(
                authResult.getToken(), 
                authResult.getExpireTime()
            );
        } catch (AcmException e) {
            return TokenResult.failure(e.getMessage());
        }
    }
    
    @Override
    public TokenResult refresh(String currentToken) {
        // 使用当前 Token 换取新 Token
        // 策略1: 复用 auth() 接口（需要 username/password）
        // 策略2: 调用 ACM 刷新接口（如果存在）
        // 当前实现采用策略1
        // TODO: 确定 ACM 是否有专门的刷新接口
    }
}
```

### ACMFacade.auth

```java
public AuthResult auth(String identifyId, String username, String password) 
        throws AcmException {
    // HTTP 请求
    POST /acm/v1/auth
    
    Headers:
        AppName: xxx
        DeviceId: xxx
        DeviceCategory: xxx
        UUID: {identifyId}              // 替换原有的 getDeviceUuid()
    
    Body:
        {
            "Account": {username},      // 用户名
            "Passwd": {password}        // 密码
        }
    
    Response:
        {
            "Token": "xxx",
            "ExpireTime": 7200
        }
}
```

## 错误处理

| 层级 | 异常/错误 | 处理方式 |
|------|-----------|----------|
| ACMFacade | AcmException (HTTP错误、网络错误) | 捕获并转为 TokenResult.failure() |
| AcmTokenRefresher | 转换异常 | 返回 TokenResult.failure(message) |
| TokenManager | 重试机制 | 最多60次重试，每次间隔3秒 |
| LoginCoordinator | Token 为空 | 回调 onTokenFailed() |

## 与旧实现的区别

| 项目 | 旧实现 (Mock) | 新实现 (真实) |
|------|---------------|---------------|
| Token来源 | 本地生成 `mock_token_xxx` | ACM 服务端颁发 |
| 有效期 | 固定7200秒 | 服务端返回的真实 expireTime |
| 认证方式 | 无认证 | username + password 认证 |
| 设备标识 | 未使用 | identifyId 放入 UUID Header |

## 后续优化点

1. **刷新策略**: 确定 ACM 是否有专门的 Token 刷新接口（vs 复用 auth 接口）
2. **缓存优化**: TokenManager 是否需要在内存中保存 username/password 用于刷新
3. **安全增强**: 考虑使用 HTTPS + 证书 pinning 防止中间人攻击
4. **降级方案**: 网络异常时是否允许使用本地缓存的 Token（需评估安全性）

## 时序图

```
用户调用 login()
    │
    ▼
LoginCoordinator
    │ startLoginFlow(username, password, ...)
    ▼
TokenManager.fetchToken(identifyId, username, password)
    │ (同步阻塞，带重试)
    ▼
AcmTokenRefresher.fetch(identifyId, username, password)
    │
    ├── 构建 HTTP 请求
    │   URL: /acm/v1/auth
    │   Header UUID: identifyId
    │   Body: {username, password}
    │
    ▼
ACMFacade.auth() ───────► ACM 服务端
    │                           │
    │                           │ 验证 username/password
    │                           │ 生成 Token
    │                           │
    │◄────────────── AuthResult
    │   {token, expireTime}
    │
    ▼
TokenResult.success(token, expireTime)
    │
    ▼
返回给 LoginCoordinator
    │
    ▼
继续 WebSocket 连接流程
```

---
**文档版本**: v1.0  
**最后更新**: 2026-03-20  
**采用方案**: A (AcmTokenRefresher 调用 ACMFacade)
