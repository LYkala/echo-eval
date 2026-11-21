/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.impl;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientException;
import com.huawei.aisfsdkv2.component.httpclient.http.HttpClient;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpResponse;
import com.huawei.aisfsdkv2.component.httpclient.model.NettyHttpRequest.MultipartPart;
import com.huawei.aisfsdkv2.component.httpclient.callback.HttpCallback;
import com.huawei.aisfsdkv2.component.httpclient.callback.HttpInterceptor;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.SSLUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.Future;

import javax.net.ssl.SSLException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP客户端实现
 *
 * @since 2026-05-25
 */
final class HttpClientImpl implements HttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("HttpClientImpl");

    private static final int DEFAULT_MAX_CONNECTIONS = 10;
    private static final int DEFAULT_ACQUIRE_TIMEOUT_MS = 5000;
    private static final int DEFAULT_MAX_PENDING_ACQUIRES = 30;
    private static final int POOL_IDLE_TIMEOUT_MS = 60000;
    private static final int POOL_CLEANUP_INTERVAL_SECONDS = 30;

    private final int readTimeoutMs;
    private final int writeTimeoutMs;
    private final int callTimeoutMs;
    private final int maxConnections;
    private final int acquireTimeoutMs;
    private final int maxPendingAcquires;

    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final SslContext sslContext;
    private final ConcurrentHashMap<String, FixedChannelPool> poolMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolStateTracker> poolTrackerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> poolLastUsedTimeMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final List<HttpInterceptor> interceptors = new CopyOnWriteArrayList<>();

    /**
     * 连接池状态追踪器（每个host:port:ssl组合独立追踪）
     * <p>追踪acquire/release计数，用于诊断连接泄漏和池耗尽问题。
     */
    private static final class PoolStateTracker {
        private final AtomicInteger activeCount = new AtomicInteger(0);
        private final AtomicLong totalAcquired = new AtomicLong(0);
        private final AtomicLong totalReleased = new AtomicLong(0);
        private final AtomicLong totalCreated = new AtomicLong(0);
        private final AtomicLong acquireTimeouts = new AtomicLong(0);
        private final AtomicLong acquireFailures = new AtomicLong(0);
        private final AtomicLong releaseFailures = new AtomicLong(0);
        private final String poolKey;
        PoolStateTracker(String poolKey) {
            this.poolKey = poolKey;
        }
        void onAcquire() {
            int active = activeCount.incrementAndGet();
            totalAcquired.incrementAndGet();
            if (active > 8) {
                LOGGER.warn("Pool[{}] high usage: active={}/maxConnections={}, "
                    + "totalAcquired={}, totalReleased={}, leakSuspect={}",
                    poolKey, active, DEFAULT_MAX_CONNECTIONS, totalAcquired.get(), totalReleased.get(),
                    totalAcquired.get() - totalReleased.get());
            }
        }
        void onRelease() {
            int active = activeCount.decrementAndGet();
            totalReleased.incrementAndGet();
            if (active < 0) {
                LOGGER.error("Pool[{}] release over-acquire: active={}, totalAcquired={}, "
                    + "totalReleased={}. Possible double-release!",
                    poolKey, active, totalAcquired.get(), totalReleased.get());
                activeCount.set(0);
            }
        }
        void onCreate() {
            totalCreated.incrementAndGet();
        }
        void onAcquireTimeout() {
            acquireTimeouts.incrementAndGet();
        }
        void onAcquireFailure() {
            acquireFailures.incrementAndGet();
        }
        void onReleaseFailure() {
            releaseFailures.incrementAndGet();
        }
        String getSummary() {
            return String.format(Locale.ROOT,
                "active=%d, maxConn=%d, totalAcquired=%d, totalReleased=%d, "
                    + "totalCreated=%d, acquireTimeouts=%d, acquireFailures=%d, leakSuspect=%d",
                activeCount.get(), DEFAULT_MAX_CONNECTIONS, totalAcquired.get(), totalReleased.get(),
                totalCreated.get(), acquireTimeouts.get(), acquireFailures.get(),
                totalAcquired.get() - totalReleased.get());
        }
    }

    HttpClientImpl() {
        this(2000, 5000, 5000, 10000);
    }

    HttpClientImpl(int connectTimeoutMs, int readTimeoutMs, int writeTimeoutMs, int callTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        this.callTimeoutMs = callTimeoutMs;
        this.maxConnections = DEFAULT_MAX_CONNECTIONS;
        this.acquireTimeoutMs = DEFAULT_ACQUIRE_TIMEOUT_MS;
        this.maxPendingAcquires = DEFAULT_MAX_PENDING_ACQUIRES;

        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
        this.sslContext = initSslContext();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupIdlePools,
            POOL_CLEANUP_INTERVAL_SECONDS,
            POOL_CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        LOGGER.info("HttpClient initialized: connect={}, read={}, write={}, call={}ms, "
            + "maxConnections={}, maxPendingAcquires={}, acquireTimeout={}ms",
            connectTimeoutMs, readTimeoutMs, writeTimeoutMs, callTimeoutMs,
            maxConnections, maxPendingAcquires, acquireTimeoutMs);
    }

    private SslContext initSslContext() {
        SslContext ctx = SSLUtil.createSslContextForNetty();
        if (ctx != null) {
            LOGGER.info("SSL context initialized from TLSConfig");
            return ctx;
        }
        LOGGER.warn("SSLUtil returned null, using system default SSL context");
        try {
            return SslContextBuilder.forClient().build();
        } catch (SSLException e) {
            throw new HttpClientException("Failed to create SSL context", e);
        }
    }

    private String getPoolKey(String host, int port, boolean ssl) {
        return host + ":" + port + ":" + (ssl ? "https" : "http");
    }

    /**
     * 连接池Handler，管理Channel生命周期中的Pipeline配置
     * <p>channelCreated：初始化Pipeline（SSL + HttpClientCodec）
     * <p>channelAcquired：添加请求级TimeoutHandler
     * <p>channelReleased：移除TimeoutHandler，避免空闲连接触发超时
     */
    private final class HttpClientPoolHandler implements ChannelPoolHandler {
        private final String poolKey;
        private final String host;
        private final int port;
        private final boolean ssl;
        private final PoolStateTracker tracker;

        HttpClientPoolHandler(String poolKey, String host, int port, boolean ssl, PoolStateTracker tracker) {
            this.poolKey = poolKey;
            this.host = host;
            this.port = port;
            this.ssl = ssl;
            this.tracker = tracker;
        }

        @Override
        public void channelCreated(Channel ch) {
            tracker.onCreate();
            LOGGER.info("Pool[{}] channelCreated: channelId={}, channelActive={}, tracker={}",
                poolKey, ch.id(), ch.isActive(), tracker.getSummary());
            ChannelPipeline p = ch.pipeline();
            if (ssl && sslContext != null) {
                p.addLast(sslContext.newHandler(ch.alloc(), host, port));
            }
            p.addLast(new HttpClientCodec());
        }

        @Override
        public void channelReleased(Channel ch) {
            tracker.onRelease();
            LOGGER.debug("Pool[{}] channelReleased: channelId={}, channelActive={}, tracker={}",
                poolKey, ch.id(), ch.isActive(), tracker.getSummary());
            ChannelPipeline p = ch.pipeline();
            try {
                p.remove(ReadTimeoutHandler.class);
            } catch (Exception e) {
                /* handler不存在则忽略 */
            }
            try {
                p.remove(WriteTimeoutHandler.class);
            } catch (Exception e) {
                /* handler不存在则忽略 */
            }
        }

        @Override
        public void channelAcquired(Channel ch) {
            tracker.onAcquire();
            LOGGER.debug("Pool[{}] channelAcquired: channelId={}, channelActive={}, tracker={}",
                poolKey, ch.id(), ch.isActive(), tracker.getSummary());
            ChannelPipeline p = ch.pipeline();
            p.addLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));
            p.addLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS));
        }
    }

    private FixedChannelPool getOrCreatePool(String host, int port, boolean ssl) {
        String key = getPoolKey(host, port, ssl);
        return poolMap.computeIfAbsent(key, k -> {
            PoolStateTracker tracker = new PoolStateTracker(k);
            poolTrackerMap.put(k, tracker);
            Bootstrap poolBootstrap = bootstrap.clone().remoteAddress(host, port);
            FixedChannelPool pool = new FixedChannelPool(poolBootstrap,
                new HttpClientPoolHandler(k, host, port, ssl, tracker),
                ChannelHealthChecker.ACTIVE,
                FixedChannelPool.AcquireTimeoutAction.FAIL, acquireTimeoutMs,
                maxConnections, maxPendingAcquires);
            poolLastUsedTimeMap.put(k, new AtomicLong(System.currentTimeMillis()));
            LOGGER.info("Created pool for {}, maxConnections={}, maxPendingAcquires={}, "
                + "acquireTimeoutMs={}ms, tracker={}",
                k, maxConnections, maxPendingAcquires, acquireTimeoutMs, tracker.getSummary());
            return pool;
        });
    }

    private void cleanupIdlePools() {
        long now = System.currentTimeMillis();
        // 定期输出所有池状态统计
        for (Map.Entry<String, PoolStateTracker> e : poolTrackerMap.entrySet()) {
            LOGGER.info("Pool stats: poolKey={}, tracker={}", e.getKey(), e.getValue().getSummary());
        }
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, AtomicLong> e : poolLastUsedTimeMap.entrySet()) {
            if (now - e.getValue().get() > POOL_IDLE_TIMEOUT_MS) {
                toRemove.add(e.getKey());
            }
        }
        for (String key : toRemove) {
            FixedChannelPool pool = poolMap.remove(key);
            PoolStateTracker tracker = poolTrackerMap.remove(key);
            poolLastUsedTimeMap.remove(key);
            if (pool != null) {
                LOGGER.info("Closing idle pool {}, finalStats={}", key, tracker != null ? tracker.getSummary() : "N/A");
                pool.close();
            }
        }
    }

    @Override
    public NettyHttpResponse executeSync(NettyHttpRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        String url = request.getUrl();
        try {
            NettyHttpResponse response;
            if (interceptors.isEmpty()) {
                response = executeInternal(request, null);
            } else {
                InterceptorChain chain = new InterceptorChain(0, request, interceptors, this, null);
                response = chain.proceed(request);
            }
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info("executeSync completed: url={}, status={}, elapsed={}ms", url,
                response != null ? response.getStatusCode() : -1, elapsed);
            return response;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("executeSync failed: url={}, elapsed={}ms, errorType={}, errorMsg={}",
                url, elapsed, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    @Override
    public void executeAsync(NettyHttpRequest request, HttpCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null for async execution");
        }
        long startTime = System.currentTimeMillis();
        String url = request.getUrl();
        HttpCallback timingCallback = new HttpCallback() {
            @Override
            public void onSuccess(NettyHttpResponse response) {
                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.info("executeAsync completed: url={}, status={}, elapsed={}ms", url,
                    response != null ? response.getStatusCode() : -1, elapsed);
                callback.onSuccess(response);
            }
            @Override
            public void onFailure(Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                LOGGER.error("executeAsync failed: url={}, elapsed={}ms, errorType={}, errorMsg={}",
                    url, elapsed, e.getClass().getSimpleName(), e.getMessage());
                callback.onFailure(e);
            }
        };
        try {
            if (interceptors.isEmpty()) {
                executeInternal(request, timingCallback);
            } else {
                InterceptorChain chain = new InterceptorChain(0, request, interceptors, this, timingCallback);
                chain.proceed(request);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("executeAsync exception: url={}, elapsed={}ms, errorType={}, errorMsg={}",
                url, elapsed, e.getClass().getSimpleName(), e.getMessage());
            callback.onFailure(e);
        }
    }

    private NettyHttpResponse executeInternal(NettyHttpRequest request, HttpCallback callback) throws Exception {
        LOGGER.info("Executing request to {}", request.getUrl());
        URI uri = new URI(request.getUrl());
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        boolean ssl = "https".equalsIgnoreCase(uri.getScheme());
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getQuery();
        if (query != null) {
            path = path + "?" + query;
        }
        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());
        DefaultFullHttpRequest httpReq = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path);
        request.getHeaders().forEach(httpReq.headers()::add);
        httpReq.headers().set(HttpHeaderNames.HOST, host);
        httpReq.headers().set(HttpHeaderNames.USER_AGENT, "HttpClient/1.0");

        if (request.isMultipart()) {
            MultipartBodyResult multipartResult = buildMultipartBody(request.getMultipartParts());
            httpReq.content().writeBytes(multipartResult.body);
            httpReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, multipartResult.body.length);
            httpReq.headers().set(HttpHeaderNames.CONTENT_TYPE, 
                "multipart/form-data; boundary=" + multipartResult.boundary);
            LOGGER.info("Multipart request prepared: Content-Length={}, boundary={}", 
                multipartResult.body.length, multipartResult.boundary);
        } else {
            byte[] requestBody = buildRequestBody(request);
            if (requestBody != null && requestBody.length > 0) {
                httpReq.content().writeBytes(requestBody);
                httpReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, requestBody.length);
            }
        }
        FixedChannelPool pool = getOrCreatePool(host, port, ssl);
        String poolKey = getPoolKey(host, port, ssl);
        PoolStateTracker tracker = poolTrackerMap.get(poolKey);
        poolLastUsedTimeMap.computeIfAbsent(poolKey, k -> new AtomicLong()).set(System.currentTimeMillis());
        long startTime = System.currentTimeMillis();
        if (callback == null) {
            return executeSync(pool, tracker, httpReq, startTime, request.isClientCloseConnection());
        } else {
            executeAsync(pool, tracker, httpReq, callback, startTime);
            return null;
        }
    }

    private byte[] buildRequestBody(NettyHttpRequest request) {
        if (request.isMultipart()) {
            return buildMultipartBody(request.getMultipartParts()).body;
        }
        return request.getBody();
    }

    /**
     * Multipart body构造结果（包含body数据和boundary）
     */
    private static class MultipartBodyResult {
        final byte[] body;
        final String boundary;
        
        MultipartBodyResult(byte[] body, String boundary) {
            this.body = body;
            this.boundary = boundary;
        }
    }

    private MultipartBodyResult buildMultipartBody(List<MultipartPart> parts) {
        String boundary = "----HttpClientBoundary" + UUID.randomUUID().toString();
        StringBuilder sb = new StringBuilder();
        ByteBuf buffer = Unpooled.buffer();
        for (MultipartPart part : parts) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(part.getName()).append("\"");
            if (part.isFile()) {
                sb.append("; filename=\"").append(part.getFilename()).append("\"");
            }
            sb.append("\r\n");
            sb.append("Content-Type: ").append(part.getContentType()).append("\r\n");
            sb.append("Content-Length: ").append(part.getData().length).append("\r\n\r\n");
            buffer.writeBytes(sb.toString().getBytes(StandardCharsets.UTF_8));
            buffer.writeBytes(part.getData());
            buffer.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
            sb.setLength(0);
        }
        buffer.writeBytes("--".getBytes(StandardCharsets.UTF_8));
        buffer.writeBytes(boundary.getBytes(StandardCharsets.UTF_8));
        buffer.writeBytes("--\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] result = new byte[buffer.readableBytes()];
        buffer.readBytes(result);
        buffer.release();
        LOGGER.info("buildMultipartBody: boundary={}, size={}bytes", boundary, result.length);
        return new MultipartBodyResult(result, boundary);
    }

    private NettyHttpResponse executeSync(FixedChannelPool pool, PoolStateTracker tracker,
        DefaultFullHttpRequest req, long startTime,
        boolean clientCloseConnection) throws InterruptedException {
        long acquireStart = System.currentTimeMillis();
        // 池内部超时(acquireTimeoutMs=5000ms)与应用层await超时相同，FAIL动作保证Promise在5s内完成
        // 因此await必定返回true，不存在await超时但Future仍pending的场景，无需处理泄漏
        Future<Channel> acquireFuture = pool.acquire();
        acquireFuture.await(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        if (!acquireFuture.isSuccess()) {
            long acquireElapsed = System.currentTimeMillis() - acquireStart;
            tracker.onAcquireFailure();
            LOGGER.error("Acquire failed: elapsed={}ms, cause={}, poolState={}", acquireElapsed,
                acquireFuture.cause() != null ? acquireFuture.cause().toString() : "unknown",
                tracker.getSummary());
            throw new HttpClientException("Acquire failed", acquireFuture.cause());
        }
        Channel ch = acquireFuture.getNow();
        long acquireElapsed = System.currentTimeMillis() - acquireStart;
        LOGGER.info("Acquire success: elapsed={}ms, channelActive={}", acquireElapsed, ch.isActive());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NettyHttpResponse> respRef = new AtomicReference<>();
        AtomicReference<HttpClientException> errRef = new AtomicReference<>();
        try {
            ch.pipeline().addLast(new SyncResponseHandler(latch, respRef, errRef));
            long writeStart = System.currentTimeMillis();
            ChannelFuture writeFuture = ch.writeAndFlush(req);
            if (!writeFuture.await(writeTimeoutMs, TimeUnit.MILLISECONDS)) {
                long writeElapsed = System.currentTimeMillis() - writeStart;
                LOGGER.error("WriteAndFlush timeout: elapsed={}ms, writeTimeoutMs={}ms, channelActive={}",
                    writeElapsed, writeTimeoutMs, ch.isActive());
                throw new HttpClientException("Write timeout");
            }
            if (!writeFuture.isSuccess()) {
                long writeElapsed = System.currentTimeMillis() - writeStart;
                LOGGER.error("WriteAndFlush failed: elapsed={}ms, channelActive={}, cause={}",
                    writeElapsed, ch.isActive(),
                    writeFuture.cause() != null ? writeFuture.cause().toString() : "unknown");
                throw new HttpClientException("Write failed", writeFuture.cause());
            }
            long writeElapsed = System.currentTimeMillis() - writeStart;
            LOGGER.debug("WriteAndFlush completed: elapsed={}ms", writeElapsed);
            long latchStart = System.currentTimeMillis();
            if (!latch.await(callTimeoutMs, TimeUnit.MILLISECONDS)) {
                long latchElapsed = System.currentTimeMillis() - latchStart;
                LOGGER.error("Request timeout: latchElapsed={}ms, callTimeoutMs={}ms, channelActive={}",
                    latchElapsed, callTimeoutMs, ch.isActive());
                throw new HttpClientException("Request timeout");
            }
            if (errRef.get() != null) {
                long totalElapsed = System.currentTimeMillis() - startTime;
                HttpClientException ex = errRef.get();
                LOGGER.error("Request failed with exception: elapsed={}ms, errorType={}, errorMsg={}",
                    totalElapsed, ex.getClass().getSimpleName(), ex.getMessage());
                throw ex;
            }
            LOGGER.info("Request completed: status={}, time={}ms",
                respRef.get() != null ? respRef.get().getStatusCode() : -1,
                System.currentTimeMillis() - startTime);
            return respRef.get();
        } finally {
            long finallyStart = System.currentTimeMillis();
            try {
                ch.pipeline().remove(SyncResponseHandler.class);
            } catch (Exception e) {
                // handler可能未被添加（addLast失败）或已被其他回调移除，忽略
                LOGGER.debug("SyncResponseHandler not found in pipeline: {}", e.getMessage());
            }
            // 检查响应headers中的Connection: close（服务端可能返回Connection:close）
            boolean connectionClose = req.headers().contains("Connection", "close", false);
            NettyHttpResponse resp = respRef.get();
            if (resp != null) {
                String respConnHeader = resp.getHeaders().get("Connection");
                if ("close".equalsIgnoreCase(respConnHeader)) {
                    connectionClose = true;
                }
            } else {
                // 异常路径下无法检测服务端Connection:close，保守策略：关闭连接不回池
                // 避免半断开或不健康的Channel被release回池后导致后续请求失败
                connectionClose = true;
                LOGGER.debug("No response available, defaulting connectionClose=true "
                    + "to prevent unhealthy channel recycling");
            }
            // clientCloseConnection=true时，无论connectionClose结果如何，客户端都主动关闭连接
            // 避免服务端先关连接导致channelInactive覆盖成功响应的竞态问题
            boolean shouldClose = clientCloseConnection || connectionClose || !ch.isActive();
            if (shouldClose) {
                // Channel不再可用或已断开，需要确保Channel完全关闭后再release到池
                // 否则正在关闭的Channel isActive()=true → Netty healthCheck判定"健康" → 放回池
                // → 下一个请求acquire到即将断开的Channel → Channel inactive → 上传失败
                if (ch.isActive()) {
                    // 等待Channel完全关闭（SSL/TCP关闭需要200-500ms）
                    // 确保release时isActive()=false → Netty丢弃Channel而不放回池
                    boolean closeCompleted = ch.close().await(3000, TimeUnit.MILLISECONDS);
                    if (!closeCompleted) {
                        LOGGER.warn("Channel close did not complete within 3000ms, connectionClose={}, active={}",
                            connectionClose, ch.isActive());
                    }
                }
                LOGGER.info("Channel closed: clientClose={}, connectionClose={}, active={}, finallyElapsed={}ms",
                    clientCloseConnection, connectionClose, ch.isActive(), System.currentTimeMillis() - finallyStart);
            } else {
                LOGGER.debug("Channel reused: connectionClose={}, active={}, finallyElapsed={}ms",
                    connectionClose, ch.isActive(), System.currentTimeMillis() - finallyStart);
            }
            pool.release(ch);
            LOGGER.debug("Pool release completed: totalTime={}ms", System.currentTimeMillis() - startTime);
        }
    }

    private void executeAsync(FixedChannelPool pool, PoolStateTracker tracker,
        DefaultFullHttpRequest req, HttpCallback callback, long startTime) {
        pool.acquire().addListener(f -> {
            if (!f.isSuccess()) {
                tracker.onAcquireFailure();
                LOGGER.error("Async acquire failed: cause={}, poolState={}",
                    f.cause() != null ? f.cause().toString() : "unknown",
                    tracker.getSummary());
                callback.onFailure(new HttpClientException("Acquire failed", f.cause()));
                return;
            }
            Channel ch = (Channel) f.getNow();
            if (ch == null) {
                LOGGER.error("Async acquire succeeded but channel is null, poolState={}", tracker.getSummary());
                callback.onFailure(new HttpClientException("Acquired channel is null"));
                return;
            }
            try {
                ch.pipeline().addLast(new AsyncResponseHandler(callback, pool, tracker, ch, req, startTime));
            } catch (Exception e) {
                // addLast失败时handler未被添加到pipeline，无法通过回调释放Channel，必须手动释放
                LOGGER.error("Async addLast failed: error={}, channelActive={}, poolState={}",
                    e.getMessage(), ch.isActive(), tracker.getSummary());
                if (ch.isActive()) {
                    ch.close().addListener(closeFuture -> {
                        LOGGER.info("Async channel closed after addLast failure: active={}", ch.isActive());
                        pool.release(ch);
                    });
                } else {
                    pool.release(ch);
                }
                callback.onFailure(new HttpClientException("Async pipeline setup failed", e));
                return;
            }
            ch.writeAndFlush(req);
        });
    }

    @Override
    public void shutdown() {
        LOGGER.info("Shutting down");
        cleanupExecutor.shutdownNow();
        poolMap.values().forEach(p -> {
            try {
                p.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close pool: {}", e.getMessage());
            }
        });
        poolMap.clear();
        poolTrackerMap.clear();
        poolLastUsedTimeMap.clear();
        eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void addInterceptor(HttpInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
    }

    @Override
    public List<HttpInterceptor> getInterceptors() {
        return new ArrayList<>(interceptors);
    }

    private static final class SyncResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final CountDownLatch latch;
        private final AtomicReference<NettyHttpResponse> respRef;
        private final AtomicReference<HttpClientException> errRef;
        private io.netty.handler.codec.http.HttpResponse response;
        private final ByteBuf buffer = Unpooled.buffer();

        // 标记请求是否已完成（成功/失败/inactive），防止多个回调方法重复处理
        // Connection:close场景下，channelInactive在LastHttpContent之后触发，
        // 如果不检查completed，channelInactive会覆盖errRef导致成功请求被误报失败
        private final AtomicBoolean completed = new AtomicBoolean(false);

        SyncResponseHandler(CountDownLatch latch, AtomicReference<NettyHttpResponse> respRef,
            AtomicReference<HttpClientException> errRef) {
            this.latch = latch;
            this.respRef = respRef;
            this.errRef = errRef;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof io.netty.handler.codec.http.HttpResponse) {
                response = (io.netty.handler.codec.http.HttpResponse) msg;
            }
            if (msg instanceof HttpContent) {
                buffer.writeBytes(((HttpContent) msg).content());
                if (msg instanceof LastHttpContent) {
                    if (!completed.compareAndSet(false, true)) {
                        LOGGER.warn("SyncResponseHandler.channelRead0: already completed, skip response processing");
                        return;
                    }
                    byte[] body = new byte[buffer.readableBytes()];
                    buffer.readBytes(body);
                    buffer.release();
                    Map<String, String> headers = new HashMap<>();
                    response.headers().forEach(e -> headers.put(e.getKey(), e.getValue()));
                    respRef.set(new NettyHttpResponse(response.status().code(),
                        response.status().reasonPhrase(), headers, body, 0));
                    latch.countDown();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!completed.compareAndSet(false, true)) {
                LOGGER.warn("SyncResponseHandler.exceptionCaught: already completed, skip");
                try {
                    if (buffer != null && buffer.refCnt() > 0) {
                        buffer.release();
                        LOGGER.debug("SyncResponseHandler: ByteBuf released in exceptionCaught (already completed)");
                    }
                } catch (io.netty.util.IllegalReferenceCountException e) {
                    LOGGER.warn("SyncResponseHandler: ByteBuf already released", e);
                }
                return;
            }
            LOGGER.error("SyncResponseHandler exceptionCaught: errorType={}, errorMsg={}, "
                + "channelActive={}, channelOpen={}",
                cause.getClass().getSimpleName(), cause.getMessage(),
                ctx.channel().isActive(), ctx.channel().isOpen());
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("SyncResponseHandler: ByteBuf released in exceptionCaught");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                LOGGER.warn("SyncResponseHandler: ByteBuf already released", e);
            }
            errRef.set(new HttpClientException(cause));
            latch.countDown();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOGGER.warn("SyncResponseHandler channelInactive: channel={}, "
                + "channelActive={}, channelOpen={}, channelRegistered={}",
                ctx.channel().id(), ctx.channel().isActive(), ctx.channel().isOpen(), ctx.channel().isRegistered());
            // Channel断开时立即通知调用方，避免连接被长时间占用等待超时才释放
            if (!completed.compareAndSet(false, true)) {
                // 已经被其他回调处理（如channelRead0成功或exceptionCaught），仅释放ByteBuf
                LOGGER.debug("SyncResponseHandler.channelInactive: already completed, only release ByteBuf");
                try {
                    if (buffer != null && buffer.refCnt() > 0) {
                        buffer.release();
                        LOGGER.debug("SyncResponseHandler: ByteBuf released in channelInactive (already completed)");
                    }
                } catch (io.netty.util.IllegalReferenceCountException e) {
                    LOGGER.warn("SyncResponseHandler: ByteBuf already released in channelInactive", e);
                }
                return;
            }
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("SyncResponseHandler: ByteBuf released in channelInactive");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                LOGGER.warn("SyncResponseHandler: ByteBuf already released in channelInactive", e);
            }
            errRef.set(new HttpClientException("Channel inactive"));
            latch.countDown();
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            // 多重保护：Handler移除时也释放ByteBuf
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("SyncResponseHandler: ByteBuf released in handlerRemoved");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                // 仅捕获Netty特定的引用计数异常
                LOGGER.warn("SyncResponseHandler: ByteBuf already released in handlerRemoved", e);
            }
        }
    }

    private static final class AsyncResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final HttpCallback callback;
        private final FixedChannelPool pool;
        private final PoolStateTracker tracker;
        private final Channel channel;
        private final DefaultFullHttpRequest request;
        private final long startTime;
        private io.netty.handler.codec.http.HttpResponse response;
        private final ByteBuf buffer = Unpooled.buffer();

        // 标记请求是否已完成（成功/失败/inactive），防止多个回调方法重复处理
        private final AtomicBoolean completed = new AtomicBoolean(false);

        AsyncResponseHandler(HttpCallback callback, FixedChannelPool pool, PoolStateTracker tracker,
            Channel channel, DefaultFullHttpRequest request, long startTime) {
            this.callback = callback;
            this.pool = pool;
            this.tracker = tracker;
            this.channel = channel;
            this.request = request;
            this.startTime = startTime;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof io.netty.handler.codec.http.HttpResponse) {
                response = (io.netty.handler.codec.http.HttpResponse) msg;
            }
            if (msg instanceof HttpContent) {
                buffer.writeBytes(((HttpContent) msg).content());
                if (msg instanceof LastHttpContent) {
                    if (!completed.compareAndSet(false, true)) {
                        // 已经被其他回调处理（如channelInactive），跳过
                        LOGGER.warn("AsyncResponseHandler.channelRead0: already completed, skip response processing");
                        return;
                    }
                    byte[] body = new byte[buffer.readableBytes()];
                    buffer.readBytes(body);
                    buffer.release();
                    Map<String, String> headers = new HashMap<>();
                    response.headers().forEach(e -> headers.put(e.getKey(), e.getValue()));
                    ctx.pipeline().remove(this);
                    // 检查请求和响应headers中的Connection: close
                    boolean needClose = request.headers().contains("Connection", "close", false);
                    String respConnHeader = headers.get("Connection");
                    if ("close".equalsIgnoreCase(respConnHeader)) {
                        needClose = true;
                    }
                    // 最终的connectionClose判断值，用于lambda表达式
                    final boolean finalNeedClose = needClose;
                    if (finalNeedClose || !channel.isActive()) {
                        // Channel需要关闭或已断开，异步等待close完成后再release到池
                        // 避免正在关闭的Channel isActive()=true → Netty放回池 → 竞态
                        if (channel.isActive()) {
                            channel.close().addListener(closeFuture -> {
                                LOGGER.info("Async channel closed: connectionClose={}, active={}",
                                    finalNeedClose, channel.isActive());
                                pool.release(channel);
                            });
                        } else {
                            LOGGER.info("Async channel already inactive: connectionClose={}, active={}",
                                finalNeedClose, channel.isActive());
                            pool.release(channel);
                        }
                    } else {
                        // Channel活跃且不需要close → 正常release到池复用
                        pool.release(channel);
                    }
                    callback.onSuccess(new NettyHttpResponse(
                        response.status().code(),
                        response.status().reasonPhrase(),
                        headers, body,
                        System.currentTimeMillis() - startTime
                    ));
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (!completed.compareAndSet(false, true)) {
                LOGGER.warn("AsyncResponseHandler.exceptionCaught: already completed, skip");
                return;
            }
            LOGGER.error("AsyncResponseHandler exceptionCaught: errorType={}, errorMsg={}, "
                + "channelActive={}, channelOpen={}, poolState={}",
                cause.getClass().getSimpleName(), cause.getMessage(),
                ctx.channel().isActive(), ctx.channel().isOpen(), tracker.getSummary());
            // 修复：异常时释放ByteBuf，防止内存泄漏
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("AsyncResponseHandler: ByteBuf released in exceptionCaught");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                // 仅捕获Netty特定的引用计数异常
                LOGGER.warn("AsyncResponseHandler: ByteBuf already released", e);
            }
            
            ctx.pipeline().remove(this);
            // 异常时也需要确保Channel完全关闭后再release到池，避免竞态
            if (channel.isActive()) {
                channel.close().addListener(closeFuture -> {
                    LOGGER.info("Async channel closed after exception: active={}", channel.isActive());
                    pool.release(channel);
                });
            } else {
                pool.release(channel);
            }
            callback.onFailure(new HttpClientException(cause));
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            // 多重保护：Handler移除时也释放ByteBuf
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("AsyncResponseHandler: ByteBuf released in handlerRemoved");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                // 仅捕获Netty特定的引用计数异常
                LOGGER.warn("AsyncResponseHandler: ByteBuf already released in handlerRemoved", e);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOGGER.warn("AsyncResponseHandler channelInactive: channel={}, "
                + "channelActive={}, channelOpen={}, poolState={}",
                ctx.channel().id(), ctx.channel().isActive(), ctx.channel().isOpen(), tracker.getSummary());
            // Channel断开时立即通知调用方，避免连接被长时间占用等待超时才释放
            if (!completed.compareAndSet(false, true)) {
                // 已经被其他回调处理（如channelRead0成功或exceptionCaught），仅释放ByteBuf
                LOGGER.debug("AsyncResponseHandler.channelInactive: already completed, only release ByteBuf");
                try {
                    if (buffer != null && buffer.refCnt() > 0) {
                        buffer.release();
                        LOGGER.debug("AsyncResponseHandler: ByteBuf released in channelInactive (already completed)");
                    }
                } catch (io.netty.util.IllegalReferenceCountException e) {
                    LOGGER.warn("AsyncResponseHandler: ByteBuf already released in channelInactive", e);
                }
                return;
            }
            // 第一次处理：释放ByteBuf、通知callback、release pool
            try {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                    LOGGER.debug("AsyncResponseHandler: ByteBuf released in channelInactive");
                }
            } catch (io.netty.util.IllegalReferenceCountException e) {
                LOGGER.warn("AsyncResponseHandler: ByteBuf already released in channelInactive", e);
            }
            ctx.pipeline().remove(this);
            pool.release(channel);
            callback.onFailure(new HttpClientException("Channel inactive"));
        }
    }

    private static final class InterceptorChain implements HttpInterceptor.Chain {
        private final int index;
        private final NettyHttpRequest request;
        private final List<HttpInterceptor> interceptors;
        private final HttpClientImpl client;
        private final HttpCallback callback;

        InterceptorChain(int index, NettyHttpRequest request, List<HttpInterceptor> interceptors,
                        HttpClientImpl client, HttpCallback callback) {
            this.index = index;
            this.request = request;
            this.interceptors = interceptors;
            this.client = client;
            this.callback = callback;
        }

        @Override
        public NettyHttpRequest request() {
            return request;
        }

        @Override
        public NettyHttpResponse proceed(NettyHttpRequest request) throws Exception {
            if (index >= interceptors.size()) {
                return client.executeInternal(request, callback);
            }
            HttpInterceptor nextInterceptor = interceptors.get(index);
            InterceptorChain nextChain = new InterceptorChain(index + 1, request, interceptors, client, callback);
            return nextInterceptor.intercept(nextChain);
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public int interceptorCount() {
            return interceptors.size();
        }
    }
}