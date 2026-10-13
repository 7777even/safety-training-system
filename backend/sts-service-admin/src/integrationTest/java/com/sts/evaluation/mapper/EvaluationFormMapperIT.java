package com.sts.evaluation.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.evaluation.entity.EvaluationForm;
import com.sts.evaluation.entity.EvaluationFormVersion;
import com.sts.evaluation.entity.EvaluationItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.evaluation.support.EvaluationFormMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = EvaluationFormMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EvaluationFormMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private EvaluationFormMapper evaluationFormMapper;
  @Autowired
  private EvaluationFormVersionMapper evaluationFormVersionMapper;
  @Autowired
  private EvaluationItemMapper evaluationItemMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_org_code_deleted：逻辑删除后可复用表单编码")
  void uniqueKey_allowsReuseAfterLogicDelete() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    EvaluationForm first = form(orgId, "IT-FORM-1");
    evaluationFormMapper.insert(first);
    Long id = first.getEvaluationFormId();
    jdbcTemplate.update("UPDATE sts_evaluation_form SET deleted = ? WHERE evaluation_form_id = ?", id, id);

    EvaluationForm second = form(orgId, "IT-FORM-1");
    evaluationFormMapper.insert(second);
    assertThat(second.getEvaluationFormId()).isNotNull();
  }

  @Test
  @DisplayName("uk_form_version 与 uk_version_code 唯一")
  void versionAndItemUniques() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    EvaluationForm form = form(orgId, "IT-FORM-2");
    evaluationFormMapper.insert(form);

    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setEvaluationFormId(form.getEvaluationFormId());
    version.setVersionNo(1);
    version.setTotalScore(new BigDecimal("100"));
    version.setPublishedStatus("PUBLISHED");
    evaluationFormVersionMapper.insert(version);

    EvaluationFormVersion dupVersion = new EvaluationFormVersion();
    dupVersion.setEvaluationFormId(form.getEvaluationFormId());
    dupVersion.setVersionNo(1);
    dupVersion.setTotalScore(new BigDecimal("100"));
    dupVersion.setPublishedStatus("PUBLISHED");
    assertThatThrownBy(() -> evaluationFormVersionMapper.insert(dupVersion))
      .isInstanceOf(DuplicateKeyException.class);

    EvaluationItem item = new EvaluationItem();
    item.setFormVersionId(version.getFormVersionId());
    item.setItemCode("I1");
    item.setItemName("项目1");
    item.setItemType("SCORE");
    item.setSortOrder(1);
    item.setIsRequired(1);
    evaluationItemMapper.insert(item);

    EvaluationItem dupItem = new EvaluationItem();
    dupItem.setFormVersionId(version.getFormVersionId());
    dupItem.setItemCode("I1");
    dupItem.setItemName("项目2");
    dupItem.setItemType("SCORE");
    dupItem.setSortOrder(2);
    dupItem.setIsRequired(1);
    assertThatThrownBy(() -> evaluationItemMapper.insert(dupItem))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("版本历史按版本号倒序，表单更新受乐观锁保护")
  void versionOrderingAndOptimisticLock() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    EvaluationForm form = form(orgId, "IT-FORM-CONTRACT");
    evaluationFormMapper.insert(form);
    EvaluationForm current = evaluationFormMapper.selectById(form.getEvaluationFormId());
    Integer oldVersion = current.getVersion();

    EvaluationFormVersion first = version(form.getEvaluationFormId(), 1);
    EvaluationFormVersion second = version(form.getEvaluationFormId(), 2);
    evaluationFormVersionMapper.insert(first);
    evaluationFormVersionMapper.insert(second);

    List<EvaluationFormVersion> versions = evaluationFormVersionMapper.selectList(
      new LambdaQueryWrapper<EvaluationFormVersion>()
        .eq(EvaluationFormVersion::getEvaluationFormId, form.getEvaluationFormId())
        .orderByDesc(EvaluationFormVersion::getVersionNo));
    assertThat(versions).extracting(EvaluationFormVersion::getVersionNo).containsExactly(2, 1);

    current.setFormName("新名称");
    assertThat(evaluationFormMapper.updateById(current)).isEqualTo(1);
    EvaluationForm stale = new EvaluationForm();
    stale.setEvaluationFormId(form.getEvaluationFormId());
    stale.setVersion(oldVersion);
    stale.setFormName("过期名称");
    assertThat(evaluationFormMapper.updateById(stale)).isZero();
  }

  private EvaluationForm form(Long orgId, String code) {
    EvaluationForm form = new EvaluationForm();
    form.setOrganizationId(orgId);
    form.setFormCode(code);
    form.setFormName("表单-" + code);
    form.setFormType("COMPOSITE");
    form.setCurrentVersionNo(0);
    form.setStatus(1);
    form.setDescription("IT");
    return form;
  }

  private EvaluationFormVersion version(Long formId, int versionNo) {
    EvaluationFormVersion version = new EvaluationFormVersion();
    version.setEvaluationFormId(formId);
    version.setVersionNo(versionNo);
    version.setTotalScore(new BigDecimal("100"));
    version.setPublishedStatus("PUBLISHED");
    return version;
  }
}
