package com.example.devicesdkdemo.utils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Netty HTTP 客户端工具类
 * 用于替代 OkHttp 进行 HTTP 请求
 */
public class NettyHttpClient {

    private final int connectTimeout;
    private final int readTimeout;

    /**
     * 创建默认配置的客户端（超时10秒）
     */
    public NettyHttpClient() {
        this(10, 10);
    }

    /**
     * 创建自定义超时配置的客户端
     * @param connectTimeout 连接超时（秒）
     * @param readTimeout 读取超时（秒）
     */
    public NettyHttpClient(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 发送 HTTP POST 请求（同步方式）
     * @param url 目标URL
     * @param requestBody 请求体内容
     * @return 响应结果对象
     */
    public HttpResponseResult post(String url, String requestBody) {
        return executeRequest(url, HttpMethod.POST, requestBody);
    }

    /**
     * 发送 HTTP GET 请求（同步方式）
     * @param url 目标URL
     * @return 响应结果对象
     */
    public HttpResponseResult get(String url) {
        return executeRequest(url, HttpMethod.GET, null);
    }

    /**
     * 执行 HTTP 请求
     */
    private HttpResponseResult executeRequest(String url, HttpMethod method, String requestBody) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = uri.getScheme().equals("https") ? 443 : 80;
            }

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout * 1000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpResponseHandler());
                        }
                    });

            // 连接服务器
            ChannelFuture connectFuture = bootstrap.connect(host, port);
            Channel channel = connectFuture.syncUninterruptibly().channel();

            // 创建请求
            FullHttpRequest request = createRequest(uri, method, requestBody);

            // 发送请求并等待响应
            HttpResponseHandler handler = (HttpResponseHandler) channel.pipeline().last();
            channel.writeAndFlush(request);

            // 同步等待响应（使用 CountDownLatch）
            HttpResponseResult result = handler.waitForResponse(readTimeout);

            channel.close().syncUninterruptibly();
            return result;

        } catch (URISyntaxException e) {
            return new HttpResponseResult(-1, "Invalid URL: " + e.getMessage());
        } catch (Exception e) {
            return new HttpResponseResult(-1, "Request failed: " + e.getMessage());
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 创建 HTTP 请求对象
     */
    private FullHttpRequest createRequest(URI uri, HttpMethod method, String requestBody) {
        String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

        DefaultFullHttpRequest request;
        if (requestBody != null) {
            request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    method,
                    path,
                    io.netty.buffer.Unpooled.copiedBuffer(requestBody, CharsetUtil.UTF_8)
            );
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
        } else {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path);
        }

        request.headers().set(HttpHeaderNames.HOST, uri.getHost());
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON);

        return request;
    }

    /**
     * HTTP 响应处理器
     */
    private static class HttpResponseHandler extends io.netty.channel.SimpleChannelInboundHandler<HttpObject> {
        private final AtomicReference<HttpResponseResult> resultRef = new AtomicReference<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        private final StringBuilder contentBuilder = new StringBuilder();
        private int statusCode = -1;

        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, HttpObject msg) {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                statusCode = response.status().code();
            }

            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                contentBuilder.append(content.content().toString(CharsetUtil.UTF_8));

                if (msg instanceof LastHttpContent) {
                    resultRef.set(new HttpResponseResult(statusCode, contentBuilder.toString()));
                    latch.countDown();
                }
            }
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            resultRef.set(new HttpResponseResult(-1, "Exception: " + cause.getMessage()));
            latch.countDown();
            ctx.close();
        }

        /**
         * 等待响应结果
         */
        HttpResponseResult waitForResponse(int timeoutSeconds) {
            try {
                if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                    return new HttpResponseResult(-1, "Timeout after " + timeoutSeconds + " seconds");
                }
                return resultRef.get();
            } catch (InterruptedException e) {
                return new HttpResponseResult(-1, "Interrupted: " + e.getMessage());
            }
        }
    }

    /**
     * HTTP 响应结果封装类
     */
    public static class HttpResponseResult {
        private final int statusCode;
        private final String body;

        public HttpResponseResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}