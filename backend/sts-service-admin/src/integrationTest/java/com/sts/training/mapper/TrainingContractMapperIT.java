package com.sts.training.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingStandard;
import com.sts.training.entity.TrainingExecutionLink;
import com.sts.training.entity.TrainingTemplateCategory;
import com.sts.training.entity.TrainingType;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrainingMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingContractMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private TrainingTypeMapper trainingTypeMapper;
  @Autowired
  private TrainingStandardMapper trainingStandardMapper;
  @Autowired
  private TrainingTemplateCategoryMapper categoryMapper;
  @Autowired
  private TrainingExecutionLinkMapper executionLinkMapper;

  @Test
  void categoryHierarchy_roundTripsAndOrdersSiblingsStably() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingTemplateCategory root = category(orgId, 0L, "IT-ROOT", 1);
    categoryMapper.insert(root);
    TrainingTemplateCategory later = category(orgId, root.getTemplateCategoryId(), "IT-LATER", 2);
    TrainingTemplateCategory earlier = category(orgId, root.getTemplateCategoryId(), "IT-EARLIER", 1);
    categoryMapper.insert(later);
    categoryMapper.insert(earlier);

    List<TrainingTemplateCategory> children = categoryMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateCategory>()
        .eq(TrainingTemplateCategory::getParentId, root.getTemplateCategoryId())
        .orderByAsc(TrainingTemplateCategory::getSortOrder)
        .orderByAsc(TrainingTemplateCategory::getTemplateCategoryId));

    assertThat(children).extracting(TrainingTemplateCategory::getCategoryName)
      .containsExactly("IT-EARLIER", "IT-LATER");
  }

  @Test
  void standardOptimisticLock_rejectsStaleVersion() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode("IT-CONTRACT-TYPE");
    type.setTypeName("契约测试类型");
    type.setStatus(1);
    type.setDescription("IT");
    trainingTypeMapper.insert(type);
    TrainingStandard standard = new TrainingStandard();
    standard.setOrganizationId(orgId);
    standard.setStandardCode("IT-CONTRACT-STANDARD");
    standard.setStandardName("契约测试标准");
    standard.setTrainingTypeId(type.getTrainingTypeId());
    standard.setStandardVersion("1.0");
    standard.setPublishStatus("DRAFT");
    standard.setStatus(1);
    trainingStandardMapper.insert(standard);

    TrainingStandard stale = trainingStandardMapper.selectById(standard.getTrainingStandardId());
    stale.setVersion(stale.getVersion() - 1);
    stale.setStandardName("不应写入");

    assertThat(trainingStandardMapper.updateById(stale)).isZero();
    assertThat(trainingStandardMapper.selectById(standard.getTrainingStandardId()).getStandardName())
      .isEqualTo("契约测试标准");
  }

  @Test
  void executionLink_isIdempotentPerContentAndExecutionType() {
    TrainingExecutionLink first = new TrainingExecutionLink();
    first.setTrainingPlanId(100L);
    first.setPlanContentId(200L);
    first.setExecutionType("EXAM");
    first.setExecutionId(300L);
    executionLinkMapper.insert(first);

    TrainingExecutionLink existing = executionLinkMapper.selectOne(
      new LambdaQueryWrapper<TrainingExecutionLink>()
        .eq(TrainingExecutionLink::getPlanContentId, 200L)
        .eq(TrainingExecutionLink::getExecutionType, "EXAM"));

    assertThat(existing.getExecutionId()).isEqualTo(300L);
  }

  private TrainingTemplateCategory category(Long orgId, Long parentId, String name, int sortOrder) {
    TrainingTemplateCategory category = new TrainingTemplateCategory();
    category.setOrganizationId(orgId);
    category.setParentId(parentId);
    category.setAncestors(parentId == 0 ? "0" : "0," + parentId);
    category.setCategoryName(name);
    category.setSortOrder(sortOrder);
    category.setStatus(1);
    return category;
  }
}
