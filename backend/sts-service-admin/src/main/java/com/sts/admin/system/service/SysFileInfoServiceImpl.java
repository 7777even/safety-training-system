package com.sts.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.FileInfo;
import com.sts.admin.storage.StorageService;
import com.sts.admin.system.converter.SysFileInfoConverter;
import com.sts.admin.system.dto.FileQuery;
import com.sts.admin.system.vo.FileInfoVO;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysFileInfoServiceImpl extends BaseServiceImpl<SysFileInfoMapper, SysFileInfo>
    implements SysFileInfoService {

    private final SysFileInfoMapper fileInfoMapper;
    private final SysFileInfoConverter fileInfoConverter;
    private final StorageService storageService;
  private final AssetWordDocumentValidator wordDocumentValidator;
  private final AssetOriginalFileSizeValidator originalFileSizeValidator;

    @Override
    public PageResult<FileInfoVO> pageFiles(PageRequest<FileQuery> request) {
        FileQuery query = request.getQuery();
        LambdaQueryWrapper<SysFileInfo> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getFileName()), SysFileInfo::getFileName, query.getFileName())
                .eq(StringUtils.hasText(query.getBucketName()), SysFileInfo::getBucketName, query.getBucketName())
                .ge(query.getBeginTime() != null, SysFileInfo::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysFileInfo::getCreateTime, query.getEndTime());
        }
        wrapper.orderByDesc(SysFileInfo::getCreateTime);

        Page<SysFileInfo> page = fileInfoMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        List<FileInfoVO> list = page.getRecords().stream().map(fileInfoConverter::toVO).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional
    public FileInfoVO upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, 5001, "上传文件不能为空");
        }
      if ("course-cover".equals(folder)) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5001, "课程封面必须使用课程封面上传接口");
      }
      originalFileSizeValidator.validateAssetFolderUpload(
        folder, file.getOriginalFilename(), file.getContentType(), file.getSize());
      try {
        wordDocumentValidator.validate(file.getOriginalFilename(), folder, file.getInputStream());
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException("文件读取失败", e);
      }
        FileInfo info = storageService.upload(file, folder);

        SysFileInfo entity = new SysFileInfo();
        entity.setFileName(info.fileName());
        entity.setBucketName(folder);
        entity.setObjectName(info.fileKey());
        entity.setFileSize(info.size());
        entity.setFileSuffix(extractSuffix(info.fileName()));
        entity.setFileUrl(storageService.getUrl(info.fileKey()));
        fileInfoMapper.insert(entity);

        return fileInfoConverter.toVO(entity);
    }

    @Override
    @Transactional
    public void deleteFile(Long fileInfoId) {
        SysFileInfo entity = fileInfoMapper.selectById(fileInfoId);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, 5002, "文件记录不存在");
        }
        storageService.delete(entity.getObjectName());
        this.removeById(fileInfoId);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteFile(id);
        }
    }

    private String extractSuffix(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }
}
