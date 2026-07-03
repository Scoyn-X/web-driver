package com.jiayuan.boot.common.util;

/**
 * 文件相关工具类
 *
 * @author didongchen
 * @since 2026/05/15
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 格式化文件大小展示
     *
     * @param bytes 字节数
     * @return 格式化后的字符串
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

}
