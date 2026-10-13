package com.sts.org.mapper;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.org.entity.OrgTag;
import com.sts.org.support.OrgMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = OrgMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrgTagMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrgTagMapper tagMapper;
  @Autowired
  private OrganizationMapper organizationMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("uk_org_tag_deleted：同机构同名第二次插入冲突")
  void uniqueKey_rejectsDuplicateTagName() {
    Long orgId = defaultOrgId();
    OrgTag first = tag(orgId, "班组长");
    tagMapper.insert(first);

    assertThatThrownBy(() -> tagMapper.insert(tag(orgId, "班组长")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后同名可再插入")
  void uniqueKey_allowsReuseAfterLogicDelete() {
    Long orgId = defaultOrgId();
    OrgTag first = tag(orgId, "复用标签");
    tagMapper.insert(first);
    Long id = first.getTagId();

    jdbcTemplate.update("UPDATE sts_org_tag SET deleted = ? WHERE tag_id = ?", id, id);

    OrgTag second = tag(orgId, "复用标签");
    tagMapper.insert(second);
    assertThat(second.getTagId()).isNotNull();
  }

  private Long defaultOrgId() {
    return organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
  }

  private static OrgTag tag(Long orgId, String name) {
    OrgTag tag = new OrgTag();
    tag.setOrganizationId(orgId);
    tag.setTagName(name);
    tag.setDescription("");
    tag.setStatus(1);
    return tag;
  }
}
