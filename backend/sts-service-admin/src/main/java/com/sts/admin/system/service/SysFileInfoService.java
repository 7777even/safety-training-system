package com.sts.admin.system.service;

import com.sts.admin.system.dto.FileQuery;
import com.sts.admin.system.vo.FileInfoVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysFileInfoService {

    PageResult<FileInfoVO> pageFiles(PageRequest<FileQuery> request);

    FileInfoVO upload(MultipartFile file, String folder);

    void deleteFile(Long fileInfoId);

    void batchDelete(List<Long> ids);
}
