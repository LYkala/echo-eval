/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.helper;

/**
 * 裁剪位置参数
 *
 * <p>定义图片裁剪的中心点和四个方向的裁剪百分比。</p>
 * <p>用于 SSIM 相似度计算前的 ROI 区域裁剪。</p>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 在点击位置(500, 800)周围裁剪 5x100 像素区域
 * CropPosition position = new CropPosition(500, 800, 5, 100);
 * </pre>
 * </p>
 *
 * @author AI Assistant
 * @version 1.0
 * @since 2026-04-03
 */
public class CropPosition {

    /** 中心点X坐标（像素） */
    private int x;

    /** 中心点Y坐标（像素） */
    private int y;

    /** 左裁剪百分比（相对于图片宽度），默认100表示不裁剪 */
    private int leftCropPerc;

    /** 右裁剪百分比（相对于图片宽度），默认100表示不裁剪 */
    private int rightCropPerc;

    /** 上裁剪百分比（相对于图片高度），默认100表示不裁剪 */
    private int upCropPerc;

    /** 下裁剪百分比（相对于图片高度），默认100表示不裁剪 */
    private int downCropPerc;

    /**
     * 默认构造方法
     */
    public CropPosition() {
        this.x = 0;
        this.y = 0;
        this.leftCropPerc = 100;
        this.rightCropPerc = 100;
        this.upCropPerc = 100;
        this.downCropPerc = 100;
    }

    /**
     * 构造方法（对称裁剪）
     *
     * <p>以(x, y)为中心点，在垂直方向裁剪 cropPercCol%，
     * 在水平方向裁剪 cropPercRow%</p>
     *
     * @param x 中心点X坐标
     * @param y 中心点Y坐标
     * @param cropPercCol 垂直方向裁剪百分比（上下）
     * @param cropPercRow 水平方向裁剪百分比（左右）
     */
    public CropPosition(int x, int y, int cropPercCol, int cropPercRow) {
        this.x = x;
        this.y = y;
        this.leftCropPerc = cropPercRow;
        this.rightCropPerc = cropPercRow;
        this.upCropPerc = cropPercCol;
        this.downCropPerc = cropPercCol;
    }

    /**
     * 获取中心点X坐标
     *
     * @return X坐标（像素）
     */
    public int getX() {
        return x;
    }

    /**
     * 设置中心点X坐标
     *
     * @param x X坐标（像素）
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * 获取中心点Y坐标
     *
     * @return Y坐标（像素）
     */
    public int getY() {
        return y;
    }

    /**
     * 设置中心点Y坐标
     *
     * @param y Y坐标（像素）
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * 获取左裁剪百分比
     *
     * @return 左裁剪百分比
     */
    public int getLeftCropPerc() {
        return leftCropPerc;
    }

    /**
     * 设置左裁剪百分比
     *
     * @param leftCropPerc 左裁剪百分比
     */
    public void setLeftCropPerc(int leftCropPerc) {
        this.leftCropPerc = leftCropPerc;
    }

    /**
     * 获取右裁剪百分比
     *
     * @return 右裁剪百分比
     */
    public int getRightCropPerc() {
        return rightCropPerc;
    }

    /**
     * 设置右裁剪百分比
     *
     * @param rightCropPerc 右裁剪百分比
     */
    public void setRightCropPerc(int rightCropPerc) {
        this.rightCropPerc = rightCropPerc;
    }

    /**
     * 获取上裁剪百分比
     *
     * @return 上裁剪百分比
     */
    public int getUpCropPerc() {
        return upCropPerc;
    }

    /**
     * 设置上裁剪百分比
     *
     * @param upCropPerc 上裁剪百分比
     */
    public void setUpCropPerc(int upCropPerc) {
        this.upCropPerc = upCropPerc;
    }

    /**
     * 获取下裁剪百分比
     *
     * @return 下裁剪百分比
     */
    public int getDownCropPerc() {
        return downCropPerc;
    }

    /**
     * 设置下裁剪百分比
     *
     * @param downCropPerc 下裁剪百分比
     */
    public void setDownCropPerc(int downCropPerc) {
        this.downCropPerc = downCropPerc;
    }

    @Override
    public String toString() {
        return "CropPosition{x=" + x + ", y=" + y
            + ", left=" + leftCropPerc + "%, right=" + rightCropPerc
            + "%, up=" + upCropPerc + "%, down=" + downCropPerc + "%}";
    }
}
