package com.sts.admin.storage;

import java.io.InputStream;

public record StorageObject(InputStream inputStream, long size, String contentType) {
}
