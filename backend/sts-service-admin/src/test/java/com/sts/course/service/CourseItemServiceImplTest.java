package com.sts.course.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.asset.entity.Asset;
import com.sts.asset.service.AssetReferenceService;
import com.sts.common.exception.BusinessException;
import com.sts.course.converter.CourseItemConverter;
import com.sts.course.converter.CourseLiveSessionConverter;
import com.sts.course.dto.ItemCreateRequest;
import com.sts.course.dto.ItemUpdateRequest;
import com.sts.course.dto.LiveSessionCreatePayload;
import com.sts.course.entity.CourseChapter;
import com.sts.course.entity.CourseItem;
import com.sts.course.entity.CourseItemQuestionBank;
import com.sts.course.entity.CourseLiveSession;
import com.sts.course.mapper.*;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseItemServiceImplTest {

  private static final Long ORG_ID = 1L;

  private CourseItemMapper itemMapper;
  private CourseLiveSessionMapper liveSessionMapper;
  private CourseChapterMapper chapterMapper;
  private CourseMapper courseMapper;
  private CourseItemConverter itemConverter;
  private CourseLiveSessionConverter liveSessionConverter;
  private AssetReferenceService assetReferenceService;
  private CourseItemProgressMapper itemProgressMapper;
  private CourseOfferingMapper offeringMapper;
  private CourseItemQuestionBankMapper itemQuestionBankMapper;
  private QuestionBankMapper questionBankMapper;
  private PaperVersionMapper paperVersionMapper;
  private PaperMapper paperMapper;
  private CourseItemServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    itemMapper = mock(CourseItemMapper.class);
    doAnswer(inv -> itemMapper.selectById((Long) inv.getArgument(0))).when(itemMapper).selectCurrent(any());
    liveSessionMapper = mock(CourseLiveSessionMapper.class);
    chapterMapper = mock(CourseChapterMapper.class);
    doAnswer(inv -> chapterMapper.selectById((Long) inv.getArgument(0))).when(chapterMapper).selectCurrent(any());
    courseMapper = mock(CourseMapper.class);
    itemConverter = mock(CourseItemConverter.class);
    liveSessionConverter = mock(CourseLiveSessionConverter.class);
    assetReferenceService = mock(AssetReferenceService.class);
    itemProgressMapper = mock(CourseItemProgressMapper.class);
    offeringMapper = mock(CourseOfferingMapper.class);
    itemQuestionBankMapper = mock(CourseItemQuestionBankMapper.class);
    questionBankMapper = mock(QuestionBankMapper.class);
    paperVersionMapper = mock(PaperVersionMapper.class);
    paperMapper = mock(PaperMapper.class);
    service = new CourseItemServiceImpl(itemMapper,
                                        liveSessionMapper,
                                        chapterMapper,
                                        courseMapper,
                                        itemConverter,
                                        liveSessionConverter,
                                        assetReferenceService,
                                        itemProgressMapper,
                                        offeringMapper,
                                        itemQuestionBankMapper,
                                        questionBankMapper,
                                        paperVersionMapper,
                                        paperMapper);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("create：未开放类型 → 400 / 5032")
  void create_unsupportedType_throws5032() {
    ItemCreateRequest request = new ItemCreateRequest();
    request.setItemType("SIMULATION");
    request.setChapterId(1L);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5032));
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  @DisplayName("create EXAM：旧课程考试类型不再开放 → 400 / 5032")
  void create_exam_legacyType_throws5032() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    ItemCreateRequest request = new ItemCreateRequest();
    request.setItemType("EXAM");
    request.setChapterId(1L);
    request.setItemTitle("期末考");
    request.setIsRequired(1);
    request.setMinimumStudySeconds(0);
    request.setSortOrder(0);
    request.setStatus(1);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5032));
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  @DisplayName("create QUIZ：直接引用本机构已发布试卷版本")
  void create_quiz_insertsWithPublishedPaperVersion() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(88L);
    version.setPaperId(77L);
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(88L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperId(77L);
    paper.setOrganizationId(ORG_ID);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    when(paperMapper.selectById(77L)).thenReturn(paper);
    CourseItem entity = new CourseItem();
    when(itemConverter.toEntity(any(ItemCreateRequest.class))).thenReturn(entity);

    ItemCreateRequest request = new ItemCreateRequest();
    request.setItemType("QUIZ");
    request.setChapterId(1L);
    request.setItemTitle("期末考");
    request.setPaperVersionId(88L);
    request.setIsRequired(1);
    request.setMinimumStudySeconds(0);
    request.setSortOrder(0);
    request.setStatus(1);

    service.create(request);

    ArgumentCaptor<CourseItem> captor = ArgumentCaptor.forClass(CourseItem.class);
    verify(itemMapper).insert(captor.capture());
    assertThat(captor.getValue().getPaperVersionId()).isEqualTo(88L);
    assertThat(captor.getValue().getAssetId()).isNull();
    verify(itemQuestionBankMapper, never()).insert(any(CourseItemQuestionBank.class));
  }

  @Test
  @DisplayName("create QUESTION_BANK：直接引用可用题库并写入唯一关联")
  void create_questionBank_insertsDirectReference() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    QuestionBank bank = new QuestionBank();
    bank.setQuestionBankId(66L);
    bank.setOrganizationId(ORG_ID);
    bank.setArchiveStatus("ACTIVE");
    bank.setStatus(1);
    when(questionBankMapper.selectById(66L)).thenReturn(bank);
    CourseItem entity = new CourseItem();
    entity.setCourseItemId(99L);
    when(itemConverter.toEntity(any(ItemCreateRequest.class))).thenReturn(entity);

    ItemCreateRequest request = new ItemCreateRequest();
    request.setItemType("QUESTION_BANK");
    request.setChapterId(1L);
    request.setItemTitle("安全生产题库");
    request.setQuestionBankId(66L);
    request.setIsRequired(1);
    request.setMinimumStudySeconds(0);
    request.setSortOrder(0);
    request.setStatus(1);

    service.create(request);

    verify(itemMapper).insert(entity);
    ArgumentCaptor<CourseItemQuestionBank> relation = ArgumentCaptor.forClass(CourseItemQuestionBank.class);
    verify(itemQuestionBankMapper).insert(relation.capture());
    assertThat(relation.getValue().getCourseItemId()).isEqualTo(99L);
    assertThat(relation.getValue().getQuestionBankId()).isEqualTo(66L);
  }

  @Test
  @DisplayName("create QUIZ：其他机构试卷版本不可引用 → 404 / 5040")
  void create_quizFromOtherOrganization_throws5040() {
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(88L);
    version.setPaperId(77L);
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(88L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperId(77L);
    paper.setOrganizationId(99L);
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    when(paperMapper.selectById(77L)).thenReturn(paper);
    when(itemConverter.toEntity(any())).thenReturn(new CourseItem());
    ItemCreateRequest request = new ItemCreateRequest();
    request.setChapterId(1L);
    request.setItemType("QUIZ");
    request.setPaperVersionId(88L);
    request.setMinimumStudySeconds(0);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5040));
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  @DisplayName("update QUESTION_BANK：试卷版本字段与类型不匹配 → 400 / 5042")
  void update_questionBankWithPaperVersion_throws5042() {
    when(itemMapper.selectById(1L)).thenReturn(item(1L, 10L, 1L, "QUESTION_BANK"));
    ItemUpdateRequest request = new ItemUpdateRequest();
    request.setPaperVersionId(88L);
    request.setMinimumStudySeconds(0);
    request.setVersion(0);

    assertThatThrownBy(() -> service.update(1L, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5042));
    verify(itemMapper, never()).updateByIdWithVersionCheck(any());
  }

  @Test
  @DisplayName("create ASSET：缺少可用素材 → 经 AssetReferenceService 校验")
  void create_asset_requiresUsableAsset() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    when(assetReferenceService.requireUsableAsset(100L)).thenReturn(new Asset());

    CourseItem entity = new CourseItem();
    when(itemConverter.toEntity(any(ItemCreateRequest.class))).thenReturn(entity);

    ItemCreateRequest request = assetRequest(100L);
    service.create(request);

    verify(assetReferenceService).requireUsableAsset(100L);
    verify(itemMapper).insert(entity);
  }

  @Test
  @DisplayName("create ASSET：要求学习时长必须大于 0")
  void create_asset_requiresPositiveMinimumStudySeconds() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    ItemCreateRequest request = assetRequest(100L);
    request.setMinimumStudySeconds(0);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5033));
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  @DisplayName("create LIVE：要求学习时长必须为 0")
  void create_liveRejectsNonZeroMinimumStudySeconds() {
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    ItemCreateRequest request = liveRequest();
    request.setMinimumStudySeconds(60);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5033));
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  @DisplayName("create LIVE：同事务插入内容项与 DRAFT 直播场次")
  void create_live_insertsItemAndSession() {
    CourseChapter chapter = chapter(1L, 10L);
    when(chapterMapper.selectById(1L)).thenReturn(chapter);
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());

    CourseItem entity = new CourseItem();
    when(itemConverter.toEntity(any(ItemCreateRequest.class))).thenReturn(entity);
    CourseLiveSession session = new CourseLiveSession();
    when(liveSessionConverter.toEntity(any(LiveSessionCreatePayload.class))).thenReturn(session);

    ItemCreateRequest request = liveRequest();
    service.create(request);

    verify(itemMapper).insert(entity);
    ArgumentCaptor<CourseLiveSession> captor = ArgumentCaptor.forClass(CourseLiveSession.class);
    verify(liveSessionMapper).insert(captor.capture());
    // 租户归属由租户插件在写入时填充，零 DB 场景不体现；跨租户边界见 TenantIsolationIT
  }

  @Test
  @DisplayName("delete：级联软删直播场次")
  void delete_cascadesRelatedRows() {
    CourseItemServiceImpl spyService = spy(service);
    CourseItem item = item(1L, 10L, 1L, "LIVE");
    when(itemMapper.selectById(1L)).thenReturn(item);
    CourseLiveSession session = new CourseLiveSession();
    session.setLiveSessionId(99L);
    when(liveSessionMapper.selectOne(any())).thenReturn(session);
    doReturn(true).when(spyService).removeById(1L);

    spyService.delete(1L);

    verify(liveSessionMapper).update(isNull(), any());
  }

  @Test
  @DisplayName("moveAllCourseItemsToChapter：按原顺序重编号 sortOrder")
  void moveAllCourseItemsToChapter_renumbersSortOrder() {
    CourseChapter target = chapter(2L, 10L);
    when(chapterMapper.selectById(2L)).thenReturn(target);
    when(chapterMapper.selectList(any())).thenReturn(List.of(chapter(1L, 10L), target));

    CourseItem first = item(11L, 10L, 1L, "ASSET");
    first.setSortOrder(0);
    CourseItem second = item(12L, 10L, 2L, "ASSET");
    second.setSortOrder(1);
    when(itemMapper.selectList(any())).thenReturn(List.of(first, second));

    service.moveAllCourseItemsToChapter(10L, 2L);

    ArgumentCaptor<CourseItem> captor = ArgumentCaptor.forClass(CourseItem.class);
    verify(itemMapper, times(2)).updateById(captor.capture());
    List<CourseItem> updated = captor.getAllValues();
    assertThat(updated.get(0).getChapterId()).isEqualTo(2L);
    assertThat(updated.get(0).getSortOrder()).isEqualTo(0);
    assertThat(updated.get(1).getSortOrder()).isEqualTo(1);
  }

  @Test
  void updateAsset_clearsLinkedQuiz() {
    CourseItem asset = item(24L, 10L, 1L, "ASSET"); asset.setLinkedQuizItemId(23L);
    when(itemMapper.selectById(24L)).thenReturn(asset);
    var request = new ItemUpdateRequest(); request.setVersion(0); request.setMinimumStudySeconds(60);
    request.setLinkedQuizItemId(null); service.update(24L, request);
    assertThat(asset.getLinkedQuizItemId()).isNull();
    verify(itemMapper).updateByIdWithVersionCheck(asset);
  }

  @Test
  void assetQuiz_rejectsReferenceToAnotherCourse() {
    when(chapterMapper.selectById(1L)).thenReturn(chapter(1L, 10L));
    when(courseMapper.selectOne(any())).thenReturn(new com.sts.course.entity.Course());
    CourseItem target = item(23L, 11L, 2L, "QUIZ");
    target.setStatus(1);
    when(itemMapper.selectById(23L)).thenReturn(target);
    when(itemConverter.toEntity(any())).thenReturn(new CourseItem());
    ItemCreateRequest request = assetRequest(1L);
    request.setLinkedQuizItemId(23L);
    assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
    verify(itemMapper, never()).insert(any(CourseItem.class));
  }

  @Test
  void deleteQuiz_rejectsReferencedQuiz() {
    CourseItem target = item(23L, 10L, 1L, "QUIZ");
    when(itemMapper.selectById(23L)).thenReturn(target);
    when(itemMapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.delete(23L)).isInstanceOf(BusinessException.class);
  }

  @Test
  void finalAssessment_uniqueCheckUsesCurrentRead() throws Exception {
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "batch6-current-read"), CourseItem.class);
    CourseItem quiz = item(23L, 10L, 1L, "QUIZ"); quiz.setIsFinalAssessment(1); quiz.setStatus(1);
    quiz.setPaperVersionId(100L);
    var paper = new PaperVersion(); paper.setPaperId(10L); paper.setPublishedStatus("PUBLISHED"); paper.setTotalScore(new java.math.BigDecimal("100"));
    var source = new Paper(); source.setStatus(1); source.setOrganizationId(ORG_ID_FOR_TENANT); source.setArchiveStatus("ACTIVE");
    when(paperVersionMapper.selectById(100L)).thenReturn(paper); when(paperMapper.selectById(10L)).thenReturn(source);
    when(itemMapper.selectCount(any())).thenAnswer(inv -> {
      var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseItem>) inv.getArgument(0);
      assertThat(query.getSqlSegment()).contains("FOR UPDATE"); return 1L;
    });
    var method = CourseItemServiceImpl.class.getDeclaredMethod("validateFinalAssessment", CourseItem.class);
    method.setAccessible(true);
    assertThatThrownBy(() -> method.invoke(service, quiz)).hasCauseInstanceOf(BusinessException.class);
  }

  private static ItemCreateRequest assetRequest(Long assetId) {
    ItemCreateRequest request = new ItemCreateRequest();
    request.setChapterId(1L);
    request.setItemType("ASSET");
    request.setItemTitle("文档");
    request.setAssetId(assetId);
    request.setIsRequired(1);
    request.setMinimumStudySeconds(60);
    request.setSortOrder(0);
    request.setStatus(1);
    return request;
  }

  private static ItemCreateRequest liveRequest() {
    ItemCreateRequest request = new ItemCreateRequest();
    request.setChapterId(1L);
    request.setItemType("LIVE");
    request.setItemTitle("直播课");
    request.setIsRequired(1);
    request.setMinimumStudySeconds(0);
    request.setSortOrder(0);
    request.setStatus(1);
    LiveSessionCreatePayload live = new LiveSessionCreatePayload();
    live.setLiveName("安全直播");
    live.setTeacherUserId(1L);
    live.setLiveUrl("https://live.example.com/room");
    live.setStartTime(LocalDateTime.of(2026, 7, 22, 10, 0));
    live.setEndTime(LocalDateTime.of(2026, 7, 22, 12, 0));
    request.setLiveSession(live);
    return request;
  }

  private static CourseChapter chapter(Long id, Long courseId) {
    CourseChapter chapter = new CourseChapter();
    chapter.setChapterId(id);
    chapter.setCourseId(courseId);
    return chapter;
  }

  private static CourseItem item(Long id, Long courseId, Long chapterId, String type) {
    CourseItem item = new CourseItem();
    item.setCourseItemId(id);
    item.setCourseId(courseId);
    item.setChapterId(chapterId);
    item.setItemType(type);
    return item;
  }
}
