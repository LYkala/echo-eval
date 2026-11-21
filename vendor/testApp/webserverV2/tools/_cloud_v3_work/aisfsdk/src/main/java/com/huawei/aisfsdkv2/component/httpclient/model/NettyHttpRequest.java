/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP请求封装类
 *
 * @since 2026-05-25
 */
public final class NettyHttpRequest {

    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final byte[] body;
    private final List<MultipartPart> multipartParts;
    private final boolean isMultipart;
    private final boolean clientCloseConnection;

    private NettyHttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = builder.headers;
        this.body = builder.body;
        this.multipartParts = builder.multipartParts;
        this.isMultipart = builder.isMultipart;
        this.clientCloseConnection = builder.clientCloseConnection;
    }

    public String getUrl() {
        return url;
    }
    public String getMethod() {
        return method;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }
    public byte[] getBody() {
        return body;
    }
    public List<MultipartPart> getMultipartParts() {
        return multipartParts;
    }
    public boolean isMultipart() {
        return isMultipart;
    }

    /**
     * 客户端是否在请求完成后主动关闭连接
     *
     * <p>当 {@code clientCloseConnection=true} 时，请求头设置为 {@code Connection: keep-alive}
     * （告诉服务端不关闭连接），但客户端在 finally 块中主动 {@code ch.close()} 关闭连接，
     * 避免服务端先关连接导致 {@code channelInactive} 覆盖成功响应的竞态问题。
     *
     * @return true 表示客户端主动关闭连接，false 表示遵循 HTTP 协议（默认行为）
     */
    public boolean isClientCloseConnection() {
        return clientCloseConnection;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NettyHttpRequest{");
        sb.append("url='").append(url).append('\'');
        sb.append(", method='").append(method).append('\'');
        sb.append(", headers=").append(headers);
        if (isMultipart) {
            sb.append(", multipartParts=").append(multipartParts.size());
        } else if (body != null) {
            sb.append(", body='").append(new String(body, StandardCharsets.UTF_8)).append('\'');
        }
        sb.append(", isMultipart=").append(isMultipart);
        sb.append(", clientCloseConnection=").append(clientCloseConnection);
        sb.append('}');
        return sb.toString();
    }

    public static final class Builder {
        private String url;
        private String method = "GET";
        private final Map<String, String> headers = new HashMap<>();
        private byte[] body;
        private final List<MultipartPart> multipartParts = new ArrayList<>();
        private boolean isMultipart = false;
        private boolean clientCloseConnection = false;

        public Builder url(String url) {
            this.url = url;
            return this;
        }
        public Builder method(String method) {
            this.method = method;
            return this;
        }
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }
        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }
        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }
        public Builder bodyString(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
            return this;
        }
        public Builder multipart() {
            this.isMultipart = true;
            this.method = "POST";
            return this;
        }
        public Builder addPart(String name, byte[] data, String filename, String contentType) {
            multipartParts.add(new MultipartPart(name, data, filename, contentType));
            return this;
        }
        public Builder addFormField(String name, String value) {
            multipartParts.add(new MultipartPart(name, value.getBytes(StandardCharsets.UTF_8), null, "text/plain"));
            return this;
        }
        public Builder addFilePart(String name, byte[] data, String filename, String contentType) {
            multipartParts.add(new MultipartPart(name, data, filename, contentType));
            return this;
        }

        /**
         * 设置客户端是否在请求完成后主动关闭连接
         *
         * <p>当 {@code clientCloseConnection=true} 时，请求头设置为 {@code Connection: keep-alive}，
         * 但客户端在 finally 块中主动关闭连接，避免服务端先关闭连接的竞态问题。
         * 默认 {@code false}，不影响现有逻辑。
         *
         * @param clientCloseConnection true 表示客户端主动关闭连接
         * @return Builder 实例
         */
        public Builder clientCloseConnection(boolean clientCloseConnection) {
            this.clientCloseConnection = clientCloseConnection;
            return this;
        }

        public NettyHttpRequest build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("url is required");
            }
            return new NettyHttpRequest(this);
        }
    }

    public static final class MultipartPart {
        private final String name;
        private final byte[] data;
        private final String filename;
        private final String contentType;

        public MultipartPart(String name, byte[] data, String filename, String contentType) {
            this.name = name;
            this.data = data;
            this.filename = filename;
            this.contentType = contentType != null ? contentType : "application/octet-stream";
        }

        public String getName() {
            return name;
        }
        public byte[] getData() {
            return data;
        }
        public String getFilename() {
            return filename;
        }
        public String getContentType() {
            return contentType;
        }
        public boolean isFile() {
            return filename != null;
        }
    }
}