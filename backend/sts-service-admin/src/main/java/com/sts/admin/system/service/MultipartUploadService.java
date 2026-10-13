package com.sts.admin.system.service;

import com.sts.admin.system.dto.MultipartCompleteRequest;
import com.sts.admin.system.dto.MultipartInitRequest;
import com.sts.admin.system.vo.MultipartCompleteVO;
import com.sts.admin.system.vo.MultipartInitVO;
import com.sts.admin.system.vo.MultipartPartVO;
import org.springframework.web.multipart.MultipartFile;

public interface MultipartUploadService {
  MultipartInitVO init(Long userId, MultipartInitRequest request);

  MultipartPartVO uploadPart(Long userId, String uploadId, Integer partNumber, MultipartFile file);

  MultipartCompleteVO complete(Long userId, String uploadId, MultipartCompleteRequest request);

  void abort(Long userId, String uploadId);

  int abortExpired();
}
