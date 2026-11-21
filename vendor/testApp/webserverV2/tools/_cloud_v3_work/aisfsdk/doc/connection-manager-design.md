# ConnectionManager 设计方案

## 现状分析

### 当前实现问题

`ConnectionManager` 目前是一个空壳，仅保存了连接参数，实际连接逻辑未实现：

```java
// 当前状态
- connect(): 调用 simulateConnect() 模拟连接
- disconnect(): 空实现（仅打印日志）
- reconnect(): 空实现（仅打印日志）
```

### 现有技术栈

**SDK 已依赖 OkHttp 4.9.3：**
```gradle
implementation 'com.squareup.okhttp3:okhttp:4.9.3'
```

**旧版 V1 实现参考：**
- `com.huawei.aisfsdk.protocol.WebsocketClient`
- 基于 OkHttp 的 WebSocket 实现
- 包含 SSL、心跳、认证头域等

**决策：直接使用 OkHttp，不复用现有 WebsocketClient 封装**

理由：
1. 减少中间层，代码更直接
2. ConnectionManager 自己掌控连接生命周期
3. 避免继承旧版设计包袱

---

## 设计方案：直接使用 OkHttp

### 核心设计

```java
public class ConnectionManager {
    private OkHttpClient okHttpClient;
    private WebSocket webSocket;
    
    // 连接参数
    private String currentUrl;
    private String currentIdentifyId;
    private String currentToken;
    private String currentUserName;
    
    // 状态
    private volatile boolean isConnected = false;
}
```

### 实现要点

#### 1. OkHttpClient 配置

```java
private OkHttpClient createOkHttpClient() {
    return new OkHttpClient.Builder()
        .sslSocketFactory(...)  // SSL 配置
        .hostnameVerifier(...)
        .pingInterval(30, TimeUnit.SECONDS)  // 心跳
        .build();
}
```

#### 2. WebSocket 连接

```java
public void connect(String url, String identifyId, String token, 
                    String userName, ConnectionCallback callback) {
    // 保存参数用于重连
    
    Request request = new Request.Builder()
        .url(url)
        .addHeader("Token", token)
        .addHeader("Sender", "TTApp")
        .addHeader("Receiver", "ACM")
        .addHeader("DeviceId", identifyId)
        .addHeader("UserName", userName)
        .build();
    
    webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            // HTTP 200/101: 连接成功
            // HTTP 401/407: 认证失败 → callback.onAuthFailed()
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            // 连接关闭 → callback.onDisconnected(code, reason)
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            // 连接失败 → callback.onDisconnected(code, t.getMessage())
        }
    });
}
```

#### 3. 认证失败检测

参考旧版实现：
```java
private boolean isAuthenticationFailure(int code) {
    return code == 401 || code == 407;
}
```

#### 4. 断开与重连

```java
public void disconnect(String reason) {
    if (webSocket != null) {
        webSocket.close(1000, reason);
    }
}

public void reconnect() {
    // 使用保存的参数重新调用 connect
    connect(currentUrl, currentIdentifyId, currentToken, 
            currentUserName, savedCallback);
}
```

---

## 待确认问题

### 1. SSL 配置

旧版实现了自定义 SSL SocketFactory 和 TrustManager：
```java
SSLSocketFactory getSSLSocketFactory() {...}
X509TrustManager getTrustManager() {...}
HostnameVerifier getHostnameVerifier() {...}
```

**问题：**
- 是否需要完全复用旧版 SSL 逻辑？
- 是否有证书校验需求？

### 2. 心跳机制

OkHttp 支持 `pingInterval()` 自动心跳。

**问题：**
- 心跳间隔多少合适？（旧版 30 秒）
- 是否需要应用层心跳（发送 Ping 消息）？

### 3. 消息收发

**问题：**
- ConnectionManager 是否负责消息收发？
- 还是只负责连接管理，消息由其他组件处理？

### 4. 自动重连

**问题：**
- ConnectionManager 内部是否实现自动重连？
- 还是仅提供 reconnect() 接口，由 LoginApiImpl 决定何时重连？

**建议：** 不自动重连，只提供接口。重连策略由上层（LoginApiImpl）控制，因为：
- 需要配合 Token 刷新
- 需要判断重连次数/间隔
- 需要回调通知业务层

### 5. 状态同步

**问题：**
- ConnectionManager 是否需要维护连接状态（isConnected）？
- 还是只依赖 WebSocket 的状态回调？

**建议：** 维护 `volatile boolean isConnected`，用于快速判断。

---

## 接口定义

```java
public class ConnectionManager {
    // 单例
    public static ConnectionManager getInstance();
    
    // 建立连接
    public void connect(String url, String identifyId, String token, 
                        String userName, ConnectionCallback callback);
    
    // 断开连接
    public void disconnect(String reason);
    
    // 重新连接（使用上次参数）
    public void reconnect();
    
    // 发送消息（如果需要）
    public boolean sendMessage(String message);
    
    // 是否已连接
    public boolean isConnected();
}

interface ConnectionCallback {
    void onConnected();
    void onDisconnected(int code, String reason);
    void onAuthFailed();
    void onMessageReceived(String message);  // 如果需要处理消息
}
```

---

## 实现步骤

1. **确认上述问题**（SSL、心跳、消息、重连）
2. **添加 OkHttp 依赖**（已存在，确认版本）
3. **实现 ConnectionManager**
   - SSL 配置（复用或简化旧版）
   - connect/disconnect/reconnect
   - WebSocketListener 回调转换
4. **移除 simulateConnect 模拟代码**
5. **集成测试**

---

## 下一步

请确认：
1. SSL 配置是否复用旧版逻辑？
2. ConnectionManager 是否处理消息收发？
3. 自动重连由谁负责？
