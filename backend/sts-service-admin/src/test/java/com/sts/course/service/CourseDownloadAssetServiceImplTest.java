package com.sts.course.service;

import com.sts.asset.entity.Asset;
import com.sts.asset.service.AssetReferenceService;
import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseDownloadAssetConverter;
import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.vo.DownloadAssetVO;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseDownloadAssetServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long COURSE_ID = 10L;

  private CourseDownloadAssetMapper downloadAssetMapper;
  private CourseDownloadAssetConverter downloadAssetConverter;
  private CourseMapper courseMapper;
  private AssetReferenceService assetReferenceService;
  private CourseDownloadAssetServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    downloadAssetMapper = mock(CourseDownloadAssetMapper.class);
    downloadAssetConverter = mock(CourseDownloadAssetConverter.class);
    courseMapper = mock(CourseMapper.class);
    assetReferenceService = mock(AssetReferenceService.class);
    service = new CourseDownloadAssetServiceImpl(
      downloadAssetMapper, downloadAssetConverter, courseMapper, assetReferenceService);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("listByCourse：返回按 sortOrder 排序的 VO 列表")
  void listByCourse_success() {
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID));
    CourseDownloadAsset row = downloadAsset(1L, 100L, 0);
    when(downloadAssetMapper.selectList(any())).thenReturn(List.of(row));
    DownloadAssetVO vo = new DownloadAssetVO();
    vo.setCourseDownloadAssetId(1L);
    vo.setAssetId(100L);
    when(downloadAssetConverter.toVO(row)).thenReturn(vo);

    List<DownloadAssetVO> result = service.listByCourse(COURSE_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getAssetId()).isEqualTo(100L);
    verify(downloadAssetConverter).toVO(row);
  }

  @Test
  @DisplayName("replaceAll：全量替换成功")
  void replaceAll_success() {
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID));
    when(assetReferenceService.requireUsableAsset(100L)).thenReturn(new Asset());
    when(assetReferenceService.requireUsableAsset(101L)).thenReturn(new Asset());
    CourseDownloadAsset existing = downloadAsset(5L, 99L, 0);
    when(downloadAssetMapper.selectList(any())).thenReturn(List.of(existing));
    CourseDownloadAsset entity1 = downloadAsset(null, 100L, 0);
    CourseDownloadAsset entity2 = downloadAsset(null, 101L, 1);
    when(downloadAssetConverter.toEntity(any(DownloadAssetReplaceRequest.DownloadAssetEntry.class)))
      .thenReturn(entity1, entity2);

    CourseDownloadAssetServiceImpl spyService = spy(service);
    doReturn(true).when(spyService).removeById(5L);

    DownloadAssetReplaceRequest request = replaceRequest(
      entry(100L, 0), entry(101L, 1));
    spyService.replaceAll(COURSE_ID, request);

    verify(spyService).removeById(5L);
    ArgumentCaptor<CourseDownloadAsset> captor = ArgumentCaptor.forClass(CourseDownloadAsset.class);
    verify(downloadAssetMapper, times(2)).insert(captor.capture());
    assertThat(captor.getAllValues()).extracting(CourseDownloadAsset::getCourseId).containsOnly(COURSE_ID);
  }

  @Test
  @DisplayName("replaceAll：重复 assetId → 400 / 5038")
  void replaceAll_duplicateAssetIds_throws5038() {
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID));

    DownloadAssetReplaceRequest request = replaceRequest(
      entry(100L, 0), entry(100L, 1));

    assertThatThrownBy(() -> service.replaceAll(COURSE_ID, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5038));

    verify(assetReferenceService, times(1)).requireUsableAsset(100L);
    verify(downloadAssetMapper, never()).insert(any(CourseDownloadAsset.class));
  }

  @Test
  @DisplayName("replaceAll：无效素材经 AssetReferenceService 抛出")
  void replaceAll_invalidAsset_propagatesFromAssetReferenceService() {
    when(courseMapper.selectOne(any())).thenReturn(course(COURSE_ID));
    when(assetReferenceService.requireUsableAsset(100L))
      .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 6011, "素材不存在"));

    DownloadAssetReplaceRequest request = replaceRequest(entry(100L, 0));

    assertThatThrownBy(() -> service.replaceAll(COURSE_ID, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(6011));

    verify(downloadAssetMapper, never()).insert(any(CourseDownloadAsset.class));
  }

  private static Course course(Long courseId) {
    Course course = new Course();
    course.setCourseId(courseId);
    course.setOrganizationId(ORG_ID);
    return course;
  }

  private static CourseDownloadAsset downloadAsset(Long id, Long assetId, int sortOrder) {
    CourseDownloadAsset entity = new CourseDownloadAsset();
    entity.setCourseDownloadAssetId(id);
    entity.setCourseId(COURSE_ID);
    entity.setAssetId(assetId);
    entity.setSortOrder(sortOrder);
    entity.setStatus(1);
    return entity;
  }

  private static DownloadAssetReplaceRequest replaceRequest(
    DownloadAssetReplaceRequest.DownloadAssetEntry... entries) {
    DownloadAssetReplaceRequest request = new DownloadAssetReplaceRequest();
    request.setAssets(List.of(entries));
    return request;
  }

  private static DownloadAssetReplaceRequest.DownloadAssetEntry entry(Long assetId, int sortOrder) {
    DownloadAssetReplaceRequest.DownloadAssetEntry entry = new DownloadAssetReplaceRequest.DownloadAssetEntry();
    entry.setAssetId(assetId);
    entry.setSortOrder(sortOrder);
    entry.setStatus(1);
    return entry;
  }
}
