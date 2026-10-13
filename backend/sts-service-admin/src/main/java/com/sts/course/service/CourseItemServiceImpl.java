package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.tenant.TenantContext;
import com.sts.asset.service.AssetReferenceService;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.converter.CourseItemConverter;
import com.sts.course.converter.CourseLiveSessionConverter;
import com.sts.course.dto.*;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import com.sts.course.vo.CourseItemVO;
import com.sts.course.vo.LiveSessionVO;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseItemServiceImpl extends BaseServiceImpl<CourseItemMapper, CourseItem>
  implements CourseItemService {

  private static final String ITEM_TYPE_ASSET = "ASSET";
  private static final String ITEM_TYPE_LIVE = "LIVE";
  private static final String ITEM_TYPE_QUESTION_BANK = "QUESTION_BANK";
  private static final String ITEM_TYPE_QUIZ = "QUIZ";
  private static final Set<String> OPEN_ITEM_TYPES = Set.of(
    ITEM_TYPE_ASSET, ITEM_TYPE_LIVE, ITEM_TYPE_QUESTION_BANK, ITEM_TYPE_QUIZ);
  private static final String LIVE_STATUS_DRAFT = "DRAFT";
  private static final String LIVE_STATUS_SCHEDULED = "SCHEDULED";
  private static final String LIVE_STATUS_CANCELLED = "CANCELLED";
  private static final String LIVE_STATUS_LIVING = "LIVING";
  private static final String LIVE_STATUS_ENDED = "ENDED";

  private final CourseItemMapper itemMapper;
  private final CourseLiveSessionMapper liveSessionMapper;
  private final CourseChapterMapper chapterMapper;
  private final CourseMapper courseMapper;
  private final CourseItemConverter itemConverter;
  private final CourseLiveSessionConverter liveSessionConverter;
  private final AssetReferenceService assetReferenceService;
  private final CourseItemProgressMapper itemProgressMapper;
  private final CourseOfferingMapper offeringMapper;
  private final CourseItemQuestionBankMapper itemQuestionBankMapper;
  private final QuestionBankMapper questionBankMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperMapper paperMapper;

  @Override
  public List<CourseItemVO> listByChapter(Long chapterId) {
    requireChapter(chapterId);
    List<CourseItem> items = itemMapper.selectList(
      new LambdaQueryWrapper<CourseItem>()
        .eq(CourseItem::getChapterId, chapterId)
        .orderByAsc(CourseItem::getSortOrder)
        .orderByAsc(CourseItem::getCourseItemId));
    return items.stream().map(this::toItemVO).toList();
  }

  @Override
  @Transactional
  public Long create(ItemCreateRequest request) {
    if (!OPEN_ITEM_TYPES.contains(request.getItemType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5032, "该内容项类型暂未开放");
    }
    CourseChapter chapter = requireChapter(request.getChapterId());
    lockCourse(chapter.getCourseId());
    chapter = requireCurrentChapter(request.getChapterId());
    requireCourseInOrg(chapter.getCourseId());
    validateMinimumStudySeconds(request.getItemType(), request.getMinimumStudySeconds());

    CourseItem item = itemConverter.toEntity(request);
    item.setCourseId(chapter.getCourseId());
    validateLinkedQuiz(item.getCourseId(), item.getItemType(), request.getLinkedQuizItemId());
    item.setLinkedQuizItemId(request.getLinkedQuizItemId());
    item.setPassScore(request.getPassScore());
    validateFinalAssessment(item);
    if (item.getIsLockedAfterParticipation() == null) {
      item.setIsLockedAfterParticipation(0);
    }

    if (ITEM_TYPE_ASSET.equals(request.getItemType())) {
      if (request.getAssetId() == null || request.getQuestionBankId() != null
        || request.getPaperVersionId() != null || request.getLiveSession() != null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5033, "素材类型内容项必须指定素材");
      }
      assetReferenceService.requireUsableAsset(request.getAssetId());
      item.setPaperVersionId(null);
      itemMapper.insert(item);
      return item.getCourseItemId();
    }

    if (ITEM_TYPE_QUESTION_BANK.equals(request.getItemType())) {
      if (request.getQuestionBankId() == null || request.getAssetId() != null
        || request.getPaperVersionId() != null || request.getLiveSession() != null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5042, "题库内容项必须且只能指定题库");
      }
      requireUsableQuestionBank(request.getQuestionBankId());
      item.setPaperVersionId(null);
      item.setAssetId(null);
      itemMapper.insert(item);
      saveQuestionBankReference(item.getCourseItemId(), request.getQuestionBankId());
      return item.getCourseItemId();
    }

    if (ITEM_TYPE_QUIZ.equals(request.getItemType())) {
      if (request.getPaperVersionId() == null || request.getAssetId() != null
        || request.getQuestionBankId() != null || request.getLiveSession() != null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5042, "测验内容项必须且只能指定试卷版本");
      }
      requireUsablePaperVersion(request.getPaperVersionId());
      item.setPaperVersionId(request.getPaperVersionId());
      item.setAssetId(null);
      itemMapper.insert(item);
      return item.getCourseItemId();
    }

    if (request.getLiveSession() == null || request.getAssetId() != null
      || request.getQuestionBankId() != null || request.getPaperVersionId() != null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5035, "直播类型内容项必须提供直播场次信息");
    }
    validateLiveUrlAndTime(request.getLiveSession().getLiveUrl(),
                           request.getLiveSession().getStartTime(), request.getLiveSession().getEndTime());
    item.setAssetId(null);
    item.setPaperVersionId(null);
    itemMapper.insert(item);

    CourseLiveSession session = liveSessionConverter.toEntity(request.getLiveSession());
    session.setCourseItemId(item.getCourseItemId());
    liveSessionMapper.insert(session);
    return item.getCourseItemId();
  }

  @Override
  @Transactional
  public void update(Long itemId, ItemUpdateRequest request) {
    CourseItem item = requireItem(itemId);
    lockCourse(item.getCourseId());
    item = requireCurrentItem(itemId);
    validateMinimumStudySeconds(item.getItemType(), request.getMinimumStudySeconds());
    item.setVersion(request.getVersion());
    validateUpdateReferenceShape(item.getItemType(), request);
    if (ITEM_TYPE_QUIZ.equals(item.getItemType()) && request.getPaperVersionId() != null
      && !Objects.equals(item.getPaperVersionId(), request.getPaperVersionId())) {
      assertAssessmentReferenceMutable(item);
      requireUsablePaperVersion(request.getPaperVersionId());
      item.setPaperVersionId(request.getPaperVersionId());
      item.setAssetId(null);
    }
    if (ITEM_TYPE_QUESTION_BANK.equals(item.getItemType()) && request.getQuestionBankId() != null) {
      CourseItemQuestionBank current = findQuestionBankReference(item.getCourseItemId());
      if (current == null || !Objects.equals(current.getQuestionBankId(), request.getQuestionBankId())) {
        assertAssessmentReferenceMutable(item);
        requireUsableQuestionBank(request.getQuestionBankId());
        replaceQuestionBankReference(item.getCourseItemId(), request.getQuestionBankId());
      }
    }
    if (ITEM_TYPE_ASSET.equals(item.getItemType()) && request.getAssetId() != null) {
      assetReferenceService.requireUsableAsset(request.getAssetId());
      item.setAssetId(request.getAssetId());
    }
    validateLinkedQuiz(item.getCourseId(), item.getItemType(), request.getLinkedQuizItemId());
    itemConverter.updateEntity(item, request);
    item.setLinkedQuizItemId(request.getLinkedQuizItemId());
    item.setPassScore(request.getPassScore());
    validateFinalAssessment(item);
    itemMapper.updateByIdWithVersionCheck(item);
  }

  @Override
  @Transactional
  public void move(Long itemId, ItemMoveRequest request) {
    CourseItem item = requireItem(itemId);
    lockCourse(item.getCourseId());
    item = requireCurrentItem(itemId);
    CourseChapter targetChapter = requireCurrentChapter(request.getChapterId());
    if (!Objects.equals(item.getCourseId(), targetChapter.getCourseId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5031, "课程内容项不存在");
    }
    item.setVersion(request.getVersion());
    itemConverter.applyMove(item, request);
    itemMapper.updateByIdWithVersionCheck(item);
  }

  @Override
  @Transactional
  public void delete(Long itemId) {
    CourseItem item = requireItem(itemId);
    lockCourse(item.getCourseId());
    item = requireCurrentItem(itemId);
    if (itemMapper.selectCount(new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getCourseId, item.getCourseId()).eq(CourseItem::getLinkedQuizItemId, itemId).last("FOR UPDATE")) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5044, "测验仍被课件引用，请先解除关联");
    }
    deleteLiveSessionForItem(itemId);
    deleteQuestionBankReferenceForItem(itemId);
    this.removeById(item.getCourseItemId());
  }

  @Override
  @Transactional
  public void moveAllCourseItemsToChapter(Long courseId, Long targetChapterId) {
    CourseChapter target = requireChapter(targetChapterId);
    if (!Objects.equals(target.getCourseId(), courseId)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5021, "课程章节不存在");
    }
    List<CourseItem> ordered = listCourseItemsOrdered(courseId);
    int order = 0;
    for (CourseItem item : ordered) {
      item.setChapterId(targetChapterId);
      item.setSortOrder(order++);
      itemMapper.updateById(item);
    }
  }

  @Override
  @Transactional
  public void deleteAllOfCourse(Long courseId) {
    lockCourse(courseId);
    List<CourseItem> items = itemMapper.selectList(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getCourseId, courseId).last("FOR UPDATE"));
    for (CourseItem item : items) {
      deleteLiveSessionForItem(item.getCourseItemId());
      deleteQuestionBankReferenceForItem(item.getCourseItemId());
      this.removeById(item.getCourseItemId());
    }
  }

  @Override
  public Long countByChapter(Long chapterId) {
    return itemMapper.selectCount(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getChapterId, chapterId));
  }

  @Override
  public void assertChapterHasNoItems(Long chapterId) {
    Long count = countByChapter(chapterId);
    if (count != null && count > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5034, "章节下存在内容项，不能删除");
    }
  }

  @Override
  public LiveSessionVO getLiveSession(Long itemId) {
    CourseItem item = requireItem(itemId);
    assertLiveItem(item);
    CourseLiveSession session = requireLiveSessionByItem(itemId);
    return liveSessionConverter.toVO(session);
  }

  @Override
  @Transactional
  public void updateLiveSession(Long itemId, LiveSessionUpdateRequest request) {
    CourseItem item = requireItem(itemId);
    lockCourse(item.getCourseId());
    item = requireCurrentItem(itemId);
    assertLiveItem(item);
    CourseLiveSession session = requireLiveSessionByItem(itemId);
    if (LIVE_STATUS_LIVING.equals(session.getLiveStatus()) || LIVE_STATUS_ENDED.equals(session.getLiveStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 5036, "直播已开始或已结束，不可修改");
    }
    validateLiveUrlAndTime(request.getLiveUrl(), request.getStartTime(), request.getEndTime());
    assertLiveStatusTransition(session.getLiveStatus(), request.getLiveStatus());
    session.setVersion(request.getVersion());
    liveSessionConverter.updateEntity(session, request);
    liveSessionMapper.updateByIdWithVersionCheck(session);
  }

  private CourseItemVO toItemVO(CourseItem item) {
    CourseItemVO vo = itemConverter.toVO(item);
    if (ITEM_TYPE_QUESTION_BANK.equals(item.getItemType())) {
      CourseItemQuestionBank relation = findQuestionBankReference(item.getCourseItemId());
      if (relation != null) {
        vo.setQuestionBankId(relation.getQuestionBankId());
        QuestionBank bank = questionBankMapper.selectById(relation.getQuestionBankId());
        vo.setQuestionBankName(bank == null ? null : bank.getBankName());
      }
    }
    if (ITEM_TYPE_QUIZ.equals(item.getItemType()) && item.getPaperVersionId() != null) {
      PaperVersion version = paperVersionMapper.selectById(item.getPaperVersionId());
      if (version != null) {
        Paper paper = paperMapper.selectById(version.getPaperId());
        vo.setPaperId(version.getPaperId());
        vo.setPaperVersionNo(version.getVersionNo());
        vo.setPaperPublishedStatus(version.getPublishedStatus());
        vo.setPaperName(paper == null ? null : paper.getPaperName());
      }
    }
    if (ITEM_TYPE_LIVE.equals(item.getItemType())) {
      CourseLiveSession session = liveSessionMapper.selectOne(
        new LambdaQueryWrapper<CourseLiveSession>()
          .eq(CourseLiveSession::getCourseItemId, item.getCourseItemId()));
      if (session != null) {
        vo.setLiveSession(liveSessionConverter.toVO(session));
      }
    }
    return vo;
  }

  private List<CourseItem> listCourseItemsOrdered(Long courseId) {
    List<CourseChapter> chapters = chapterMapper.selectList(
      new LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .orderByAsc(CourseChapter::getSortOrder)
        .orderByAsc(CourseChapter::getChapterId));
    Map<Long, Integer> chapterOrder = new HashMap<>();
    for (int i = 0; i < chapters.size(); i++) {
      chapterOrder.put(chapters.get(i).getChapterId(), i);
    }
    List<CourseItem> items = new ArrayList<>(itemMapper.selectList(
      new LambdaQueryWrapper<CourseItem>().eq(CourseItem::getCourseId, courseId)));
    items.sort(Comparator
                 .comparing((CourseItem i) -> chapterOrder.getOrDefault(i.getChapterId(), Integer.MAX_VALUE))
                 .thenComparing(CourseItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                 .thenComparing(CourseItem::getCourseItemId, Comparator.nullsLast(Long::compareTo)));
    return items;
  }

  /**
   * 校验内容项唯一的学习时长规则。
   */
  /** 课件配题只引用同课程启用测验，不能跨课程借用执行。 */
  /** 结业测评必须为必修QUIZ，及格线在试卷满分内；启用的结业项每课程仅一个。 */
  private void validateFinalAssessment(CourseItem item) {
    boolean marked = Integer.valueOf(1).equals(item.getIsFinalAssessment());
    if ((marked || item.getPassScore() != null) && !"QUIZ".equals(item.getItemType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5045, "仅课程测验可配置结业规则");
    }
    if (!marked && item.getPassScore() != null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5045, "普通测验不能覆盖及格线");
    }
    if (!marked) return;
    // 同一课程的结业配置串行检查，避免并发新增两个启用的结业项。
    lockCourse(item.getCourseId());
    item.setIsRequired(1);
    PaperVersion paper = requireUsablePaperVersion(item.getPaperVersionId());
    if (item.getPassScore() != null && (item.getPassScore().signum() < 0
      || item.getPassScore().compareTo(paper.getTotalScore()) > 0)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5045, "结业及格线必须在零至试卷满分之间");
    }
    if (Integer.valueOf(1).equals(item.getStatus()) && itemMapper.selectCount(new LambdaQueryWrapper<CourseItem>()
      .eq(CourseItem::getCourseId, item.getCourseId()).eq(CourseItem::getIsFinalAssessment, 1)
      .eq(CourseItem::getStatus, 1).ne(item.getCourseItemId() != null, CourseItem::getCourseItemId, item.getCourseItemId()).last("FOR UPDATE")) > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 5045, "课程最多启用一个结业测评");
    }
  }

  /** 所有目录/关联写操作按课程串行，再读取最新行避免旧快照校验。 */
  private void lockCourse(Long courseId) {
    courseMapper.selectOne(new LambdaQueryWrapper<Course>().eq(Course::getCourseId, courseId).last("FOR UPDATE"));
  }

  private CourseItem requireCurrentItem(Long itemId) {
    CourseItem item = itemMapper.selectCurrent(itemId);
    if (item == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5031, "课程内容项不存在");
    return item;
  }

  private CourseChapter requireCurrentChapter(Long chapterId) {
    CourseChapter chapter = chapterMapper.selectCurrent(chapterId);
    if (chapter == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5021, "课程章节不存在");
    return chapter;
  }

  private void validateLinkedQuiz(Long courseId, String type, Long quizId) {
    if (quizId == null) return;
    CourseItem quiz = itemMapper.selectCurrent(quizId);
    if (!ITEM_TYPE_ASSET.equals(type) || quiz == null || !ITEM_TYPE_QUIZ.equals(quiz.getItemType())
      || !Objects.equals(quiz.getCourseId(), courseId) || !Integer.valueOf(1).equals(quiz.getStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5044, "课件试题必须选择同课程启用测验");
    }
  }

  private void validateMinimumStudySeconds(String itemType, Integer minimumStudySeconds) {
    if (ITEM_TYPE_ASSET.equals(itemType)) {
      if (minimumStudySeconds == null || minimumStudySeconds <= 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5033, "素材类型内容项的要求学习时长必须大于 0");
      }
      return;
    }
    if (minimumStudySeconds == null || minimumStudySeconds != 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5033, "非素材类型内容项的要求学习时长必须为 0");
    }
  }

  private void validateLiveUrlAndTime(String liveUrl, java.time.LocalDateTime startTime,
                                      java.time.LocalDateTime endTime) {
    if (!StringUtils.hasText(liveUrl)
      || !(liveUrl.regionMatches(true, 0, "http://", 0, 7)
      || liveUrl.regionMatches(true, 0, "https://", 0, 8))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5035, "直播链接无效，必须以 http:// 或 https:// 开头");
    }
    if (endTime == null || startTime == null || !endTime.isAfter(startTime)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5035, "直播结束时间必须晚于开始时间");
    }
  }

  private void assertLiveStatusTransition(String currentStatus, String targetStatus) {
    if (Objects.equals(currentStatus, targetStatus)) {
      return;
    }
    boolean allowed = (LIVE_STATUS_DRAFT.equals(currentStatus) && LIVE_STATUS_SCHEDULED.equals(targetStatus))
      || (LIVE_STATUS_SCHEDULED.equals(currentStatus) && LIVE_STATUS_DRAFT.equals(targetStatus))
      || (LIVE_STATUS_SCHEDULED.equals(currentStatus) && LIVE_STATUS_CANCELLED.equals(targetStatus));
    if (!allowed) {
      throw new BusinessException(HttpStatus.CONFLICT, 5036, "直播状态流转不合法");
    }
  }

  private void assertLiveItem(CourseItem item) {
    if (!ITEM_TYPE_LIVE.equals(item.getItemType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5039, "直播场次不存在");
    }
  }

  private CourseItem requireItem(Long itemId) {
    CourseItem item = itemMapper.selectById(itemId);
    if (item == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5031, "课程内容项不存在");
    }
    return item;
  }

  private CourseChapter requireChapter(Long chapterId) {
    CourseChapter chapter = chapterMapper.selectById(chapterId);
    if (chapter == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5021, "课程章节不存在");
    }
    return chapter;
  }

  private CourseLiveSession requireLiveSessionByItem(Long itemId) {
    CourseLiveSession session = liveSessionMapper.selectOne(
      new LambdaQueryWrapper<CourseLiveSession>()
        .eq(CourseLiveSession::getCourseItemId, itemId));
    if (session == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5039, "直播场次不存在");
    }
    return session;
  }

  private void requireCourseInOrg(Long courseId) {
    Course course = courseMapper.selectOne(
      new LambdaQueryWrapper<Course>()
        .eq(Course::getCourseId, courseId));
    if (course == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
  }

  private QuestionBank requireUsableQuestionBank(Long questionBankId) {
    QuestionBank bank = questionBankMapper.selectById(questionBankId);
    if (bank == null || !Objects.equals(bank.getOrganizationId(), TenantContext.requireTenantId())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5040, "题库不存在");
    }
    if (!"ACTIVE".equals(bank.getArchiveStatus()) || !Objects.equals(bank.getStatus(), 1)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5041, "题库已归档或已停用");
    }
    return bank;
  }

  private PaperVersion requireUsablePaperVersion(Long paperVersionId) {
    PaperVersion version = paperVersionMapper.selectById(paperVersionId);
    if (version == null || !"PUBLISHED".equals(version.getPublishedStatus())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5040, "已发布试卷版本不存在");
    }
    Paper paper = paperMapper.selectById(version.getPaperId());
    if (paper == null || !Objects.equals(paper.getOrganizationId(), TenantContext.requireTenantId())) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5040, "已发布试卷版本不存在");
    }
    if (!"ACTIVE".equals(paper.getArchiveStatus()) || !Objects.equals(paper.getStatus(), 1)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5041, "试卷已归档或已停用");
    }
    return version;
  }

  private void assertAssessmentReferenceMutable(CourseItem item) {
    boolean progressExists = itemProgressMapper.selectCount(new LambdaQueryWrapper<CourseItemProgress>()
                                                              .eq(CourseItemProgress::getCourseItemId,
                                                                  item.getCourseItemId())) > 0;
    boolean offeringExists = offeringMapper.selectCount(new LambdaQueryWrapper<CourseOffering>()
                                                          .eq(CourseOffering::getCourseId, item.getCourseId())) > 0;
    if (progressExists || offeringExists) {
      throw new BusinessException(HttpStatus.CONFLICT, 5043, "内容项已开课或已有参与事实，不可修改考核资源");
    }
  }

  private void validateUpdateReferenceShape(String itemType, ItemUpdateRequest request) {
    boolean invalid = ITEM_TYPE_ASSET.equals(itemType)
      ? request.getQuestionBankId() != null || request.getPaperVersionId() != null
      : ITEM_TYPE_QUESTION_BANK.equals(itemType)
      ? request.getAssetId() != null || request.getPaperVersionId() != null
      : ITEM_TYPE_QUIZ.equals(itemType)
      ? request.getAssetId() != null || request.getQuestionBankId() != null
      : request.getAssetId() != null || request.getQuestionBankId() != null
      || request.getPaperVersionId() != null;
    if (invalid) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5042, "内容项资源字段与类型不匹配");
    }
  }

  private CourseItemQuestionBank findQuestionBankReference(Long courseItemId) {
    return itemQuestionBankMapper.selectOne(new LambdaQueryWrapper<CourseItemQuestionBank>()
                                              .eq(CourseItemQuestionBank::getCourseItemId, courseItemId));
  }

  private void saveQuestionBankReference(Long courseItemId, Long questionBankId) {
    CourseItemQuestionBank relation = new CourseItemQuestionBank();
    relation.setCourseItemId(courseItemId);
    relation.setQuestionBankId(questionBankId);
    itemQuestionBankMapper.insert(relation);
  }

  private void replaceQuestionBankReference(Long courseItemId, Long questionBankId) {
    deleteQuestionBankReferenceForItem(courseItemId);
    saveQuestionBankReference(courseItemId, questionBankId);
  }

  private void deleteQuestionBankReferenceForItem(Long courseItemId) {
    itemQuestionBankMapper.delete(new LambdaQueryWrapper<CourseItemQuestionBank>()
                                    .eq(CourseItemQuestionBank::getCourseItemId, courseItemId));
  }

  private void deleteLiveSessionForItem(Long itemId) {
    CourseLiveSession session = liveSessionMapper.selectOne(
      new LambdaQueryWrapper<CourseLiveSession>()
        .eq(CourseLiveSession::getCourseItemId, itemId));
    if (session != null) {
      liveSessionMapper.update(null,
                               Wrappers.<CourseLiveSession>lambdaUpdate()
                                 .setSql("deleted = live_session_id")
                                 .eq(CourseLiveSession::getLiveSessionId, session.getLiveSessionId())
                                 .eq(CourseLiveSession::getDeleted, 0L));
    }
  }

}
