/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.impl;

import static android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.concurrent.CountDownLatch;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityOperationHandler;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.ClickResult;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.NodeContent;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.model.NodeType;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.animation.ClickFloatViewAnimator;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 无障碍管理器实现类
 * <p>基于 Android AccessibilityService 实现自动化操作</p>
 */
public class AccessibilityManagerImpl extends AccessibilityService implements AccessibilityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AccessibilityManagerImpl");

    /** 点击操作默认持续时间（毫秒） */
    private static final long CLICK_DURATION = 100L;
    /** 长按操作默认持续时间（毫秒） */
    private static final long LONG_CLICK_DURATION = 1000L;
    /** 滑动操作默认持续时间（毫秒） */
    private static final int SWIPE_DURATION = 300;
    /** 单例实例 */
    private static AccessibilityManagerImpl instance = null;
    /** 主页包名缓存 */
    private String homePackageName = "";
    /** ACTION_IME_ENTER 需要的最低 Android 版本 */
    private static final int ACTION_IME_ENTER_VERSION = 30;
    /** ACTION_IME_ENTER 的动作 ID */
    private static final int ACTION_IME_ENTER_ID = 16908372;
    /** 获取根节点最大重试次数 */
    private static final int ROOT_NODE_MAX_RETRY_TIMES = 20;
    /** 获取根节点重试间隔（毫秒） */
    private static final int ROOT_NODE_RETRY_INTERVAL_MS = 300;
    /** 检查应用是否在屏幕上的最大重试次数 */
    private static final int APP_ON_SCREEN_MAX_RETRY_TIMES = 10;
    /** 检查应用是否在屏幕上的重试间隔（毫秒） */
    private static final int APP_ON_SCREEN_RETRY_INTERVAL_MS = 300;
    /** 获取前台应用包名最大重试次数 */
    private static final int FOREGROUND_PACKAGE_MAX_RETRY_TIMES = 10;
    /** 获取前台应用包名重试间隔（毫秒） */
    private static final int FOREGROUND_PACKAGE_RETRY_INTERVAL_MS = 100;
    /** 默认重试间隔（毫秒） */
    private static final int DEFAULT_RETRY_INTERVAL_MS = 1000;
    /** 窗口事件缓存前台包名的有效期（毫秒） */
    private static final long WINDOW_PACKAGE_CACHE_TTL_MS = 20000L;

    /** 最近一次 WINDOW_STATE_CHANGED 的包名（getRoot 为空时用于就绪判定） */
    private volatile String lastWindowPackageName = "";
    /** 最近一次 WINDOW_STATE_CHANGED 的时间戳 */
    private volatile long lastWindowPackageAtMs = 0L;

    /** 执行回车后等待焦点变化的时间（毫秒） */
    private static final int ENTER_FOCUS_WAIT_MS = 300;
    /** 屏幕内容变更计数器，订阅特定事件类型时自动+1 */
    private final AtomicLong screenGeneratorNum = new AtomicLong(0L);
    /** 注册的无障碍事件处理器集合（线程安全） */
    private final Set<AccessibilityOperationHandler> handlers = new CopyOnWriteArraySet<>();

    /**
     * 无障碍服务连接回调
     * <p>当无障碍服务成功连接时调用，设置单例实例</p>
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        LOGGER.info("AccessibilityManagerImpl service connected");
    }

    /**
     * 获取单例实例
     *
     * @return AccessibilityManagerImpl 实例，如果服务未启动则返回 null
     */
    public static AccessibilityManagerImpl getInstance() {
        return instance;
    }

    /**
     * 检查无障碍服务是否已启用
     *
     * @return true 已启用
     */
    @Override
    public boolean isAccessibilityEnabled() {
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            boolean result = enabled == 1;
            LOGGER.info("isAccessibilityEnabled: accessibility service is {}", result ? "enabled" : "disabled");
            return result;
        } catch (Exception e) {
            LOGGER.error("isAccessibilityEnabled: failed to check accessibility status, error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 执行点击操作
     *
     * @param x X坐标（像素）
     * @param y Y坐标（像素）
     * @return true 点击成功
     */
    @Override
    public ClickResult click(int x, int y, List<String> controlBlackList) {
        LOGGER.info("click: executing at [{}, {}]", x, y);
        if (instance == null) {
            LOGGER.error("click: AccessibilityManagerImpl instance is null, service not running");
            return new ClickResult(false, false);
        }
        List<NodeContent> nodeContents = getNodeInfoAtCoordinates(x, y);
        LOGGER.info("click: target node contents at [{}, {}] {}", x, y, nodeContents);
        List<String> matchedKeywords = new ArrayList<>();
        if (isBlacklisted(nodeContents, controlBlackList, matchedKeywords)) {
            return new ClickResult(false, true, matchedKeywords);
        }
        ClickFloatViewAnimator.showClickAnim(this, x, y, 1000);
        ClickResult result = dispatchClickGesture(x, y);
        return new ClickResult(result.isSuccess(), false, matchedKeywords);
    }

    /**
     * 检查目标节点是否在黑名单中
     *
     * @param nodeContents 目标节点内容列表
     * @param controlBlackList 黑名单关键词列表
     * @return true 命中黑名单
     */
    private boolean isBlacklisted(List<NodeContent> nodeContents, List<String> controlBlackList, List<String> matchedKeywords) {
        if (controlBlackList == null || controlBlackList.isEmpty()) {
            return false;
        }
        for (NodeContent content : nodeContents) {
            if (content == null) {
                continue;
            }
            if (content.getNodeType() != NodeType.BUTTON && content.getNodeType() != NodeType.TEXT_VIEW) {
                continue;
            }
            String text = content.getText();
            matchedKeywords.add(text);
            for (String keyword : controlBlackList) {
                if (keyword != null && text.contains(keyword)) {
                    matchedKeywords.add(keyword);
                    LOGGER.error("click: target BUTTON '{}' contains blacklisted keyword '{}', " +
                            "click cancelled", text, keyword);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 分发点击手势
     *
     * @param x X坐标
     * @param y Y坐标
     * @return ClickResult 点击结果
     */
    private ClickResult dispatchClickGesture(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, CLICK_DURATION);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    LOGGER.info("click: succeeded at [{}, {}]", x, y);
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    LOGGER.error("click: cancelled at [{}, {}]", x, y);
                }
            }, null);
            LOGGER.info("click: dispatched gesture at [{}, {}], result = {}", x, y, result);
            return new ClickResult(result, false);
        } catch (Exception e) {
            LOGGER.error("click: failed at [{}, {}], error: {}", x, y, e.getMessage());
            return new ClickResult(false, false);
        }
    }

    /**
     * 执行长按操作
     *
     * @param x        X坐标
     * @param y        Y坐标
     * @param duration 长按时长（毫秒）
     * @return true 长按成功
     */
    @Override
    public boolean longClick(int x, int y, int duration) {
        LOGGER.info("longClick: executing at [{}, {}] for {}ms", x, y, duration);
        if (instance == null) {
            LOGGER.error("longClick: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        // 显示长按动画（视觉反馈）
        ClickFloatViewAnimator.showClickAnim(this, x, y, duration > 0 ? duration : 1000);
        try {
            long actualDuration = duration > 0 ? duration : LONG_CLICK_DURATION;
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, actualDuration);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            boolean result = dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    LOGGER.info("longClick: succeeded at [{}, {}]", x, y);
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    LOGGER.error("longClick: cancelled at [{}, {}]", x, y);
                }
            }, null);
            LOGGER.info("longClick: dispatched gesture at [{}, {}], result = {}", x, y, result);
            return result;
        } catch (Exception e) {
            LOGGER.error("longClick: failed at [{}, {}], error: {}", x, y, e.getMessage());
            return false;
        }
    }

    /**
     * 执行滑动操作
     *
     * @param startX   起始 X 坐标
     * @param startY   起始 Y 坐标
     * @param endX     结束 X 坐标
     * @param endY     结束 Y 坐标
     * @param duration 滑动时长（毫秒），默认 300ms
     * @return true 滑动成功
     */
    @Override
    public boolean swipe(int startX, int startY, int endX, int endY, int duration) {
        LOGGER.info("swipe: executing from [{}, {}] to [{}, {}] for {}ms", startX, startY, endX, endY, duration);
        if (instance == null) {
            LOGGER.error("swipe: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        try {
            int dur = duration > 0 ? duration : SWIPE_DURATION;
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, dur);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            boolean result = dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    LOGGER.info("swipe: succeeded from [{}, {}] to [{}, {}]", startX, startY, endX, endY);
                }
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    LOGGER.error("swipe: cancelled from [{}, {}] to [{}, {}]", startX, startY, endX, endY);
                }
            }, null);
            LOGGER.info("swipe: dispatched gesture from [{}, {}] to [{}, {}], result = {}", startX, startY, endX, endY, result);
            return result;
        } catch (Exception e) {
            LOGGER.error("swipe: failed from [{}, {}] to [{}, {}], error: {}", startX, startY, endX, endY, e.getMessage());
            return false;
        }
    }

    /**
     * 输入文本
     * <p>点击指定位置后查找 EditText 节点并输入文本</p>
     *
     * @param x    点击位置的 X 坐标
     * @param y    点击位置的 Y 坐标
     * @param text 要输入的文本
     * @return true 输入成功
     */
    @Override
    public boolean inputText(int x, int y, String text) {
        LOGGER.info("inputText: executing at [{}, {}], text: {}", x, y, text);
        if (instance == null) {
            LOGGER.error("inputText: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        if (text == null || text.isEmpty()) {
            LOGGER.error("inputText: text is null or empty");
            return false;
        }
        
        // 策略1: 尝试直接输入
        boolean result = performTextInputInternal(x, y, text);
        if (result){
            LOGGER.info("inputText: direct input success");
            return true;
        }
        // 策略4: 多点位点击激活后重试
        LOGGER.warn("inputText: all stages failed, try click to activate input box at[{},{}]", x, y);
        boolean activated = clickToActivateInputBox(x, y);
        if (!activated){
            LOGGER.error("inputText: failed to activate input box at [{},{}]", x, y);
            return false;
        }
        result = performTextInputInternal(x, y, text);
        if (!result){
            LOGGER.error("inputText: all strategies failed at [{},{}]", x, y);
            return false;
        }
        LOGGER.info("inputText: click activation + retry input success at [{},{}]", x, y);
        return true;
    }

    /**
     * 执行文本输入（内部实现）
     * @param text 要输入的文本
     * @return true 输入成功
     */
    private boolean performTextInputInternal(int x, int y, String text){
        LOGGER.info("performTextInputInternal: start");
        AccessibilityNodeInfo root = getValidRootNodeWithRetry(
            ROOT_NODE_MAX_RETRY_TIMES, ROOT_NODE_RETRY_INTERVAL_MS);
        if (root == null) {
            LOGGER.error("performTextInputInternal: root is null");
            return false;
        }
        // Strategy 1: 已聚焦输入框
        AccessibilityNodeInfo node = findFocusedOrInputNode(root);
        if (tryInput(node, text, "Stage1 - focused")){
            return true;
        }
        // Strategy 2: 坐标匹配，找到可输入节点并执行输入
        node = findEditableNodeAtCoordinates(root, x, y);
        if (tryInput(node, text, "Stage2 - coordinate")){
            return true;
        }
        // Strategy 3: 坐标不匹配但节点存在
        node = findNodeInfo(root, this::isInputNode);
        if (tryInput(node, text, "Stage3 - global")){
            return true;
        }
        LOGGER.error("performTextInputInternal: ALL STAGES FAILED");
        return false;
    }

    /**
     * 判断节点是否为可输入节点
     * @param node 节点
     * @return true 可输入节点
     */
    private boolean isInputNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        return node.isEditable() && node.isEnabled();
    }

    /**
     * 尝试对指定节点执行文本输入
     * @param node 目标输入节点
     * @param text 要输入的文本
     * @param stage 当前策略阶段标识
     * @return true输入成功，false输入失败
     */
    private boolean tryInput(AccessibilityNodeInfo node, String text, String stage){
        if (node == null){
            LOGGER.info("{} - node is null", stage);
            return false;
        }
        boolean result = performTextInput(node, text);
        if (result) {
            LOGGER.info("{} SUCCESS", stage);
        } else {
            LOGGER.warn("{} FAILED", stage);
        }
        return result;
    }

    /**
     * 根据坐标查找可编辑输入节点
     * @param node
     * @param x X坐标
     * @param y Y坐标
     * @return 匹配的可编辑节点，未找到返回null
     */
    private AccessibilityNodeInfo findEditableNodeAtCoordinates(
        AccessibilityNodeInfo node, int x, int y) {
        if (node == null) {
            return null;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return null;
        }
        // 先查找子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findEditableNodeAtCoordinates(child, x, y);
            if (result != null) {
                return result;
            }
        }
        // 再判断当前节点
        if (isInputNode(node)) {
            return node;
        }
        return null;
    }

    /**
     * 在指定root下查找输入节点
     * @param root 根节点
     * @return 输入节点
     */
    private AccessibilityNodeInfo findFocusedOrInputNode(AccessibilityNodeInfo root){
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo node = findNodeInfo(root,
            n -> n.isFocused() && n.isEditable());
        if (node != null) {
            return node;
        }
        return findNodeInfo(root, this::isInputNode);
    }

    /** 坐标点击激活输入框时的固定偏移量像素，用于找不到节点时的兜底点击 */

    private static final int ACTIVATE_RETRY_OFFSET = 50;

    /**
     * 多点位尝试点击激活输入框
     * @param x 目标输入框X坐标
     * @param y 目标输入框Y坐标
     * @return true激活成功，false所有点位尝试均失败
     */
    private boolean clickToActivateInputBox(int x, int y) {
        int[][] offsets = buildClickOffsets(x, y);
        for (int i = 0; i < offsets.length; i++) {
            int clickX = x + offsets[i][0];
            int clickY = y + offsets[i][1];
            LOGGER.info("clickToActivateInputBox: attempt {}/{} at [{}, {}]",
                i + 1, offsets.length, clickX, clickY);
            if (!click(clickX, clickY, null).isSuccess()) {
                LOGGER.warn("clickToActivateInputBox: click failed at at [{}, {}], try next", 
                clickX, clickY);
                continue;
            }
            AccessibilityNodeInfo editNode = findNodeInfo(getValidRootNode(), 
            node -> node.isEditable() && node.isEnabled());
            if (editNode != null) {
                LOGGER.info("clickToActivateInputBox: editable node appeared at [{}, {}]", clickX
                , clickY);
                return true;
            }
            LOGGER.warn("clickToActivateInputBox: editable node not appeared at [{}, {}]", clickX, clickY);
        }
        LOGGER.error("clickToActivateInputBox: all attempts failed");
        return false;
    }
    
    /**
     * 根据目标坐标构建点击点位偏移量数组
     * @param x 目标节点X
     * @param y 目标节点Y
     * @return 偏移量数组，每个元素为[dx, dy], 实际点击坐标为[x + dx, y + dy]
     */
    private int[][] buildClickOffsets(int x, int y) {
        AccessibilityNodeInfo targetView = findNodeInfo(getValidRootNode(), node -> {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            return bounds.contains(x, y) && node.isClickable();
        });
        if (targetView == null) {
            LOGGER.warn("buildClickOffsets: no clickable node found, use fixed offset");
            return new int[][]{{0, 0}, {-ACTIVATE_RETRY_OFFSET, 0}, {ACTIVATE_RETRY_OFFSET, 0},
        {0, -ACTIVATE_RETRY_OFFSET}, {0, ACTIVATE_RETRY_OFFSET}};
        }
        Rect bounds = new Rect();
        targetView.getBoundsInScreen(bounds);
        int cx = (bounds.left + bounds.right) / 2;
        int cy = (bounds.top + bounds.bottom) / 2;
        int hw = (bounds.right - bounds.left) / 4;
        int hh = (bounds.bottom - bounds.top) / 4;
        LOGGER.info("buildClickOffsets: center = [{}, {}], bounds={}", cx, cy, bounds.toShortString());
        return new int[][]{{cx - x, cy - y}, {cx - x - hw, cy - y}, {cx - x + hw, cy - y},
            {cx - x, cy - y - hh}, {cx - x, cy - y + hh}};
    }


    /**
     * 查找与指定坐标最近的 EditText 节点
     *
     * <p>遍历节点树，查找 Y 坐标落在控件范围内且水平距离最近的 EditText。</p>
     *
     * @param node 当前节点
     * @param x X坐标
     * @param y Y坐标
     * @param minDistance 最小距离引用（int[1]）
     * @return 最近的 EditText 节点，未找到返回 null
     */
    private AccessibilityNodeInfo findClosestEditTextNode(AccessibilityNodeInfo node, int x, int y, int[] minDistance) {
        if (node == null) {
            return null;
        }
        AccessibilityNodeInfo closest = null;
        CharSequence className = node.getClassName();
        if (className != null && "android.widget.EditText".equals(className.toString())
                && node.isEditable() && node.isEnabled()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (y >= bounds.top && y <= bounds.bottom) {
                int dist = Math.min(Math.abs(bounds.left - x), Math.abs(bounds.right - x));
                if (dist < minDistance[0]) {
                    minDistance[0] = dist;
                    closest = node;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childClosest = findClosestEditTextNode(node.getChild(i), x, y, minDistance);
            if (childClosest != null) {
                closest = childClosest;
            }
        }
        return closest;
    }

    /**
     * 根据坐标获取控件内容列表
     *
     * <p>遍历视图树，查找包含指定坐标的所有控件节点，收集它们的类型和内容。</p>
     *
     * @param x X坐标
     * @param y Y坐标
     * @return 控件内容列表（类型+文本），未找到返回空列表
     */
    private List<NodeContent> getNodeInfoAtCoordinates(int x, int y) {
        List<NodeContent> contents = new ArrayList<>();
        AccessibilityNodeInfo rootNode = getValidRootNode();
        if (rootNode == null) {
            return contents;
        }
        findNodeContentsAtCoordinates(rootNode, x, y, contents);
        return contents;
    }

    /**
     * 在节点树中查找包含指定坐标的节点，并按从具体到通用的顺序收集内容
     *
     * <p>遍历顺序：先递归查找子节点（最内层），再处理当前节点（最外层），
     * 这样列表中第一个元素是最具体的控件内容，最后一个是最通用的容器内容。</p>
     *
     * @param node 当前节点
     * @param x X坐标
     * @param y Y坐标
     * @param contents 内容收集列表
     */
    private void findNodeContentsAtCoordinates(AccessibilityNodeInfo node, int x, int y, List<NodeContent> contents) {
        if (node == null) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return;
        }
        // 先递归查找子节点（更具体的控件），确保子节点内容排在前面
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findNodeContentsAtCoordinates(child, x, y, contents);
            }
        }
        // 再收集当前节点的内容（更通用的容器）
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        NodeType nodeType = NodeType.fromClassName(className);
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            contents.add(new NodeContent(nodeType, text.toString()));
        } else {
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                contents.add(new NodeContent(nodeType, desc.toString()));
            }
        }
    }

    /**
     * 获取有效根节点
     *
     * @return AccessibilityNodeInfo 根节点
     */
    private AccessibilityNodeInfo getValidRootNode() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        LOGGER.info("getValidRootNode: getRootInActiveWindow result: {}, childCount: {}",
                rootNode != null ? rootNode.getPackageName() : "null",
                rootNode != null ? rootNode.getChildCount() : -1);
        // 允许 childCount==0：部分 App 启动瞬间根节点尚无子节点，但仍可判定包名
        if (rootNode != null && rootNode.getPackageName() != null) {
            return rootNode;
        }
        if (rootNode != null) {
            rootNode.recycle();
        }
        List<AccessibilityWindowInfo> windowList = getWindows();
        LOGGER.info("getValidRootNode: windowList size: {}", windowList != null ? windowList.size() : -1);
        AccessibilityNodeInfo windowRootNode = null;
        if (windowList != null && !windowList.isEmpty()) {
            for (AccessibilityWindowInfo windowInfo : windowList) {
                if (windowInfo != null) {
                    AccessibilityNodeInfo loggedRoot = null;
                    try {
                        loggedRoot = windowInfo.getRoot();
                        LOGGER.info("getValidRootNode: window type={}, title={}, root={}",
                                windowInfo.getType(),
                                windowInfo.getTitle(),
                                loggedRoot != null ? loggedRoot.getPackageName() : "null");
                    } catch (RuntimeException e) {
                        LOGGER.warn("getValidRootNode: window log failed: {}", e.getMessage());
                    } finally {
                        if (loggedRoot != null) {
                            try {
                                loggedRoot.recycle();
                            } catch (RuntimeException ignore) {
                                // ignore
                            }
                        }
                    }
                }
                if (windowInfo != null && windowInfo.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    AccessibilityNodeInfo candidate = windowInfo.getRoot();
                    if (candidate != null && candidate.getPackageName() != null) {
                        windowRootNode = candidate;
                        if (candidate.getChildCount() > 0) {
                            break;
                        }
                    }
                }
            }
            for (AccessibilityWindowInfo windowInfo : windowList) {
                if (windowInfo != null) {
                    windowInfo.recycle();
                }
            }
        }
        return windowRootNode;
    }

    /**
     * 获取有效根节点（带重试）
     *
     * @param retryTimes     重试次数
     * @param retryIntervalMs 重试间隔（毫秒）
     * @return AccessibilityNodeInfo 根节点
     */
    private AccessibilityNodeInfo getValidRootNodeWithRetry(int retryTimes, int retryIntervalMs) {
        LOGGER.info("getValidRootNodeWithRetry: entering");
        AccessibilityNodeInfo rootNode = null;
        int retryCount = 0;
        while (retryCount < retryTimes) {
            rootNode = getValidRootNode();
            if (rootNode != null) {
                break;
            }
            retryCount++;
            LOGGER.info("getValidRootNodeWithRetry: retry {} times...", retryCount);
            ThreadUtil.sleepNoExcept(retryIntervalMs);
        }
        return rootNode;
    }

    /**
     * 查找节点信息
     *
     * @param nodeInfo 节点信息
     * @param findFunc 查找函数
     * @return AccessibilityNodeInfo 找到的节点
     */
    private AccessibilityNodeInfo findNodeInfo(AccessibilityNodeInfo nodeInfo, java.util.function.Function<AccessibilityNodeInfo, Boolean> findFunc) {
        if (nodeInfo == null) {
            return null;
        }
        if (findFunc.apply(nodeInfo)) {
            return nodeInfo;
        }
        for (int i = 0; i < nodeInfo.getChildCount(); i++) {
            AccessibilityNodeInfo child = nodeInfo.getChild(i);
            if (child == null) {
                continue;
            }
            if (findFunc.apply(child)) {
                return child;
            }
            AccessibilityNodeInfo node = findNodeInfo(child, findFunc);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * 执行文本输入
     *
     * @param node 节点
     * @param text 文本
     * @return true 输入成功
     */
    private boolean performTextInput(AccessibilityNodeInfo node, String text) {
        if (node == null) {
            return false;
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        LOGGER.info("performTextInput: inputting text to [{}, {}]", (rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2);
        args.putIntArray("android.view.accessibility.extra.EVENT_COORDINATES", new int[]{(rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2});
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    /**
     * 查找焦点节点
     *
     * @param root 根节点
     * @return AccessibilityNodeInfo 焦点节点
     */
    private AccessibilityNodeInfo findFocusedNode(AccessibilityNodeInfo root) {
        if (root == null) {
            return null;
        }
        if (root.isFocused()) {
            return root;
        }
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo focusedChild = findFocusedNode(child);
                if (focusedChild != null) {
                    return focusedChild;
                }
            }
        }
        return null;
    }

    /**
     * 回到桌面
     *
     * @return true 操作成功
     */
    @Override
    public boolean goHome() {
        LOGGER.info("goHome: entering");
        if (instance == null) {
            LOGGER.error("goHome: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        boolean homeActionResult = performGlobalActionOnMain(GLOBAL_ACTION_HOME);
        if (!homeActionResult) {
            LOGGER.error("goHome: performGlobalAction failed");
            return false;
        }
        String homePageName = getDefaultHomePackageName(this);
        if (homePageName == null) {
            LOGGER.warn("goHome: getDefaultHomePackageName failed, skip compare");
            ThreadUtil.sleepNoExcept(DEFAULT_RETRY_INTERVAL_MS);
            return true;
        }
        LOGGER.info("goHome: home page package name {}", homePageName);
        boolean isHomePageReady = isTargetAppOnScreenWithRetry(homePageName, APP_ON_SCREEN_MAX_RETRY_TIMES, APP_ON_SCREEN_RETRY_INTERVAL_MS);
        LOGGER.info("goHome: isHomePageReady {}", isHomePageReady);
        return isHomePageReady;
    }

    /**
     * 在主线程执行 GlobalAction（后台 Handler 线程上直接调用常失败）
     */
    private boolean performGlobalActionOnMain(final int action) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return performGlobalAction(action);
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = {false};
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                try {
                    ok[0] = performGlobalAction(action);
                } catch (Exception e) {
                    LOGGER.warn("performGlobalActionOnMain: action {} failed: {}", action, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            if (!latch.await(2, TimeUnit.SECONDS)) {
                LOGGER.warn("performGlobalActionOnMain: timeout action={}", action);
                return false;
            }
            return ok[0];
        } catch (Exception e) {
            LOGGER.warn("performGlobalActionOnMain: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前系统默认主屏幕（Launcher/Home）的包名
     *
     * @param context 上下文
     * @return String 默认Home的包名，若获取失败返回null
     */
    private String getDefaultHomePackageName(Context context) {
        if (homePackageName != null && !homePackageName.isEmpty()) {
            return homePackageName;
        }
        if (context == null) {
            LOGGER.error("getDefaultHomePackageName: context is null");
            return null;
        }
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addCategory(Intent.CATEGORY_DEFAULT);
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) {
            LOGGER.error("getDefaultHomePackageName: packageManager is null");
            return null;
        }
        ResolveInfo resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null || resolveInfo.activityInfo.packageName == null) {
            LOGGER.error("getDefaultHomePackageName: resolveInfo or activityInfo or packageName is null");
            return null;
        }
        homePackageName = resolveInfo.activityInfo.packageName;
        return resolveInfo.activityInfo.packageName;
    }

    /**
     * 获取当前屏幕最上层应用的包名
     *
     * @return 最上层应用包名，获取失败返回 null
     */
    @Override
    public String getForegroundPackageName() {
        AccessibilityNodeInfo rootNode = getValidRootNodeWithRetry(FOREGROUND_PACKAGE_MAX_RETRY_TIMES, FOREGROUND_PACKAGE_RETRY_INTERVAL_MS);
        if (rootNode == null) {
            LOGGER.error("getForegroundPackageName: failed to get rootNode");
            return null;
        }
        CharSequence packageChar = rootNode.getPackageName();
        rootNode.recycle();
        if (packageChar == null) {
            LOGGER.error("getForegroundPackageName: packageName is null");
            return null;
        }
        return packageChar.toString();
    }

    /**
     * 判断当前屏幕是否为目标应用
     *
     * <p>判定顺序：
     * <ol>
     *   <li>active window 根节点包名</li>
     *   <li>遍历 Accessibility windows（含 TYPE_APPLICATION）</li>
     *   <li>近期 WINDOW_STATE_CHANGED 缓存包名（getRoot/windowList 为空时的兜底）</li>
     *   <li>ActivityManager 顶层任务（尽力而为）</li>
     * </ol>
     *
     * @param targetPackageName 目标应用包名
     * @return boolean true=当前屏幕是目标应用页面
     */
    @Override
    public boolean isTargetAppOnScreen(String targetPackageName) {
        if (targetPackageName == null || targetPackageName.isEmpty()) {
            return false;
        }

        // 1) active root（不要求 childCount>0）
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                boolean match = pkg != null && targetPackageName.equalsIgnoreCase(pkg.toString());
                root.recycle();
                if (match) {
                    LOGGER.info("isTargetAppOnScreen: match via activeRoot {}", targetPackageName);
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("isTargetAppOnScreen: activeRoot failed: {}", e.getMessage());
        }

        // 2) 扫描全部 window
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                boolean matched = false;
                for (AccessibilityWindowInfo window : windows) {
                    if (window == null) {
                        continue;
                    }
                    AccessibilityNodeInfo wr = null;
                    try {
                        wr = window.getRoot();
                        if (wr != null) {
                            CharSequence pkg = wr.getPackageName();
                            if (pkg != null && targetPackageName.equalsIgnoreCase(pkg.toString())) {
                                matched = true;
                                LOGGER.info("isTargetAppOnScreen: match via window type={} pkg={}",
                                        window.getType(), targetPackageName);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("isTargetAppOnScreen: window root failed: {}", e.getMessage());
                    } finally {
                        if (wr != null) {
                            try {
                                wr.recycle();
                            } catch (Exception ignore) {
                                // ignore
                            }
                        }
                    }
                }
                for (AccessibilityWindowInfo window : windows) {
                    if (window != null) {
                        try {
                            window.recycle();
                        } catch (Exception ignore) {
                            // ignore
                        }
                    }
                }
                if (matched) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("isTargetAppOnScreen: windows scan failed: {}", e.getMessage());
        }

        // 3) 事件缓存（小米上 Launch 后 getRoot 常长时间 null，但 STATE_CHANGED 已带包名）
        String cached = lastWindowPackageName;
        long cachedAt = lastWindowPackageAtMs;
        if (cached != null && !cached.isEmpty()
                && targetPackageName.equalsIgnoreCase(cached)
                && (System.currentTimeMillis() - cachedAt) <= WINDOW_PACKAGE_CACHE_TTL_MS) {
            LOGGER.info("isTargetAppOnScreen: match via windowEventCache pkg={} ageMs={}",
                    cached, System.currentTimeMillis() - cachedAt);
            return true;
        }

        // 4) ActivityManager 尽力而为（部分机型仍可读顶层任务）
        if (isPackageLikelyForegroundByAm(targetPackageName)) {
            LOGGER.info("isTargetAppOnScreen: match via ActivityManager {}", targetPackageName);
            return true;
        }

        LOGGER.error("isTargetAppOnScreen: target {} not on screen (cache={} ageMs={})",
                targetPackageName, cached,
                cachedAt > 0 ? (System.currentTimeMillis() - cachedAt) : -1);
        return false;
    }

    /**
     * ActivityManager 顶层任务兜底（API 限制下可能失败，失败不抛）
     */
    private boolean isPackageLikelyForegroundByAm(String targetPackageName) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            @SuppressWarnings("deprecation")
            List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(3);
            if (tasks == null || tasks.isEmpty()) {
                return false;
            }
            for (android.app.ActivityManager.RunningTaskInfo task : tasks) {
                if (task == null) {
                    continue;
                }
                android.content.ComponentName top = task.topActivity;
                if (top != null && targetPackageName.equalsIgnoreCase(top.getPackageName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("isPackageLikelyForegroundByAm failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 带重试的判断当前屏幕是否为目标应用
     *
     * @param targetPackageName 目标应用包名
     * @param retryTimes 重试次数
     * @param retryIntervalMs 重试间隔（毫秒）
     * @return boolean true=当前屏幕是目标应用页面
     */
    private boolean isTargetAppOnScreenWithRetry(String targetPackageName, int retryTimes, int retryIntervalMs) {
        int retryCount = 0;
        while (retryCount < retryTimes) {
            if (isTargetAppOnScreen(targetPackageName)) {
                return true;
            }
            retryCount++;
            ThreadUtil.sleepNoExcept(retryIntervalMs);
        }
        return false;
    }

    /**
     * 返回键
     *
     * @return true 操作成功
     */
    @Override
    public boolean goBack() {
        LOGGER.info("goBack: entering");
        if (instance == null) {
            LOGGER.error("goBack: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /**
     * 执行确认/回车操作
     *
     * @return true 操作成功
     */
    @Override
    public boolean goEnter() {
        LOGGER.info("goEnter: entering");
        if (instance == null) {
            LOGGER.error("goEnter: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        AccessibilityNodeInfo focusedNode = findFocusedNode(getValidRootNode());
        if (focusedNode == null) {
            LOGGER.error("goEnter: cannot get focused node");
            return false;
        }
        // ACTION_IME_ENTER_ID = 16908372, 需要 Android 11 (API 30) 及以上
        // see https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction#ACTION_IME_ENTER
        if (android.os.Build.VERSION.SDK_INT >= ACTION_IME_ENTER_VERSION) {
            return focusedNode.performAction(ACTION_IME_ENTER_ID);
        }
        LOGGER.warn("goEnter: android system version is too old to support ACTION_IME_ENTER");
        return false;
    }

    /**
     * 自动执行回车操作
     * <p>查找当前有焦点的输入框并执行回车/确认操作</p>
     *
     * @return true 操作成功
     */
    @Override
    public boolean autoPerformEnter() {
        LOGGER.info("autoPerformEnter: entering");
        if (instance == null) {
            LOGGER.error("autoPerformEnter: AccessibilityManagerImpl instance is null, service not running");
            return false;
        }
        AccessibilityNodeInfo inputNode = findTextNode();
        if (inputNode == null || !inputNode.isEnabled() || !inputNode.isFocused()) {
            LOGGER.error("autoPerformEnter: text node not found or not focused");
            return false;
        }
        boolean isSuccess = false;
        if (Build.VERSION.SDK_INT >= ACTION_IME_ENTER_VERSION) {
            isSuccess = inputNode.performAction(ACTION_IME_ENTER_ID);
        } else {
            LOGGER.warn("autoPerformEnter: android system version is too old to support ACTION_IME_ENTER");
        }
        inputNode.recycle();
        if (!isSuccess) {
            return false;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(ENTER_FOCUS_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("autoPerformEnter: wait interrupted");
        }
        AccessibilityNodeInfo currentFocusedNode = findTextNode();
        if (currentFocusedNode != null) {
            try {
                if (currentFocusedNode.isFocused()) {
                    goBack();
                    return true;
                }
            } finally {
                currentFocusedNode.recycle();
            }
        }
        LOGGER.info("autoPerformEnter: result {}", isSuccess);
        return isSuccess;
    }

    /**
     * 查找文本节点
     * <p>优先查找已聚焦的可编辑节点，其次查找 EditText</p>
     *
     * @return AccessibilityNodeInfo 文本节点，未找到返回 null
     */
    private AccessibilityNodeInfo findTextNode() {
        // 首先查找已聚焦的可编辑节点
        AccessibilityNodeInfo textNode = findNodeInfo(getValidRootNode(),
                node -> node.isFocused() && node.isEditable());
        if (textNode != null) {
            return textNode;
        }
        // 兜底查找 EditText
        return findNodeInfo(getValidRootNode(), node -> {
            CharSequence className = node.getClassName();
            return className != null
                    && className.toString().equals("android.widget.EditText")
                    && node.isEditable()
                    && node.isEnabled();
        });
    }

    /**
     * 单次截图
     * <p>执行一次截图操作，不带重试和过滤逻辑</p>
     *
     * @return Bitmap 截图位图，失败返回 null
     */
    @Override
    public Bitmap takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            LOGGER.error("takeScreenshot: not supported, android version {}", Build.VERSION.SDK_INT);
            return null;
        }
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null || (info.getCapabilities() & CAPABILITY_CAN_TAKE_SCREENSHOT) == 0) {
            LOGGER.error("takeScreenshot: accessibility takeScreenshot is not enabled");
            return null;
        }
        LOGGER.info("takeScreenshot: starting, Android {}", Build.VERSION.SDK_INT);
        final Bitmap[] bitmap = new Bitmap[1];
        final boolean[] isSuccess = {false};
        final CountDownLatch latch = new CountDownLatch(1);
        takeScreenshot(Display.DEFAULT_DISPLAY, ThreadPoolManager.getInstance().getShareThreadPool(),
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        LOGGER.info("takeScreenshot: takeScreenshot success");
                        HardwareBuffer buffer = result.getHardwareBuffer();
                        bitmap[0] = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        isSuccess[0] = true;
                        buffer.close();
                        latch.countDown();
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        LOGGER.error("takeScreenshot: takeScreenshot failed, errorCode = {}", errorCode);
                        isSuccess[0] = false;
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.warn("takeScreenshot: latch interrupted");
        }
        if (isSuccess[0]) {
            return bitmap[0];
        }
        return null;
    }

    /**
     * 处理无障碍事件
     *
     * <p>订阅屏幕内容变更事件，当以下类型事件触发时，计数器自动+1：
     * <ul>
     *   <li>窗口状态变化 (TYPE_WINDOW_STATE_CHANGED)</li>
     *   <li>文本选择变化 (TYPE_VIEW_TEXT_SELECTION_CHANGED)</li>
     *   <li>Windows变化 (TYPE_WINDOWS_CHANGED)</li>
     * </ul>
     *
     * <p>事件会分发给所有注册的 {@link AccessibilityOperationHandler} 处理</p>
     *
     * @param event 无障碍事件
     */
    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        int eventType = event.getEventType();
        if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && pkg.length() > 0) {
                lastWindowPackageName = pkg.toString();
                lastWindowPackageAtMs = System.currentTimeMillis();
            }
        }
        if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            screenGeneratorNum.incrementAndGet();
        }
        // 分发事件给注册的处理器
        for (AccessibilityOperationHandler handler : handlers) {
            try {
                handler.onAccessibilityEvent(event);
            } catch (Exception e) {
                LOGGER.error("onAccessibilityEvent: handler exception, error = {}", e.getMessage());
            }
        }
    }

    /**
     * 注册无障碍事件处理器
     *
     * @param handler 无障碍事件处理器
     */
    @Override
    public void registerAccessibilityHandler(AccessibilityOperationHandler handler) {
        if (handler == null) {
            LOGGER.warn("registerAccessibilityHandler: handler is null");
        } else {
            handlers.add(handler);
            LOGGER.info("registerAccessibilityHandler: handler registered, total handlers = {}", handlers.size());
        }
    }

    /**
     * 注销无障碍事件处理器
     *
     * @param handler 无障碍事件处理器
     */
    @Override
    public void unregisterAccessibilityHandler(AccessibilityOperationHandler handler) {
        if (handler == null) {
            LOGGER.warn("unregisterAccessibilityHandler: handler is null");
        } else {
            handlers.remove(handler);
            LOGGER.info("unregisterAccessibilityHandler: handler unregistered, total handlers = {}", handlers.size());
        }
    }

    /**
     * 获取屏幕内容变更计数器
     *
     * <p>每当屏幕内容发生以下类型变化时，计数器自动+1：
     * <ul>
     *   <li>窗口状态变化 (TYPE_WINDOW_STATE_CHANGED)</li>
     *   <li>文本选择变化 (TYPE_VIEW_TEXT_SELECTION_CHANGED)</li>
     *   <li>Windows变化 (TYPE_WINDOWS_CHANGED)</li>
     * </ul>
     *
     * @return 当前变更次数
     */
    @Override
    public long getScreenGeneratorNum() {
        return screenGeneratorNum.get();
    }

    /**
     * 无障碍服务中断回调
     * <p>当系统中断无障碍服务时调用（如服务被禁用或系统资源不足）</p>
     */
    @Override
    public void onInterrupt() {
        LOGGER.info("AccessibilityManagerImpl service interrupted");
    }
}
