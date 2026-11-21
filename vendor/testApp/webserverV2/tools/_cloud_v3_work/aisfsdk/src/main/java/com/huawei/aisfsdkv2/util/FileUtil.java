/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2021. All rights reserved.
 */

package com.huawei.aisfsdkv2.util;

import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * 文件工具类
 *
 * @author l00504561
 * @since 2021-05-17
 */
public class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("FileUtil");
    /**
     * 文件分隔符
     */
    public static final String FILE_SEPARATOR = "/";

    /**
     * 文件名称非法字符集
     */
    private static final String[] FILENAME_INVALID_CHARS = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|", "..", ";", "'", "&", "\n"};

    /**
     * 文件路径非法字符集
     */
    private static final String[] FILEPATH_INVALID_CHARS = {"\\", "\"", "'", "<", ">", "|", "&", ";", "\n", "`", ".."};

    /**
     * 判断文件名是否合法
     *
     * @param fileName 文件名
     * @return 文件路径
     */
    public static boolean isSecurityFileName(String fileName) {
        if (StringUtil.isNullOrEmpty(fileName)) {
            return false;
        }
        for (String filepathInvalidChar : FILENAME_INVALID_CHARS) {
            if (fileName.contains(filepathInvalidChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断文件路径是否合法
     *
     * @param filePath 文件路径
     * @return 文件路径
     */
    public static boolean isSecurityFilePath(String filePath) {
        if (StringUtil.isNullOrEmpty(filePath)) {
            return false;
        }
        for (String filepathInvalidChar : FILEPATH_INVALID_CHARS) {
            if (filePath.contains(filepathInvalidChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取文件路径
     *
     * @param filePath 文件路径
     * @return 文件路径
     */
    public static String getSecurityFilePath(String filePath) {
        if (StringUtil.isNullOrEmpty(filePath)) {
            return StringUtil.EMPTY;
        }
        return getSecurityFilePath(new File(filePath));
    }

    /**
     * 获取文件路径
     *
     * @param file 文件
     * @return 文件路径
     */
    public static String getSecurityFilePath(File file) {
        String filePath = file.getPath();
        filePath = Normalizer.normalize(filePath, Normalizer.Form.NFKC);
        try {
            filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("FileUtil.getSecurityFilePath: " + e.getMessage(), e);
        }
        if (!isSecurityFilePath(filePath)) {
            return StringUtil.EMPTY;
        }
        return filePath;
    }

    /**
     * 获取文件路径
     * 1. 如果是符号链接文件，返回getAbsolutePath
     * 2. isSymlink如果传入是个中文的文件，可能会失败
     *
     * @param file 文件
     * @return 全路径
     */
    public static String getFilePath(File file) {
        if (file == null) {
            return StringUtil.EMPTY;
        }
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            LOGGER.error("FileUtil.getFilePath: " + e.getMessage(), e);
        }
        return StringUtil.EMPTY;
    }

    /**
     * 判断文件是否存在
     *
     * @param filePath 文件
     * @return 是否存在
     */
    public static boolean exist(String filePath) {
        return new File(filePath).exists();
    }

    /**
     * 创建空文件
     *
     * @param file 文件
     * @return 创建文件结果是否成功
     */
    public static boolean touchNewFile(File file) {
        if (file.exists()) {
            boolean result = FileUtil.deleteDirectory(file);
            if (!result) {
                return false;
            }
        }
        try {
            return file.createNewFile();
        } catch (Exception e) {
            LOGGER.error("FileUtil.touchNewFile: " + e.getMessage(), e);
        }
        return false;
    }

    /**
     * 递归创建文件夹，支持重试
     *
     * @param file file
     * @return 是否成功
     */
    public static boolean mkdirs(File file) {
        if (file.exists() && file.isDirectory()) {
            return true;
        }
        Boolean lastResult = RetryUtil.retry(file::mkdirs, (result) -> result, RetryUtil.RETRY_COUNT_3,
                RetryUtil.RETRY_BACKUP_500_MS);
        if (lastResult == null) {
            return false;
        }
        return lastResult;
    }

    /**
     * 删除目录下的所有文件,包括文件夹本身
     *
     * @param file 文件
     * @return 操作是否成功
     */
    public static boolean deleteDirectory(File file) {
        if (file == null || (!file.exists())) {
            return true;
        }
        if (file.isFile()) {
            boolean delOk = file.delete();
            LOGGER.info("FileUtil.deleteDirectory: file={}, result={}", file.getAbsolutePath(), delOk);
            return delOk;
        } else if (file.isDirectory()) {
            File[] fileArr = file.listFiles();
            if (fileArr != null) {
                for (File item : fileArr) {
                    deleteDirectory(item);
                }
            }
            boolean delOk = file.delete();
            LOGGER.info("FileUtil.deleteDirectory: dir={}, result={}", file.getAbsolutePath(), delOk);
            return delOk;
        }
        return true;
    }

    /**
     * 删掉全部子文件
     *
     * @param dirFile dirFile
     * @return 删掉全部子文件是否成功
     */
    public static boolean deleteChildFile(File dirFile) {
        File[] childFileList = dirFile.listFiles();
        if (childFileList != null) {
            for (File file : childFileList) {
                boolean result = FileUtil.deleteDirectory(file);
                if (!result) {
                    LOGGER.error("FileUtil.deleteChildFile: failed to delete file={}", file.getAbsolutePath());
                    return false;
                }
            }
        }
        return true;
    }
}
