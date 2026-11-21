/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.session.impl;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务栈管理器
 *
 * <p>管理嵌套任务的入栈、出栈和层级关系。</p>
 * <p>支持 TaskID 格式：A → A_X → A_X_Y</p>
 */
public class TaskStackManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("TaskStackManager");
    private final String sessionId;
    private final List<String> taskStack;

    /**
     * 创建任务栈管理器
     *
     * @param sessionId 会话ID，用于日志记录
     */
    public TaskStackManager(String sessionId) {
        this.sessionId = sessionId;
        this.taskStack = new CopyOnWriteArrayList<>();
    }

    /**
     * 处理新任务入栈
     *
     * <p>根据任务ID层级关系自动管理任务栈：</p>
     * <ul>
     *   <li>顶层任务（无下划线）：清空栈，入栈</li>
     *   <li>子任务（有父任务在栈中）：入栈</li>
     *   <li>父任务不存在：记录警告</li>
     * </ul>
     *
     * @param taskId 任务ID
     */
    public void pushTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        int level = getTaskLevel(taskId);
        if (level == 0) {
            handleTopLevelTask(taskId);
        } else {
            handleChildTask(taskId, level);
        }
    }

    /**
     * 处理顶层任务入栈
     */
    private void handleTopLevelTask(String taskId) {
        if (!taskStack.isEmpty()) {
            LOGGER.warn("TaskStackManager: clearing task stack for new top-level task, sessionId={}, oldStack={}",
                    sessionId, taskStack);
            taskStack.clear();
        }
        taskStack.add(taskId);
        LOGGER.info("TaskStackManager: task pushed, sessionId={}, taskStack={}", sessionId, taskStack);
    }

    /**
     * 处理子任务入栈
     */
    private void handleChildTask(String taskId, int level) {
        if (taskStack.contains(taskId)) {
            LOGGER.warn("TaskStackManager: duplicate task push ignored, sessionId={}, taskId={}, stack={}",
                    sessionId, taskId, taskStack);
            return;
        }
        boolean parentFound = false;
        for (int i = taskStack.size() - 1; i >= 0; i--) {
            String stackTask = taskStack.get(i);
            if (isDirectChild(taskId, stackTask)) {
                parentFound = true;
                break;
            }
        }
        if (parentFound) {
            taskStack.add(taskId);
            LOGGER.info("TaskStackManager: task pushed, sessionId={}, taskStack={}", sessionId, taskStack);
        } else {
            LOGGER.warn("TaskStackManager: parent task not found, auto-filling, sessionId={}, taskId={}",
                    sessionId, taskId);
            autoFillParentTasks(taskId);
            taskStack.add(taskId);
            LOGGER.info("TaskStackManager: task pushed, sessionId={}, taskStack={}",
                    sessionId, taskStack);
        }
    }

    /**
     * 自动补充缺失的父任务
     *
     * <p>递归补充所有缺失的父任务直到顶层。</p>
     *
     * @param taskId 任务ID
     */
    private void autoFillParentTasks(String taskId) {
        String parentId = extractParentId(taskId);
        if (parentId == null) {
            return;
        }
        if (taskStack.contains(parentId)) {
            return;
        }
        autoFillParentTasks(parentId);
        taskStack.add(parentId);
        LOGGER.info("TaskStackManager: auto-filled parent task, sessionId={}, parentId={}",
                sessionId, parentId);
    }

    /**
     * 提取父任务ID
     *
     * <p>从子任务ID中提取直接父任务ID。</p>
     * <p>示例："A_1" → "A"，"A_1_2" → "A_1"</p>
     *
     * @param taskId 任务ID
     * @return 父任务ID，如果是顶层任务则返回null
     */
    private String extractParentId(String taskId) {
        int lastUnderscore = taskId.lastIndexOf('_');
        return lastUnderscore > 0 ? taskId.substring(0, lastUnderscore) : null;
    }

    /**
     * 任务结束出栈，移除该任务及其所有后代，并清理无后代的祖先
     *
     * @param taskId 结束的任务ID
     */
    public void popTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        int index = taskStack.indexOf(taskId);
        if (index < 0) {
            LOGGER.warn("TaskStackManager: task not found in stack, sessionId={}, taskId={}, stack={}",
                    sessionId, taskId, taskStack);
            return;
        }
        for (int i = taskStack.size() - 1; i >= index; i--) {
            taskStack.remove(i);
        }
        for (int i = taskStack.size() - 1; i >= 0; i--) {
            String ancestor = taskStack.get(i);
            boolean hasDescendants = false;
            for (String task : taskStack) {
                if (isDescendant(task, ancestor)) {
                    hasDescendants = true;
                    break;
                }
            }
            if (!hasDescendants) {
                taskStack.remove(i);
            } else {
                break;
            }
        }
        LOGGER.info("TaskStackManager: task popped, sessionId={}, taskStack={}", sessionId, taskStack);
    }

    /**
     * 获取当前任务栈（从顶层到当前层）
     *
     * @return 任务ID列表副本
     */
    public List<String> getTaskStack() {
        return new ArrayList<>(taskStack);
    }

    /**
     * 获取当前正在执行的任务ID（栈顶）
     *
     * @return 当前任务ID，null表示无任务
     */
    public String getCurrentTaskId() {
        return taskStack.isEmpty() ? null : taskStack.get(taskStack.size() - 1);
    }

    /**
     * 获取父任务ID（用于GUI任务消息）
     *
     * <p>逻辑：</p>
     * <ul>
     *   <li>栈深度>1：返回父任务ID（栈顶的上一级）</li>
     *   <li>栈深度=1：返回当前栈顶任务ID</li>
     *   <li>栈为空：返回null</li>
     * </ul>
     *
     * @return 父任务ID或当前任务ID，栈为空时返回null
     */
    public String getParentTaskId() {
        if (taskStack.isEmpty()) {
            return null;
        }
        if (taskStack.size() == 1) {
            return taskStack.get(0);
        }
        return taskStack.get(taskStack.size() - 2);
    }

    /**
     * 获取任务栈深度
     *
     * @return 0=无任务，1=只有顶层任务，以此类推
     */
    public int getStackDepth() {
        return taskStack.size();
    }

    /**
     * 清空任务栈
     */
    public void clear() {
        if (!taskStack.isEmpty()) {
            taskStack.clear();
            LOGGER.info("TaskStackManager: stack cleared, sessionId={}, taskStack={}", sessionId, taskStack);
        }
    }

    /**
     * 计算任务层级深度
     *
     * @param taskId 任务ID
     * @return 层级深度（0=顶层），-1表示无效
     */
    private int getTaskLevel(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return -1;
        }
        int count = 0;
        for (char c : taskId.toCharArray()) {
            if (c == '_') {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断是否为直接子任务
     *
     * @param childId 子任务ID
     * @param parentId 父任务ID
     * @return true 是直接子任务
     */
    private boolean isDirectChild(String childId, String parentId) {
        if (childId == null || parentId == null) {
            return false;
        }
        return childId.startsWith(parentId + "_")
                && getTaskLevel(childId) == getTaskLevel(parentId) + 1;
    }

    /**
     * 判断是否为后代任务
     *
     * @param taskId 任务ID
     * @param ancestorId 祖先任务ID
     * @return true 是后代任务
     */
    private boolean isDescendant(String taskId, String ancestorId) {
        if (taskId == null || ancestorId == null) {
            return false;
        }
        return taskId.startsWith(ancestorId + "_");
    }
}
