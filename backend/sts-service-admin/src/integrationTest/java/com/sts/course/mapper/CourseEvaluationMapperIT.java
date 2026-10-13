package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.CourseEvaluation;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证评价分页的真实 SQL、逻辑删除与租户隔离。 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseEvaluationMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired private CourseEvaluationMapper evaluationMapper;
  @Autowired private OrganizationMapper organizationMapper;

  @Test
  void pageByOffering_keepsOrderAndTenantBoundary() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    CourseEvaluation earlier = evaluation(orgId, 70001L, 81001L, 9);
    CourseEvaluation later = evaluation(orgId, 70001L, 81002L, 10);
    evaluationMapper.insert(earlier);
    evaluationMapper.insert(later);
    evaluationMapper.insert(evaluation(orgId, 70002L, 81003L, 11));

    var first = evaluationMapper.selectPage(new Page<>(1, 1), byOffering(70001L));
    var second = evaluationMapper.selectPage(new Page<>(2, 1), byOffering(70001L));
    assertThat(first.getTotal()).isEqualTo(2);
    assertThat(first.getRecords()).extracting(CourseEvaluation::getCourseEvaluationId)
      .containsExactly(later.getCourseEvaluationId());
    assertThat(second.getRecords()).extracting(CourseEvaluation::getCourseEvaluationId)
      .containsExactly(earlier.getCourseEvaluationId());
    evaluationMapper.deleteById(earlier.getCourseEvaluationId());
    assertThat(evaluationMapper.selectPage(new Page<>(1, 20), byOffering(70001L)).getTotal()).isEqualTo(1);

    TenantContext.setTenantId(999999L);
    try {
      assertThat(evaluationMapper.selectPage(new Page<>(1, 20), byOffering(70001L)).getTotal()).isZero();
    } finally {
      TenantContext.setTenantId(orgId);
    }
  }

  /** 与学员评价列表使用相同的过滤和稳定排序条件。 */
  private LambdaQueryWrapper<CourseEvaluation> byOffering(Long offeringId) {
    return new LambdaQueryWrapper<CourseEvaluation>()
      .eq(CourseEvaluation::getCourseOfferingId, offeringId)
      .eq(CourseEvaluation::getStatus, 1)
      .orderByDesc(CourseEvaluation::getSubmittedTime)
      .orderByDesc(CourseEvaluation::getCourseEvaluationId);
  }

  /** 插入当前机构内的评价事实，不依赖现有业务种子 ID。 */
  private CourseEvaluation evaluation(Long orgId, Long offeringId, Long enrollmentId, int hour) {
    CourseEvaluation item = new CourseEvaluation();
    item.setOrganizationId(orgId);
    item.setCourseOfferingId(offeringId);
    item.setCourseEnrollmentId(enrollmentId);
    item.setUserId(enrollmentId);
    item.setRating(BigDecimal.valueOf(4.5));
    item.setIsAnonymous(0);
    item.setSubmittedTime(LocalDateTime.of(2026, 9, 29, hour, 0));
    item.setStatus(1);
    return item;
  }
}
