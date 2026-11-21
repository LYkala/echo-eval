# ConnectionManager 接口设计方案

## 背景

当前 `ConnectionManager` 是一个具体的实现类，直接包含 OkHttp 相关代码。为了：
1. **解耦**：接口与实现分离，便于测试和替换实现
2. **可测试性**：可以 mock 接口进行单元测试
3. **扩展性**：未来可能需要不同的连接实现（如更换 WebSocket 库）

## 设计方案

### 目录结构变更

```
login/connection/
├── ConnectionManager.java          # 接口（公共 API）
├── ConnectionParam.java            # 连接参数封装类
├── ConnectionCallback.java         # 回调接口（已存在）
└── impl/
    └── ConnectionManagerImpl.java  # 实现（基于 OkHttp）
```

### 1. ConnectionParam 参数封装类

```java
package com.huawei.aisfsdkv2.login.connection;

/**
 * WebSocket 连接参数
 * 封装建立连接所需的所有参数
 */
public class ConnectionParam {
    private final String url;
    private final String identifyId;
    private final String token;
    private final String userName;
    private final ConnectionCallback callback;

    private ConnectionParam(Builder builder) {
        this.url = builder.url;
        this.identifyId = builder.identifyId;
        this.token = builder.token;
        this.userName = builder.userName;
        this.callback = builder.callback;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getUrl() { return url; }
    public String getIdentifyId() { return identifyId; }
    public String getToken() { return token; }
    public String getUserName() { return userName; }
    public ConnectionCallback getCallback() { return callback; }

    /**
     * Builder 模式构建参数
     */
    public static class Builder {
        private String url;
        private String identifyId;
        private String token;
        private String userName;
        private ConnectionCallback callback;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder identifyId(String identifyId) {
            this.identifyId = identifyId;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder callback(ConnectionCallback callback) {
            this.callback = callback;
            return this;
        }

        public ConnectionParam build() {
            if (url == null || identifyId == null || token == null) {
                throw new IllegalStateException("url, identifyId, token are required");
            }
            return new ConnectionParam(this);
        }
    }
}
```

### 2. ConnectionManager 接口（精简版）

```java
package com.huawei.aisfsdkv2.login.connection;

/**
 * 连接管理器接口
 * 管理 WebSocket 连接的建立、断开和重连
 */
public interface ConnectionManager {

    /**
     * 获取连接管理器实例（单例）
     *
     * @return ConnectionManager 实例
     */
    static ConnectionManager getInstance() {
        return ConnectionManagerImpl.getInstance();
    }

    /**
     * 建立 WebSocket 连接
     *
     * @param param 连接参数（包含 URL、认证信息、回调等）
     */
    void connect(ConnectionParam param);

    /**
     * 断开 WebSocket 连接
     */
    void disconnect();

    /**
     * 重新连接
     * <p>使用上次保存的参数重新建立连接
     */
    void reconnect();

    /**
     * 是否已连接
     *
     * @return true 已连接，false 未连接
     */
    boolean isConnected();
}
```

### 3. ConnectionManagerImpl 实现类

```java
package com.huawei.aisfsdkv2.login.connection.impl;

import com.huawei.aisfsdkv2.login.connection.ConnectionCallback;
import com.huawei.aisfsdkv2.login.connection.ConnectionManager;
import com.huawei.aisfsdkv2.login.connection.ConnectionParam;

/**
 * 连接管理器实现（基于 OkHttp）
 */
public class ConnectionManagerImpl implements ConnectionManager {

    // ... 原有字段

    @Override
    public void connect(ConnectionParam param) {
        // 保存参数
        this.currentParam = param;
        
        // 构建请求
        Request request = new Request.Builder()
                .url(param.getUrl())
                .addHeader("Token", param.getToken())
                .addHeader("Sender", "TTApp")
                .addHeader("Receiver", "ACM")
                .addHeader("DeviceId", param.getIdentifyId())
                .addHeader("UserName", param.getUserName())
                .build();
        
        // 创建 WebSocket...
    }

    @Override
    public void disconnect() {
        // 内部使用默认原因断开
        disconnectInternal("User disconnect");
    }

    @Override
    public void reconnect() {
        // 使用保存的 currentParam 重新连接
        if (currentParam == null) {
            LOGGER.error("No previous connection params");
            return;
        }
        disconnect();
        // 延迟后 connect(currentParam)
    }

    // ... 其他方法
}
```

### 4. 使用示例

```java
// 构建连接参数
ConnectionParam param = ConnectionParam.builder()
        .url("wss://example.com/ws")
        .identifyId("1.PHONE.user_uuid@AISF.cu.gd.com.cn")
        .token("acm_token")
        .userName("username")
        .callback(new ConnectionCallback() {
            @Override
            public void onConnected() {
                // 连接成功
            }
            
            @Override
            public void onDisconnected(int code, String reason) {
                // 连接断开
            }
            
            @Override
            public void onAuthFailed() {
                // 认证失败
            }
        })
        .build();

// 连接
ConnectionManager.getInstance().connect(param);

// 断开
ConnectionManager.getInstance().disconnect();

// 重连
ConnectionManager.getInstance().reconnect();
```

## 变更分析

### 1. connect 参数封装（ConnectionParam）

**优点：**
- ✅ 参数清晰，使用 Builder 模式可读性好
- ✅ 便于扩展（后续添加参数无需修改接口）
- ✅ 必填参数校验（build() 时检查）

**缺点：**
- 需要多创建一个类
- 调用方代码量增加（Builder 链式调用）

**结论：** 推荐，参数多时使用 Builder 模式是标准做法

### 2. disconnect 移除参数

**优点：**
- ✅ 接口更简洁
- ✅ 断开操作本身不需要外部指定原因（内部记录即可）

**缺点：**
- 丢失断开原因信息（日志中无法区分是正常断开还是异常）

**结论：** 推荐，实现内部使用默认原因（如 "User disconnect"），日志足够

### 3. 删除 scheduleReconnect

**优点：**
- ✅ 接口更精简
- ✅ 重连策略完全由上层控制（LoginApiImpl）

**缺点：**
- 上层需要自行实现延迟重连逻辑

**结论：** 推荐，`reconnect()` 已足够，上层控制策略更灵活

## 接口对比

### 变更前
```java
void connect(String url, String identifyId, String token, 
             String userName, ConnectionCallback callback);
void disconnect(String reason);
void reconnect();
void scheduleReconnect(Runnable task);  // 删除
boolean isConnected();
```

### 变更后
```java
void connect(ConnectionParam param);     // 参数封装
void disconnect();                        // 无参
void reconnect();
boolean isConnected();
```

## 实施步骤

1. **创建 `ConnectionParam` 类**
   - 使用 Builder 模式
   - 必填参数校验

2. **创建 `ConnectionManager` 接口**
   - 精简后的方法签名
   - 静态 `getInstance()` 方法

3. **重构 `ConnectionManagerImpl`**
   - 实现新接口
   - 保存 `ConnectionParam` 用于重连

4. **更新使用方**
   - `LoginCoordinator` 改为使用 `ConnectionParam.builder()`

5. **编译测试**

## 确认问题

1. **Builder 模式是否接受？**（也可用带参数的构造函数）
2. **disconnect 内部原因默认值用什么？**（推荐 "User disconnect"）
3. **是否还有其他需要调整的方法？**
