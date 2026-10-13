package com.sts.course.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.FileInfo;
import com.sts.admin.storage.StorageService;
import com.sts.admin.storage.TenantObjectKeyService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseCoverServiceImplTest {

  private SysFileInfoMapper fileMapper;
  private CourseMapper courseMapper;
  private StorageService storageService;
  private TenantObjectKeyService objectKeys;
  private CourseCoverServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(1L);
    fileMapper = mock(SysFileInfoMapper.class);
    courseMapper = mock(CourseMapper.class);
    storageService = mock(StorageService.class);
    objectKeys = mock(TenantObjectKeyService.class);
    when(objectKeys.isLegacyOrCurrentTenantKey(anyString(), eq("course-cover/")))
      .thenAnswer(invocation -> {
        String key = invocation.getArgument(0);
        return key.startsWith("course-cover/") || key.startsWith("ACME/course-cover/");
      });
    service = new CourseCoverServiceImpl(fileMapper, courseMapper, storageService, objectKeys);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void uploadAcceptsVerifiedPngAndHidesStorageDetails() {
    byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", png);
    when(storageService.upload(file, "course-cover"))
      .thenReturn(new FileInfo("course-cover/2026/08/id.png", "cover.png", png.length, "image/png"));
    doAnswer(invocation -> {
      SysFileInfo entity = invocation.getArgument(0);
      entity.setFileInfoId(88L);
      return 1;
    }).when(fileMapper).insert(any(SysFileInfo.class));

    var result = service.upload(file);

    assertThat(result.getFileInfoId()).isEqualTo(88L);
    assertThat(result.getContentType()).isEqualTo("image/png");
    assertThat(result.getFileName()).isEqualTo("cover.png");
    verify(storageService).upload(file, "course-cover");
  }

  @Test
  void uploadPreservesDatabaseFailureWhenObjectCleanupAlsoFails() {
    byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", png);
    when(storageService.upload(file, "course-cover"))
      .thenReturn(new FileInfo("course-cover/2026/08/123e4567-e89b-12d3-a456-426614174000.png",
                               "cover.png", png.length, "image/png"));
    IllegalStateException databaseFailure = new IllegalStateException("db failed");
    doThrow(databaseFailure).when(fileMapper).insert(any(SysFileInfo.class));
    doThrow(new IllegalStateException("cleanup failed")).when(storageService).delete(any());

    assertThatThrownBy(() -> service.upload(file)).isSameAs(databaseFailure)
      .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
  }

  @Test
  void uploadRejectsExtensionMimeOrSignatureMismatchBeforeStorage() {
    MockMultipartFile fake = new MockMultipartFile(
      "file", "cover.png", "image/png", "not-a-png".getBytes());

    assertThatThrownBy(() -> service.upload(fake))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5090));
    verifyNoInteractions(storageService, fileMapper);
  }

  @Test
  void uploadAcceptsJpegAndWebpSignatures() {
    byte[] jpeg = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
    byte[] webp = new byte[]{'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'};
    MockMultipartFile jpegFile = new MockMultipartFile("file", "cover.jpeg", "image/jpeg", jpeg);
    MockMultipartFile webpFile = new MockMultipartFile("file", "cover.webp", "image/webp", webp);
    when(storageService.upload(eq(jpegFile), eq("course-cover"))).thenReturn(
      new FileInfo("course-cover/2026/08/423e4567-e89b-12d3-a456-426614174000.jpeg",
                   "cover.jpeg", jpeg.length, "image/jpeg"));
    when(storageService.upload(eq(webpFile), eq("course-cover"))).thenReturn(
      new FileInfo("course-cover/2026/08/523e4567-e89b-12d3-a456-426614174000.webp",
                   "cover.webp", webp.length, "image/webp"));

    assertThat(service.upload(jpegFile).getContentType()).isEqualTo("image/jpeg");
    assertThat(service.upload(webpFile).getContentType()).isEqualTo("image/webp");
  }

  @Test
  void uploadRejectsOversizedImageBeforeStorage() {
    MockMultipartFile oversized = new MockMultipartFile(
      "file", "cover.png", "image/png", new byte[(int) CourseCoverServiceImpl.MAX_FILE_SIZE + 1]);

    assertThatThrownBy(() -> service.upload(oversized))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5090));
    verifyNoInteractions(storageService, fileMapper);
  }

  @Test
  void validateReferenceRejectsCrossTenantAndWrongFolder() {
    when(fileMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.validateReference(88L))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5091));

    SysFileInfo wrongFolder = file(89L, "asset", "png");
    when(fileMapper.selectOne(any())).thenReturn(wrongFolder);
    assertThatThrownBy(() -> service.validateReference(89L))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5091));
  }

  @Test
  void validateReferenceRejectsForgedCourseCoverObjectName() {
    SysFileInfo forged = file(90L, "course-cover", "png");
    forged.setObjectName("source/2026/08/123e4567-e89b-12d3-a456-426614174000.png");
    when(fileMapper.selectOne(any())).thenReturn(forged);

    assertThatThrownBy(() -> service.validateReference(90L))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5091));
  }

  @Test
  void validateReferenceAcceptsCurrentTenantPrefixAndRejectsAnotherTenantPrefix() {
    SysFileInfo current = file(91L, "course-cover", "png");
    current.setObjectName("ACME/course-cover/2026/08/123e4567-e89b-12d3-a456-426614174000.png");
    when(fileMapper.selectOne(any())).thenReturn(current);
    service.validateReference(91L);

    SysFileInfo other = file(92L, "course-cover", "png");
    other.setObjectName("OTHER/course-cover/2026/08/123e4567-e89b-12d3-a456-426614174000.png");
    when(fileMapper.selectOne(any())).thenReturn(other);
    assertThatThrownBy(() -> service.validateReference(92L))
      .isInstanceOfSatisfying(BusinessException.class,
                              error -> assertThat(error.getCode()).isEqualTo(5091));
  }

  @Test
  void openUsesCourseAssociationAndCurrentTenant() {
    Course course = new Course();
    course.setCourseId(10L);
    course.setOrganizationId(1L);
    course.setCoverFileId(88L);
    SysFileInfo file = file(88L, "course-cover", "png");
    file.setFileName("安全课程.png");
    file.setObjectName("course-cover/2026/08/123e4567-e89b-12d3-a456-426614174000.png");
    when(courseMapper.selectById(10L)).thenReturn(course);
    when(fileMapper.selectOne(any())).thenReturn(file);
    when(storageService.download(file.getObjectName())).thenReturn(new ByteArrayResource("png".getBytes()));

    var opened = service.open(10L);

    assertThat(opened.fileName()).isEqualTo("安全课程.png");
    assertThat(opened.contentType()).isEqualTo("image/png");
    assertThat(opened.resource()).isNotNull();
  }

  @Test
  void openRejectsCrossTenantCourseWithoutReadingFile() {
    Course course = new Course();
    course.setCourseId(10L);
    course.setOrganizationId(2L);
    course.setCoverFileId(88L);
    when(courseMapper.selectById(10L)).thenReturn(course);

    assertThatThrownBy(() -> service.open(10L))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5011));
    verify(fileMapper, never()).selectOne(any());
    verifyNoInteractions(storageService);
  }

  @Test
  void openMapsMissingAssociatedFileToNotFoundWithoutReadingStorage() {
    Course course = new Course();
    course.setCourseId(10L);
    course.setOrganizationId(1L);
    course.setCoverFileId(88L);
    when(courseMapper.selectById(10L)).thenReturn(course);
    when(fileMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.open(10L))
      .isInstanceOf(BusinessException.class)
      .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(5092));
    verifyNoInteractions(storageService);
  }

  @Test
  void openMapsMissingOrFailedStorageObjectToStableNotFound() {
    Course course = new Course();
    course.setCourseId(10L);
    course.setOrganizationId(1L);
    course.setCoverFileId(88L);
    SysFileInfo file = file(88L, "course-cover", "png");
    when(courseMapper.selectById(10L)).thenReturn(course);
    when(fileMapper.selectOne(any())).thenReturn(file);
    Resource missing = mock(Resource.class);
    when(missing.exists()).thenReturn(false);
    when(storageService.download(file.getObjectName())).thenReturn(missing);

    assertThatThrownBy(() -> service.open(10L))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(5092);
        assertThat(error.getMessage()).isEqualTo("课程封面不存在");
      });

    when(storageService.download(file.getObjectName()))
      .thenThrow(new IllegalStateException("secret-bucket/course-cover/object.png"));
    assertThatThrownBy(() -> service.open(10L))
      .isInstanceOfSatisfying(BusinessException.class, error -> {
        assertThat(error.getCode()).isEqualTo(5092);
        assertThat(error.getMessage()).isEqualTo("课程封面不存在");
      });
  }

  private static SysFileInfo file(Long id, String folder, String suffix) {
    SysFileInfo file = new SysFileInfo();
    file.setFileInfoId(id);
    file.setBucketName(folder);
    file.setFileSuffix(suffix);
    file.setFileName("cover." + suffix);
    file.setObjectName(folder + "/2026/08/123e4567-e89b-12d3-a456-426614174000." + suffix);
    return file;
  }
}
