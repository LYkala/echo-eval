# HttpClientManager 接口设计方案

## 背景

当前 `HttpClientManager` 是一个具体的实现类，管理两种 OkHttpClient 实例（通用客户端和文件操作客户端）。为了：
1. **解耦**：接口与实现分离，便于测试和替换实现
2. **可测试性**：可以 mock 接口进行单元测试
3. **扩展性**：未来可能需要不同的 HTTP 客户端实现

## 当前实现分析

```java
public class HttpClientManager {
    private OkHttpClient commonHttpClient;   // 通用客户端（短超时）
    private OkHttpClient fileHttpClient;     // 文件客户端（长超时，低并发）
    
    public OkHttpClient getCommonHttpClient() { ... }
    public OkHttpClient getFileHttpClient() { ... }
}
```

**配置差异：**

| 配置项 | 通用客户端 | 文件客户端 |
|--------|-----------|-----------|
| connectTimeout | 2s | 5s |
| readTimeout | 5s | 120s |
| writeTimeout | 5s | 120s |
| callTimeout | 10s | 300s |
| maxRequestsPerHost | 5 | 2 |

## 设计方案

### 目录结构变更

```
component/httpclient/
├── HttpClientManager.java          # 接口（公共 API）
└── impl/
    └── HttpClientManagerImpl.java  # 实现
```

### 1. HttpClientManager 接口

```java
package com.huawei.aisfsdkv2.component.httpclient;

import com.huawei.aisfsdkv2.component.httpclient.impl.HttpClientManagerImpl;

import okhttp3.OkHttpClient;

/**
 * HTTP 客户端管理器接口
 * 提供通用和文件操作两种 HTTP 客户端
 */
public interface HttpClientManager {

    /**
     * 获取 HTTP 客户端管理器实例（单例）
     *
     * @return HttpClientManager 实例
     */
    static HttpClientManager getInstance() {
        return HttpClientManagerImpl.getInstance();
    }

    /**
     * 获取通用 HTTP 客户端
     * <p>适用于普通 API 请求，超时时间较短
     *
     * @return OkHttpClient 实例
     */
    OkHttpClient getCommonHttpClient();

    /**
     * 获取文件操作 HTTP 客户端
     * <p>适用于文件上传/下载，超时时间较长，并发数限制更低
     *
     * @return OkHttpClient 实例
     */
    OkHttpClient getFileHttpClient();
}
```

### 2. HttpClientManagerImpl 实现类

```java
package com.huawei.aisfsdkv2.component.httpclient.impl;

import android.os.Build;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/**
 * HTTP 客户端管理器实现
 */
public class HttpClientManagerImpl implements HttpClientManager {

    private OkHttpClient commonHttpClient;
    private OkHttpClient fileHttpClient;

    private static class Holder {
        private static final HttpClientManagerImpl INSTANCE = new HttpClientManagerImpl();
    }

    public static HttpClientManagerImpl getInstance() {
        return Holder.INSTANCE;
    }

    private HttpClientManagerImpl() {
        initCommonHttpClient();
        initFileHttpClient();
    }

    @Override
    public OkHttpClient getCommonHttpClient() {
        return commonHttpClient;
    }

    @Override
    public OkHttpClient getFileHttpClient() {
        return fileHttpClient;
    }

    private void initCommonHttpClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(5);
            ConnectionPool connectionPool = new ConnectionPool(1, 1, TimeUnit.MINUTES);
            commonHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .readTimeout(Duration.ofSeconds(5))
                    .writeTimeout(Duration.ofSeconds(5))
                    .callTimeout(Duration.ofSeconds(10))
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .build();
        }
    }

    private void initFileHttpClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(2);
            ConnectionPool connectionPool = new ConnectionPool(1, 1, TimeUnit.MINUTES);
            fileHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(120))
                    .writeTimeout(Duration.ofSeconds(120))
                    .callTimeout(Duration.ofSeconds(300))
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .build();
        }
    }
}
```

### 3. 使用方式（不变）

```java
// 获取通用客户端（API 请求）
OkHttpClient client = HttpClientManager.getInstance().getCommonHttpClient();

// 获取文件客户端（上传/下载）
OkHttpClient fileClient = HttpClientManager.getInstance().getFileHttpClient();
```

## 变更分析

### 优点

1. **接口与实现分离**：便于单元测试和 mock
2. **使用方无感知**：`HttpClientManager.getInstance()` 调用方式不变
3. **未来可扩展**：可以更换实现（如使用其他 HTTP 库）

### 影响范围

| 文件 | 变更 |
|------|------|
| `HttpClientManager.java` | 变为接口，提取公共方法 |
| 新增 `HttpClientManagerImpl.java` | 移入 `impl` 包，实现接口 |
| 使用方 | 无需修改，调用方式不变 |

## 待讨论问题

### 1. 是否需要添加关闭/释放资源方法？

当前实现没有关闭方法，`OkHttpClient` 的资源释放依赖垃圾回收。

**建议：** 暂不添加，需要时后续扩展。

### 2. 是否需要支持动态配置？

当前超时和并发配置是硬编码的。

**建议：** 暂不添加，需要时后续扩展（可通过构造函数或 Builder 模式）。

### 3. Android 版本检查

当前只在 `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` 时初始化。

**问题：** 低版本返回 null，是否需要处理？

**建议：** 保持现状（或添加默认值），因为 SDK minSdk 是 28（Android 9.0），而 O 是 26。

## 实施步骤

1. **创建 `component/httpclient/impl` 目录**
2. **创建 `HttpClientManager` 接口**
   - 提取 `getCommonHttpClient` 和 `getFileHttpClient` 方法
   - 添加静态 `getInstance()` 方法
3. **创建 `HttpClientManagerImpl`**
   - 移入 `impl` 包
   - 实现 `HttpClientManager` 接口
   - 单例 `getInstance()` 改为 public
4. **删除原 `HttpClientManager` 类**（或替换为接口）
5. **编译测试**

## 确认问题

1. **是否需要添加关闭方法 `shutdown()`？**
2. **是否需要支持动态配置？**
3. **确认后开始实现？**
