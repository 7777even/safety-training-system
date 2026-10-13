package com.sts.admin.system.service;

import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.system.converter.SysFileInfoConverter;
import com.sts.asset.service.AssetOriginalFileSizeValidator;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SysFileInfoServiceImplTest {

  @Test
  void genericUploadRejectsReservedCourseCoverFolder() {
    SysFileInfoMapper mapper = mock(SysFileInfoMapper.class);
    StorageService storage = mock(StorageService.class);
    SysFileInfoServiceImpl service = new SysFileInfoServiceImpl(
      mapper, mock(SysFileInfoConverter.class), storage, mock(AssetWordDocumentValidator.class),
      new AssetOriginalFileSizeValidator());
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

    assertThatThrownBy(() -> service.upload(file, "course-cover"))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5001));
    verifyNoInteractions(storage, mapper);
  }

  @Test
  void incompatibleAssetDocxIsRejectedBeforeStorageWrite() {
    SysFileInfoMapper mapper = mock(SysFileInfoMapper.class);
    SysFileInfoConverter converter = mock(SysFileInfoConverter.class);
    StorageService storage = mock(StorageService.class);
    AssetWordDocumentValidator validator = mock(AssetWordDocumentValidator.class);
    MockMultipartFile file = new MockMultipartFile(
      "file", "legacy.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      new byte[]{1, 2, 3});
    doThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 5006, "请另存为标准 DOCX"))
      .when(validator).validate(any(), any(), any());
    SysFileInfoServiceImpl service = new SysFileInfoServiceImpl(
      mapper, converter, storage, validator, new AssetOriginalFileSizeValidator());

    assertThatThrownBy(() -> service.upload(file, "asset"))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5006));

    verifyNoInteractions(storage, mapper);
  }

  @Test
  void oversizedAssetImageIsRejectedBeforeStorageWrite() {
    SysFileInfoMapper mapper = mock(SysFileInfoMapper.class);
    SysFileInfoConverter converter = mock(SysFileInfoConverter.class);
    StorageService storage = mock(StorageService.class);
    AssetWordDocumentValidator wordValidator = mock(AssetWordDocumentValidator.class);
    byte[] content = new byte[16 * 1024 * 1024 + 1];
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", content);
    SysFileInfoServiceImpl service = new SysFileInfoServiceImpl(
      mapper, converter, storage, wordValidator, new AssetOriginalFileSizeValidator());

    assertThatThrownBy(() -> service.upload(file, "asset"))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(5007);
        org.assertj.core.api.Assertions.assertThat(error.getMessage()).isEqualTo("图片原始文件不能超过 16MB");
      });

    verifyNoInteractions(storage, mapper, wordValidator);
  }
}
