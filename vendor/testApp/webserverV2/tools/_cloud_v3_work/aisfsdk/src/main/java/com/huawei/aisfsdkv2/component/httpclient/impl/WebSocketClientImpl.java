/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.impl;

import com.huawei.aisfsdkv2.component.httpclient.HttpClientException;
import com.huawei.aisfsdkv2.component.httpclient.HttpClientManager;
import com.huawei.aisfsdkv2.component.httpclient.websocket.WebSocketClient;
import com.huawei.aisfsdkv2.component.httpclient.callback.WebSocketCallback;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.SSLUtil;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.ScheduledFuture;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket客户端实现
 *
 * @since 2026-05-25
 */
final class WebSocketClientImpl implements WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("WebSocketClientImpl");

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int MAX_CONTENT_LENGTH = 65536;

    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final SslContext sslContext;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    private Channel channel;
    private WebSocketClientHandshaker handshaker;
    private WebSocketCallback callback;

    WebSocketClientImpl() {
        this.eventLoopGroup = new NioEventLoopGroup();
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
        this.sslContext = initSslContext();
    }

    private SslContext initSslContext() {
        SslContext ctx = SSLUtil.createSslContextForNetty();
        if (ctx != null) {
            LOGGER.info("SSL context created from TLSConfig");
            return ctx;
        }
        LOGGER.warn("SSLUtil returned null, using system default context");
        try {
            return SslContextBuilder.forClient().build();
        } catch (SSLException e) {
            throw new HttpClientException("Failed to create SSL context", e);
        }
    }

    @Override
    public boolean connect(String url, Map<String, String> headers, WebSocketCallback callback) {
        long startTime = System.currentTimeMillis();
        if (isConnected.get()) {
            LOGGER.warn("Already connected");
            return false;
        }
        this.callback = callback;
        try {
            ConnectionInfo info = parseConnectionInfo(url, headers);
            LOGGER.info("Connection info parsed: host={}, port={}, ssl={}, elapsed={}ms",
                info.host, info.port, info.ssl, System.currentTimeMillis() - startTime);
            configurePipeline(info, startTime);
            initiateConnection(info, startTime);
            return true;
        } catch (Exception e) {
            LOGGER.error("Invalid URL, elapsed={}ms: {}", System.currentTimeMillis() - startTime, e.getMessage(), e);
            if (callback != null) {
                callback.onConnectionFailed(new HttpClientException("Invalid URL: " + url, e));
            }
            return false;
        }
    }

    private ConnectionInfo parseConnectionInfo(String url, Map<String, String> headers) throws Exception {
        URI uri = new URI(url);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? ("wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        boolean ssl = "wss".equalsIgnoreCase(uri.getScheme());
        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        this.handshaker = WebSocketClientHandshakerFactory.newHandshaker(
            uri, WebSocketVersion.V13, null, false, httpHeaders, MAX_CONTENT_LENGTH
        );
        return new ConnectionInfo(host, port, ssl);
    }

    private void configurePipeline(ConnectionInfo info, long startTime) {
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();
                if (info.ssl) {
                    p.addLast(sslContext.newHandler(ch.alloc(), info.host, info.port));
                }
                p.addLast(new HttpClientCodec());
                p.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                p.addLast(new WsPingHandler(HttpClientManager.WEBSOCKET_PING_INTERVAL_MS));
                p.addLast(new WsMessageHandler(handshaker, WebSocketClientImpl.this.callback, isConnected, startTime));
            }
        });
    }

    private void initiateConnection(ConnectionInfo info, long startTime) {
        LOGGER.info("Initiating TCP connection: host={}, port={}, ssl={}", info.host, info.port, info.ssl);
        ChannelFuture connectFuture = bootstrap.connect(info.host, info.port);
        connectFuture.addListener(f -> {
            long tcpElapsed = System.currentTimeMillis() - startTime;
            if (f.isSuccess()) {
                channel = ((ChannelFuture) f).channel();
                LOGGER.info("TCP connection established: host={}, port={}, channelActive={}, elapsed={}ms",
                    info.host, info.port, channel.isActive(), tcpElapsed);
                long handshakeStart = System.currentTimeMillis();
                handshaker.handshake(channel);
                LOGGER.info("Handshake started: uri={}, elapsed={}ms, handshakeStart={}ms",
                    handshaker.uri(), tcpElapsed, System.currentTimeMillis() - handshakeStart);
            } else {
                LOGGER.error("TCP connection failed: host={}, port={}, elapsed={}ms, cause={}",
                    info.host, info.port, tcpElapsed, f.cause() != null ? f.cause().toString() : "unknown");
                if (callback != null) {
                    callback.onConnectionFailed(new HttpClientException("Connection failed", f.cause()));
                }
            }
        });
    }

    private static final class ConnectionInfo {
        final String host;
        final int port;
        final boolean ssl;

        ConnectionInfo(String host, int port, boolean ssl) {
            this.host = host;
            this.port = port;
            this.ssl = ssl;
        }
    }

    @Override
    public boolean sendText(String message) {
        if (!isConnected.get() || channel == null || !channel.isActive()) {
            LOGGER.warn("Not connected");
            return false;
        }
        channel.writeAndFlush(new TextWebSocketFrame(message));
        return true;
    }

    @Override
    public boolean sendBinary(byte[] data) {
        if (!isConnected.get() || channel == null || !channel.isActive()) {
            LOGGER.warn("Not connected");
            return false;
        }
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
        return true;
    }

    @Override
    public void disconnect() {
        LOGGER.info("Disconnecting");
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new CloseWebSocketFrame(1000, "Normal closure"));
            channel.close();
        }
        channel = null;
        handshaker = null;
        callback = null;
        isConnected.set(false);
    }

    @Override
    public boolean isConnected() {
        return isConnected.get() && channel != null && channel.isActive();
    }

    @Override
    public void shutdown() {
        disconnect();
        eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    /** 握手完成事件标识，用于Pipeline内部通信 */
    private static final String HANDSHAKE_COMPLETE_EVENT = "HANDSHAKE_COMPLETE";

    /**
     * WebSocket定时Ping处理器
     *
     * <p>模拟OkHttp的pingInterval行为：连接建立后按固定间隔定时发送Ping帧，
     * 不依赖IdleStateEvent（空闲触发）。OkHttp的pingInterval是定时器驱动，
     * 无论是否有数据往来都会固定间隔发Ping；而Netty的IdleStateHandler是事件驱动，
     * 只有无读数据时才触发Ping。服务端有空闲超时策略（约60秒），需要客户端
     * 定时主动保活，且Ping间隔必须小于服务端超时（当前30秒<60秒），避免边界冲突。
     *
     * <p>Ping策略：
     * <ol>
     *   <li>WebSocket握手完成后立即发送首个Ping，向服务端表明客户端存活</li>
     *   <li>之后每隔pingIntervalMs固定发送Ping，不管是否有数据往来</li>
     *   <li>收到Pong时不做特殊处理（Pong已由WebSocket协议自动回复）</li>
     *   <li>连接断开时自动取消定时任务</li>
     * </ol>
     */
    private static final class WsPingHandler extends ChannelDuplexHandler {
        private final long pingIntervalMs;
        private ScheduledFuture<?> pingScheduleFuture;

        WsPingHandler(long pingIntervalMs) {
            this.pingIntervalMs = pingIntervalMs;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOGGER.info("WsPingHandler: channelInactive, cancelling ping schedule");
            cancelPingSchedule();
            ctx.fireChannelInactive();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (HANDSHAKE_COMPLETE_EVENT.equals(evt)) {
                LOGGER.info("WsPingHandler: received HANDSHAKE_COMPLETE event, starting ping schedule");
                startPingSchedule(ctx);
            }
            ctx.fireUserEventTriggered(evt);
        }

        /**
         * 监听握手完成事件，启动定时Ping
         *
         * <p>握手完成后立即发送首个Ping，然后按pingIntervalMs间隔定时发送。
         * 使用Channel的EventLoop调度，确保在IO线程上执行，避免并发问题。
         */
        private void startPingSchedule(ChannelHandlerContext ctx) {
            if (pingScheduleFuture != null && !pingScheduleFuture.isCancelled()) {
                LOGGER.debug("WsPingHandler: ping schedule already running");
                return;
            }
            LOGGER.info("WsPingHandler: starting ping schedule, interval={}ms", pingIntervalMs);
            // 立即发送首个Ping
            ctx.writeAndFlush(new PingWebSocketFrame());
            LOGGER.info("WsPingHandler: initial Ping sent");
            // 定时发送后续Ping
            pingScheduleFuture = ctx.channel().eventLoop().scheduleAtFixedRate(
                () -> {
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(new PingWebSocketFrame());
                        LOGGER.debug("WsPingHandler: scheduled Ping sent");
                    } else {
                        LOGGER.warn("WsPingHandler: channel not active, skipping Ping");
                        cancelPingSchedule();
                    }
                },
                pingIntervalMs, pingIntervalMs, TimeUnit.MILLISECONDS
            );
        }

        private void cancelPingSchedule() {
            if (pingScheduleFuture != null) {
                pingScheduleFuture.cancel(false);
                pingScheduleFuture = null;
                LOGGER.info("WsPingHandler: ping schedule cancelled");
            }
        }
    }

    private static final class WsMessageHandler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final WebSocketCallback callback;
        private final AtomicBoolean isConnected;
        private final long startTime;

        WsMessageHandler(WebSocketClientHandshaker handshaker, WebSocketCallback callback,
            AtomicBoolean isConnected, long startTime) {
            this.handshaker = handshaker;
            this.callback = callback;
            this.isConnected = isConnected;
            this.startTime = startTime;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (!handshaker.isHandshakeComplete()) {
                try {
                    handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
                    isConnected.set(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    LOGGER.info("Handshake complete: elapsed={}ms, channelActive={}, uri={}",
                        elapsed, ctx.channel().isActive(), handshaker.uri());
                    // 通知上游WsPingHandler启动定时Ping
                    ctx.pipeline().fireUserEventTriggered(HANDSHAKE_COMPLETE_EVENT);
                    if (callback != null) {
                        callback.onConnected();
                    }
                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    LOGGER.error("Handshake failed: elapsed={}ms, errorType={}, errorMsg={}, uri={}",
                        elapsed, e.getClass().getSimpleName(), e.getMessage(), handshaker.uri());
                    if (callback != null) {
                        callback.onConnectionFailed(e);
                    }
                    ctx.close();
                }
                return;
            }
            if (msg instanceof WebSocketFrame) {
                handleFrame(ctx, (WebSocketFrame) msg);
            }
        }

        private void handleFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof TextWebSocketFrame) {
                if (callback != null) {
                    callback.onTextMessage(((TextWebSocketFrame) frame).text());
                }
            } else if (frame instanceof BinaryWebSocketFrame) {
                ByteBuf content = frame.content();
                byte[] data = new byte[content.readableBytes()];
                content.readBytes(data);
                if (callback != null) {
                    callback.onBinaryMessage(data);
                }
            } else if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            } else if (frame instanceof CloseWebSocketFrame) {
                isConnected.set(false);
                if (callback != null) {
                    callback.onDisconnected(
                        ((CloseWebSocketFrame) frame).statusCode(),
                        ((CloseWebSocketFrame) frame).reasonText()
                    );
                }
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            isConnected.set(false);
            if (callback != null) {
                callback.onDisconnected(-1, "Channel inactive");
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            isConnected.set(false);
            if (callback != null) {
                callback.onError(new HttpClientException(cause));
            }
            ctx.close();
        }
    }
}