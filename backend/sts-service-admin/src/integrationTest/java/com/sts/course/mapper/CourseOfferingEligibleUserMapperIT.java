package com.sts.course.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.course.entity.CourseOfferingEligibleUser;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(classes = CourseMapperItConfig.class)
@Transactional
class CourseOfferingEligibleUserMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseOfferingEligibleUserMapper mapper;

  @BeforeEach
  void setUpTenant() {
    TenantContext.setTenantId(1L);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void activeWhitelistUserIsUniqueWithinOffering() {
    mapper.insert(eligible(100L, 8L));

    assertThatThrownBy(() -> mapper.insert(eligible(100L, 8L)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  private static CourseOfferingEligibleUser eligible(Long offeringId, Long userId) {
    CourseOfferingEligibleUser entity = new CourseOfferingEligibleUser();
    entity.setCourseOfferingId(offeringId);
    entity.setUserId(userId);
    return entity;
  }
}
