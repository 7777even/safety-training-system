package com.sts.admin.storage;

public record UploadedStoragePart(int partNumber, String etag, long size) {
}
