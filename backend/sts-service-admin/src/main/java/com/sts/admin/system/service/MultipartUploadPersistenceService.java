package com.sts.admin.system.service;

import com.sts.admin.entity.FileMultipartUpload;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.FileMultipartUploadMapper;
import com.sts.admin.mapper.SysFileInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MultipartUploadPersistenceService {
  private final FileMultipartUploadMapper sessionMapper;
  private final SysFileInfoMapper fileInfoMapper;

  @Transactional
  public Long complete(FileMultipartUpload session, SysFileInfo fileInfo) {
    fileInfoMapper.insert(fileInfo);
    session.setFileInfoId(fileInfo.getFileInfoId());
    session.setUploadStatus("COMPLETED");
    session.setCompletedTime(LocalDateTime.now());
    sessionMapper.updateByIdWithVersionCheck(session);
    return fileInfo.getFileInfoId();
  }
}
