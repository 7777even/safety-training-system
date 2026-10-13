package com.sts.admin.storage;

import org.springframework.core.io.Resource;

/**
 * 受控下载的真实存储对象。
 */
public record StoredDownload(Resource resource, String fileName, String contentType, long fileSize) {
}
