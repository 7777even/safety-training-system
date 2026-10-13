package com.sts.admin.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    FileInfo upload(MultipartFile file, String folder);

    Resource download(String fileKey);

    void delete(String fileKey);

    String getUrl(String fileKey);
}
