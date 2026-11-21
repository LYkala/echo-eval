/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2014-2026. All rights reserved.
 */

package com.huawei.aisfsdkv2.component.logger.local;

import android.util.Log;

import com.huawei.aisfsdkv2.api.AISFSDKClient;
import com.huawei.aisfsdkv2.api.log.LogCollectCallback;
import com.huawei.aisfsdkv2.api.log.LogUploadCallback;
import com.huawei.aisfsdkv2.api.log.LogLevelEnum;
import com.huawei.aisfsdkv2.api.login.LoginStatus;
import com.huawei.aisfsdkv2.api.log.SdkLocalLogConfig;
import com.huawei.aisfsdkv2.apiimpl.log.LogApiImpl;
import com.huawei.aisfsdkv2.component.logger.Logger;
import com.huawei.aisfsdkv2.component.logger.LoggerFactory;
import com.huawei.aisfsdkv2.component.logger.model.LogEvent;
import com.huawei.aisfsdkv2.component.threadpool.ThreadPoolManager;
import com.huawei.aisfsdkv2.constants.NumberConstants;
import com.huawei.aisfsdkv2.util.FileUtil;
import com.huawei.aisfsdkv2.util.StringUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 本地文件日志
 *
 * @author l00504561
 * @since 2026-02-07
 */
public class LocalFileLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("LocalFileLogger");

    private static final LocalFileLogger localFileLogger = new LocalFileLogger();

    /**
     * 日志目录名
     */
    private static final String LOG_DIR = "sdklogs";

    /**
     * 日志名
     */
    private static final String LOG_FILE_NAME = "aisfsdk.log";

    /**
     * App 回调日志文件大小上限（10MB）
     */
    private static final long MAX_APP_LOG_SIZE = 10 * 1024 * 1024;

    /**
     * 队列大小
     */
    private static final int LOG_EVENT_QUEUE_SIZE = 10000;

    /**
     * 特殊事件
     */
    private static final String LOG_AGE_EVENT = "LOG_AGE_EVENT";
    private static final String LOG_UPLOAD_EVENT = "LOG_UPLOAD_EVENT";

    /**
     * 是否生效
     */
    private volatile boolean enable = false;

    /**
     * 日志配置
     */
    private SdkLocalLogConfig sdkLocalLogConfig;

    /**
     * 日志目录
     */
    private volatile String logDir;

    /**
     * 日志文件路径
     */
    private String logFilePath;

    /**
     * 日志文件句柄
     */
    private BufferedWriter currentWriter;

    /**
     * 日志队列
     */
    private final BlockingQueue<LogEvent> logEventQueue = new LinkedBlockingQueue<>(LOG_EVENT_QUEUE_SIZE);

    /**
     * 上传重入保护锁
     */
    private final AtomicBoolean isUploading = new AtomicBoolean(false);

    /**
     * 日志上传结果回调
     */
    private volatile LogUploadCallback logUploadCallback;

    private LocalFileLogger() {
    }

    // -------------------------------------------------------------------------
    // 单例 & 生命周期
    // -------------------------------------------------------------------------

    /**
     * 获取单例
     *
     * @return 获取单例
     */
    public static LocalFileLogger getInstance() {
        return localFileLogger;
    }

    /**
     * 判断本地文件日志是否已启动
     *
     * @return true 已启动，false 未启动
     */
    public boolean isStarted() {
        return enable;
    }

    /**
     * 启动本地日志文件
     *
     * @param sdkLocalLogConfig sdkLocalLogConfig
     */
    public synchronized void start(SdkLocalLogConfig sdkLocalLogConfig) {
        if (this.enable) {
            LOGGER.debug("LocalFileLogger.start: LocalFileLogger already started, skip");
            return;
        }
        if (sdkLocalLogConfig == null) {
            LOGGER.warn("LocalFileLogger.start: LocalFileLogger start failed: sdkLocalLogConfig is null");
            return;
        }
        try {
            String sdkDataDir = AISFSDKClient.getBaseApi().getKeyFileInfo().getSdkDataDir();
            if (sdkDataDir == null || sdkDataDir.isEmpty()) {
                LOGGER.warn("LocalFileLogger.start: LocalFileLogger start failed: sdkDataDir is empty");
                return;
            }
            File logDirFile = new File(sdkDataDir, LOG_DIR);
            this.logDir = FileUtil.getFilePath(logDirFile);
            FileUtil.mkdirs(logDirFile);
            this.logFilePath = FileUtil.getFilePath(new File(this.logDir, LOG_FILE_NAME));
            this.sdkLocalLogConfig = sdkLocalLogConfig;
            this.enable = true;
            // 启动线程
            ThreadPoolManager.getInstance().executeIndependentThreadPool(this::logEventQueueHandle);
            // 定时老化检查（60秒）
            ThreadPoolManager.getInstance().addScheduleTaskSharedThreadPool(this::triggerLogAgeEvent, 1000, 60000, TimeUnit.MILLISECONDS);
            // 定时 flush（1秒），批量刷盘提高性能
            ThreadPoolManager.getInstance().addScheduleTaskSharedThreadPool(this::triggerFlushEvent, 1000, 1000, TimeUnit.MILLISECONDS);
            LOGGER.info("LocalFileLogger.start: LocalFileLogger started, logDir: {}", this.logDir);
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.start: LocalFileLogger start failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取日志目录路径
     *
     * @return 日志目录绝对路径，未初始化时返回null
     */
    public String getLogDirectory() {
        return this.logDir;
    }

    /**
     * 设置日志上传结果回调
     *
     * @param callback 上传结果回调
     */
    public void setLogUploadCallback(LogUploadCallback callback) {
        this.logUploadCallback = callback;
    }

    /**
     * 通知上传结果
     *
     * @param success 上传是否成功
     */
    private void notifyUploadResult(boolean success) {
        LogUploadCallback callback = this.logUploadCallback;
        if (callback == null) {
            return;
        }
        try {
            callback.onUploadResult(success);
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.notifyUploadResult: callback exception: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // 公共写入入口
    // -------------------------------------------------------------------------

    /**
     * 写日志文件（异步）
     *
     * @param logEvent 日志事件
     */
    public void writeLogFile(LogEvent logEvent) {
        if (!this.enable) {
            return;
        }
        if (logEvent.getLogLevel().getLogLevel() < this.sdkLocalLogConfig.getLogLevel().getLogLevel()) {
            return;
        }
        boolean result = this.logEventQueue.offer(logEvent);
        if (result) {
            return;
        }
        Log.e(this.getClass().getName(), "writeLogFile logEventQueue error");
    }

    /**
     * 写日志文件（同步）
     *
     * @param logEvent 日志事件
     */
    public void writeCrashLogFile(LogEvent logEvent) {
        if (!this.enable) {
            return;
        }
        if (logEvent.getLogLevel().getLogLevel() < this.sdkLocalLogConfig.getLogLevel().getLogLevel()) {
            return;
        }
        File crashLogFile = new File(this.logDir, String.format(Locale.ENGLISH, "%s_%d", LOG_FILE_NAME, System.currentTimeMillis()));
        try (BufferedWriter currentWriter = new BufferedWriter(new FileWriter(crashLogFile, Boolean.TRUE))) {
            currentWriter.write(logEvent.toJSONString());
            currentWriter.flush();
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.writeCrashLogFile: " + e.getMessage(), e);
        }
    }

    /**
     * 触发日志文件上传事件
     */
    public void triggerLogUploadEvent() {
        LOGGER.info("LocalFileLogger.triggerLogUploadEvent: Log upload triggered");
        LogEvent logEvent = new LogEvent(LogLevelEnum.ERROR, LOG_UPLOAD_EVENT, LOG_UPLOAD_EVENT);
        this.writeLogFile(logEvent);
    }

    // -------------------------------------------------------------------------
    // 队列消费 & 事件分发
    // -------------------------------------------------------------------------

    /**
     * 处理队列
     */
    private void logEventQueueHandle() {
        while (System.currentTimeMillis() > 0) {
            try {
                LogEvent logEvent = this.logEventQueue.poll(NumberConstants.NUMBER_300, TimeUnit.SECONDS);
                if (logEvent == null) {
                    continue;
                }
                if (LOG_AGE_EVENT.equals(logEvent.getLogEventMessage().getDetail())) {
                    this.logAgeEventHandle();
                } else if (LOG_UPLOAD_EVENT.equals(logEvent.getLogEventMessage().getDetail())) {
                    this.logUploadEventHandle();
                } else {
                    this.logEventHandle(logEvent);
                }
            } catch (InterruptedException e) {
                LOGGER.error("LocalFileLogger.logEventQueueHandle: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 触发日志文件满了、日志文件老化
     */
    private void triggerLogAgeEvent() {
        LogEvent logEvent = new LogEvent(LogLevelEnum.ERROR, LOG_AGE_EVENT, LOG_AGE_EVENT);
        this.writeLogFile(logEvent);
    }

    /**
     * 触发定时 flush 事件
     * 批量刷盘，提高性能
     */
    private void triggerFlushEvent() {
        synchronized (this) {
            if (this.currentWriter != null) {
                try {
                    this.currentWriter.flush();
                } catch (IOException e) {
                    LOGGER.error("LocalFileLogger.triggerFlushEvent: Log flush failed: {}", e.getMessage());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 事件处理
    // -------------------------------------------------------------------------

    /**
     * 处理日志事件
     * 混合策略：ERROR/WARN 立即刷盘，DEBUG/INFO 依赖定时 flush
     */
    private void logEventHandle(LogEvent logEvent) {
        if (this.currentWriter == null) {
            initLogFile();
        }
        if (this.currentWriter == null) {
            LOGGER.warn("LocalFileLogger.logEventHandle: Log write skipped: currentWriter is null after resetLogFile");
            return;
        }
        try {
            this.currentWriter.write(logEvent.toJSONString());
            this.currentWriter.newLine();
            // 混合策略：ERROR/WARN 立即刷盘（关键日志不丢），DEBUG/INFO 批量刷盘（性能优先）
            if (logEvent.getLogLevel().getLogLevel() >= LogLevelEnum.WARN.getLogLevel()) {
                this.currentWriter.flush();
            }
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.logUploadEventHandle: " + e.getMessage(), e);
            resetLogFile();
        }
    }

    /**
     * 处理日志老化事件(日志文件满了、日志文件老化)
     */
    private void logAgeEventHandle() {
        File logFile = new File(this.logFilePath);
        if (logFile.exists() && logFile.length() > this.sdkLocalLogConfig.getMaxLogFileSize()) {
            this.resetLogFile();
        }
        File logDirFile = new File(this.logDir);
        File[] fileList = logDirFile.listFiles((dir, name) -> name.startsWith(LOG_FILE_NAME + StringUtil.UNDERSCORE));
        if (fileList == null) {
            return;
        }
        List<String> fileNameList = new ArrayList<>();
        for (File file : fileList) {
            String fileName = file.getName();
            String[] splits = fileName.split(StringUtil.UNDERSCORE);
            try {
                String timestampStr = splits[1].replace(".gz", "");
                long ignore = Long.parseLong(timestampStr);
            } catch (Exception e) {
                FileUtil.deleteDirectory(file);
                continue;
            }
            fileNameList.add(fileName);
        }
        fileNameList.sort((o1, o2) -> {
            String[] o1Splits = o1.split(StringUtil.UNDERSCORE);
            String[] o2Splits = o2.split(StringUtil.UNDERSCORE);
            long o1Timestamp = Long.parseLong(o1Splits[1].replace(".gz", ""));
            long o2Timestamp = Long.parseLong(o2Splits[1].replace(".gz", ""));
            return (int) (o1Timestamp - o2Timestamp);
        });
        while (fileNameList.size() > this.sdkLocalLogConfig.getMaxLogFileCount()) {
            String fileName = fileNameList.get(0);
            FileUtil.deleteDirectory(new File(logDir, fileName));
            fileNameList.remove(0);
        }
    }

    /**
     * 处理日志上传事件
     *
     * <p>分为两部分执行：</p>
     * <ol>
     *   <li>前置检查与准备（同步）：检查登录状态、重置日志文件、准备参数</li>
     *   <li>压缩与上传（异步）：在线程池中执行文件压缩和上传</li>
     * </ol>
     *
     * <p>使用 {@link AtomicBoolean} 上传锁防止重入，短时间内多次触发仅执行一次。</p>
     */
    private void logUploadEventHandle() {
        if (!isUploading.compareAndSet(false, true)) {
            LOGGER.info("LocalFileLogger.logUploadEventHandle: upload already in progress, skip");
            return;
        }
        try {
            String zipName = prepareUploadContext();
            if (zipName == null) {
                isUploading.set(false);
                return;
            }
            this.resetLogFile();
            ThreadPoolManager.getInstance().executeSharedThreadPool(() -> {
                try {
                    compressAndUploadLogs(zipName);
                } finally {
                    isUploading.set(false);
                }
            });
        } catch (Exception e) {
            isUploading.set(false);
            throw e;
        }
    }

    /**
     * 准备上传上下文（同步）
     *
     * <p>检查登录状态并构建上传所需的 zip 文件名。</p>
     *
     * @return zip 文件名，未登录返回 null
     */
    private String prepareUploadContext() {
        if (AISFSDKClient.getLoginApi() == null
                || AISFSDKClient.getLoginApi().getLoginInfo() == null
                || AISFSDKClient.getLoginApi().getLoginInfo().getStatus() != LoginStatus.CONNECTED) {
            LOGGER.warn("LocalFileLogger.prepareUploadContext: Not logged in, skip upload");
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        String timeStamp = sdf.format(new Date());
        String usernameValue = AISFSDKClient.getLoginApi().getLoginInfo().getUsername();
        String deviceTypeValue = "1";
        if (AISFSDKClient.getBaseApi() != null && AISFSDKClient.getBaseApi().getDeviceInfo() != null
                && AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceType() != null) {
            deviceTypeValue = AISFSDKClient.getBaseApi().getDeviceInfo().getDeviceType().getValue();
        }
        return String.format(Locale.ENGLISH, "alllog_%s_%s_%s.zip", usernameValue, deviceTypeValue, timeStamp);
    }

    /**
     * 压缩并上传日志（异步）
     *
     * <p>在线程池中执行，流程：</p>
     * <ol>
     *   <li>收集 SDK 日志文件</li>
     *   <li>通过回调采集 App 日志（如有）</li>
     *   <li>合并打包为单个 zip</li>
     *   <li>上传合并 zip</li>
     * </ol>
     *
     * @param zipName zip 文件名
     */
    private void compressAndUploadLogs(String zipName) {
        LOGGER.info("LocalFileLogger.compressAndUploadLogs: Start compressing log files");
        File logDirFile = new File(this.logDir);
        File[] sdkLogFiles = logDirFile.listFiles((dir, name) -> name.startsWith(LOG_FILE_NAME + "_"));
        if (sdkLogFiles == null || sdkLogFiles.length == 0) {
            LOGGER.warn("LocalFileLogger.compressAndUploadLogs: No log files found in {}", this.logDir);
            return;
        }
        LOGGER.info("LocalFileLogger.compressAndUploadLogs: Found {} SDK log files", sdkLogFiles.length);
        File appZipFile = collectAppLog();
        File mergedZip = new File(this.logDir, zipName);
        if (!mergeAndCompressLogs(sdkLogFiles, appZipFile, mergedZip)) {
            LOGGER.error("LocalFileLogger.compressAndUploadLogs: merge failed");
            return;
        }
        LOGGER.info("LocalFileLogger.compressAndUploadLogs: Merge success, uploading {}", zipName);
        UploadResult result = UploadLogManager.getInstance().uploadLogFile(mergedZip);
        if (result.success) {
            if (mergedZip.exists()) {
                mergedZip.delete();
                LOGGER.info("LocalFileLogger.compressAndUploadLogs: uploaded zip deleted: {}", zipName);
            }
            notifyUploadResult(true);
        } else {
            LOGGER.error("LocalFileLogger.compressAndUploadLogs: upload failed");
            notifyUploadResult(false);
        }
    }

    /**
     * 收集 App 日志
     *
     * <p>通过已注册的 {@link LogCollectCallback} 回调获取 App 侧日志压缩包。
     * 对回调返回值进行校验：null/不存在/超过 10MB 均返回 null。</p>
     *
     * @return App 日志 zip 文件，无回调或校验不通过时返回 null
     */
    private File collectAppLog() {
        LogCollectCallback callback = LogApiImpl.getInstance().getLogCollectCallback();
        if (callback == null) {
            LOGGER.info("LocalFileLogger.collectAppLog: no callback registered, skip");
            return null;
        }
        try {
            String appZipPath = callback.onCollectLog();
            if (appZipPath == null) {
                LOGGER.info("LocalFileLogger.collectAppLog: callback returned null");
                return null;
            }
            File appZipFile = new File(appZipPath);
            if (!appZipFile.exists()) {
                LOGGER.warn("LocalFileLogger.collectAppLog: file not exist: {}", appZipPath);
                return null;
            }
            if (appZipFile.length() > MAX_APP_LOG_SIZE) {
                LOGGER.warn("LocalFileLogger.collectAppLog: file too large ({}B > {}B), skip",
                        appZipFile.length(), MAX_APP_LOG_SIZE);
                return null;
            }
            LOGGER.info("LocalFileLogger.collectAppLog: collected app log: {}, size={}B",
                    appZipFile.getName(), appZipFile.length());
            return appZipFile;
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.collectAppLog: callback error", e);
            return null;
        }
    }

    /**
     * 合并 SDK 日志和 App 日志为一个 zip
     *
     * <p>SDK 日志原样放入 {@code sdk/} 子目录（保留 gzip 压缩，不解压），
     * App 日志解压后放入 {@code app/} 子目录。
     * 合并失败时删除临时 zip 文件。</p>
     *
     * @param sdkLogFiles SDK 日志文件数组（可能为 .gz 压缩文件）
     * @param appZipFile  App 日志 zip 文件，可为 null
     * @param outputZip   输出 zip 文件
     * @return true 合并成功，false 合并失败
     */
    private boolean mergeAndCompressLogs(File[] sdkLogFiles, File appZipFile, File outputZip) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            byte[] buffer = new byte[1024];
            if (sdkLogFiles != null) {
                for (File logFile : sdkLogFiles) {
                    if (!logFile.isFile()) {
                        continue;
                    }
                    String entryName = "sdk/" + logFile.getName();
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (FileInputStream fis = new FileInputStream(logFile)) {
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            }
            if (appZipFile != null && appZipFile.exists()) {
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(appZipFile))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        zos.putNextEntry(new ZipEntry("app/" + entry.getName()));
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        zos.closeEntry();
                    }
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.mergeAndCompressLogs: error", e);
            if (outputZip.exists()) {
                outputZip.delete();
            }
            return false;
        }
    }

    /**
     * 压缩日志文件
     *
     * <p>将日志文件压缩到 zip 包中，zip 条目重命名为 aisfsdklog_{username}_{deviceType}_{时间戳}.log 格式。</p>
     *
     * @param fileList   日志文件列表
     * @param zipFile    目标 zip 文件
     * @param username   用户名
     * @param deviceType 设备类型
     * @return true 压缩成功，false 压缩失败
     */
    private boolean compressLogFiles(File[] fileList, File zipFile, String username, String deviceType) {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            byte[] bytes = new byte[1024];
            for (File logFile : fileList) {
                if (logFile.isFile()) {
                    String entryName = buildZipEntryName(logFile.getName(), username, deviceType);
                    try (FileInputStream fis = new FileInputStream(logFile)) {
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zipOut.putNextEntry(zipEntry);
                        int length;
                        while ((length = fis.read(bytes)) >= 0) {
                            zipOut.write(bytes, 0, length);
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("LocalFileLogger.compressLogFiles: Compress failed, zipName={}, error={}", zipFile.getName(), e.getMessage(), e);
            FileUtil.deleteDirectory(zipFile);
            return false;
        }
    }

    /**
     * 构建 zip 条目名称
     *
     * <p>原始文件名格式：aisfsdk.log_1775725141570<br>
     * 目标格式：aisfsdklog_{username}_{deviceType}_{时间戳}.log</p>
     *
     * @param originalName 原始文件名
     * @param username     用户名
     * @param deviceType   设备类型
     * @return zip 条目名称
     */
    private String buildZipEntryName(String originalName, String username, String deviceType) {
        String timestamp = originalName.substring(originalName.lastIndexOf('_') + 1);
        return String.format(Locale.ENGLISH, "aisfsdklog_%s_%s_%s.log", username, deviceType, timestamp);
    }

    // -------------------------------------------------------------------------
    // 文件操作
    // -------------------------------------------------------------------------

    /**
     * 初始化日志文件
     *
     * <p>首次打开日志文件建立 writer，追加模式写入，不做文件滚动。</p>
     */
    private void initLogFile() {
        FileUtil.mkdirs(new File(this.logDir));
        try {
            this.currentWriter = new BufferedWriter(new FileWriter(this.logFilePath, Boolean.TRUE));
        } catch (IOException e) {
            LOGGER.error("LocalFileLogger.initLogFile: " + e.getMessage(), e);
            this.currentWriter = null;
        }
    }

    /**
     * 滚动日志文件
     *
     * <p>将当前日志文件压缩为 {@code aisfsdk.log_{timestamp}.gz} 后删除原文件，
     * 然后创建新的空日志文件继续写入。</p>
     */
    private void resetLogFile() {
        if (this.currentWriter != null) {
            try {
                this.currentWriter.flush();
                this.currentWriter.close();
            } catch (IOException e) {
                Log.e("LocalFileLogger", "resetLogFile: Failed to close log writer", e);
            } finally {
                this.currentWriter = null;
            }
        }
        File logFile = new File(this.logFilePath);
        if (logFile.exists() && logFile.length() > 0) {
            File gzipFile = new File(this.logDir,
                    String.format(Locale.ENGLISH, "%s_%d.gz", LOG_FILE_NAME, System.currentTimeMillis()));
            compressToGzip(logFile, gzipFile);
            logFile.delete();
        }
        FileUtil.touchNewFile(new File(this.logFilePath));
        try {
            this.currentWriter = new BufferedWriter(new FileWriter(this.logFilePath, Boolean.TRUE));
        } catch (IOException e) {
            LOGGER.error("LocalFileLogger.resetLogFile: " + e.getMessage(), e);
            this.currentWriter = null;
        }
    }

    /**
     * 压缩文件为 gzip 格式
     *
     * @param input  输入文件
     * @param output 输出 gzip 文件
     */
    private void compressToGzip(File input, File output) {
        try (FileInputStream fis = new FileInputStream(input);
                GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            LOGGER.error("LocalFileLogger.compressToGzip: error", e);
        }
    }
}
