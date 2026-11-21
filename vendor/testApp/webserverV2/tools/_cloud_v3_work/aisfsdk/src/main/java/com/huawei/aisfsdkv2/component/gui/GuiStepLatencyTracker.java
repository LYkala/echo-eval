/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.gui;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.message.model.GuiCmdType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GUI 单步时延采集：端侧（指令→截图上传）与云侧（截图上传→下发指令）。
 */
public final class GuiStepLatencyTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("GuiStepLatencyTracker");
    private static final GuiStepLatencyTracker INSTANCE = new GuiStepLatencyTracker();
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final String CLOUD_FINISH_NAME = "截图上传→下发操控完成";

    public interface SnapshotListener {
        void onGuiStepLatencySnapshot(String jsonPayload);
    }

    /** 一条操控指令已经执行完，并且这一步的结果截图已上传。 */
    public interface ControlShotListener {
        void onControlStepShotReady(String cmdDesc);
    }

    private static final class StepRow {
        final String name;
        final long startMs;
        final long endMs;

        StepRow(String name, long startMs, long endMs) {
            this.name = name;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private final Object lock = new Object();
    private final List<StepRow> deviceSteps = new ArrayList<>();
    private final List<StepRow> cloudSteps = new ArrayList<>();
    private final List<SnapshotListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ControlShotListener> controlShotListeners = new CopyOnWriteArrayList<>();

    private String pendingCmdName;
    private long pendingCmdAtMs;
    private Long lastUploadAtMs;

    private GuiStepLatencyTracker() {
    }

    public static GuiStepLatencyTracker getInstance() {
        return INSTANCE;
    }

    public void addSnapshotListener(SnapshotListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeSnapshotListener(SnapshotListener listener) {
        listeners.remove(listener);
    }

    public void addControlShotListener(ControlShotListener listener) {
        if (listener != null && !controlShotListeners.contains(listener)) {
            controlShotListeners.add(listener);
        }
    }

    public void removeControlShotListener(ControlShotListener listener) {
        controlShotListeners.remove(listener);
    }

    public void reset(String reason) {
        synchronized (lock) {
            deviceSteps.clear();
            cloudSteps.clear();
            // 不统计 START_GUI 后首帧「GUI开始→截图上传」；等真实操作指令再记端侧步
            pendingCmdName = null;
            pendingCmdAtMs = 0L;
            lastUploadAtMs = null;
        }
        LOGGER.info("GuiStepLatencyTracker.reset reason={}", reason);
    }

    /** AISF 下发 GUI 指令到达端侧。 */
    public void onCommandReceived(GuiCmdType cmdType) {
        if (cmdType == null) {
            return;
        }
        // 纯截图/上传/状态类：不是「操作→截图上传」的有意义边界
        if (cmdType == GuiCmdType.SCREEN_SHOT
                || cmdType == GuiCmdType.UPLOAD_SCREEN_SHOT
                || cmdType == GuiCmdType.UNKNOWN
                || cmdType == GuiCmdType.EXCEPTION
                || cmdType == GuiCmdType.APP_INSTALL_STATUS
                || cmdType == GuiCmdType.CHECK_APP_INSTALLED) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (lastUploadAtMs != null) {
                String cloudName = cmdType == GuiCmdType.COMPLETED
                        ? CLOUD_FINISH_NAME
                        : ("截图上传→下发" + cmdType.getDesc());
                cloudSteps.add(new StepRow(cloudName, lastUploadAtMs, now));
            }
            // 操控完成：只记云侧收尾，不再开端侧「操控完成→截图上传」
            if (cmdType == GuiCmdType.COMPLETED) {
                pendingCmdName = null;
                pendingCmdAtMs = 0L;
                lastUploadAtMs = null;
                LOGGER.info("GuiStepLatencyTracker.cmdReceived COMPLETED cloud-only at={}", now);
                return;
            }
            pendingCmdName = cmdType.getDesc();
            pendingCmdAtMs = now;
        }
        LOGGER.info("GuiStepLatencyTracker.cmdReceived cmd={} at={}", cmdType, now);
    }

    /** 端侧截图已上传并回传 AISF（UPLOAD_SCREEN_SHOT 发出）。 */
    public void onScreenshotUploaded() {
        long now = System.currentTimeMillis();
        String finishedCmd = null;
        synchronized (lock) {
            if (pendingCmdName != null && pendingCmdAtMs > 0L) {
                finishedCmd = pendingCmdName;
                deviceSteps.add(new StepRow(
                        pendingCmdName + "→截图上传",
                        pendingCmdAtMs,
                        now));
                pendingCmdName = null;
                pendingCmdAtMs = 0L;
            }
            lastUploadAtMs = now;
        }
        LOGGER.info("GuiStepLatencyTracker.screenshotUploaded at={} cmd={}", now, finishedCmd);
        if (finishedCmd != null) {
            for (ControlShotListener listener : controlShotListeners) {
                try {
                    listener.onControlStepShotReady(finishedCmd);
                } catch (Throwable t) {
                    LOGGER.warn("GuiStepLatencyTracker control shot listener failed: {}", t.getMessage());
                }
            }
        }
    }

    /**
     * STOP_GUI / 任务结束时常没有单独的 COMPLETED 指令；
     * 在导出前用「末次截图上传 → 结束时刻」补云侧收尾行。
     */
    private void ensureGuiFinishCloudStep(String reason) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (lastUploadAtMs == null) {
                return;
            }
            for (StepRow row : cloudSteps) {
                if (row.name != null && row.name.contains("操控完成")) {
                    lastUploadAtMs = null;
                    pendingCmdName = null;
                    pendingCmdAtMs = 0L;
                    return;
                }
            }
            cloudSteps.add(new StepRow(CLOUD_FINISH_NAME, lastUploadAtMs, now));
            lastUploadAtMs = null;
            pendingCmdName = null;
            pendingCmdAtMs = 0L;
            LOGGER.info("GuiStepLatencyTracker.ensureFinish reason={} at={}", reason, now);
        }
    }

    /** GUI 结束：导出并通知监听方上报。 */
    public String snapshotAndNotify(String reason) {
        ensureGuiFinishCloudStep(reason != null ? reason : "snapshot");
        String json = snapshotJson(reason);
        if (json == null) {
            return null;
        }
        for (SnapshotListener listener : listeners) {
            try {
                listener.onGuiStepLatencySnapshot(json);
            } catch (Throwable t) {
                LOGGER.warn("GuiStepLatencyTracker listener failed: {}", t.getMessage());
            }
        }
        return json;
    }

    public String snapshotJson(String reason) {
        ensureGuiFinishCloudStep(reason != null ? reason : "snapshotJson");
        synchronized (lock) {
            if (deviceSteps.isEmpty() && cloudSteps.isEmpty()) {
                return null;
            }
            try {
                JSONObject root = new JSONObject();
                root.put("reason", reason != null ? reason : "");
                root.put("captured_at_ms", System.currentTimeMillis());
                root.put("device_steps", toArray(deviceSteps));
                root.put("cloud_steps", toArray(cloudSteps));
                return root.toString();
            } catch (Exception e) {
                LOGGER.warn("GuiStepLatencyTracker.snapshotJson failed: {}", e.getMessage());
                return null;
            }
        }
    }

    private JSONArray toArray(List<StepRow> rows) throws Exception {
        JSONArray arr = new JSONArray();
        int i = 1;
        for (StepRow row : rows) {
            JSONObject o = new JSONObject();
            o.put("seq", i++);
            o.put("name", row.name);
            o.put("start_ms", row.startMs);
            o.put("end_ms", row.endMs);
            o.put("start_time", formatTs(row.startMs));
            o.put("end_time", formatTs(row.endMs));
            o.put("latency_ms", Math.max(0L, row.endMs - row.startMs));
            arr.put(o);
        }
        return arr;
    }

    private static String formatTs(long ms) {
        synchronized (TS) {
            return TS.format(new Date(ms));
        }
    }
}
