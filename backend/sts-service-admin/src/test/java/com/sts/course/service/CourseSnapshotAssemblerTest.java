package com.sts.course.service;

import com.sts.asset.entity.Asset;
import com.sts.asset.mapper.AssetMapper;
import com.sts.asset.service.AssetReferenceService;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.course.entity.CourseDownloadAsset;
import com.sts.course.entity.CourseItem;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseCourseCategoryMapper;
import com.sts.course.mapper.CourseDownloadAssetMapper;
import com.sts.course.mapper.CourseItemMapper;
import com.sts.course.mapper.CourseItemQuestionBankMapper;
import com.sts.paper.mapper.PaperMapper;
import com.sts.paper.mapper.PaperVersionMapper;
import com.sts.paper.entity.Paper;
import com.sts.paper.entity.PaperVersion;
import com.sts.question.mapper.QuestionBankMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseSnapshotAssemblerTest {

  @Test
  @DisplayName("课程版本快照保存课程附件名称")
  void assembleAndValidate_savesDownloadAssetName() {
    CourseCourseCategoryMapper categoryMapper = mock(CourseCourseCategoryMapper.class);
    CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
    CourseItemMapper itemMapper = mock(CourseItemMapper.class);
    CourseDownloadAssetMapper downloadAssetMapper = mock(CourseDownloadAssetMapper.class);
    AssetMapper assetMapper = mock(AssetMapper.class);
    CourseItemService itemService = mock(CourseItemService.class);
    AssetReferenceService assetReferenceService = mock(AssetReferenceService.class);
    CourseItemQuestionBankMapper itemQuestionBankMapper = mock(CourseItemQuestionBankMapper.class);
    QuestionBankMapper questionBankMapper = mock(QuestionBankMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);

    Course course = new Course();
    course.setCourseId(10L);
    when(categoryMapper.selectList(any())).thenReturn(List.of());

    CourseChapter chapter = new CourseChapter();
    chapter.setChapterId(20L);
    chapter.setCourseId(10L);
    when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));
    when(itemMapper.selectList(any())).thenReturn(List.of());

    CourseDownloadAsset downloadAsset = new CourseDownloadAsset();
    downloadAsset.setAssetId(30L);
    downloadAsset.setSortOrder(1);
    downloadAsset.setStatus(1);
    when(downloadAssetMapper.selectList(any())).thenReturn(List.of(downloadAsset));

    Asset asset = new Asset();
    asset.setAssetId(30L);
    asset.setAssetName("课程附件.pdf");
    when(assetMapper.selectById(30L)).thenReturn(asset);

    CourseSnapshotAssembler assembler = new CourseSnapshotAssembler(
      categoryMapper, chapterMapper, itemMapper, downloadAssetMapper, assetMapper,
      itemService, assetReferenceService, itemQuestionBankMapper, questionBankMapper,
      paperVersionMapper, paperMapper, org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));

    CourseContentSnapshot snapshot = assembler.assembleAndValidate(course);

    assertThat(snapshot.getDownloadAssets()).singleElement()
      .extracting(item -> item.getAssetName())
      .isEqualTo("课程附件.pdf");
  }

  @Test
  @DisplayName("课程测验快照冻结试卷版本与显示信息")
  void assembleAndValidate_freezesQuizPaperVersion() {
    CourseCourseCategoryMapper categoryMapper = mock(CourseCourseCategoryMapper.class);
    CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
    CourseItemMapper itemMapper = mock(CourseItemMapper.class);
    CourseDownloadAssetMapper downloadAssetMapper = mock(CourseDownloadAssetMapper.class);
    PaperVersionMapper paperVersionMapper = mock(PaperVersionMapper.class);
    PaperMapper paperMapper = mock(PaperMapper.class);
    Course course = new Course();
    course.setCourseId(10L);
    CourseChapter chapter = new CourseChapter();
    chapter.setChapterId(20L);
    when(categoryMapper.selectList(any())).thenReturn(List.of());
    when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));
    when(downloadAssetMapper.selectList(any())).thenReturn(List.of());
    CourseItem item = new CourseItem();
    item.setCourseItemId(30L);
    item.setItemType("QUIZ");
    item.setPaperVersionId(40L);
    item.setMinimumStudySeconds(0);
    item.setIsFinalAssessment(1); item.setPassScore(new java.math.BigDecimal("55")); item.setStatus(1);
    when(itemMapper.selectList(any())).thenReturn(List.of(item));
    PaperVersion version = new PaperVersion();
    version.setPaperVersionId(40L);
    version.setPaperId(50L);
    version.setVersionNo(3);
    version.setPassScore(new java.math.BigDecimal("60")); version.setTotalScore(new java.math.BigDecimal("100"));
    version.setPublishedStatus("PUBLISHED");
    when(paperVersionMapper.selectById(40L)).thenReturn(version);
    Paper paper = new Paper();
    paper.setPaperId(50L);
    paper.setPaperName("消防理论测验");
    paper.setArchiveStatus("ACTIVE");
    paper.setStatus(1);
    when(paperMapper.selectById(50L)).thenReturn(paper);

    var teacherMapper = mock(com.sts.course.mapper.TeacherFollowMapper.class);
    var teacher = new com.sts.course.vo.TeacherPublicVO(); teacher.setTeacherId(8L); teacher.setTeacherName("张老师");
    when(teacherMapper.selectTeachers(null, 8L)).thenReturn(List.of(teacher)); course.setTeacherUserId(8L);
    CourseSnapshotAssembler assembler = new CourseSnapshotAssembler(
      categoryMapper, chapterMapper, itemMapper, downloadAssetMapper, mock(AssetMapper.class),
      mock(CourseItemService.class), mock(AssetReferenceService.class),
      mock(CourseItemQuestionBankMapper.class), mock(QuestionBankMapper.class),
      paperVersionMapper, paperMapper, teacherMapper);

    var published = assembler.assembleAndValidate(course);
    var snapshot = published.getChapters().getFirst().getItems().getFirst();
    teacher.setTeacherName("李老师");
    assertThat(published.getCourse().getTeacherName()).isEqualTo("张老师");
    assertThat(snapshot.getIsFinalAssessment()).isEqualTo(1);
    assertThat(snapshot.getIsRequired()).isEqualTo(1);
    assertThat(snapshot.getPassScore()).isEqualByComparingTo("55");
    assertThat(snapshot.getPaperVersionId()).isEqualTo(40L);
    assertThat(snapshot.getPaperId()).isEqualTo(50L);
    assertThat(snapshot.getPaperName()).isEqualTo("消防理论测验");
    assertThat(snapshot.getPaperVersionNo()).isEqualTo(3);
    assertThat(snapshot.getPaperPublishedStatus()).isEqualTo("PUBLISHED");
  }

  @Test
  @DisplayName("新快照只写入要求学习时长，不写入旧材料规则")
  void assembleAndValidate_writesOnlyMinimumStudySecondsForAsset() {
    CourseCourseCategoryMapper categoryMapper = mock(CourseCourseCategoryMapper.class);
    CourseChapterMapper chapterMapper = mock(CourseChapterMapper.class);
    CourseItemMapper itemMapper = mock(CourseItemMapper.class);
    CourseDownloadAssetMapper downloadAssetMapper = mock(CourseDownloadAssetMapper.class);
    AssetReferenceService assetReferenceService = mock(AssetReferenceService.class);
    Course course = new Course();
    course.setCourseId(10L);
    CourseChapter chapter = new CourseChapter();
    chapter.setChapterId(20L);
    CourseItem item = new CourseItem();
    item.setCourseItemId(30L);
    item.setItemType("ASSET");
    item.setAssetId(40L);
    item.setMinimumStudySeconds(60);
    when(categoryMapper.selectList(any())).thenReturn(List.of());
    when(chapterMapper.selectList(any())).thenReturn(List.of(chapter));
    when(itemMapper.selectList(any())).thenReturn(List.of(item));
    when(downloadAssetMapper.selectList(any())).thenReturn(List.of());
    when(assetReferenceService.requireUsableAsset(40L)).thenReturn(new Asset());

    CourseSnapshotAssembler assembler = new CourseSnapshotAssembler(
      categoryMapper, chapterMapper, itemMapper, downloadAssetMapper, mock(AssetMapper.class),
      mock(CourseItemService.class), assetReferenceService,
      mock(CourseItemQuestionBankMapper.class), mock(QuestionBankMapper.class),
      mock(PaperVersionMapper.class), mock(PaperMapper.class), org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));

    String json = assembler.toCompactJson(assembler.assembleAndValidate(course));

    assertThat(json).contains("\"minimumStudySeconds\":60");
    assertThat(json).doesNotContain("materialRule");
  }

  @Test
  @DisplayName("旧快照材料规则仍可反序列化")
  void parseContentSnapshot_keepsLegacyMaterialRuleForReadOnlyCompatibility() {
    CourseSnapshotAssembler assembler = new CourseSnapshotAssembler(
      mock(CourseCourseCategoryMapper.class), mock(CourseChapterMapper.class), mock(CourseItemMapper.class),
      mock(CourseDownloadAssetMapper.class), mock(AssetMapper.class), mock(CourseItemService.class),
      mock(AssetReferenceService.class), mock(CourseItemQuestionBankMapper.class), mock(QuestionBankMapper.class),
      mock(PaperVersionMapper.class), mock(PaperMapper.class), org.mockito.Mockito.mock(com.sts.course.mapper.TeacherFollowMapper.class));
    String json = """
      {"chapters":[{"items":[{"itemType":"ASSET","minimumStudySeconds":30,
      "materialRule":{"source":"CUSTOM","requiredSeconds":60,"watermarkMode":"SYSTEM"}}]}]}
      """;

    var item = assembler.parseContentSnapshot(json).getChapters().getFirst().getItems().getFirst();

    assertThat(item.getMaterialRule().getSource()).isEqualTo("CUSTOM");
    assertThat(item.getMaterialRule().getRequiredSeconds()).isEqualTo(60);
  }
}
