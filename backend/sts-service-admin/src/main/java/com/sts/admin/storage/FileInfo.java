package com.sts.admin.storage;

public record FileInfo(
    String fileKey,
    String fileName,
    long size,
    String contentType
) {
}
