/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.helper;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将 ACM 截图与 AIS 回复异步镜像到测试 WebServer（不阻塞 ACM / GUI 主路径）。
 *
 * <p>由 Demo 在启动时 {@link #configure}；未配置或未启用时所有方法立即返回。</p>
 */
public final class WebServerMirror {

    private static final Logger LOGGER = LoggerFactory.getLogger("WebServerMirror");
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "WebServerMirror-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile boolean enabled;
    private static volatile String baseUrl = "";
    private static volatile String deviceId = "";
    private static volatile String taskId = "";
    private static volatile String caseId = "";

    private WebServerMirror() {
    }

    /**
     * @param enabled  是否启用镜像
     * @param baseUrl  如 http://192.168.1.10:5000 （无尾斜杠）
     * @param deviceId 设备 ID，供服务端 get_device_current_task 回退
     * @param taskId   可选；空则依赖服务端按 deviceId 回退
     * @param caseId   可选；空则依赖服务端按 deviceId 回退
     */
    public static void configure(boolean enabled, String baseUrl, String deviceId,
            String taskId, String caseId) {
        WebServerMirror.enabled = enabled;
        WebServerMirror.baseUrl = normalizeBase(baseUrl);
        WebServerMirror.deviceId = deviceId != null ? deviceId.trim() : "";
        WebServerMirror.taskId = taskId != null ? taskId.trim() : "";
        WebServerMirror.caseId = caseId != null ? caseId.trim() : "";
        LOGGER.info("WebServerMirror.configure: enabled={} baseUrl={} deviceId={} taskId={} caseId={}",
                enabled, WebServerMirror.baseUrl, WebServerMirror.deviceId,
                WebServerMirror.taskId, WebServerMirror.caseId);
    }

    public static boolean isEnabled() {
        return enabled && !baseUrl.isEmpty();
    }

    public static String getDeviceId() {
        return deviceId;
    }

    public static String getTaskId() {
        return taskId;
    }

    public static String getCaseId() {
        return caseId;
    }

    /**
     * Fire-and-forget：镜像 JPEG 到 POST /api/device/screenshot。
     */
    public static void mirrorScreenshotAsync(byte[] imageData, String fileName) {
        if (!isEnabled() || imageData == null || imageData.length == 0) {
            return;
        }
        final String name;
        if (fileName != null && !fileName.isEmpty()) {
            name = fileName.startsWith("aisf_gui_") ? fileName : ("aisf_gui_" + fileName);
        } else {
            name = "aisf_gui_" + System.currentTimeMillis() + ".jpg";
        }
        final byte[] copy = imageData;
        EXECUTOR.execute(() -> {
            try {
                postMultipartScreenshot(copy, name);
            } catch (Exception e) {
                LOGGER.warn("WebServerMirror.mirrorScreenshotAsync: failed fileName={}, err={}",
                        name, e.getMessage());
            }
        });
    }

    /**
     * Fire-and-forget：POST /api/device/case_reply JSON。
     */
    public static void postCaseReplyAsync(String jsonBody) {
        if (!isEnabled() || jsonBody == null || jsonBody.isEmpty()) {
            return;
        }
        final String body = jsonBody;
        EXECUTOR.execute(() -> {
            try {
                postJson(baseUrl + "/api/device/case_reply", body);
            } catch (Exception e) {
                LOGGER.warn("WebServerMirror.postCaseReplyAsync: failed err={}", e.getMessage());
            }
        });
    }

    private static String normalizeBase(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static void postMultipartScreenshot(byte[] imageData, String fileName) throws Exception {
        String boundary = "----CloudMirror" + System.currentTimeMillis();
        URL url = new URL(baseUrl + "/api/device/screenshot");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeFormField(bos, boundary, "taskId", taskId);
        writeFormField(bos, boundary, "caseId", caseId);
        writeFormField(bos, boundary, "testId", caseId);
        writeFormField(bos, boundary, "deviceId", deviceId);
        writeFilePart(bos, boundary, "file", fileName, "image/jpeg", imageData);
        bos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] payload = bos.toByteArray();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
            os.flush();
        }

        int code = conn.getResponseCode();
        String resp = readBody(conn, code);
        if (code >= 200 && code < 300) {
            LOGGER.info("WebServerMirror: screenshot mirrored ok fileName={} code={}", fileName, code);
        } else {
            LOGGER.warn("WebServerMirror: screenshot mirror http={} body={}", code, resp);
        }
        conn.disconnect();
    }

    private static void postJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
            os.flush();
        }
        int code = conn.getResponseCode();
        String resp = readBody(conn, code);
        if (code >= 200 && code < 300) {
            LOGGER.info("WebServerMirror: case_reply ok code={}", code);
        } else {
            LOGGER.warn("WebServerMirror: case_reply http={} body={}", code, resp);
        }
        conn.disconnect();
    }

    private static void writeFormField(ByteArrayOutputStream bos, String boundary,
            String name, String value) throws Exception {
        bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        bos.write((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream bos, String boundary,
            String name, String fileName, String contentType, byte[] data) throws Exception {
        bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(data);
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readBody(HttpURLConnection conn, int code) {
        try {
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            String s = bos.toString("UTF-8");
            return s.length() > 300 ? s.substring(0, 300) : s;
        } catch (Exception e) {
            return "";
        }
    }
}
