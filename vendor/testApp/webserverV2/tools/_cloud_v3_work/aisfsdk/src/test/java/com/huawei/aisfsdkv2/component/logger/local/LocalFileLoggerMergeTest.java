/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * LocalFileLogger.mergeAndCompressLogs() 单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class LocalFileLoggerMergeTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LocalFileLogger logger;

    @Before
    public void setUp() {
        logger = LocalFileLogger.getInstance();
    }

    private boolean invokeMergeAndCompressLogs(File[] sdkLogFiles, File appZipFile,
            File outputZip) throws Exception {
        java.lang.reflect.Method method = LocalFileLogger.class.getDeclaredMethod(
                "mergeAndCompressLogs", File[].class, File.class, File.class);
        method.setAccessible(true);
        return (boolean) method.invoke(logger, sdkLogFiles, appZipFile, outputZip);
    }

    private List<String> readZipEntries(File zipFile) throws Exception {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private File createAppZip(String... entryNames) throws Exception {
        File appZip = tempFolder.newFile("app_logs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(appZip))) {
            for (String name : entryNames) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(("content of " + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return appZip;
    }

    // ======= 仅 SDK 日志（无 App 日志） =======

    @Test
    public void testMerge_SdkLogsOnly_ContainsSdkEntries() throws Exception {
        File sdkLog1 = tempFolder.newFile("aisfsdk.log_20260101");
        File sdkLog2 = tempFolder.newFile("aisfsdk.log_20260102");
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{sdkLog1, sdkLog2}, null, outputZip);

        assertTrue(result);
        assertTrue(outputZip.exists());
        List<String> entries = readZipEntries(outputZip);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.startsWith("sdk/")));
    }

    // ======= SDK + App 日志合并 =======

    @Test
    public void testMerge_SdkAndAppLogs_ContainsBothEntries() throws Exception {
        File sdkLog = tempFolder.newFile("aisfsdk.log_20260101");
        File appZip = createAppZip("app_main.log", "crash.log");
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{sdkLog}, appZip, outputZip);

        assertTrue(result);
        List<String> entries = readZipEntries(outputZip);
        assertEquals(3, entries.size());
        assertTrue(entries.contains("sdk/" + sdkLog.getName()));
        assertTrue(entries.contains("app/app_main.log"));
        assertTrue(entries.contains("app/crash.log"));
    }

    // ======= 仅 App 日志（SDK 日志为空数组） =======

    @Test
    public void testMerge_AppLogsOnly_ContainsAppEntries() throws Exception {
        File appZip = createAppZip("app.log");
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{}, appZip, outputZip);

        assertTrue(result);
        List<String> entries = readZipEntries(outputZip);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).startsWith("app/"));
    }

    // ======= SDK 日志为 null =======

    @Test
    public void testMerge_NullSdkLogs_AppOnly() throws Exception {
        File appZip = createAppZip("app.log");
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(null, appZip, outputZip);

        assertTrue(result);
        List<String> entries = readZipEntries(outputZip);
        assertEquals(1, entries.size());
    }

    // ======= SDK 和 App 日志均为 null =======

    @Test
    public void testMerge_BothNull_EmptyZip() throws Exception {
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(null, null, outputZip);

        assertTrue(result);
        assertTrue(outputZip.exists());
        List<String> entries = readZipEntries(outputZip);
        assertEquals(0, entries.size());
    }

    // ======= App zip 文件损坏（非 zip 格式内容） =======

    @Test
    public void testMerge_CorruptedAppZip_HandlesGracefully() throws Exception {
        File sdkLog = tempFolder.newFile("aisfsdk.log_20260101");
        File badZip = tempFolder.newFile("bad_app.zip");
        try (FileOutputStream fos = new FileOutputStream(badZip)) {
            fos.write("not a zip file".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{sdkLog}, badZip, outputZip);

        // 损坏的 App zip 导致合并失败，临时 zip 被清理
        if (!result) {
            assertFalse(outputZip.exists());
        } else {
            // 某些 JDK 实现中 ZipInputStream 对非 zip 内容返回 null 而非抛异常
            // 此时合并成功但仅包含 SDK 日志
            assertTrue(outputZip.exists());
            List<String> entries = readZipEntries(outputZip);
            assertTrue(entries.stream().allMatch(e -> e.startsWith("sdk/")));
        }
    }

    // ======= 输出路径不可写 =======

    @Test
    public void testMerge_OutputPathInvalid_ReturnsFalse() throws Exception {
        File sdkLog = tempFolder.newFile("aisfsdk.log_20260101");
        File outputZip = new File("/nonexistent/dir/alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{sdkLog}, null, outputZip);

        assertFalse(result);
    }

    // ======= 验证 zip 内容完整性 =======

    @Test
    public void testMerge_VerifyContentIntegrity() throws Exception {
        File sdkLog = tempFolder.newFile("aisfsdk.log_test");
        String sdkContent = "SDK log line 1\nSDK log line 2\n";
        try (FileWriter fw = new FileWriter(sdkLog)) {
            fw.write(sdkContent);
        }
        File appZip = createAppZip("app.log");
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");

        boolean result = invokeMergeAndCompressLogs(
                new File[]{sdkLog}, appZip, outputZip);

        assertTrue(result);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputZip))) {
            ZipEntry entry = zis.getNextEntry();
            assertEquals("sdk/" + sdkLog.getName(), entry.getName());
            byte[] buf = new byte[1024];
            int len = zis.read(buf);
            String content = new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(sdkContent, content);
        }
    }

    // ======= SDK 日志为真正的 gzip 压缩文件（回归测试） =======

    /**
     * 验证 .gz 压缩的 SDK 日志合并后，zip 内条目仍为真正的 gzip 数据
     *
     * <p>回归测试：修复前 mergeAndCompressLogs 会用 GZIPInputStream 把 .gz 解压成明文写入 zip，
     * 但条目名仍叫 .gz，导致"名为 .gz 实为明文"，服务器侧无法用 gzip 解压。
     * 修复后 .gz 文件原样写入 zip，条目可用 GZIPInputStream 正常解压还原。</p>
     */
    @Test
    public void testMerge_GzipSdkLog_PreservedAsGzipInZip() throws Exception {
        // 1. 准备明文 SDK 日志内容，并用 GZIPOutputStream 压缩为真正的 .gz 文件
        String sdkContent = "SDK log line 1\nSDK log line 2\n";
        File sdkGzFile = tempFolder.newFile("aisfsdk.log_20260101.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(sdkGzFile))) {
            gos.write(sdkContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        long gzSize = sdkGzFile.length();

        // 2. 合并
        File outputZip = new File(tempFolder.getRoot(), "alllog.zip");
        boolean result = invokeMergeAndCompressLogs(new File[]{sdkGzFile}, null, outputZip);
        assertTrue(result);

        // 3. 从输出 zip 读取 sdk/xxx.gz 条目，并用 GZIPInputStream 验证可解压还原
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(outputZip))) {
            ZipEntry entry = zis.getNextEntry();
            assertEquals("sdk/" + sdkGzFile.getName(), entry.getName());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = zis.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            // 条目字节长度应与原 .gz 文件一致（原样写入，未解压）
            assertEquals(gzSize, baos.size());
            // 条目字节应为真正的 gzip 数据，可用 GZIPInputStream 解压还原
            try (GZIPInputStream gis = new GZIPInputStream(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
                while ((len = gis.read(buf)) > 0) {
                    decompressed.write(buf, 0, len);
                }
                String content = new String(decompressed.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(sdkContent, content);
            }
        }
    }
}
