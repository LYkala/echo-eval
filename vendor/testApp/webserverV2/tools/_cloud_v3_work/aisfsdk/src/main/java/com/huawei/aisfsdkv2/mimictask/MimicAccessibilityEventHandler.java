/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.mimictask;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.huawei.aisfsdkv2.component.facade.ACMFacade;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityOperationHandler;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.message.MessageProcessor;
import com.huawei.aisfsdkv2.message.model.SessionMsgInfo;
import com.huawei.aisfsdkv2.apiimpl.session.SessionApiImpl;
import com.huawei.aisfsdkv2.session.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模仿任务无障碍事件处理器
 *
 * <p>负责处理用户操作事件（点击、滑动），用于模仿任务场景。</p>
 *
 * <p>实现 {@link AccessibilityOperationHandler} 接口，通过观察者模式注册到
 * {@link com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager}。</p>
 *
 * @since 2026-04-09
 */
public class MimicAccessibilityEventHandler implements AccessibilityOperationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("MimicAccessibilityEventHandler");

    private static final MimicAccessibilityEventHandler INSTANCE = new MimicAccessibilityEventHandler();

    /** JPEG压缩质量 */
    private static final int JPEG_COMPRESS_QUALITY = 60;

    /** 上传重试次数 */
    private static final int UPLOAD_RETRY_COUNT = 3;

    /** 上传重试间隔（毫秒） */
    private static final long UPLOAD_RETRY_INTERVAL_MS = 500;

    /** 滚动事件上报code */
    private static final int CODE_SCROLL_EVENT = 210002;

    /** 操作类型：滑动 */
    private static final String ACTION_TYPE_SCROLL = "scroll";

    /** JSON键：direction */
    private static final String KEY_DIRECTION = "direction";

    /** JSON键：package_name */
    private static final String KEY_PACKAGE_NAME = "package_name";

    /** 点击事件上报code */
    private static final int CODE_CLICK_EVENT = 210002;

    /** 操作类型：点击 */
    private static final String ACTION_TYPE_CLICK = "click";

    /** 操作类型：长按 */
    private static final String ACTION_TYPE_LONG_PRESS = "long_press";

    /** JSON键：action_type */
    private static final String KEY_ACTION_TYPE = "action_type";

    /** JSON键：target */
    private static final String KEY_TARGET = "target";

    /** JSON键：action */
    private static final String KEY_ACTION = "action";

    /** JSON键：normal_action_box */
    private static final String KEY_NORMAL_ACTION_BOX = "normal_action_box";

    /** JSON键：normal_points */
    private static final String KEY_NORMAL_POINTS = "normal_points";

    /** JSON键：text */
    private static final String KEY_TEXT = "text";

    /** JSON键：focus_app */
    private static final String KEY_FOCUS_APP = "focus_app";

    /** JSON键：image_path */
    private static final String KEY_IMAGE_PATH = "image_path";

    /** JSON键：code */
    private static final String KEY_CODE = "code";

    /** JSON键：data */
    private static final String KEY_DATA = "data";

    /** 会话属性键：标记会话处于模仿任务模式 */
    private static final String ATTRIBUTE_KEY_MIMIC = "MIMIC";

    /** 事件队列 */
    private final LinkedBlockingQueue<AccessibilityEventInfo> eventQueue = new LinkedBlockingQueue<>();

    private final AtomicInteger uploadPictureCounter = new AtomicInteger(0);

    /** 滚动状态映射 */
    private final Map<AccessibilityNodeInfo, ScrollState> scrollStateMap = new HashMap<>();

    private MimicAccessibilityEventHandler() {
        ThreadPoolManager.getInstance().executeIndependentThreadPool(this::runWorker);
    }

    /**
     * 获取单例
     *
     * @return MimicAccessibilityEventHandler 实例
     */
    public static MimicAccessibilityEventHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 工作线程主循环
     */
    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AccessibilityEventInfo info = eventQueue.take();
                processEvent(info);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 处理事件
     *
     * @param info 事件信息
     */
    private void processEvent(AccessibilityEventInfo info) {
        if (info.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            info.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            processViewClick(info);
        } else if (info.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            processViewScroll(info);
        }
        if (info.getSource() != null) {
            info.getSource().recycle();
        }
    }

    /**
     * 重置状态
     *
     * <p>在模仿任务开始或停止时调用，将计数器等状态重置。</p>
     */
    public void resetStatus() {
        uploadPictureCounter.set(0);
        scrollStateMap.clear();
    }

    /**
     * 处理无障碍事件
     *
     * @param event 无障碍事件
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        eventQueue.offer(AccessibilityEventInfo.fromEvent(event));
    }

    /**
     * 处理点击事件
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>获取操作前截图</li>
     *   <li>上传截图到ACM服务</li>
     *   <li>组装上报数据</li>
     *   <li>发送数据</li>
     * </ol>
     *
     * @param info 事件信息
     */
    private void processViewClick(AccessibilityEventInfo info) {
        String imagePath = capturePreActionScreenshot(info.getEventTime(), "processViewClick");
        if (imagePath == null) {
            return;
        }
        try {
            Rect bounds = extractBounds(info.getSource());
            String packageName = info.getPackageName() != null ? info.getPackageName().toString() : "";
            String text = extractText(info);
            JSONObject finalData = buildClickData(bounds, text, packageName, info.getEventType(), imagePath);
            sendMimicMsg(finalData);
        } catch (Exception e) {
            LOGGER.error("processViewClick error: {}", e.getMessage());
        }
    }

    /**
     * 查找操作前截图并上传到ACM服务
     *
     * <p>根据事件时间从 {@link ScheduleScreenshotManager} 获取操作前截图，
     * 上传后回收截图资源。</p>
     *
     * @param eventTime 事件时间戳
     * @param caller 调用方名称，用于日志
     * @return 上传成功返回文件名，失败或未找到截图返回null
     */
    private String capturePreActionScreenshot(long eventTime, String caller) {
        ScheduleScreenshotRecord record = ScheduleScreenshotManager.getInstance().findPreActionImage(eventTime);
        if (record == null || record.isRecycled()) {
            LOGGER.warn("{}: no pre-action image found for eventTime={}", caller, eventTime);
            return null;
        }
        Bitmap image = record.image;
        String imagePath;
        try {
            imagePath = uploadScreenshot(image);
        } finally {
            record.recycle();
        }
        if (imagePath == null) {
            LOGGER.error("{}: failed to upload screenshot", caller);
        }
        return imagePath;
    }

    /**
     * 上传截图到ACM服务
     *
     * <p>将Bitmap压缩为JPEG格式并上传，支持重试机制（最多3次，每次间隔500ms）。</p>
     *
     * @param image 截图位图
     * @return 上传成功返回文件名，失败返回null
     */
    private String uploadScreenshot(Bitmap image) {
        if (image == null || image.isRecycled()) {
            return null;
        }
        int order = uploadPictureCounter.incrementAndGet();
        String fileName = System.currentTimeMillis() + "_pic_" + order + ".jpg";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            image.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESS_QUALITY, baos);
            byte[] bytes = baos.toByteArray();
            for (int i = 0; i < UPLOAD_RETRY_COUNT; i++) {
                try {
                    UploadResult result = ACMFacade.getInstance().uploadImageFile(fileName, bytes);
                    LOGGER.info("uploadScreenshot: uploaded {}, uuid={}", fileName, result.getFileUuid());
                    return fileName;
                } catch (AcmException e) {
                    LOGGER.warn("uploadScreenshot: upload failed, attempt={}, error={}", i + 1, e.getMessage());
                    if (i < UPLOAD_RETRY_COUNT - 1) {
                        TimeUnit.MILLISECONDS.sleep(UPLOAD_RETRY_INTERVAL_MS);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("uploadScreenshot: compress error={}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("uploadScreenshot: interrupted");
        }
        return null;
    }

    /**
     * 从节点提取屏幕边界坐标
     *
     * <p>获取节点在屏幕上的边界矩形，如果坐标异常（left>right 或 top>bottom）则进行修复。</p>
     *
     * @param source 无障碍节点信息，可能为null
     * @return 边界矩形，如果source为null则返回空矩形
     */
    private Rect extractBounds(AccessibilityNodeInfo source) {
        Rect bounds = new Rect();
        if (source != null) {
            source.getBoundsInScreen(bounds);
            if (bounds.left > bounds.right || bounds.top > bounds.bottom) {
                int tempLeft = Math.min(bounds.left, bounds.right);
                int tempRight = Math.max(bounds.left, bounds.right);
                int tempTop = Math.min(bounds.top, bounds.bottom);
                int tempBottom = Math.max(bounds.top, bounds.bottom);
                bounds.set(tempLeft, tempTop, tempRight, tempBottom);
            }
        }
        return bounds;
    }

    /**
     * 提取控件文本
     *
     * <p>优先从节点获取text，如果不存在则获取contentDescription；
     * 如果节点为null，则使用事件文本。</p>
     *
     * @param info 事件信息
     * @return 控件文本，如果未找到则返回空字符串
     */
    private String extractText(AccessibilityEventInfo info) {
        CharSequence text;
        if (info.getSource() == null) {
            text = info.getText();
        } else {
            text = info.getSource().getText();
            if (text == null) {
                text = info.getSource().getContentDescription();
            }
        }
        return text != null ? text.toString() : "";
    }

    /**
     * 构建点击事件数据JSON
     *
     * <p>构造包含操作类型、坐标、文本、包名等信息的上报数据JSON对象。</p>
     *
     * @param bounds 控件边界矩形
     * @param text 控件文本
     * @param packageName 应用包名
     * @param eventType 事件类型（TYPE_VIEW_CLICKED 或 TYPE_VIEW_LONG_CLICKED）
     * @param imagePath 上传的图片路径
     * @return 包含完整上报数据的JSONObject
     * @throws Exception JSON构建异常
     */
    private JSONObject buildClickData(Rect bounds, String text, String packageName, int eventType, String imagePath) throws Exception {
        JSONObject actionObject = new JSONObject();
        String actionType = (eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) ? ACTION_TYPE_LONG_PRESS : ACTION_TYPE_CLICK;
        actionObject.put(KEY_ACTION_TYPE, actionType);
        actionObject.put(KEY_TARGET, text);
        String actionStr = actionObject.toString();
        JSONObject operationInfo = new JSONObject();
        operationInfo.put(KEY_ACTION, actionStr);
        JSONArray actionBox = new JSONArray()
                .put(bounds.left)
                .put(bounds.top)
                .put(bounds.right)
                .put(bounds.bottom);
        operationInfo.put(KEY_NORMAL_ACTION_BOX, actionBox);
        operationInfo.put(KEY_NORMAL_POINTS, new JSONArray()
                .put((bounds.left + bounds.right) / 2)
                .put((bounds.top + bounds.bottom) / 2));
        operationInfo.put(KEY_TEXT, text);
        operationInfo.put(KEY_FOCUS_APP, packageName);
        operationInfo.put(KEY_IMAGE_PATH, imagePath);
        JSONObject finalData = new JSONObject();
        finalData.put(KEY_CODE, CODE_CLICK_EVENT);
        finalData.put(KEY_DATA, operationInfo);
        return finalData;
    }

    /**
     * 处理滑动事件
     *
     * @param info 事件信息
     */
    private void processViewScroll(AccessibilityEventInfo info) {
        try {
            AccessibilityNodeInfo source = info.getSource();
            if (source == null) {
                return;
            }
            String imagePath = capturePreActionScreenshot(info.getEventTime(), "processViewScroll");
            Rect bounds = extractBounds(source);
            String packageName = info.getPackageName() != null ? info.getPackageName().toString() : "";
            String text = extractText(info);
            ScrollState state = scrollStateMap.computeIfAbsent(source, k -> new ScrollState());
            String direction = determineScrollDirection(info, state);
            state.setLastScrollX(info.getScrollX());
            state.setLastScrollY(info.getScrollY());
            state.setMaxScrollX(info.getMaxScrollX());
            state.setMaxScrollY(info.getMaxScrollY());
            state.setLastEventTime(System.currentTimeMillis());
            if (!"unknown".equals(direction)) {
                JSONObject finalData = buildScrollData(direction, bounds, text, packageName, imagePath);
                sendMimicMsg(finalData);
            }
        } catch (Exception e) {
            LOGGER.error("processViewScroll error: {}", e.getMessage());
        }
    }

    /**
     * 判定滑动方向
     *
     * @param info 事件信息
     * @param state 滚动状态
     * @return 滑动方向（left/right/up/down/unknown）
     */
    private String determineScrollDirection(AccessibilityEventInfo info, ScrollState state) {
        int scrollX = info.getScrollX();
        int scrollY = info.getScrollY();
        if (scrollX > state.getLastScrollX() && scrollX > 0) {
            return "right";
        } else if (scrollX < state.getLastScrollX() && scrollX < state.getMaxScrollX()) {
            return "left";
        } else if (scrollY > state.getLastScrollY() && scrollY > 0) {
            return "down";
        } else if (scrollY < state.getLastScrollY() && scrollY < state.getMaxScrollY()) {
            return "up";
        }
        return "unknown";
    }

    /**
     * 构建滑动事件数据JSON
     *
     * @param direction 滑动方向
     * @param bounds 控件边界矩形
     * @param text 控件文本
     * @param packageName 应用包名
     * @param imagePath 上传的图片路径，可为null
     * @return 包含完整上报数据的JSONObject
     * @throws Exception JSON构建异常
     */
    private JSONObject buildScrollData(String direction, Rect bounds, String text, String packageName,
                                       String imagePath) throws Exception {
        JSONObject actionObject = new JSONObject();
        actionObject.put(KEY_ACTION_TYPE, ACTION_TYPE_SCROLL);
        actionObject.put(KEY_DIRECTION, direction);
        String actionStr = actionObject.toString();
        JSONObject operationInfo = new JSONObject();
        operationInfo.put(KEY_ACTION, actionStr);
        JSONArray actionBox = new JSONArray()
                .put(bounds.left)
                .put(bounds.top)
                .put(bounds.right)
                .put(bounds.bottom);
        operationInfo.put(KEY_NORMAL_ACTION_BOX, actionBox);
        operationInfo.put(KEY_NORMAL_POINTS, new JSONArray()
                .put((bounds.left + bounds.right) / 2)
                .put((bounds.top + bounds.bottom) / 2));
        operationInfo.put(KEY_TEXT, text != null ? text : "");
        operationInfo.put(KEY_PACKAGE_NAME, packageName);
        operationInfo.put(KEY_FOCUS_APP, packageName);
        if (imagePath != null) {
            operationInfo.put(KEY_IMAGE_PATH, imagePath);
        }
        JSONObject finalData = new JSONObject();
        finalData.put(KEY_CODE, CODE_SCROLL_EVENT);
        finalData.put(KEY_DATA, operationInfo);
        return finalData;
    }

    /**
     * 发送模仿任务消息
     *
     * <p>通过 MIMIC 属性过滤出当前处于模仿任务模式的会话，
     * 从中获取 sessionId 和 taskId 设置到消息头中。</p>
     *
     * @param userInputObject 用户输入对象JSON
     */
    private void sendMimicMsg(JSONObject userInputObject) {
        SessionMsgInfo sessionMsgInfo = new SessionMsgInfo();
        sessionMsgInfo.getHeader().setMessageType("DataExchange");
        sessionMsgInfo.getData().setMessageType("UserInput");
        sessionMsgInfo.getData().setUserInputObject(userInputObject.toString());
        Session mimicSession = findMimicSession();
        if (mimicSession != null) {
            sessionMsgInfo.getHeader().setClientSessionId(mimicSession.getId());
            String taskId = mimicSession.getCurrentTaskId();
            if (taskId != null && !taskId.isEmpty()) {
                sessionMsgInfo.getHeader().setTaskId(taskId);
            }
            String teamEnvId = mimicSession.getTeamEnvironmentId();
            if (teamEnvId != null && !teamEnvId.isEmpty()) {
                sessionMsgInfo.getHeader().setTeamEnvironmentId(teamEnvId);
            }
        }
        boolean result = MessageProcessor.getInstance().send(sessionMsgInfo);
        if (result) {
            LOGGER.info("sendMimicMsg: sent successfully");
        } else {
            LOGGER.error("sendMimicMsg: failed to send");
        }
    }

    /**
     * 查找当前处于模仿任务模式的会话
     *
     * <p>通过 MIMIC 属性过滤会话列表，返回第一个匹配的会话。</p>
     *
     * @return 模仿任务会话，未找到返回null
     */
    private Session findMimicSession() {
        java.util.List<Session> sessions = SessionApiImpl.getInstance().getSessions(
                session -> Boolean.TRUE.toString().equals(session.getAttribute(ATTRIBUTE_KEY_MIMIC)));
        if (sessions.isEmpty()) {
            LOGGER.warn("findMimicSession: no session with MIMIC attribute found");
            return null;
        }
        if (sessions.size() > 1) {
            LOGGER.warn("findMimicSession: multiple sessions with MIMIC attribute found, count={}", sessions.size());
        }
        return sessions.get(0);
    }
}
