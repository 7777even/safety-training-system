package com.sts.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 冻结考试人员档案快照。
 *
 * <p>对应 {@code sts_exam_candidate.candidate_profile_snapshot_json}，其 DDL 要求存
 * 账号、姓名、机构、部门路径、工号和职务。快照必须保留全部键，取不到的写 null，
 * 以便区分「无此人」与「未快照」；{@code ExamAttemptQueryMapper.xml} 依赖这些键做
 * COALESCE 回退。
 */
@Component
@RequiredArgsConstructor
public class ExamCandidateProfileSnapshotAssembler {

  private final SysUserMapper sysUserMapper;
  private final SysDeptMapper sysDeptMapper;
  private final OrganizationMapper organizationMapper;
  private final ObjectMapper objectMapper;

  public String assemble(Long userId, Long organizationId) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("userId", userId);
    node.put("organizationId", organizationId);

    SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
    node.put("userCode", user == null ? null : user.getUserCode());
    node.put("userName", user == null ? null : user.getUserName());

    SysDept dept = user == null || user.getDeptId() == null
      ? null : sysDeptMapper.selectById(user.getDeptId());
    node.put("deptPath", dept == null ? null : dept.getAncestors());

    Organization organization = organizationId == null
      ? null : organizationMapper.selectById(organizationId);
    node.put("organizationName", organization == null ? null : organization.getOrgName());

    // 机构成员档案已并入用户：user_code 即租户内工号
    node.put("employeeNo", user == null ? null : user.getUserCode());
    node.put("jobTitle", user == null ? null : user.getJobTitle());

    try {
      return objectMapper.writeValueAsString(node);
    } catch (Exception ex) {
      throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, 7007, "考试人员档案快照序列化失败");
    }
  }
}
