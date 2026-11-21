package com.example.testapp.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 截图工具类
 * 支持保存截图到应用私有目录和公共下载目录
 */
public class ScreenshotUtil {

    private static final String TAG = "ScreenshotUtil";
    /** 结果截图 JPEG 质量（0-100） */
    public static final int RESULT_JPEG_QUALITY = 80;
    /** 结果截图最大宽度，超过则等比缩小 */
    public static final int RESULT_MAX_WIDTH = 1080;

    private Context context;
    private SimpleDateFormat dateFormat;

    public ScreenshotUtil(Context context) {
        this.context = context.getApplicationContext();
        this.dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    }

    /**
     * 截图文件名：{captureMs}_{tag}.png，文件名前缀即拍摄时刻，便于服务端/页面按时间排序。
     */
    public static String buildCaptureFileName(String tag) {
        String safe = (tag == null || tag.isEmpty()) ? "shot" : tag.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return System.currentTimeMillis() + "_" + safe + ".png";
    }

    /**
     * 保存截图到应用私有目录（文件管理可见）
     */
    public File saveToPrivateStorage(Bitmap bitmap, String testName, String stepName) {
        try {
            // 创建截图目录
            File screenshotsDir = new File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "screenshots/" + (testName != null ? testName : "default")
            );

            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }

            String fileName = buildCaptureFileName(stepName);
            File imageFile = new File(screenshotsDir, fileName);

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Logger.safeInfo(TAG, "截图已保存: " + imageFile.getAbsolutePath());
            return imageFile;

        } catch (Exception e) {
            Logger.safeError(TAG, "保存截图失败", e);
            return null;
        }
    }

    /**
     * 保存截图到公共下载目录（用户可见）
     */
    public File saveToPublicStorage(Bitmap bitmap, String testName, String stepName) {
        try {
            // 创建截图目录
            File screenshotsDir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "TestApp/Screenshots/" + (testName != null ? testName : "default")
            );

            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs();
            }

            String fileName = buildCaptureFileName(stepName);
            File imageFile = new File(screenshotsDir, fileName);

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            // 通知媒体库扫描
            MediaScannerConnection.scanFile(context,
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{"image/png"},
                    (path, uri) -> Logger.safeDebug(TAG, "媒体库已扫描: " + path));

            Logger.safeInfo(TAG, "截图已保存到下载目录: " + imageFile.getAbsolutePath());
            return imageFile;

        } catch (Exception e) {
            Logger.safeError(TAG, "保存截图到公共目录失败", e);
            return null;
        }
    }

    /**
     * 获取截图保存目录
     */
    public File getScreenshotDir(String testName) {
        return new File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshots/" + (testName != null ? testName : "default")
        );
    }

    /**
     * 获取公共截图目录
     */
    public File getPublicScreenshotDir(String testName) {
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TestApp/Screenshots/" + (testName != null ? testName : "default")
        );
    }

    /**
     * 获取所有截图目录列表
     */
    public File[] getAllScreenshotDirs() {
        File baseDir = new File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshots"
        );

        if (baseDir.exists() && baseDir.isDirectory()) {
            return baseDir.listFiles(File::isDirectory);
        }
        return new File[0];
    }

    /**
     * 获取目录中的所有截图
     */
    public File[] getScreenshotsInDir(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            return dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            });
        }
        return new File[0];
    }

    /**
     * 压缩单张结果截图：等比缩放到最大宽度内，并转为 JPEG。
     *
     * @return 压缩后的文件；失败时返回原文件
     */
    public static File compressResultImage(File sourceFile) {
        return compressResultImage(sourceFile, RESULT_MAX_WIDTH, RESULT_JPEG_QUALITY);
    }

    public static File compressResultImage(File sourceFile, int maxWidth, int jpegQuality) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            return sourceFile;
        }
        if (maxWidth <= 0) {
            maxWidth = RESULT_MAX_WIDTH;
        }
        if (jpegQuality <= 0 || jpegQuality > 100) {
            jpegQuality = RESULT_JPEG_QUALITY;
        }

        Bitmap bitmap = null;
        Bitmap scaled = null;
        try {
            bitmap = decodeScaledBitmap(sourceFile, maxWidth);
            if (bitmap == null) {
                return sourceFile;
            }

            scaled = scaleDownIfNeeded(bitmap, maxWidth);
            if (scaled != bitmap) {
                bitmap.recycle();
                bitmap = null;
            }

            String baseName = sourceFile.getName().replaceAll("(?i)\\.(png|jpe?g)$", "");
            File outputFile = new File(sourceFile.getParentFile(), baseName + ".jpg");

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos);
                fos.flush();
            }

            if (!sourceFile.equals(outputFile) && sourceFile.exists() && !sourceFile.delete()) {
                Logger.safeWarn(TAG, "原图删除失败: " + sourceFile.getName());
            }

            Logger.safeInfo(TAG, "截图已压缩: " + outputFile.getName()
                    + ", " + (outputFile.length() / 1024) + "KB"
                    + " (maxW=" + maxWidth + " q=" + jpegQuality + ")");
            return outputFile;
        } catch (Exception e) {
            Logger.safeWarn(TAG, "截图压缩失败，使用原图: " + e.getMessage());
            return sourceFile;
        } finally {
            if (scaled != null) {
                scaled.recycle();
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    /** 大图先 inSampleSize 解码，避免 1.xMB PNG 全尺寸 decode 堵死线程 */
    private static Bitmap decodeScaledBitmap(File sourceFile, int targetMaxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, targetMaxWidth);
        return BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), decodeOpts);
    }

    private static int computeInSampleSize(int width, int height, int targetMaxWidth) {
        int sample = 1;
        int maxDim = Math.max(width, height);
        while (maxDim / sample > targetMaxWidth * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static Bitmap scaleDownIfNeeded(Bitmap bitmap, int maxWidth) {
        if (bitmap.getWidth() <= maxWidth) {
            return bitmap;
        }
        float ratio = (float) maxWidth / bitmap.getWidth();
        int newHeight = Math.round(bitmap.getHeight() * ratio);
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true);
    }
}