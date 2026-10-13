package com.sts.ops.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.ops.vo.OpsWorkbenchSummaryVO;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OpsWorkbenchServiceImplTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Course.class);
    TableInfoHelper.initTableInfo(assistant, Exam.class);
    TableInfoHelper.initTableInfo(assistant, TrainingPlan.class);
    TableInfoHelper.initTableInfo(assistant, TrainingParticipant.class);
  }

  private CourseMapper courseMapper;
  private ExamMapper examMapper;
  private TrainingPlanMapper trainingPlanMapper;
  private TrainingParticipantMapper trainingParticipantMapper;
  private OpsWorkbenchServiceImpl service;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    courseMapper = mock(CourseMapper.class);
    examMapper = mock(ExamMapper.class);
    trainingPlanMapper = mock(TrainingPlanMapper.class);
    trainingParticipantMapper = mock(TrainingParticipantMapper.class);
    service = new OpsWorkbenchServiceImpl(courseMapper, examMapper, trainingPlanMapper, trainingParticipantMapper);
    stp = mockStatic(StpUtil.class);
    stp.when(() -> StpUtil.hasPermission(any())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  @DisplayName("enterpriseSummary：概览、分类待办可加总，快捷入口服从权限")
  void enterpriseSummary_aggregatesExplainableTodos() {
    when(courseMapper.selectCount(any())).thenReturn(5L, 2L);
    when(examMapper.selectCount(any())).thenReturn(3L);
    when(trainingPlanMapper.selectCount(any())).thenReturn(1L, 4L);
    when(trainingPlanMapper.selectList(any())).thenReturn(java.util.List.of());
    when(trainingParticipantMapper.selectCount(any())).thenReturn(7L, 6L);

    OpsWorkbenchSummaryVO vo = service.enterpriseSummary();
    assertThat(vo.getPublishedCourseCount()).isEqualTo(5);
    assertThat(vo.getRunningExamCount()).isEqualTo(3);
    assertThat(vo.getRunningPlanCount()).isEqualTo(1);
    assertThat(vo.getCurrentParticipantCount()).isEqualTo(7);
    assertThat(vo.getShortcuts()).extracting("code")
      .contains("COURSE", "EXAM", "TRAINING", "CERTIFICATE", "ORG_USER");
    assertThat(vo.getShortcuts()).extracting("path")
      .contains("/admin/business-center/course/center", "/admin/business-center/exams/exam",
                "/admin/business-center/training/plan", "/admin/result/certificate/certificates", "/admin/system/org/user");
    assertThat(vo.getTodos()).hasSize(3);
    assertThat(vo.getOpenTodoCount()).isEqualTo(vo.getTodos().stream().mapToLong(t -> t.getCount()).sum());
  }

  @Test
  @DisplayName("无权限模块不出现在快捷入口和待办中")
  void enterpriseSummary_hidesUnauthorizedShortcuts() {
    stp.when(() -> StpUtil.hasPermission(any())).thenReturn(false);
    when(courseMapper.selectCount(any())).thenReturn(1L);
    when(examMapper.selectCount(any())).thenReturn(1L);
    when(trainingPlanMapper.selectCount(any())).thenReturn(1L);
    when(trainingPlanMapper.selectList(any())).thenReturn(java.util.List.of());
    when(trainingParticipantMapper.selectCount(any())).thenReturn(1L);

    OpsWorkbenchSummaryVO vo = service.enterpriseSummary();
    assertThat(vo.getShortcuts()).isEmpty();
    assertThat(vo.getTodos()).isEmpty();
    assertThat(vo.getOpenTodoCount()).isZero();
  }
}
