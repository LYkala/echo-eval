/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.androidfunc.accessibility.animation;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

/**
 * 点击动画悬浮视图
 * <p>在屏幕上显示点击位置的视觉反馈动画（黄色水波纹效果）</p>
 */
public class ClickFloatViewAnimator extends View {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClickFloatViewAnimator");
    private static final String TAG = "ClickFloatViewAnimator";
    // 点击动画的画笔
    private final Paint mPaint;
    // 动画的圆心坐标 (模拟点击的坐标)
    private PointF mCenterPoint;
    // 动画当前半径/最大半径/当前透明度
    private float mCurrentRadius;
    private final float mMaxRadius;
    private long duration;
    private int mCurrentAlpha;
    // 系统窗口管理器 & 布局参数
    private final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mLayoutParams;
    private int mOffsetX = 0;
    private int mOffsetY = 0;

    /**
     * 显示点击动画（自动处理线程切换）
     *
     * @param context  上下文
     * @param x        点击X坐标
     * @param y        点击Y坐标
     * @param duration 动画持续时间（毫秒）
     */
    public static void showClickAnim(Context context, float x, float y, long duration) {
        LOGGER.info("showClickAnim: showing click animation at [{}, {}] for {}ms", x, y, duration);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new ClickFloatViewAnimator(context, x, y, duration).startClickAnimator();
        } else {
            new Handler(Looper.getMainLooper()).post(() -> {
                new ClickFloatViewAnimator(context, x, y, duration).startClickAnimator();
            });
        }
    }

    public ClickFloatViewAnimator(Context context, float x, float y, long duration) {
        super(context);
        this.mCenterPoint = new PointF(x, y);
        this.mMaxRadius = dp2px(context, 40);
        this.duration = duration;
        this.mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mLayoutParams = createLayoutParams();
        this.mPaint = initPaint();
        mCurrentRadius = 0f;
        mCurrentAlpha = 255;
    }

    /**
     * 启动点击动画
     */
    public void startClickAnimator() {
        LOGGER.info("startClickAnimator: starting click animation at [{}, {}]", mCenterPoint.x, mCenterPoint.y);
        mWindowManager.addView(this, mLayoutParams);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, mMaxRadius);
        animator.setDuration(this.duration);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            mCurrentRadius = (float) animation.getAnimatedValue();
            mCurrentAlpha = (int) (255 * (1 - animation.getAnimatedFraction()));
            invalidate();
        });
        animator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override public void onAnimationStart(android.animation.Animator animation) {}
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                LOGGER.info("onAnimationCancel: animation cancelled");
                removeFloatView();
            }
            @Override public void onAnimationRepeat(android.animation.Animator animation) {}
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                LOGGER.info("onAnimationEnd: animation ended");
                removeFloatView();
            }
        });
        animator.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int[] location = new int[2];
        getLocationOnScreen(location);
        mOffsetX = location[0];
        mOffsetY = location[1];
    }

    /**
     * 绘制圆形扩散动画
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setAlpha(mCurrentAlpha);
        canvas.drawCircle(mCenterPoint.x - mOffsetX, mCenterPoint.y - mOffsetY, mCurrentRadius, mPaint);
    }

    /**
     * 初始化悬浮窗布局参数
     */
    private WindowManager.LayoutParams createLayoutParams() {
        LOGGER.info("createLayoutParams: initializing layout params");
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        params.format = android.graphics.PixelFormat.RGBA_8888;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = android.view.Gravity.LEFT | android.view.Gravity.TOP;
        params.x = 0;
        params.y = 0;
        return params;
    }

    /**
     * 初始化画笔 - 圆形水波纹样式
     */
    private Paint initPaint() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.parseColor("#FFFFFF00"));
        paint.setAlpha(255);
        return paint;
    }

    /**
     * 移除悬浮窗
     */
    private void removeFloatView() {
        try {
            if (this.getParent() != null) {
                mWindowManager.removeViewImmediate(this);
            }
        } catch (Exception e) {
            LOGGER.error("removeFloatView: failed to remove view, error: {}", e.getMessage());
        }
    }

    /**
     * dp转px
     */
    private static float dp2px(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
