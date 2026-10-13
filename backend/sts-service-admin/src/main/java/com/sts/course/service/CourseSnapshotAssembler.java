package com.sts.course.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.service.AssetReferenceService;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.*;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseCourseCategoryMapper;
import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.mapper.CourseItemQuestionBankMapper;
import com.sts.course.snapshot.*;
import com.sts.course.vo.LiveSessionVO;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.question.entity.QuestionBank;
import com.sts.question.mapper.QuestionBankMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CourseSnapshotAssembler {

  private static final Set<String> OPEN_ITEM_TYPES =
    Set.of("ASSET", "LIVE", "QUESTION_BANK", "QUIZ");
  private static final String ITEM_TYPE_ASSET = "ASSET";
  private static final String ITEM_TYPE_LIVE = "LIVE";
  private static final String ITEM_TYPE_QUESTION_BANK = "QUESTION_BANK";
  private static final String ITEM_TYPE_QUIZ = "QUIZ";

  private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private final CourseCourseCategoryMapper courseCategoryRelMapper;
  private final CourseChapterMapper chapterMapper;
  private final CourseItemMapper itemMapper;
  private final CourseDownloadAssetMapper downloadAssetMapper;
  private final AssetMapper assetMapper;
  private final CourseItemService itemService;
  private final AssetReferenceService assetReferenceService;
  private final CourseItemQuestionBankMapper itemQuestionBankMapper;
  private final QuestionBankMapper questionBankMapper;
  private final PaperVersionMapper paperVersionMapper;
  private final PaperMapper paperMapper;
  /** 发布时冻结既有教师目录中的姓名。 */
  private final com.sts.course.mapper.TeacherFollowMapper teacherMapper;

  public CourseContentSnapshot assembleAndValidate(Course course) {
    Long courseId = course.getCourseId();
    List<CourseChapter> chapters = chapterMapper.selectList(
      new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .orderByAsc(CourseChapter::getSortOrder)
        .orderByAsc(CourseChapter::getChapterId));
    if (chapters.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5051, "课程无可发布内容");
    }

    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    snapshot.setSchemaVersion(2);
    snapshot.setCourse(toCourseSnapshot(course));
    snapshot.setCategoryIds(listCategoryIds(courseId));
    snapshot.setChapters(chapters.stream().map(ch -> toChapterSnapshot(ch)).toList());
    long finalCount = snapshot.getChapters().stream().filter(ch -> Integer.valueOf(1).equals(ch.getStatus()))
      .flatMap(ch -> ch.getItems().stream()).filter(item -> Integer.valueOf(1).equals(item.getStatus())
        && Integer.valueOf(1).equals(item.getIsFinalAssessment())).count();
    if (finalCount > 1) throw new BusinessException(HttpStatus.BAD_REQUEST, 5045, "课程最多启用一个结业测评");
    snapshot.setDownloadAssets(listDownloadAssets(courseId));
    return snapshot;
  }

  /** 参与时读取当前直播状态，取消或停用立即生效。 */
  public LiveSessionVO currentLiveSession(Long itemId) {
    return itemService.getLiveSession(itemId);
  }

  /** 新结业报名初始化零认可学时，旧版本保留空值兼容口径。 */
  public Integer initialRecognizedStudySeconds(CourseVersion version) {
    CourseContentSnapshot snapshot = version == null ? null : parseContentSnapshot(version.getContentSnapshotJson());
    if (snapshot == null || snapshot.getChapters() == null) return null;
    return snapshot.getChapters().stream().filter(ch -> Integer.valueOf(1).equals(ch.getStatus()))
      .flatMap(ch -> ch.getItems().stream()).anyMatch(item -> Integer.valueOf(1).equals(item.getStatus())
        && "QUIZ".equals(item.getItemType()) && Integer.valueOf(1).equals(item.getIsFinalAssessment())) ? 0 : null;
  }

  public String toCompactJson(CourseContentSnapshot snapshot) {
    try {
      return SNAPSHOT_MAPPER.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("课程快照序列化失败", e);
    }
  }

  public CourseContentSnapshot parseContentSnapshot(String snapshotJson) {
    if (snapshotJson == null || snapshotJson.isBlank()) {
      return null;
    }
    try {
      return SNAPSHOT_MAPPER.readValue(snapshotJson, CourseContentSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照无效");
    }
  }

  public String computeSnapshotHash(CourseContentSnapshot snapshot) {
    try {
      byte[] bytes = SNAPSHOT_MAPPER.writeValueAsBytes(snapshot);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("课程快照序列化失败", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  private List<Long> listCategoryIds(Long courseId) {
    return courseCategoryRelMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseCourseCategory>()
          .eq(CourseCourseCategory::getCourseId, courseId))
      .stream().map(CourseCourseCategory::getCourseCategoryId).toList();
  }

  private List<DownloadAssetSnapshot> listDownloadAssets(Long courseId) {
    return downloadAssetMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseDownloadAsset>()
          .eq(CourseDownloadAsset::getCourseId, courseId)
          .orderByAsc(CourseDownloadAsset::getSortOrder)
          .orderByAsc(CourseDownloadAsset::getCourseDownloadAssetId))
      .stream().map(this::toDownloadAssetSnapshot).toList();
  }

  private ChapterSnapshot toChapterSnapshot(CourseChapter chapter) {
    ChapterSnapshot chapterSnapshot = new ChapterSnapshot();
    chapterSnapshot.setChapterId(chapter.getChapterId());
    chapterSnapshot.setParentId(chapter.getParentId());
    chapterSnapshot.setChapterName(chapter.getChapterName());
    chapterSnapshot.setSortOrder(chapter.getSortOrder());
    chapterSnapshot.setStatus(chapter.getStatus());
    List<CourseItem> items = itemMapper.selectList(
      new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseItem>()
        .eq(CourseItem::getChapterId, chapter.getChapterId())
        .orderByAsc(CourseItem::getSortOrder)
        .orderByAsc(CourseItem::getCourseItemId));
    chapterSnapshot.setItems(items.stream().map(item -> toItemSnapshot(item)).toList());
    return chapterSnapshot;
  }

  private ItemSnapshot toItemSnapshot(CourseItem item) {
    if (!OPEN_ITEM_TYPES.contains(item.getItemType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5032, "该内容项类型暂未开放");
    }
    ItemSnapshot itemSnapshot = new ItemSnapshot();
    itemSnapshot.setCourseItemId(item.getCourseItemId());
    itemSnapshot.setItemType(item.getItemType());
    itemSnapshot.setItemTitle(item.getItemTitle());
    itemSnapshot.setAssetId(item.getAssetId());
    itemSnapshot.setLinkedQuizItemId(item.getLinkedQuizItemId());
    itemSnapshot.setIsFinalAssessment(item.getIsFinalAssessment());
    itemSnapshot.setPaperVersionId(item.getPaperVersionId());
    itemSnapshot.setIsRequired(item.getIsRequired());
    itemSnapshot.setMinimumStudySeconds(item.getMinimumStudySeconds());
    itemSnapshot.setSortOrder(item.getSortOrder());
    itemSnapshot.setStatus(item.getStatus());

    if (ITEM_TYPE_ASSET.equals(item.getItemType())) {
      if (item.getAssetId() == null) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "素材类型内容项缺少可用素材");
      }
      if (item.getMinimumStudySeconds() == null || item.getMinimumStudySeconds() <= 0) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "素材类型内容项的要求学习时长必须大于 0");
      }
      if (item.getLinkedQuizItemId() != null) {
        CourseItem quiz = itemMapper.selectById(item.getLinkedQuizItemId());
        if (quiz == null || !"QUIZ".equals(quiz.getItemType()) || !Integer.valueOf(1).equals(quiz.getStatus())
          || !java.util.Objects.equals(item.getCourseId(), quiz.getCourseId())) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 5044, "课件关联测验无效，请重新配置");
        }
        CourseChapter quizChapter = chapterMapper.selectById(quiz.getChapterId());
        if (quizChapter == null || !Integer.valueOf(1).equals(quizChapter.getStatus())) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 5044, "课件关联测验章节已停用");
        }
      }
      assetReferenceService.requireUsableAsset(item.getAssetId());
      itemSnapshot.setLiveSession(null);
      return itemSnapshot;
    }

    if (item.getMinimumStudySeconds() == null || item.getMinimumStudySeconds() != 0) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "非素材类型内容项的要求学习时长必须为 0");
    }

    if (ITEM_TYPE_LIVE.equals(item.getItemType())) {
      LiveSessionVO session;
      try {
        session = itemService.getLiveSession(item.getCourseItemId());
      } catch (BusinessException ex) {
        if (ex.getCode() == 5039) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "直播类型内容项缺少直播场次");
        }
        throw ex;
      }
      itemSnapshot.setLiveSession(toLiveSessionSnapshot(session));
      return itemSnapshot;
    }

    if (ITEM_TYPE_QUESTION_BANK.equals(item.getItemType())) {
      CourseItemQuestionBank relation = itemQuestionBankMapper.selectOne(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseItemQuestionBank>()
          .eq(CourseItemQuestionBank::getCourseItemId, item.getCourseItemId()));
      QuestionBank bank = relation == null ? null : questionBankMapper.selectById(relation.getQuestionBankId());
      if (bank == null || !"ACTIVE".equals(bank.getArchiveStatus()) || !Integer.valueOf(1).equals(bank.getStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "题库内容项缺少可用题库");
      }
      itemSnapshot.setQuestionBankId(bank.getQuestionBankId());
      itemSnapshot.setQuestionBankName(bank.getBankName());
      itemSnapshot.setLiveSession(null);
      return itemSnapshot;
    }

    if (ITEM_TYPE_QUIZ.equals(item.getItemType())) {
      PaperVersion version = item.getPaperVersionId() == null ? null
        : paperVersionMapper.selectById(item.getPaperVersionId());
      Paper paper = version == null ? null : paperMapper.selectById(version.getPaperId());
      if (version == null || paper == null || !"PUBLISHED".equals(version.getPublishedStatus())
        || !"ACTIVE".equals(paper.getArchiveStatus()) || !Integer.valueOf(1).equals(paper.getStatus())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5052, "测验内容项缺少可用的已发布试卷版本");
      }
      if (Integer.valueOf(1).equals(item.getIsFinalAssessment())) {
        var pass = item.getPassScore() == null ? version.getPassScore() : item.getPassScore();
        if (pass == null || pass.signum() < 0 || pass.compareTo(version.getTotalScore()) > 0) {
          throw new BusinessException(HttpStatus.BAD_REQUEST, 5045, "结业及格线无效");
        }
        itemSnapshot.setPassScore(pass);
        itemSnapshot.setIsRequired(1);
      }
      itemSnapshot.setPaperId(paper.getPaperId());
      itemSnapshot.setPaperName(paper.getPaperName());
      itemSnapshot.setPaperVersionNo(version.getVersionNo());
      itemSnapshot.setPaperPublishedStatus(version.getPublishedStatus());
      itemSnapshot.setLiveSession(null);
      return itemSnapshot;
    }

    throw new BusinessException(HttpStatus.BAD_REQUEST, 5032, "该内容项类型暂未开放");
  }

  private CourseSnapshot toCourseSnapshot(Course course) {
    CourseSnapshot snapshot = new CourseSnapshot();
    snapshot.setCourseId(course.getCourseId());
    snapshot.setCourseCode(course.getCourseCode());
    snapshot.setCourseName(course.getCourseName());
    snapshot.setTeacherUserId(course.getTeacherUserId());
    if (course.getTeacherUserId() != null) {
      var teachers = teacherMapper.selectTeachers(null, course.getTeacherUserId());
      if (teachers.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, 5014, "主讲教师已失效，请重新选择");
      snapshot.setTeacherName(teachers.get(0).getTeacherName());
    }
    snapshot.setCoverFileId(course.getCoverFileId());
    snapshot.setIntroduction(course.getIntroduction());
    snapshot.setTeachingObjective(course.getTeachingObjective());
    snapshot.setSyllabus(course.getSyllabus());
    snapshot.setAssessmentMethod(course.getAssessmentMethod());
    snapshot.setContentMode(course.getContentMode());
    snapshot.setCredit(course.getCredit());
    snapshot.setPointMode(course.getPointMode());
    snapshot.setPointValue(course.getPointValue());
    snapshot.setSourceDownloadMode(course.getSourceDownloadMode());
    snapshot.setIsSpeedPlayAllowed(course.getIsSpeedPlayAllowed());
    snapshot.setIsStudyAnticheatEnabled(course.getIsStudyAnticheatEnabled());
    snapshot.setIsStudyDescriptionEnabled(course.getIsStudyDescriptionEnabled());
    snapshot.setIsWatermarkEnabled(course.getIsWatermarkEnabled());
    snapshot.setStatus(course.getStatus());
    return snapshot;
  }

  private LiveSessionSnapshot toLiveSessionSnapshot(LiveSessionVO session) {
    LiveSessionSnapshot snapshot = new LiveSessionSnapshot();
    snapshot.setLiveSessionId(session.getLiveSessionId());
    snapshot.setLiveName(session.getLiveName());
    snapshot.setTeacherUserId(session.getTeacherUserId());
    snapshot.setLiveUrl(session.getLiveUrl());
    snapshot.setStartTime(session.getStartTime());
    snapshot.setEndTime(session.getEndTime());
    snapshot.setLiveStatus(session.getLiveStatus());
    snapshot.setStatus(session.getStatus());
    return snapshot;
  }

  private DownloadAssetSnapshot toDownloadAssetSnapshot(CourseDownloadAsset asset) {
    DownloadAssetSnapshot snapshot = new DownloadAssetSnapshot();
    snapshot.setAssetId(asset.getAssetId());
    Asset sourceAsset = assetMapper.selectById(asset.getAssetId());
    snapshot.setAssetName(sourceAsset == null ? null : sourceAsset.getAssetName());
    snapshot.setSortOrder(asset.getSortOrder());
    snapshot.setStatus(asset.getStatus());
    return snapshot;
  }
}
