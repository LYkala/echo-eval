/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.ByteArrayOutputStream;

/**
 * 图片工具类
 * <p>提供图片处理相关的通用工具方法，与业务无关</p>
 */
public final class ImageUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger("ImageUtil");

    /** 颜色偏差阈值 */
    private static final int COLOR_DEVIATION_THRESHOLD = 10;

    /** 压缩图片目标尺寸 */
    private static final int TARGET_SMALL_SIZE = 32;

    /** JPEG压缩质量 */
    public static final int JPEG_QUALITY = 60;

    private ImageUtil() {
        // 私有构造函数，防止实例化
    }

    /**
     * 将Bitmap压缩为JPEG字节数组
     *
     * <p>通用工具方法，与业务无关</p>
     *
     * @param bitmap 原始Bitmap
     * @return JPEG字节数组，失败返回null
     */
    public static byte[] compressToJpeg(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.error("compressToJpeg: failed to compress bitmap, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 将图片转换为灰度图
     *
     * <p>通用工具方法，与业务无关</p>
     *
     * @param src 原始图片
     * @return 灰度图，失败返回null
     */
    public static Bitmap toGrayscale(Bitmap src) {
        if (src == null) {
            return null;
        }

        try {
            // 兼容性处理：判断是否是硬件Bitmap
            Bitmap workingBitmap = src;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && src.getConfig() == Bitmap.Config.HARDWARE) {
                workingBitmap = src.copy(Bitmap.Config.ARGB_8888, false);
                if (workingBitmap == null) {
                    LOGGER.error("toGrayscale: failed to convert hardware bitmap to software");
                    return null;
                }
            }

            Bitmap gray = Bitmap.createBitmap(workingBitmap.getWidth(), workingBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(gray);
            Paint paint = new Paint();
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0); // 设置饱和度为0，变成灰度图
            ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
            paint.setColorFilter(f);
            canvas.drawBitmap(workingBitmap, 0, 0, paint);

            // 如果创建了临时副本，需要回收
            if (workingBitmap != src) {
                workingBitmap.recycle();
            }

            return gray;
        } catch (Exception e) {
            LOGGER.error("toGrayscale: failed to convert, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 判断图片是否为 null 或纯色图片
     *
     * @param bitmap 位图
     * @return true 是 null 或纯色图片
     */
    public static boolean isNullOrPureImage(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        boolean result = isSolidColorImage(bitmap);
        LOGGER.info("isNullOrPureImage: isSolidColorImage = {}", result);
        return result;
    }

    /**
     * 判断 Bitmap 是否为纯色图片
     *
     * @param sourceBitmap 待判断的图片 Bitmap
     * @return true：纯色图片；false：非纯色图片
     */
    public static boolean isSolidColorImage(Bitmap sourceBitmap) {
        if (sourceBitmap == null || sourceBitmap.isRecycled()
                || sourceBitmap.getWidth() <= 0 || sourceBitmap.getHeight() <= 0) {
            LOGGER.error("isSolidColorImage: Illegal image input");
            return true;
        }

        // 快速压缩图片
        Bitmap smallBitmap = compressBitmapToSmallSize(sourceBitmap);
        if (smallBitmap == null) {
            LOGGER.error("isSolidColorImage: failed to compress bitmap");
            return true;
        }

        // 批量获取所有像素
        int width = smallBitmap.getWidth();
        int height = smallBitmap.getHeight();
        int[] pixels = new int[width * height];
        smallBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        // 取第一个像素作为基准颜色
        if (pixels.length == 0) {
            LOGGER.error("isSolidColorImage: image pixels is empty");
            recycleBitmap(smallBitmap, sourceBitmap);
            return true;
        }
        int basePixel = pixels[0];
        // 提取基准像素的 RGB 分量
        int baseRed = (basePixel >> 16) & 0xFF;
        int baseGreen = (basePixel >> 8) & 0xFF;
        int baseBlue = basePixel & 0xFF;

        // 遍历所有像素，与基准颜色对比
        for (int pixel : pixels) {
            // 提取当前像素的 RGB 分量
            int currentRed = (pixel >> 16) & 0xFF;
            int currentGreen = (pixel >> 8) & 0xFF;
            int currentBlue = pixel & 0xFF;

            // 计算 RGB 三个分量的偏差绝对值
            int redDeviation = Math.abs(currentRed - baseRed);
            int greenDeviation = Math.abs(currentGreen - baseGreen);
            int blueDeviation = Math.abs(currentBlue - baseBlue);

            // 超出颜色偏差阈值，判定为非纯色
            if (redDeviation > COLOR_DEVIATION_THRESHOLD
                    || greenDeviation > COLOR_DEVIATION_THRESHOLD
                    || blueDeviation > COLOR_DEVIATION_THRESHOLD) {
                recycleBitmap(smallBitmap, sourceBitmap);
                LOGGER.debug("isSolidColorImage: image is not solid color image");
                return false;
            }
        }

        recycleBitmap(smallBitmap, sourceBitmap);
        LOGGER.info("isSolidColorImage: image is solid color image");
        return true;
    }

    /**
     * 释放临时 Bitmap 内存
     *
     * @param smallBitmap   临时 Bitmap
     * @param sourceBitmap  原始 Bitmap
     */
    private static void recycleBitmap(Bitmap smallBitmap, Bitmap sourceBitmap) {
        if (smallBitmap != null && smallBitmap != sourceBitmap && !smallBitmap.isRecycled()) {
            smallBitmap.recycle();
        }
    }

    /**
     * 将 Bitmap 压缩到指定小尺寸
     *
     * @param sourceBitmap 源 Bitmap
     * @return 压缩后的小尺寸 Bitmap（RGB_565 格式）
     */
    private static Bitmap compressBitmapToSmallSize(Bitmap sourceBitmap) {
        int srcWidth = sourceBitmap.getWidth();
        int srcHeight = sourceBitmap.getHeight();

        // 计算压缩比例（保持宽高比，最大边不超过 TARGET_SMALL_SIZE）
        float scale = Math.min((float) TARGET_SMALL_SIZE / srcWidth, (float) TARGET_SMALL_SIZE / srcHeight);
        if (scale >= 1.0f) {
            return sourceBitmap.copy(Bitmap.Config.RGB_565, false);
        }

        // 矩阵缩放
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        try {
            // 创建压缩图片并转换为 RGB_565 格式
            Bitmap compressedBitmap = Bitmap.createBitmap(
                    sourceBitmap, 0, 0, srcWidth, srcHeight, matrix, false
            );
            return compressedBitmap.copy(Bitmap.Config.RGB_565, false);
        } catch (OutOfMemoryError | IllegalArgumentException e) {
            LOGGER.error("compressBitmapToSmallSize: failed to compress bitmap, error: {}", e.getMessage());
            return null;
        }
    }
}
