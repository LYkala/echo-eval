package com.example.testapp.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 将设备日志分片实时推送到 WebServer（WebSocket log_chunk）。
 */
public final class LiveLogStreamer {

    private static final String TAG = "LiveLogStreamer";
    private static final long FLUSH_INTERVAL_MS = 800L;
    private static final int MAX_CHUNK_CHARS = 24_000;

    private static final Object LOCK = new Object();
    private static final StringBuilder BUFFER = new StringBuilder();
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean active = false;
    private static boolean resetPending = false;
    private static boolean flushScheduled = false;

    private static final Runnable FLUSH_RUNNABLE = () -> {
        synchronized (LOCK) {
            flushScheduled = false;
        }
        flush();
        scheduleNextFlush();
    };

    private LiveLogStreamer() {
    }

    public static void start() {
        synchronized (LOCK) {
            BUFFER.setLength(0);
            active = true;
            resetPending = true;
            flushScheduled = false;
        }
        HANDLER.removeCallbacks(FLUSH_RUNNABLE);
        scheduleNextFlush();
        Log.i(TAG, "live log stream started");
    }

    public static void stop() {
        flush();
        synchronized (LOCK) {
            active = false;
            BUFFER.setLength(0);
            resetPending = false;
            flushScheduled = false;
        }
        HANDLER.removeCallbacks(FLUSH_RUNNABLE);
        Log.i(TAG, "live log stream stopped");
    }

    public static void append(String line) {
        if (!active || line == null || line.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            if (!active) {
                return;
            }
            BUFFER.append(line);
            if (!line.endsWith("\n")) {
                BUFFER.append('\n');
            }
            if (BUFFER.length() >= MAX_CHUNK_CHARS) {
                flushLocked();
            }
        }
    }

    public static void flush() {
        synchronized (LOCK) {
            flushLocked();
        }
    }

    private static void scheduleNextFlush() {
        synchronized (LOCK) {
            if (!active || flushScheduled) {
                return;
            }
            flushScheduled = true;
        }
        HANDLER.postDelayed(FLUSH_RUNNABLE, FLUSH_INTERVAL_MS);
    }

    private static void flushLocked() {
        if (BUFFER.length() == 0 && !resetPending) {
            return;
        }
        String text = BUFFER.toString();
        BUFFER.setLength(0);
        boolean reset = resetPending;
        resetPending = false;

        try {
            WebServerClient client = WebServerClient.peekInstance();
            if (client == null) {
                return;
            }
            client.sendLogChunk(text, reset);
        } catch (Exception e) {
            Log.w(TAG, "flush failed: " + e.getMessage());
        }
    }
}
