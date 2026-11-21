package com.example.testapp.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.example.testapp.model.Params;
import com.example.testapp.model.Target;
import com.example.testapp.model.TestStep;
import com.example.testapp.service.TestAccessibilityService;
import com.example.testapp.util.Logger;

import java.util.List;

/**
 * 命令执行器 - CommandExecutor
 *
 * 【核心功能】
 * 根据TestStep定义的操作类型，执行相应的UI自动化操作
 *
 * 【支持的操作类型】
 * - click: 点击操作（支持ID定位和文本定位）
 * - input: 输入操作（在输入框中输入文本）
 * - wait: 等待操作（暂停指定时间）
 * - launchApp: 启动应用
 * - screenshot: 截图（由Service层处理）
 * - log: 日志记录
 * - back: 返回操作
 * - home: Home键操作
 * - scroll: 滚动操作（上滑/下滑）
 * - scrollUntilClick: 滚动直到目标可见后点击
 * - swipe: 滑动操作（自定义坐标）
 * - broadcast: 向目标应用发送显式广播（语音助手文字注入）
 *
 * 【定位策略】
 * - id: 通过资源ID定位元素（如 "com.example.app:id/button"）
 * - text: 通过文本内容定位元素（部分匹配）
 */
public class CommandExecutor {

    private static final String TAG = "CommandExecutor";

    /** 应用上下文 */
    private Context context;

    /** 无障碍服务引用 */
    private TestAccessibilityService accessibilityService;

    /** 屏幕尺寸信息 */
    private DisplayMetrics displayMetrics;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public CommandExecutor(Context context) {
        this.context = context.getApplicationContext();

        // 初始化屏幕尺寸
        initDisplayMetrics();

        Logger.safeInfo(TAG, "CommandExecutor 初始化完成, 屏幕尺寸: " +
                displayMetrics.widthPixels + "x" + displayMetrics.heightPixels);
    }

    /**
     * 初始化屏幕尺寸信息
     * 用于坐标计算和手势操作
     */
    private void initDisplayMetrics() {
        displayMetrics = new DisplayMetrics();
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
            } else {
                // 默认尺寸
                displayMetrics.widthPixels = 1080;
                displayMetrics.heightPixels = 1920;
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "获取屏幕尺寸失败", e);
            displayMetrics.widthPixels = 1080;
            displayMetrics.heightPixels = 1920;
        }
    }

    /**
     * 设置无障碍服务引用
     * 在测试开始前必须调用此方法设置服务实例
     *
     * @param service 无障碍服务实例
     */
    public void setAccessibilityService(TestAccessibilityService service) {
        this.accessibilityService = service;
        Logger.safeInfo(TAG, "设置无障碍服务: " + (service != null ? "成功" : "null"));
    }

    /**
     * 执行测试步骤
     * 根据步骤的action类型分发到具体的执行方法
     *
     * @param step 测试步骤对象
     * @return true表示执行成功，false表示执行失败
     */
    public boolean execute(TestStep step) {
        // 参数校验
        if (step == null) {
            Logger.safeError(TAG, "step is null");
            return false;
        }

        String action = step.getAction();
        Logger.safeDebug(TAG, "执行动作: " + action);

        try {
            // 根据action类型分发执行
            switch (action) {
                case "click":
                    // 点击操作
                    return executeClick(step);

                case "input":
                    // 输入操作
                    return executeInput(step);

                case "wait":
                    // 等待操作
                    return executeWait(step);

                case "launchApp":
                    // 启动应用（由Service层处理）
                    return true;

                case "screenshot":
                    // 截图操作（由Service层处理）
                    return true;

                case "log":
                    // 日志记录
                    return true;

                case "back":
                    // 返回键操作
                    if (accessibilityService != null) {
                        return accessibilityService.performBack();
                    }
                    return false;

                case "home":
                    // Home键操作
                    if (accessibilityService != null) {
                        return accessibilityService.performHome();
                    }
                    return false;

                case "scroll":
                    // 滚动操作
                    return executeScroll(step);

                case "scrollUntilClick":
                    return executeScrollUntilClick(step);

                case "swipe":
                    // 滑动操作
                    return executeSwipe(step);

                case "broadcast":
                    return executeBroadcast(step);

                default:
                    Logger.safeWarn(TAG, "未知的动作类型: " + action);
                    return false;
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "执行步骤异常: " + action, e);
            return false;
        }
    }

    /**
     * 向其他应用发送显式广播。语音助手文字用例用它注入语料。
     */
    private boolean executeBroadcast(TestStep step) {
        Params params = step.getParams();
        if (params == null) {
            Logger.safeError(TAG, "broadcast: params is null");
            return false;
        }
        String action = params.getBroadcastAction();
        String text = params.getText() == null ? "" : params.getText().trim();
        if (action == null || action.trim().isEmpty() || text.isEmpty()) {
            Logger.safeError(TAG, "broadcast: missing action or text");
            return false;
        }
        Intent intent = new Intent(action.trim());
        intent.putExtra("text", text);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        String component = params.getComponent();
        if (component != null && component.contains("/")) {
            int slash = component.indexOf('/');
            String pkg = component.substring(0, slash);
            String cls = component.substring(slash + 1);
            if (cls.startsWith(".")) {
                cls = pkg + cls;
            }
            intent.setComponent(new ComponentName(pkg, cls));
        }
        context.sendBroadcast(intent);
        Logger.safeInfo(TAG, "broadcast sent action=" + action + " len=" + text.length());
        return true;
    }

    /**
     * 执行点击操作
     *
     * 点击流程：
     * 1. 根据target定位元素
     * 2. 调用无障碍服务的performClick方法
     *
     * @param step 测试步骤
     * @return true表示点击成功
     */
    private boolean executeClick(TestStep step) {
        Target target = step.getTarget();
        if (target == null) {
            Logger.safeError(TAG, "click: target is null");
            return false;
        }

        // 查找目标节点
        AccessibilityNodeInfo node = findNode(target);
        if (node == null) {
            Logger.safeWarn(TAG, "click: 未找到目标元素");
            return false;
        }

        // 执行点击
        if (accessibilityService != null) {
            return accessibilityService.performClick(node);
        }
        return false;
    }

    /**
     * 执行输入操作
     *
     * 输入流程：
     * 1. 根据target定位输入框元素
     * 2. 获取params中的text参数
     * 3. 调用无障碍服务的performInput方法
     *
     * @param step 测试步骤
     * @return true表示输入成功
     */
    private boolean executeInput(TestStep step) {
        Target target = step.getTarget();
        Params params = step.getParams();

        if (target == null || params == null) {
            Logger.safeError(TAG, "input: target or params is null");
            return false;
        }

        // 查找输入框节点
        AccessibilityNodeInfo node = findNode(target);
        if (node == null) {
            Logger.safeWarn(TAG, "input: 未找到目标元素");
            return false;
        }

        // 执行输入
        if (accessibilityService != null) {
            return accessibilityService.performInput(node, params.getText());
        }
        return false;
    }

    /**
     * 执行等待操作
     *
     * @param step 测试步骤
     * @return 始终返回true
     */
    private boolean executeWait(TestStep step) {
        Params params = step.getParams();
        if (params == null) {
            return false;
        }

        try {
            // 线程休眠
            Thread.sleep(params.getDuration());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 执行滚动操作
     *
     * 滚动方向说明：
     * - "up": 手指从下往上滑动，页面向下滚动（查看下方内容）
     * - "down": 手指从上往下滑动，页面向上滚动（查看上方内容）
     *
     * @param step 测试步骤
     * @return true表示滚动成功
     */
    private boolean executeScroll(TestStep step) {
        Params params = step.getParams();
        if (params == null) {
            Logger.safeError(TAG, "scroll: params is null");
            return false;
        }

        String direction = params.getDirection();
        if (direction == null) {
            Logger.safeError(TAG, "scroll: direction is null");
            return false;
        }

        return performScroll(direction);
    }

    /**
     * 先检测目标是否已在屏上可点；不可见则按方向滚动，直到可见后点击。
     */
    private boolean executeScrollUntilClick(TestStep step) {
        Target target = step.getTarget();
        if (target == null) {
            Logger.safeError(TAG, "scrollUntilClick: target is null");
            return false;
        }
        Params params = step.getParams();
        String direction = (params != null && params.getDirection() != null)
                ? params.getDirection() : "up";
        int maxScrolls = (params != null && params.getMaxScrolls() > 0)
                ? params.getMaxScrolls() : 6;
        long waitMs = (params != null && params.getDuration() > 0)
                ? params.getDuration() : 600;

        for (int attempt = 0; attempt <= maxScrolls; attempt++) {
            AccessibilityNodeInfo node = findNode(target);
            if (isNodeVisibleOnScreen(node)) {
                Logger.safeInfo(TAG, "scrollUntilClick: 目标已可见，直接点击 (attempt=" + attempt + ")");
                return accessibilityService != null && accessibilityService.performClick(node);
            }
            if (attempt >= maxScrolls) {
                Logger.safeWarn(TAG, "scrollUntilClick: 达到最大滚动次数仍未找到可点击目标");
                return false;
            }
            Logger.safeInfo(TAG, "scrollUntilClick: 目标不可见，滚动 " + direction
                    + " (" + (attempt + 1) + "/" + maxScrolls + ")");
            if (!performScroll(direction)) {
                return false;
            }
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean performScroll(String direction) {
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        int centerX = screenWidth / 2;
        int duration = 500;
        int startX, startY, endX, endY;

        switch (direction) {
            case "up":
                startX = endX = centerX;
                startY = screenHeight * 2 / 3;
                endY = screenHeight / 3;
                Logger.safeDebug(TAG, "scroll up: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");
                break;
            case "down":
                startX = endX = centerX;
                startY = screenHeight / 3;
                endY = screenHeight * 2 / 3;
                Logger.safeDebug(TAG, "scroll down: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ")");
                break;
            default:
                Logger.safeWarn(TAG, "scroll: 未知的方向 " + direction);
                return false;
        }

        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return accessibilityService.performSwipe(startX, startY, endX, endY, duration);
        }
        return executeShellSwipe(startX, startY, endX, endY, duration);
    }

    private boolean isNodeVisibleOnScreen(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) {
            return false;
        }
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (rect.isEmpty()) {
            return false;
        }
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        return rect.bottom > 0 && rect.top < screenHeight
                && rect.right > 0 && rect.left < screenWidth;
    }

    /**
     * 执行滑动操作（自定义坐标）
     *
     * @param step 测试步骤
     * @return true表示滑动成功
     */
    private boolean executeSwipe(TestStep step) {
        Params params = step.getParams();
        if (params == null) {
            Logger.safeError(TAG, "swipe: params is null");
            return false;
        }

        // 获取滑动参数
        int startX = params.getStartX();
        int startY = params.getStartY();
        int endX = params.getEndX();
        int endY = params.getEndY();
        int duration = params.getDuration();

        // 默认持续时间500ms
        if (duration <= 0) {
            duration = 500;
        }

        Logger.safeDebug(TAG, "swipe: (" + startX + "," + startY + ") -> (" + endX + "," + endY + ") duration=" + duration);

        // 使用无障碍服务执行滑动手势
        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return accessibilityService.performSwipe(startX, startY, endX, endY, duration);
        }

        // 备用方案：使用shell命令
        return executeShellSwipe(startX, startY, endX, endY, duration);
    }

    /**
     * 使用shell命令执行滑动（备用方案）
     * 需要系统权限或root权限
     *
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 持续时间
     * @return true表示执行成功
     */
    private boolean executeShellSwipe(int startX, int startY, int endX, int endY, int duration) {
        try {
            // 构建input swipe命令
            String cmd = "input swipe " + startX + " " + startY + " " + endX + " " + endY + " " + duration;
            Logger.safeDebug(TAG, "执行shell: " + cmd);

            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();

            return process.exitValue() == 0;
        } catch (Exception e) {
            Logger.safeError(TAG, "shell swipe失败", e);
            return false;
        }
    }

    /**
     * 查找UI元素节点
     *
     * 支持的定位策略：
     * - "id": 通过资源ID定位
     * - "text": 通过文本内容定位
     *
     * @param target 目标描述对象
     * @return 找到的节点，未找到返回null
     */
    private AccessibilityNodeInfo findNode(Target target) {
        if (accessibilityService == null) {
            Logger.safeError(TAG, "findNode: 无障碍服务未连接");
            return null;
        }

        String strategy = target.getStrategy();
        String value = target.getValue();

        Logger.safeDebug(TAG, "findNode: strategy=" + strategy + ", value=" + value);

        try {
            switch (strategy) {
                case "id":
                    // 通过ID定位
                    return accessibilityService.findNodeById(value);

                case "text":
                    // 通过文本定位
                    return accessibilityService.findNodeByText(value);

                default:
                    Logger.safeWarn(TAG, "未知的定位策略: " + strategy);
                    return null;
            }
        } catch (Exception e) {
            Logger.safeError(TAG, "findNode失败", e);
            return null;
        }
    }

    /**
     * 获取屏幕尺寸信息
     */
    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    /**
     * 获取屏幕宽度
     */
    public int getScreenWidth() {
        return displayMetrics.widthPixels;
    }

    /**
     * 获取屏幕高度
     */
    public int getScreenHeight() {
        return displayMetrics.heightPixels;
    }
}