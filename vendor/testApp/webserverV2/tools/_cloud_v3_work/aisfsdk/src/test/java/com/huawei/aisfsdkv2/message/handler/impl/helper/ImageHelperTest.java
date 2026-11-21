/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.message.handler.impl.helper;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.huawei.aisfsdkv2.component.androidfunc.accessibility.AccessibilityManager;
import com.huawei.aisfsdkv2.component.facade.impl.ACMFacadeImpl;
import com.huawei.aisfsdkv2.component.facade.model.acm.AcmException;
import com.huawei.aisfsdkv2.component.facade.model.acm.UploadResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * ImageHelper 单元测试
 * 使用 Robolectric 创建真实的 Bitmap 对象
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImageHelperTest {
    @Mock
    private ACMFacadeImpl mockAcmFacade;
    @Mock
    private UploadResult mockUploadResult;
    @Mock
    private AccessibilityManager mockAccessibilityManager;
    private MockedStatic<ACMFacadeImpl> mockedStaticAcm;
    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedStaticAcm = mockStatic(ACMFacadeImpl.class);
        when(ACMFacadeImpl.getInstance()).thenReturn(mockAcmFacade);
    }

    @After
    public void tearDown() throws Exception {
        mockedStaticAcm.close();
        mocks.close();
    }

    @Test
    public void testCompressToJpeg_ValidBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        byte[] result = ImageHelper.compressToJpeg(bitmap);
        assertNotNull(result);
        assertTrue(result.length > 0);
        bitmap.recycle();
    }

    @Test
    public void testCompressToJpeg_NullBitmap() {
        byte[] result = ImageHelper.compressToJpeg(null);
        assertNull(result);
    }

    @Test
    public void testIsActionRoiStableDynamic_NullBitmap1() {
        CropPosition crop = new CropPosition(50, 50, 10, 10);
        assertTrue(ImageHelper.isActionRoiStableDynamic(null,
                Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), 0.8f, crop));
    }

    @Test
    public void testIsActionRoiStableDynamic_NullBitmap2() {
        CropPosition crop = new CropPosition(50, 50, 10, 10);
        assertTrue(ImageHelper.isActionRoiStableDynamic(
                Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), null, 0.8f, crop));
    }

    @Test
    public void testIsActionRoiStableDynamic_NullCropPosition() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        assertTrue(ImageHelper.isActionRoiStableDynamic(bitmap, bitmap, 0.8f, null));
        bitmap.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_RecycledBitmap() {
        Bitmap recycled = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        recycled.recycle();
        Bitmap valid = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        CropPosition crop = new CropPosition(50, 50, 10, 10);
        assertTrue(ImageHelper.isActionRoiStableDynamic(recycled, valid, 0.8f, crop));
        valid.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_SameImages() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        CropPosition crop = new CropPosition(50, 50, 50, 50);
        assertTrue(ImageHelper.isActionRoiStableDynamic(bitmap, bitmap, 0.8f, crop));
        bitmap.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_DifferentImages_HighThreshold() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap1.eraseColor(Color.RED);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap2.eraseColor(Color.BLUE);
        CropPosition crop = new CropPosition(50, 50, 50, 50);
        // 不同颜色的图片，在高阈值下应该不满足
        boolean result = ImageHelper.isActionRoiStableDynamic(bitmap1, bitmap2, 0.99f, crop);
        assertFalse(result);
        bitmap1.recycle();
        bitmap2.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_DifferentImages_LowThreshold() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap1.eraseColor(Color.RED);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        bitmap2.eraseColor(Color.BLUE);
        CropPosition crop = new CropPosition(50, 50, 50, 50);
        // 不同颜色的图片，在低阈值下也可能不满足，因为SSIM很低
        boolean result = ImageHelper.isActionRoiStableDynamic(bitmap1, bitmap2, 0.0f, crop);
        assertTrue(result);
        bitmap1.recycle();
        bitmap2.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_SmallROI() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        // 极小的裁剪区域会导致 ROI size too small
        CropPosition crop = new CropPosition(50, 50, 1, 1);
        assertTrue(ImageHelper.isActionRoiStableDynamic(bitmap1, bitmap2, 0.8f, crop));
        bitmap1.recycle();
        bitmap2.recycle();
    }

    @Test
    public void testIsActionRoiStableDynamic_LargeImages() {
        Bitmap bitmap1 = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
        bitmap1.eraseColor(Color.GREEN);
        Bitmap bitmap2 = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);
        bitmap2.eraseColor(Color.GREEN);
        CropPosition crop = new CropPosition(250, 250, 20, 20);
        assertTrue(ImageHelper.isActionRoiStableDynamic(bitmap1, bitmap2, 0.8f, crop));
        bitmap1.recycle();
        bitmap2.recycle();
    }

    @Test
    public void testUploadScreenshot_Success() throws AcmException {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class))).thenReturn(mockUploadResult);
        when(mockUploadResult.getFileUuid()).thenReturn("test-uuid-123");
        byte[] imageData = new byte[]{1, 2, 3, 4, 5};
        String result = ImageHelper.uploadScreenshot(imageData, "task123");
        assertEquals("test-uuid-123", result);
    }

    @Test
    public void testUploadScreenshot_NullResult() throws AcmException {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class))).thenReturn(null);
        byte[] imageData = new byte[]{1, 2, 3};
        String result = ImageHelper.uploadScreenshot(imageData, "task123");
        assertNull(result);
    }

    @Test
    public void testUploadScreenshot_NullFileUuid() throws AcmException {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class))).thenReturn(mockUploadResult);
        when(mockUploadResult.getFileUuid()).thenReturn(null);
        byte[] imageData = new byte[]{1, 2, 3};
        String result = ImageHelper.uploadScreenshot(imageData, "task123");
        assertNull(result);
    }

    @Test
    public void testUploadScreenshot_Exception() throws AcmException {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("Network error"));
        byte[] imageData = new byte[]{1, 2, 3};
        String result = ImageHelper.uploadScreenshot(imageData, "task123");
        assertNull(result);
    }

    @Test
    public void testUploadScreenshot_NullTaskId() throws Exception {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class))).thenReturn(mockUploadResult);
        when(mockUploadResult.getFileUuid()).thenReturn("uuid");
        byte[] imageData = new byte[]{1, 2, 3};
        String result = ImageHelper.uploadScreenshot(imageData, null);
        assertEquals("uuid", result);
        // 验证文件名包含 "unknown"
        verify(mockAcmFacade).uploadImageFile(contains("unknown"), eq(imageData));
    }

    @Test
    public void testUploadScreenshot_EmptyTaskId() throws Exception {
        when(mockAcmFacade.uploadImageFile(anyString(), any(byte[].class))).thenReturn(mockUploadResult);
        when(mockUploadResult.getFileUuid()).thenReturn("uuid");
        byte[] imageData = new byte[]{1, 2, 3};
        String result = ImageHelper.uploadScreenshot(imageData, "");
        assertEquals("uuid", result);
        verify(mockAcmFacade).uploadImageFile(contains("unknown"), eq(imageData));
    }

    @Test
    public void testUploadRetryTimes() {
        assertEquals(3, ImageHelper.UPLOAD_RETRY_TIMES);
    }

    @Test
    public void testUploadRetryDelayMs() {
        assertEquals(500L, ImageHelper.UPLOAD_RETRY_DELAY_MS);
    }

    @Test
    public void testGenerateScreenshotFileName_WithTaskId() throws Exception {
        Method method = ImageHelper.class.getDeclaredMethod("generateScreenshotFileName", String.class);
        method.setAccessible(true);
        String fileName = (String) method.invoke(null, "task123");
        assertNotNull(fileName);
        assertTrue(fileName.startsWith("screenshot_task123_"));
        assertTrue(fileName.endsWith(".jpg"));
    }

    @Test
    public void testGenerateScreenshotFileName_NullTaskId() throws Exception {
        Method method = ImageHelper.class.getDeclaredMethod("generateScreenshotFileName", String.class);
        method.setAccessible(true);
        String fileName = (String) method.invoke(null, (String) null);
        assertNotNull(fileName);
        assertTrue(fileName.startsWith("screenshot_unknown_"));
        assertTrue(fileName.endsWith(".jpg"));
    }

    @Test
    public void testGenerateScreenshotFileName_EmptyTaskId() throws Exception {
        Method method = ImageHelper.class.getDeclaredMethod("generateScreenshotFileName", String.class);
        method.setAccessible(true);
        String fileName = (String) method.invoke(null, "");
        assertNotNull(fileName);
        assertTrue(fileName.startsWith("screenshot_unknown_"));
        assertTrue(fileName.endsWith(".jpg"));
    }

    @Test
    public void testWaitForScreenStable_AlreadyStable() {
        when(mockAccessibilityManager.getScreenGeneratorNum()).thenReturn(100L);
        // 屏幕已经是稳定的
        ImageHelper.waitForScreenStable(mockAccessibilityManager, 2);
        // 应该多次调用 getScreenGeneratorNum
        verify(mockAccessibilityManager, atLeast(10)).getScreenGeneratorNum();
    }

    @Test
    public void testWaitForScreenStable_ChangingScreen() {
        when(mockAccessibilityManager.getScreenGeneratorNum())
                .thenReturn(1L, 2L, 3L, 4L, 5L);
        ImageHelper.waitForScreenStable(mockAccessibilityManager, 1);
        verify(mockAccessibilityManager, atLeast(1)).getScreenGeneratorNum();
    }

    @Test
    public void testWaitForScreenStable_ZeroMaxWait() {
        when(mockAccessibilityManager.getScreenGeneratorNum()).thenReturn(1L);
        // 最大等待时间为0，应该立即返回
        ImageHelper.waitForScreenStable(mockAccessibilityManager, 0);
    }

    @Test
    public void testCropPosition_InImageHelper() {
        CropPosition crop = new CropPosition(100, 200, 10, 20);
        assertEquals(100, crop.getX());
        assertEquals(200, crop.getY());
        assertEquals(20, crop.getLeftCropPerc());
        assertEquals(20, crop.getRightCropPerc());
        assertEquals(10, crop.getUpCropPerc());
        assertEquals(10, crop.getDownCropPerc());
    }
}
