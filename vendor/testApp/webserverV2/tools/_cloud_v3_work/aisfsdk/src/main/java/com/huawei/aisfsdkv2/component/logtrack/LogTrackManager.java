/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logtrack;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import org.json.JSONObject;



/**
 * 日志追踪管理器
 *
 * <p>用于在关键节点（如消息收发）进行日志埋点，输出统一格式的追踪日志。</p>
 *
 * <p>埋点格式：^^^^^^{埋点内容}&&&&&&</p>
 *
 * <p>埋点内容 JSON 结构：</p>
 * <pre>
 * {
 *   "timestamp": "2026-04-14T07:30:19.123Z",
 *   "direct": {"from":"Client","to":"Server"},
 *   "data": "消息内容"
 * }
 * </pre>
 */
public class LogTrackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("LogTrack");

    private static final String LOG_PREFIX = "^^^^^^";
    private static final String LOG_SUFFIX = "&&&&&&";

    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_DIRECT = "direct";
    private static final String FIELD_FROM = "from";
    private static final String FIELD_TO = "to";
    private static final String FIELD_DATA = "data";

    private static class Holder {
        private static final LogTrackManager INSTANCE = new LogTrackManager();
    }

    /**
     * 获取 LogTrackManager 单例
     *
     * @return LogTrackManager 单例实例
     */
    public static LogTrackManager getInstance() {
        return Holder.INSTANCE;
    }

    private LogTrackManager() {}

    /**
     * 记录追踪日志
     *
     * @param type 追踪类型（发送或接收）
     * @param data 消息内容
     */
    public void track(LogTrackType type, String data) {
        if (type == null) {
            return;
        }
        try {
            JSONObject trackInfo = new JSONObject();
            trackInfo.put(FIELD_TIMESTAMP, System.currentTimeMillis());
            JSONObject direct = new JSONObject();
            String from = type.getFrom();
            String to = type.getTo();
            if (data != null && !data.isEmpty()) {
                try {
                    JSONObject jsonData = new JSONObject(data);
                    JSONObject header = jsonData.optJSONObject("Header");
                    if (header != null) {
                        if (type == LogTrackType.RECEIVE) {
                            String sender = header.optString("Sender", "");
                            if (!sender.isEmpty()) {
                                from = sender;
                            }
                        } else if (type == LogTrackType.SEND) {
                            String receiver = header.optString("Receiver", "");
                            if (!receiver.isEmpty()) {
                                to = receiver;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("LogTrackManager.track: failed to parse Header, use default");
                }
            }
            direct.put(FIELD_FROM, from);
            direct.put(FIELD_TO, to);
            trackInfo.put(FIELD_DIRECT, direct);
            trackInfo.put(FIELD_DATA, data != null ? data : "");
            String logContent = LOG_PREFIX + trackInfo + LOG_SUFFIX;
            LOGGER.info(logContent);
        } catch (Exception e) {
            LOGGER.error("LogTrackManager.track: failed to build track log, error={}", e.getMessage());
        }
    }

}
