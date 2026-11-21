/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.httpclient.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP响应封装类
 *
 * @since 2026-05-25
 */
public final class NettyHttpResponse {

    private final int statusCode;
    private final String statusText;
    private final Map<String, String> headers;
    private final byte[] body;
    private final long responseTimeMs;
    private volatile boolean bodyConsumed;

    public NettyHttpResponse(int statusCode, String statusText, Map<String, String> headers,
            byte[] body, long responseTimeMs) {
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
        this.responseTimeMs = responseTimeMs;
        this.bodyConsumed = false;
    }

    public int getStatusCode() {
        return statusCode;
    }
    public String getStatusText() {
        return statusText;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }
    public byte[] getBody() {
        return body;
    }
    public String getBodyAsString() {
        return body != null ? new String(body, StandardCharsets.UTF_8) : "";
    }
    public long getResponseTimeMs() {
        return responseTimeMs;
    }
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    public InputStream getBodyStream() {
        if (body == null) {
            return new ByteArrayInputStream(new byte[0]);
        }
        bodyConsumed = true;
        return new ByteArrayInputStream(body);
    }
    public boolean isBodyConsumed() {
        return bodyConsumed;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NettyHttpResponse{");
        sb.append("statusCode=").append(statusCode);
        sb.append(", statusText='").append(statusText).append('\'');
        sb.append(", headers=").append(headers);
        if (body != null) {
            sb.append(", body='").append(getBodyAsString()).append('\'');
        } else {
            sb.append(", body=null");
        }
        sb.append(", responseTimeMs=").append(responseTimeMs);
        sb.append('}');
        return sb.toString();
    }

    public long getContentLength() {
        String contentLength = headers != null ? headers.get("Content-Length") : null;
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                return body != null ? body.length : 0;
            }
        }
        return body != null ? body.length : 0;
    }
}