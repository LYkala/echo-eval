/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * FileUtil 单元测试
 */
public class FileUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ======= isSecurityFileName =======

    @Test
    public void testIsSecurityFileName_ValidName() {
        assertTrue(FileUtil.isSecurityFileName("validfile.txt"));
    }

    @Test
    public void testIsSecurityFileName_Null_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName(null));
    }

    @Test
    public void testIsSecurityFileName_Empty_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName(""));
    }

    @Test
    public void testIsSecurityFileName_WithSlash_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName("path/file.txt"));
    }

    @Test
    public void testIsSecurityFileName_WithBackslash_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName("path\\file.txt"));
    }

    @Test
    public void testIsSecurityFileName_WithDotDot_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName(".."));
    }

    @Test
    public void testIsSecurityFileName_WithColon_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName("file:name"));
    }

    @Test
    public void testIsSecurityFileName_WithStar_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName("file*.txt"));
    }

    @Test
    public void testIsSecurityFileName_WithNewline_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFileName("file\nname"));
    }

    // ======= isSecurityFilePath =======

    @Test
    public void testIsSecurityFilePath_ValidPath() {
        assertTrue(FileUtil.isSecurityFilePath("/data/user/file.txt"));
    }

    @Test
    public void testIsSecurityFilePath_Null_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFilePath(null));
    }

    @Test
    public void testIsSecurityFilePath_Empty_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFilePath(""));
    }

    @Test
    public void testIsSecurityFilePath_WithDotDot_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFilePath("/data/../etc/passwd"));
    }

    @Test
    public void testIsSecurityFilePath_WithSemicolon_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFilePath("/data/file;rm -rf"));
    }

    @Test
    public void testIsSecurityFilePath_WithAmpersand_ReturnsFalse() {
        assertFalse(FileUtil.isSecurityFilePath("/data/file&other"));
    }

    // ======= exist =======

    @Test
    public void testExist_FileExists() throws IOException {
        File file = tempFolder.newFile("test.txt");
        assertTrue(FileUtil.exist(file.getAbsolutePath()));
    }

    @Test
    public void testExist_FileNotExists() {
        assertFalse(FileUtil.exist("/nonexistent/path/file.txt"));
    }

    // ======= touchNewFile =======

    @Test
    public void testTouchNewFile_CreatesFile() throws IOException {
        File file = new File(tempFolder.getRoot(), "newfile.txt");
        assertFalse(file.exists());
        boolean result = FileUtil.touchNewFile(file);
        assertTrue(result);
        assertTrue(file.exists());
    }

    @Test
    public void testTouchNewFile_ReplacesExistingFile() throws IOException {
        File file = tempFolder.newFile("existing.txt");
        assertTrue(file.exists());
        boolean result = FileUtil.touchNewFile(file);
        assertTrue(result);
        assertTrue(file.exists());
    }

    // ======= mkdirs =======

    @Test
    public void testMkdirs_CreatesDirectory() {
        File dir = new File(tempFolder.getRoot(), "subdir/nested");
        assertFalse(dir.exists());
        boolean result = FileUtil.mkdirs(dir);
        assertTrue(result);
        assertTrue(dir.exists());
    }

    @Test
    public void testMkdirs_ExistingDirectory_ReturnsTrue() throws IOException {
        File dir = tempFolder.newFolder("existing");
        assertTrue(FileUtil.mkdirs(dir));
    }

    // ======= deleteDirectory =======

    @Test
    public void testDeleteDirectory_DeletesFile() throws IOException {
        File file = tempFolder.newFile("todelete.txt");
        assertTrue(file.exists());
        boolean result = FileUtil.deleteDirectory(file);
        assertTrue(result);
        assertFalse(file.exists());
    }

    @Test
    public void testDeleteDirectory_DeletesEmptyDir() throws IOException {
        File dir = tempFolder.newFolder("emptydir");
        boolean result = FileUtil.deleteDirectory(dir);
        assertTrue(result);
        assertFalse(dir.exists());
    }

    @Test
    public void testDeleteDirectory_DeletesDirWithFiles() throws IOException {
        File dir = tempFolder.newFolder("dirwithfiles");
        new File(dir, "a.txt").createNewFile();
        new File(dir, "b.txt").createNewFile();
        boolean result = FileUtil.deleteDirectory(dir);
        assertTrue(result);
        assertFalse(dir.exists());
    }

    @Test
    public void testDeleteDirectory_NullFile_ReturnsTrue() {
        assertTrue(FileUtil.deleteDirectory(null));
    }

    @Test
    public void testDeleteDirectory_NonExistentFile_ReturnsTrue() {
        File file = new File("/nonexistent/path/file.txt");
        assertTrue(FileUtil.deleteDirectory(file));
    }

    // ======= getFilePath =======

    @Test
    public void testGetFilePath_NullFile_ReturnsEmpty() {
        assertEquals(StringUtil.EMPTY, FileUtil.getFilePath(null));
    }

    @Test
    public void testGetFilePath_ValidFile() throws IOException {
        File file = tempFolder.newFile("test.txt");
        String path = FileUtil.getFilePath(file);
        assertNotNull(path);
        assertFalse(path.isEmpty());
    }

    // ======= deleteChildFile =======

    @Test
    public void testDeleteChildFile_DeletesChildren() throws IOException {
        File dir = tempFolder.newFolder("parent");
        new File(dir, "child1.txt").createNewFile();
        new File(dir, "child2.txt").createNewFile();
        boolean result = FileUtil.deleteChildFile(dir);
        assertTrue(result);
        assertTrue(dir.exists()); // parent remains
        assertEquals(0, dir.listFiles().length);
    }
}
