package com.example.testapp.network;

import java.util.List;

/**
 * API响应模型
 */
public class ApiResponse {

    /** 是否成功 */
    private boolean success;

    /** 响应消息 */
    private String message;

    /** 是否有新文件 */
    private boolean hasNewFiles;

    /** 文件列表 */
    private List<FileInfo> files;

    /** 设备列表 */
    private List<DeviceInfo> devices;

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isHasNewFiles() {
        return hasNewFiles;
    }

    public void setHasNewFiles(boolean hasNewFiles) {
        this.hasNewFiles = hasNewFiles;
    }

    public List<FileInfo> getFiles() {
        return files;
    }

    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }

    public List<DeviceInfo> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceInfo> devices) {
        this.devices = devices;
    }

    /**
     * 文件信息
     */
    public static class FileInfo {
        private String name;
        private long size;
        private String uploadTime;
        private String content; // Used for download

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getUploadTime() {
            return uploadTime;
        }

        public void setUploadTime(String uploadTime) {
            this.uploadTime = uploadTime;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 设备信息
     */
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private String lastSeen;
        private String status;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        public String getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(String lastSeen) {
            this.lastSeen = lastSeen;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}