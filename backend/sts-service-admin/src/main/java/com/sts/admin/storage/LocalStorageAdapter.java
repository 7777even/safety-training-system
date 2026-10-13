package com.sts.admin.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class LocalStorageAdapter implements StorageService {

    private final StorageProperties props;
  private final TenantObjectKeyService objectKeys;

    @Override
    public FileInfo upload(MultipartFile file, String folder) {
        String ext = getExtension(file);
      String prefix = "course-cover".equals(folder)
        ? folder + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
        : folder;
      String fileKey = objectKeys.forCurrentTenant(prefix + "/" + UUID.randomUUID() + ext);
        Path target = Paths.get(props.getLocal().getBasePath(), fileKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("文件存储失败", e);
        }
        return new FileInfo(fileKey, file.getOriginalFilename(), file.getSize(), file.getContentType());
    }

    @Override
    public Resource download(String fileKey) {
        Path path = Paths.get(props.getLocal().getBasePath(), fileKey);
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String fileKey) {
        Path path = Paths.get(props.getLocal().getBasePath(), fileKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件失败: {}", fileKey, e);
        }
    }

    @Override
    public String getUrl(String fileKey) {
        return props.getLocal().getUrlPrefix() + "/" + fileKey;
    }

    private String getExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf(".")).toLowerCase();
        }
        return "";
    }
}
