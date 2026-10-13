package com.sts.org.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.org.entity.OrgPosition;
import com.sts.org.entity.OrgTag;
import com.sts.org.entity.OrgUserPosition;
import com.sts.org.entity.OrgUserTag;
import com.sts.org.support.OrgMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 标签 / 岗位与用户关联的持久化验证（Testcontainers 真 MySQL）。
 * <p>
 * 机构成员表删除后，圈人链路由「标签 → 成员 → 用户」两跳收敛为「标签 → 用户」一跳，
 * 这里验证新的唯一键、租户隔离与按标签/岗位取人的结果。
 */
@SpringBootTest(classes = OrgMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class OrgUserTagPositionMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private OrgTagMapper tagMapper;
  @Autowired
  private OrgPositionMapper positionMapper;
  @Autowired
  private OrgUserTagMapper userTagMapper;
  @Autowired
  private OrgUserPositionMapper userPositionMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long newTag(String name) {
    OrgTag tag = new OrgTag();
    tag.setOrganizationId(demoTenantId());
    tag.setTagName(name);
    tag.setDescription("");
    tag.setStatus(1);
    tagMapper.insert(tag);
    return tag.getTagId();
  }

  private Long newPosition(String code) {
    OrgPosition position = new OrgPosition();
    position.setOrganizationId(demoTenantId());
    position.setPositionCode(code);
    position.setPositionName("岗位-" + code);
    position.setDescription("");
    position.setStatus(1);
    positionMapper.insert(position);
    return position.getPositionId();
  }

  private Long newUser(String code) {
    jdbcTemplate.update(
      "INSERT INTO us_sys_user (organization_id, user_code, user_name, member_type, status, dept_id)"
        + " VALUES (?,?,?, 'INTERNAL', 1, 1)", demoTenantId(), code, "用户-" + code);
    return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
  }

  private void link(Long userId, Long tagId) {
    OrgUserTag link = new OrgUserTag();
    link.setUserId(userId);
    link.setTagId(tagId);
    userTagMapper.insert(link);
  }

  @Test
  @DisplayName("按标签取人：一跳直达 user_id，不再经成员表")
  void selectUsersByTag_resolvesDirectly() {
    Long tagId = newTag("特种作业");
    Long alice = newUser("tag-alice");
    Long bob = newUser("tag-bob");
    newUser("tag-carol");
    link(alice, tagId);
    link(bob, tagId);

    List<Long> userIds = userTagMapper.selectList(
        new LambdaQueryWrapper<OrgUserTag>().eq(OrgUserTag::getTagId, tagId))
      .stream().map(OrgUserTag::getUserId).toList();

    assertThat(userIds).containsExactlyInAnyOrder(alice, bob);
  }

  @Test
  @DisplayName("uk_user_tag：同一用户同一标签不可重复绑定")
  void uniqueKey_rejectsDuplicateUserTag() {
    Long tagId = newTag("重复标签");
    Long userId = newUser("tag-dup");
    link(userId, tagId);

    assertThatThrownBy(() -> link(userId, tagId)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("按岗位取人，并保留唯一主岗位标记")
  void selectUsersByPosition_keepsPrimaryFlag() {
    Long positionId = newPosition("ELECTRICIAN");
    Long userId = newUser("pos-alice");

    OrgUserPosition primary = new OrgUserPosition();
    primary.setUserId(userId);
    primary.setPositionId(positionId);
    primary.setIsPrimary(1);
    userPositionMapper.insert(primary);

    List<OrgUserPosition> links = userPositionMapper.selectList(
      new LambdaQueryWrapper<OrgUserPosition>().eq(OrgUserPosition::getPositionId, positionId));

    assertThat(links).hasSize(1);
    assertThat(links.getFirst().getUserId()).isEqualTo(userId);
    assertThat(links.getFirst().getIsPrimary()).isEqualTo(1);
  }

  @Test
  @DisplayName("标签关联表带租户列，跨租户读不到对方的绑定")
  void userTagLinks_areTenantIsolated() {
    Long tagId = newTag("隔离标签");
    Long userId = newUser("tag-iso");
    link(userId, tagId);

    jdbcTemplate.update(
      "INSERT INTO sts_org_organization (org_code, org_name, org_type, status, description)"
        + " VALUES ('TAG-OTHER', '另一家企业', 'ENTERPRISE', 1, '')");
    Long other = jdbcTemplate.queryForObject(
      "SELECT organization_id FROM sts_org_organization WHERE org_code = 'TAG-OTHER'", Long.class);

    List<OrgUserTag> seenByOther = TenantContext.callWithTenant(other, () -> userTagMapper.selectList(
      new LambdaQueryWrapper<OrgUserTag>().eq(OrgUserTag::getTagId, tagId)));

    assertThat(seenByOther).isEmpty();
  }
}
