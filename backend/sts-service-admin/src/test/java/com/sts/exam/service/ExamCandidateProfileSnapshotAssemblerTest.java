package com.sts.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExamCandidateProfileSnapshotAssemblerTest {

  private static final String[] SNAPSHOT_KEYS = {
    "userId", "userCode", "userName", "organizationId", "organizationName",
    "deptPath", "employeeNo", "jobTitle"
  };

  private SysUserMapper sysUserMapper;
  private SysDeptMapper sysDeptMapper;
  private OrganizationMapper organizationMapper;
  private ExamCandidateProfileSnapshotAssembler assembler;

  @BeforeEach
  void setUp() {
    sysUserMapper = mock(SysUserMapper.class);
    sysDeptMapper = mock(SysDeptMapper.class);
    organizationMapper = mock(OrganizationMapper.class);
    assembler = new ExamCandidateProfileSnapshotAssembler(sysUserMapper, sysDeptMapper,
                                                          organizationMapper,
                                                          new ObjectMapper());
  }

  @Test
  @DisplayName("assemble：冻结账号、姓名、机构、部门路径、工号和职务")
  void assemble_freezesFullProfile() throws Exception {
    SysUser user = new SysUser();
    user.setUserId(10L);
    user.setUserCode("U1001");
    user.setUserName("张三");
    user.setJobTitle("安全员");
    user.setDeptId(200L);
    when(sysUserMapper.selectById(10L)).thenReturn(user);
    SysDept dept = new SysDept();
    dept.setDeptId(200L);
    dept.setAncestors("0,1,2");
    when(sysDeptMapper.selectById(200L)).thenReturn(dept);
    Organization org = new Organization();
    org.setOrganizationId(1L);
    org.setOrgName("示范企业");
    when(organizationMapper.selectById(1L)).thenReturn(org);

    JsonNode node = new ObjectMapper().readTree(assembler.assemble(10L, 1L));

    assertThat(node.get("userId").asLong()).isEqualTo(10L);
    assertThat(node.get("userCode").asText()).isEqualTo("U1001");
    assertThat(node.get("userName").asText()).isEqualTo("张三");
    assertThat(node.get("organizationId").asLong()).isEqualTo(1L);
    assertThat(node.get("organizationName").asText()).isEqualTo("示范企业");
    assertThat(node.get("deptPath").asText()).isEqualTo("0,1,2");
    assertThat(node.get("employeeNo").asText())
      .describedAs("成员档案并入用户后，工号即 user_code").isEqualTo("U1001");
    assertThat(node.get("jobTitle").asText()).isEqualTo("安全员");
  }

  @Test
  @DisplayName("assemble：关联数据缺失时写 null，不省略键")
  void assemble_missingRelations_keepsNullKeys() throws Exception {
    when(sysUserMapper.selectById(10L)).thenReturn(null);
    when(organizationMapper.selectById(1L)).thenReturn(null);

    JsonNode node = new ObjectMapper().readTree(assembler.assemble(10L, 1L));

    for (String key : SNAPSHOT_KEYS) {
      assertThat(node.has(key)).as("快照必须保留键 %s", key).isTrue();
    }
    assertThat(node.get("userId").asLong()).isEqualTo(10L);
    assertThat(node.get("organizationId").asLong()).isEqualTo(1L);
    assertThat(node.get("userName").isNull()).isTrue();
    assertThat(node.get("userCode").isNull()).isTrue();
    assertThat(node.get("deptPath").isNull()).isTrue();
    assertThat(node.get("employeeNo").isNull()).isTrue();
    assertThat(node.get("jobTitle").isNull()).isTrue();
    verifyNoInteractions(sysDeptMapper);
  }

  @Test
  @DisplayName("assemble：用户无部门时 deptPath 为 null 且不查部门表")
  void assemble_userWithoutDept_skipsDeptLookup() throws Exception {
    SysUser user = new SysUser();
    user.setUserId(10L);
    user.setUserCode("U1001");
    user.setUserName("张三");
    user.setJobTitle("安全员");
    user.setDeptId(null);
    when(sysUserMapper.selectById(10L)).thenReturn(user);
    when(organizationMapper.selectById(1L)).thenReturn(null);

    JsonNode node = new ObjectMapper().readTree(assembler.assemble(10L, 1L));

    assertThat(node.get("deptPath").isNull()).isTrue();
    verifyNoInteractions(sysDeptMapper);
  }
}
