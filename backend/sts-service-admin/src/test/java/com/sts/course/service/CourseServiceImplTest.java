package com.sts.course.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseConverter;
import com.sts.course.dto.CategoryAssignRequest;
import com.sts.course.dto.ContentModeSwitchRequest;
import com.sts.course.dto.CourseCreateRequest;
import com.sts.course.dto.CourseUpdateRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link CourseServiceImpl} 业务逻辑单元测试。纯 Mockito，零 DB。
 * <p>
 * 覆盖：创建课程时自动建默认章节、课程编码冲突、删除级联章节与分类关联、
 * 内容模式切换（幂等 + 触发章节合并）、分类关联的机构校验。
 */
class CourseServiceImplTest {

  private static final Long ORG_ID = 1L;

  private CourseMapper courseMapper;
  private CourseConverter courseConverter;
  private CourseChapterMapper chapterMapper;
  private CourseChapterService chapterService;
  private CourseItemService itemService;
  private CourseDownloadAssetService downloadAssetService;
  private CourseCourseCategoryMapper courseCategoryRelMapper;
  private CourseCategoryMapper categoryMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseCoverService coverService;
  private CourseServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    courseMapper = mock(CourseMapper.class);
    courseConverter = mock(CourseConverter.class);
    chapterMapper = mock(CourseChapterMapper.class);
    chapterService = mock(CourseChapterService.class);
    itemService = mock(CourseItemService.class);
    downloadAssetService = mock(CourseDownloadAssetService.class);
    courseCategoryRelMapper = mock(CourseCourseCategoryMapper.class);
    categoryMapper = mock(CourseCategoryMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    coverService = mock(CourseCoverService.class);
    service = new CourseServiceImpl(courseMapper, courseConverter,
                                    chapterMapper, chapterService, itemService, downloadAssetService,
                                    courseCategoryRelMapper, categoryMapper, offeringMapper, coverService, org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  void createCourse_invalidTeacherRejected() {
    CourseCreateRequest request = new CourseCreateRequest();
    request.setTeacherUserId(99L);
    when(courseConverter.toEntity(any())).thenReturn(new Course());
    assertThatThrownBy(() -> service.createCourse(request)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("createCourse：成功创建后自动生成唯一默认章节，publishStatus 恒为 DRAFT")
  void createCourse_createsDefaultChapter() {
    Course entity = new Course();
    when(courseConverter.toEntity(any(CourseCreateRequest.class))).thenReturn(entity);

    CourseCreateRequest request = new CourseCreateRequest();
    request.setCourseCode("SAFETY-001");
    request.setCourseName("安全教育入门");

    service.createCourse(request);

    // 租户归属由租户插件在写入时填充，零 DB 场景不体现；跨租户边界见 TenantIsolationIT
    assertThat(entity.getPublishStatus()).isEqualTo("DRAFT");
    verify(courseMapper).insert(entity);

    ArgumentCaptor<CourseChapter> chapterCaptor = ArgumentCaptor.forClass(CourseChapter.class);
    verify(chapterMapper).insert(chapterCaptor.capture());
    CourseChapter defaultChapter = chapterCaptor.getValue();
    assertThat(defaultChapter.getParentId()).isEqualTo(0L);
    assertThat(defaultChapter.getAncestors()).isEqualTo("0");
    assertThat(defaultChapter.getChapterName()).isEqualTo("默认章节");
  }

  @Test
  @DisplayName("createCourse：提供封面时先校验课程封面引用")
  void createCourse_withCover_validatesReference() {
    Course entity = new Course();
    when(courseConverter.toEntity(any(CourseCreateRequest.class))).thenReturn(entity);
    CourseCreateRequest request = new CourseCreateRequest();
    request.setCourseCode("SAFETY-COVER");
    request.setCourseName("带封面的课程");
    request.setCoverFileId(88L);

    service.createCourse(request);

    verify(coverService).validateReference(88L);
  }

  @Test
  @DisplayName("updateCourse：替换封面时先校验引用并执行乐观锁更新")
  void updateCourse_withCover_validatesReference() {
    Course course = new Course();
    course.setCourseId(1L);
    when(courseMapper.selectOne(any())).thenReturn(course);
    CourseUpdateRequest request = new CourseUpdateRequest();
    request.setCoverFileId(88L);
    request.setVersion(3);

    service.updateCourse(1L, request);

    verify(coverService).validateReference(88L);
    verify(courseConverter).updateEntity(course, request);
    verify(courseMapper).updateByIdWithVersionCheck(course);
  }

  @Test
  @DisplayName("createCourse：课程编码冲突 → 409 / 5012，不建默认章节")
  void createCourse_duplicateCode_throws409() {
    when(courseConverter.toEntity(any(CourseCreateRequest.class))).thenReturn(new Course());
    doThrow(new DuplicateKeyException("dup")).when(courseMapper).insert(any(Course.class));

    CourseCreateRequest request = new CourseCreateRequest();
    request.setCourseCode("SAFETY-001");
    request.setCourseName("安全教育入门");

    assertThatThrownBy(() -> service.createCourse(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5012));
    verify(chapterMapper, never()).insert(any(CourseChapter.class));
  }

  @Test
  @DisplayName("getCourse：课程不存在 → 404 / 5011")
  void getCourse_notFound_throws404() {
    when(courseMapper.selectById(1L)).thenReturn(null);

    assertThatThrownBy(() -> service.getCourse(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5011));
  }

  @Test
  @DisplayName("getCourse：本机构课程返回详情")
  void getCourse_sameOrganization_returnsDetail() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    com.sts.course.vo.CourseVO vo = new com.sts.course.vo.CourseVO();
    when(courseMapper.selectById(1L)).thenReturn(course);
    when(courseConverter.toVO(course)).thenReturn(vo);
    when(courseCategoryRelMapper.selectList(any())).thenReturn(List.of());

    assertThat(service.getCourse(1L)).isSameAs(vo);
    assertThat(vo.getCategoryIds()).isEmpty();
  }

  @Test
  @DisplayName("getCourse：跨机构课程一律按不存在处理 → 404 / 5011")
  void getCourse_crossOrg_throws5011() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(99L);
    when(courseMapper.selectById(1L)).thenReturn(course);
    when(courseConverter.toVO(course)).thenReturn(new com.sts.course.vo.CourseVO());
    when(courseCategoryRelMapper.selectList(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.getCourse(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5011));
    verify(courseConverter, never()).toVO(any());
  }

  @Test
  @DisplayName("deleteCourse：课程不存在 → 404 / 5011，不触发级联删除")
  void deleteCourse_notFound_throws404() {
    when(courseMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> service.deleteCourse(1L))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5011));
    verify(chapterService, never()).deleteAllChaptersOfCourse(any());
    verify(courseCategoryRelMapper, never()).delete(any());
  }

  // deleteCourse 成功路径（级联删除章节、分类关联，再逻辑删除课程本身）以 this.removeById 收尾，
  // 依赖 BaseServiceImpl 的真实 MyBatis 表元信息（纯 Mockito 构造的 Service 拿不到），
  // 该行为由 CourseCategoryMapperIT 同类的 Testcontainers 真库集成测试覆盖，不在本单测重复验证。

  @Test
  @DisplayName("switchContentMode：目标模式与当前一致 → 幂等更新，不触发章节合并")
  void switchContentMode_samMode_isIdempotent() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    course.setContentMode("MULTI_CHAPTER");
    when(courseMapper.selectOne(any())).thenReturn(course);

    ContentModeSwitchRequest request = new ContentModeSwitchRequest();
    request.setContentMode("MULTI_CHAPTER");
    request.setVersion(0);

    service.switchContentMode(1L, request);

    verify(chapterService, never()).mergeChaptersToSingle(any());
    verify(courseMapper).updateByIdWithVersionCheck(course);
  }

  @Test
  @DisplayName("switchContentMode：切到 SINGLE → 触发章节合并")
  void switchContentMode_toSingle_triggersMerge() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    course.setContentMode("MULTI_CHAPTER");
    when(courseMapper.selectOne(any())).thenReturn(course);

    ContentModeSwitchRequest request = new ContentModeSwitchRequest();
    request.setContentMode("SINGLE");
    request.setVersion(0);

    service.switchContentMode(1L, request);

    verify(chapterService).mergeChaptersToSingle(1L);
    assertThat(course.getContentMode()).isEqualTo("SINGLE");
  }

  @Test
  @DisplayName("assignCategories：引用的分类不属于当前机构 → 400 / 5013，不写关联")
  void assignCategories_categoryNotInOrganization_throws400() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    when(courseMapper.selectOne(any())).thenReturn(course);
    when(categoryMapper.selectCount(any())).thenReturn(1L); // 只匹配到 1 个，但请求了 2 个

    CategoryAssignRequest request = new CategoryAssignRequest();
    request.setCategoryIds(List.of(10L, 20L));

    assertThatThrownBy(() -> service.assignCategories(1L, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5013));
    verify(courseCategoryRelMapper, never()).insert(any(com.sts.course.entity.CourseCourseCategory.class));
  }

  @Test
  @DisplayName("switchContentMode：存在有效开课时 → 400 / 5065，不触发章节合并")
  void switchContentMode_activeOffering_throws5065() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    course.setContentMode("MULTI_CHAPTER");
    when(courseMapper.selectOne(any())).thenReturn(course);
    when(offeringMapper.selectCount(any())).thenReturn(1L);

    ContentModeSwitchRequest request = new ContentModeSwitchRequest();
    request.setContentMode("SINGLE");
    request.setVersion(0);

    assertThatThrownBy(() -> service.switchContentMode(1L, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5065));
    verify(chapterService, never()).mergeChaptersToSingle(any());
    verify(courseMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("assignCategories：全部分类属于当前机构 → 先清空再逐条写入")
  void assignCategories_valid_replacesAll() {
    Course course = new Course();
    course.setCourseId(1L);
    course.setOrganizationId(ORG_ID);
    when(courseMapper.selectOne(any())).thenReturn(course);
    when(categoryMapper.selectCount(any())).thenReturn(2L);

    CategoryAssignRequest request = new CategoryAssignRequest();
    request.setCategoryIds(List.of(10L, 20L));

    service.assignCategories(1L, request);

    verify(courseCategoryRelMapper).delete(any());
    verify(courseCategoryRelMapper, times(2)).insert(any(com.sts.course.entity.CourseCourseCategory.class));
  }
}
