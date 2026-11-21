/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.helper;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.huawei.aisfsdkv2.api.fileserver.FileUploadCallback;
import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.facade.impl.ACMFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.util.ImageUtil;
import com.huawei.aisfsdkv2.util.ThreadUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 图像处理辅助类
 *
 * <p>提供与业务相关的图像处理功能，如SSIM对比、截图上传等。</p>
 * <p><b>注意：</b>通用图像处理工具方法请使用 {@link com.huawei.aisfsdkv2.util.ImageUtil}</p>
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>SSIM对比：isActionRoiStableDynamic(Bitmap, Bitmap, float, CropPosition)</li>
 *   <li>上传截图：uploadScreenshot(byte[], String)</li>
 *   <li>JPEG压缩：通过 {@link ImageUtil#compressToJpeg(Bitmap)}</li>
 *   <li>屏幕稳定等待：waitForScreenStable(AccessibilityManager, int)</li>
 * </ul>
 * </p>
 *
 * @author AI Assistant
 * @version 1.0
 * @since 2026-04-03
 */
public class ImageHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("ImageHelper");

    /** 上传重试次数 */
    public static final int UPLOAD_RETRY_TIMES = 3;

    /** 上传重试间隔（毫秒） */
    public static final long UPLOAD_RETRY_DELAY_MS = 500;

    /** 屏幕稳定检查间隔（毫秒） */
    private static final int STABLE_CHECK_INTERVAL_MS = 100;

    /** 屏幕稳定阈值（连续相同次数） */
    private static final int STABLE_THRESHOLD = 10;

    /** SSIM计算常量K1 */
    private static final double K1 = 0.01;

    /** SSIM计算常量K2 */
    private static final double K2 = 0.03;

    /** 图像动态范围 */
    private static final double L = 255;

    /**
     * 将Bitmap压缩为JPEG字节数组
     *
     * <p>委托给 {@link ImageUtil#compressToJpeg(Bitmap)}</p>
     *
     * @param bitmap 原始Bitmap
     * @return JPEG字节数组，失败返回null
     */
    public static byte[] compressToJpeg(Bitmap bitmap) {
        return ImageUtil.compressToJpeg(bitmap);
    }

    /**
     * 判断操作区域是否稳定（ROI裁剪后SSIM对比）
     *
     * <p>将两张图片转为灰度图，按指定ROI区域裁剪后，计算SSIM相似度。</p>
     * <p>如果相似度大于等于阈值，则认为两张图片相似（区域稳定）。</p>
     *
     * @param bitmap1 图片1（不会被修改）
     * @param bitmap2 图片2（不会被修改）
     * @param ssimThreshold SSIM相似度阈值（如0.8f）
     * @param cropPosition 裁剪位置和范围
     * @return 相似度是否超过阈值，异常时默认返回true（认为稳定）
     */
    public static boolean isActionRoiStableDynamic(Bitmap bitmap1, Bitmap bitmap2,
                                                   float ssimThreshold,
                                                   CropPosition cropPosition) {
        if (bitmap1 == null || bitmap2 == null || bitmap1.isRecycled() || bitmap2.isRecycled()) {
            LOGGER.warn("isActionRoiStableDynamic: input bitmap is null or recycled, return stable");
            return true;
        }
        if (cropPosition == null) {
            LOGGER.warn("isActionRoiStableDynamic: cropPosition is null, return stable");
            return true;
        }
        Bitmap gray1 = null;
        Bitmap gray2 = null;
        Bitmap roi1 = null;
        Bitmap roi2 = null;
        try {
            gray1 = ImageUtil.toGrayscale(bitmap1);
            gray2 = ImageUtil.toGrayscale(bitmap2);
            if (gray1 == null || gray2 == null) {
                LOGGER.warn("isActionRoiStableDynamic: failed to convert to grayscale, return stable");
                return true;
            }
            roi1 = cropBitmap(gray1, cropPosition);
            roi2 = cropBitmap(gray2, cropPosition);
            if (roi1 == null || roi2 == null) {
                LOGGER.warn("isActionRoiStableDynamic: failed to crop ROI, return stable");
                return true;
            }
            int roiWidth = roi1.getWidth();
            int roiHeight = roi1.getHeight();
            if (roiHeight < 7 || roiWidth < 7) {
                LOGGER.warn("isActionRoiStableDynamic: ROI size too small [{}x{}], return stable", roiWidth, roiHeight);
                return true;
            }
            double score = calculateSSIM(roi1, roi2);
            LOGGER.info("isActionRoiStableDynamic: SSIM score={}, threshold={}", score, ssimThreshold);
            return score >= ssimThreshold;
        } catch (Exception e) {
            LOGGER.error("isActionRoiStableDynamic: exception during comparison, error={}", e.getMessage());
            return true;
        } finally {
            if (gray1 != null && gray1 != bitmap1) {
                gray1.recycle();
            }
            if (gray2 != null && gray2 != bitmap2) {
                gray2.recycle();
            }
            if (roi1 != null && roi1 != gray1) {
                roi1.recycle();
            }
            if (roi2 != null && roi2 != gray2) {
                roi2.recycle();
            }
        }
    }

    /**
     * 裁剪图片
     *
     * @param bitmap 原始图
     * @param cropPosition 裁剪位置
     * @return 裁剪后图片，失败返回null
     */
    private static Bitmap cropBitmap(Bitmap bitmap, CropPosition cropPosition) {
        if (bitmap == null || cropPosition == null) {
            return null;
        }
        try {
            int originalWidth = bitmap.getWidth();
            int originalHeight = bitmap.getHeight();
            int left = (int) (cropPosition.getX() - (cropPosition.getLeftCropPerc() * originalWidth / 100.0));
            int right = (int) (cropPosition.getX() + (cropPosition.getRightCropPerc() * originalWidth / 100.0));
            int top = (int) (cropPosition.getY() - (cropPosition.getUpCropPerc() * originalHeight / 100.0));
            int bottom = (int) (cropPosition.getY() + (cropPosition.getDownCropPerc() * originalHeight / 100.0));
            left = Math.max(0, left);
            right = Math.min(originalWidth, right);
            top = Math.max(0, top);
            bottom = Math.min(originalHeight, bottom);
            if (left >= right || top >= bottom) {
                LOGGER.warn("cropBitmap: invalid crop area, left={}, right={}, top={}, bottom={}", left, right, top, bottom);
                return null;
            }
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        } catch (Exception e) {
            LOGGER.error("cropBitmap: failed to crop, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算两张图片的SSIM相似度
     *
     * @param img1 图片1
     * @param img2 图片2
     * @return SSIM相似度（0.0~1.0），值越大越相似
     * @throws IllegalArgumentException 图片尺寸不匹配时抛出
     */
    private static double calculateSSIM(Bitmap img1, Bitmap img2) throws IllegalArgumentException {
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            throw new IllegalArgumentException("Images must have the same dimensions");
        }
        int width = img1.getWidth();
        int height = img1.getHeight();
        double c1 = Math.pow((K1 * L), 2);
        double c2 = Math.pow((K2 * L), 2);
        double meanX = 0;
        double meanY = 0;
        double varianceX = 0;
        double varianceY = 0;
        double covarianceXY = 0;
        int[] pixels1 = new int[width * height];
        int[] pixels2 = new int[width * height];
        img1.getPixels(pixels1, 0, width, 0, 0, width, height);
        img2.getPixels(pixels2, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels1.length; i++) {
            int gray1 = rgbToGray(pixels1[i]);
            int gray2 = rgbToGray(pixels2[i]);
            meanX += gray1;
            meanY += gray2;
        }
        meanX /= (width * height);
        meanY /= (width * height);
        for (int i = 0; i < pixels1.length; i++) {
            int gray1 = rgbToGray(pixels1[i]);
            int gray2 = rgbToGray(pixels2[i]);
            varianceX += Math.pow(gray1 - meanX, 2);
            varianceY += Math.pow(gray2 - meanY, 2);
            covarianceXY += (gray1 - meanX) * (gray2 - meanY);
        }
        varianceX /= (width * height);
        varianceY /= (width * height);
        covarianceXY /= (width * height);
        double numerator = (2 * meanX * meanY + c1) * (2 * covarianceXY + c2);
        double denominator = (meanX * meanX + meanY * meanY + c1) * (varianceX + varianceY + c2);
        return numerator / denominator;
    }

    /**
     * RGB颜色转灰度值
     *
     * @param pixel RGB像素值
     * @return 灰度值（0~255）
     */
    private static int rgbToGray(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * 上传截图到ACM服务器
     *
     * @param imageData 图片字节数据
     * @param taskId 任务ID，用于生成文件名
     * @return 文件URL，失败返回null
     */
    public static String uploadScreenshot(byte[] imageData, String taskId) {
        String fileName = generateScreenshotFileName(taskId);
        LOGGER.info("ImageHelper: uploading screenshot, fileName={}", fileName);
        for (int i = 0; i < UPLOAD_RETRY_TIMES; i++) {
            try {
                UploadResult result = ACMFacadeImpl.getInstance().uploadImageFile(fileName, imageData);
                if (result != null && result.getFileUuid() != null) {
                    LOGGER.info("ImageHelper: upload success on attempt {}", i + 1);
                    return result.getFileUuid();
                }
                LOGGER.warn("ImageHelper: upload returned null on attempt {}", i + 1);
            } catch (Exception e) {
                LOGGER.warn("ImageHelper: upload failed on attempt {}, error={}", i + 1, e.getMessage());
            }
            if (i < UPLOAD_RETRY_TIMES - 1) {
                ThreadUtil.sleepNoExcept(UPLOAD_RETRY_DELAY_MS);
            }
        }
        return null;
    }

    /**
     * 异步上传截图到ACM服务器
     *
     * <p>使用异步HTTP请求，不阻塞调用线程，结果通过回调通知。</p>
     * <p>支持重试机制，重试次数为 UPLOAD_RETRY_TIMES。</p>
     *
     * @param imageData 图片字节数据
     * @param taskId 任务ID，用于生成文件名
     * @param callback 上传结果回调
     */
    public static void uploadScreenshotAsync(byte[] imageData, String taskId, UploadCallback callback) {
        if (imageData == null || imageData.length == 0) {
            LOGGER.error("ImageHelper.uploadScreenshotAsync: imageData is null or empty");
            if (callback != null) {
                callback.onFailure("Image data is null or empty");
            }
            return;
        }
        if (callback == null) {
            LOGGER.error("ImageHelper.uploadScreenshotAsync: callback is null");
            return;
        }

        String fileName = generateScreenshotFileName(taskId);
        LOGGER.info("ImageHelper.uploadScreenshotAsync: start async upload, fileName={}", fileName);

        // 使用异步上传（内部已使用线程池）
        uploadWithRetryAsync(fileName, imageData, 0, callback);
    }

    /**
     * 异步上传（带重试机制）
     *
     * @param fileName 文件名
     * @param imageData 图片数据
     * @param retryCount 当前重试次数
     * @param callback 回调
     */
    private static void uploadWithRetryAsync(String fileName, byte[] imageData, int retryCount,
            UploadCallback callback) {
        ACMFacadeImpl.getInstance().uploadImageFileAsync(fileName, imageData, new FileUploadCallback() {
            @Override
            public void onSuccess(String uploadedFileName, String fileUuid) {
                LOGGER.info("ImageHelper.uploadWithRetryAsync: upload success on attempt {}, fileUuid={}",
                        retryCount + 1, fileUuid);
                callback.onSuccess(fileUuid);
            }

            @Override
            public void onFailure(String uploadedFileName, int errorCode, String errorMsg) {
                LOGGER.warn("ImageHelper.uploadWithRetryAsync: upload failed on attempt {}, code={}, msg={}",
                        retryCount + 1, errorCode, errorMsg);

                // 重试逻辑
                if (retryCount < UPLOAD_RETRY_TIMES - 1) {
                    LOGGER.info("ImageHelper.uploadWithRetryAsync: retrying after {}ms, attempt {}",
                            UPLOAD_RETRY_DELAY_MS, retryCount + 2);
                    ThreadUtil.sleepNoExcept(UPLOAD_RETRY_DELAY_MS);
                    uploadWithRetryAsync(fileName, imageData, retryCount + 1, callback);
                } else {
                    LOGGER.error("ImageHelper.uploadWithRetryAsync: all {} attempts failed", UPLOAD_RETRY_TIMES);
                    callback.onFailure("Upload failed after " + UPLOAD_RETRY_TIMES + " retries: " + errorMsg);
                }
            }
        });
    }

    /**
     * 上传回调接口
     */
    public interface UploadCallback {
        /**
         * 上传成功回调
         *
         * @param fileUrl 文件URL（UUID）
         */
        void onSuccess(String fileUrl);

        /**
         * 上传失败回调
         *
         * @param error 错误信息
         */
        void onFailure(String error);
    }

    /**
     * 生成截图文件名
     *
     * @param taskId 任务ID
     * @return 文件名
     */
    private static String generateScreenshotFileName(String taskId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault());
        String timeStamp = sdf.format(new Date());
        String safeTaskId = (taskId != null && !taskId.isEmpty()) ? taskId : "unknown";
        return "screenshot_" + safeTaskId + "_" + timeStamp + ".jpg";
    }

    /**
     * 等待屏幕稳定
     *
     * <p>通过检查屏幕生成器编号的变化来判断屏幕是否稳定。
     * 连续 STABLE_THRESHOLD 次检查无变化则认为屏幕已稳定。</p>
     *
     * @param accessibilityManager 无障碍服务管理器
     * @param maxWaitTimeSec 最大等待时间（秒）
     */
    public static void waitForScreenStable(AccessibilityManager accessibilityManager, int maxWaitTimeSec) {
        LOGGER.info("waitForScreenStable: waiting for screen stable, maxWaitTime={}s", maxWaitTimeSec);
        long lastGen = accessibilityManager.getScreenGeneratorNum();
        int stableCount = 0;
        int maxRetries = maxWaitTimeSec * 1000 / STABLE_CHECK_INTERVAL_MS;
        for (int i = 0; i < maxRetries; i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(STABLE_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                LOGGER.warn("waitForScreenStable: sleep interrupted");
                break;
            }
            long currentGen = accessibilityManager.getScreenGeneratorNum();
            if (currentGen == lastGen) {
                stableCount++;
                if (stableCount >= STABLE_THRESHOLD) {
                    LOGGER.info("waitForScreenStable: screen is stable after {} checks", stableCount);
                    break;
                }
            } else {
                lastGen = currentGen;
                stableCount = 1;
            }
        }
    }
}