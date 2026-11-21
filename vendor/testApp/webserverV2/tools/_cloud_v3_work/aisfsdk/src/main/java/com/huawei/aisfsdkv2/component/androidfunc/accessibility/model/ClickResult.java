/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.model;

import java.util.List;

/**
 * 点击操作结果
 *
 * <p>封装点击操作的执行结果，包含总体执行状态和是否被拦截的信息。</p>
 *
 * <p><b>结果状态组合：</b>
 * <ul>
 *   <li>success=true, intercepted=false：点击成功执行</li>
 *   <li>success=false, intercepted=true：被黑名单拦截</li>
 *   <li>success=false, intercepted=false：执行失败（如服务未启动、手势取消等）</li>
 * </ul>
 * </p>
 *
 * @since 2026-04-18
 */
public class ClickResult {

    /**
     * 总体执行结果
     * <p>true 表示点击成功执行（未被拦截且手势执行成功）</p>
     */
    private final boolean success;

    /**
     * 是否被黑名单拦截
     * <p>true 表示目标控件内容命中黑名单，点击被拦截取消</p>
     */
    private final boolean intercepted;

    /**
     * 黑名单匹配关键词
     * <p>true 黑名单匹配关键词 </p>
     */
    private List<String> matchedKeywords;

    /**
     * 构造点击结果
     *
     * @param success 总体执行结果
     * @param intercepted 是否被黑名单拦截
     */
    public ClickResult(boolean success, boolean intercepted) {
        this.success = success;
        this.intercepted = intercepted;
    }

    public ClickResult(boolean success, boolean intercepted, List<String> matchedKeywords) {
        this.success = success;
        this.intercepted = intercepted;
        this.matchedKeywords = matchedKeywords;
    }

    /**
     * 获取总体执行结果
     *
     * @return true 表示点击成功执行
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取是否被黑名单拦截
     *
     * @return true 表示被黑名单拦截
     */
    public boolean isIntercepted() {
        return intercepted;
    }

    /**
     * 获取黑名单匹配结果
     *
     * @return List 黑名单匹配关键词数组
     */
    public List<String> getMatchedKeywords() {
        return matchedKeywords;
    }

    @Override
    public String toString() {
        return "ClickResult{success=" + success + ", intercepted=" + intercepted + "}";
    }
}
