package com.sts.training.service;
import com.sts.training.entity.TrainingMatrix;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/** 多岗位合并必须按资源和版本去重，不能叠加要求学时。 */
class TrainingMatrixServiceTest {
  @Test void sameResourceVersionUsesMaximumHours() {
    var result = TrainingMatrixService.merge(List.of(row(1L, 10L, "2"), row(1L, 10L, "3"), row(1L, 11L, "1")));
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getRequiredHours()).isEqualByComparingTo("3");
    assertThat(result.get(0).getResourceVersionId()).isEqualTo(10L);
    assertThat(result.get(1).getResourceVersionId()).isEqualTo(11L);
    assertThat(result.get(0).getExecutionConfig().getCompletionRule()).isEqualTo("ALL_REQUIRED_ITEMS");
  }
  @Test void emptyMatrixCannotCreateEmptyPlan() {
    assertThatThrownBy(() -> TrainingMatrixService.merge(List.of())).isInstanceOf(BusinessException.class);
  }
  @Test void foreignMatrixCannotBeCopied() {
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    try {
      var mapper = org.mockito.Mockito.mock(com.sts.training.mapper.TrainingMatrixMapper.class);
      var foreign = row(1L, 10L, "2"); foreign.setOrganizationId(2L);
      org.mockito.Mockito.when(mapper.selectById(7L)).thenReturn(foreign);
      var plans = org.mockito.Mockito.mock(TrainingPlanService.class);
      var service = new TrainingMatrixService(mapper, null, null, null, null, plans, null);
      var request = new com.sts.training.dto.TrainingMatrixPlanRequest();
      request.setPlan(new com.sts.training.dto.TrainingPlanCreateRequest()); request.setMatrixIds(List.of(7L));
      assertThatThrownBy(() -> service.createPlan(request)).isInstanceOf(BusinessException.class);
      org.mockito.Mockito.verifyNoInteractions(plans);
    } finally { com.sts.admin.tenant.TenantContext.clear(); }
  }
  @Test void staleMatrixVersionCannotOverwriteRequirements() {
    com.sts.admin.tenant.TenantContext.setTenantId(1L);
    try {
      var mapper = org.mockito.Mockito.mock(com.sts.training.mapper.TrainingMatrixMapper.class);
      var positions = org.mockito.Mockito.mock(com.sts.org.mapper.OrgPositionMapper.class);
      var courses = org.mockito.Mockito.mock(com.sts.course.mapper.CourseMapper.class);
      var versions = org.mockito.Mockito.mock(com.sts.course.mapper.CourseVersionMapper.class);
      var position = new com.sts.org.entity.OrgPosition(); position.setOrganizationId(1L); position.setStatus(1);
      var course = new com.sts.course.entity.Course(); course.setOrganizationId(1L); course.setStatus(1); course.setPublishStatus("PUBLISHED");
      var version = new com.sts.course.entity.CourseVersion(); version.setCourseId(1L);
      var current = row(1L,10L,"2"); current.setOrganizationId(1L); current.setVersion(3);
      org.mockito.Mockito.when(mapper.selectById(7L)).thenReturn(current);
      org.mockito.Mockito.when(positions.selectById(2L)).thenReturn(position);
      org.mockito.Mockito.when(courses.selectById(1L)).thenReturn(course);
      org.mockito.Mockito.when(versions.selectById(10L)).thenReturn(version);
      var service = new TrainingMatrixService(mapper, null, positions, courses, versions, null, null);
      var request = new com.sts.training.dto.TrainingMatrixRequest(); request.setPositionId(2L); request.setCourseId(1L); request.setCourseVersionId(10L); request.setVersion(2);
      assertThatThrownBy(() -> service.save(7L,request)).isInstanceOf(BusinessException.class);
      org.mockito.Mockito.verify(mapper,org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(TrainingMatrix.class));
    } finally { com.sts.admin.tenant.TenantContext.clear(); }
  }
  @Test void mergedStageHoursCannotOverflowExistingPlanSchema() {
    assertThatThrownBy(() -> TrainingMatrixService.merge(List.of(row(1L,10L,"600000"),row(2L,20L,"600000"))))
      .isInstanceOf(BusinessException.class).hasMessageContaining("学时超过上限");
  }
  private TrainingMatrix row(Long course, Long version, String hours) {
    var row = new TrainingMatrix(); row.setCourseId(course); row.setCourseVersionId(version);
    row.setContentName("岗位内容"); row.setRequiredHours(new BigDecimal(hours)); return row;
  }
}
