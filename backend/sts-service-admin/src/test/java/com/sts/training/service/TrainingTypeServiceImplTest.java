package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingTypeCreateRequest;
import com.sts.training.dto.TrainingTypeUpdateRequest;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingTypeServiceImplTest {

  private TrainingTypeMapper trainingTypeMapper;
  private SysFileInfoMapper fileInfoMapper;
  private StorageService storageService;
  private TrainingTypeServiceImpl service;

  @BeforeEach
  void setUp() {
    trainingTypeMapper = mock(TrainingTypeMapper.class);
    fileInfoMapper = mock(SysFileInfoMapper.class);
    storageService = mock(StorageService.class);
    service = new TrainingTypeServiceImpl(trainingTypeMapper, fileInfoMapper, storageService);
  }

  @Test
  @DisplayName("update：版本冲突返回 409/8003")
  void update_versionConflict_throws() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setVersion(2);
    when(trainingTypeMapper.selectById(1L)).thenReturn(type);
    when(trainingTypeMapper.updateById(any(TrainingType.class))).thenReturn(0);

    assertThatThrownBy(() -> service.update(1L, updateRequest(1)))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 8003);
  }

  @Test
  @DisplayName("update：版本一致时正常更新")
  void update_versionMatched_ok() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setVersion(1);
    when(trainingTypeMapper.selectById(1L)).thenReturn(type);
    when(trainingTypeMapper.updateById(any(TrainingType.class))).thenReturn(1);

    assertThatCode(() -> service.update(1L, updateRequest(1))).doesNotThrowAnyException();
    verify(trainingTypeMapper).updateById(type);
  }

  @Test
  @DisplayName("create：写入 coverFileId")
  void create_persistsCoverFileId() {
    when(trainingTypeMapper.insert(any(TrainingType.class))).thenAnswer(invocation -> {
      TrainingType type = invocation.getArgument(0);
      type.setTrainingTypeId(8L);
      return 1;
    });

    TrainingTypeCreateRequest request = new TrainingTypeCreateRequest();
    request.setTypeCode("T1");
    request.setTypeName("高压电工作业");
    request.setCoverFileId(900L);
    request.setInitialHours(new BigDecimal("80"));
    request.setReviewHours(new BigDecimal("24"));
    request.setStatus(1);
    request.setDescription("类型");

    assertThat(service.create(request)).isEqualTo(8L);
    ArgumentCaptor<TrainingType> captor = ArgumentCaptor.forClass(TrainingType.class);
    verify(trainingTypeMapper).insert(captor.capture());
    assertThat(captor.getValue().getCoverFileId()).isEqualTo(900L);
  }

  @Test
  @DisplayName("openCover：按类型 coverFileId 返回原始文件")
  void openCover_returnsTypeOwnedFile() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setCoverFileId(900L);
    when(trainingTypeMapper.selectById(1L)).thenReturn(type);
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(900L);
    file.setFileName("type-cover.png");
    file.setObjectName("DEMO/training-type/cover.png");
    file.setFileSize(4L);
    file.setFileSuffix("png");
    file.setVersion(1);
    when(fileInfoMapper.selectById(900L)).thenReturn(file);
    ByteArrayResource resource = new ByteArrayResource("png!".getBytes());
    when(storageService.download("DEMO/training-type/cover.png")).thenReturn(resource);

    TrainingCoverFile cover = service.openCover(1L);

    assertThat(cover.fileName()).isEqualTo("type-cover.png");
    assertThat(cover.contentType()).isEqualTo("image/png");
    assertThat(cover.fileSize()).isEqualTo(4L);
    assertThat(cover.etag()).isEqualTo("\"900-1-4\"");
    assertThat(cover.resource()).isSameAs(resource);
  }

  @Test
  @DisplayName("openCover：无封面返回 404/8164")
  void openCover_missingFile_throws8164() {
    TrainingType type = new TrainingType();
    type.setTrainingTypeId(1L);
    type.setCoverFileId(null);
    when(trainingTypeMapper.selectById(1L)).thenReturn(type);

    assertThatThrownBy(() -> service.openCover(1L))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(error.getCode()).isEqualTo(8164);
      });
    verifyNoInteractions(storageService);
  }

  private TrainingTypeUpdateRequest updateRequest(Integer version) {
    TrainingTypeUpdateRequest req = new TrainingTypeUpdateRequest();
    req.setVersion(version);
    req.setTypeName("类型1");
    req.setStatus(1);
    req.setDescription("desc");
    return req;
  }
}
