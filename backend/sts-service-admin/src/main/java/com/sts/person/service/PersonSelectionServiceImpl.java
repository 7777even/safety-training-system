package com.sts.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.PersonSelectionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PersonSelectionServiceImpl implements PersonSelectionService {

  private static final int INVALID_SELECTION_CODE = 4111;

  private final SysUserMapper userMapper;
  private final SysDeptMapper deptMapper;
  private final SysDeptService deptService;
  /** 岗位有效性与租户归属。 */
  private final com.sts.org.mapper.OrgPositionMapper positionMapper;
  /** 固定分类条件与用户组当前成员。 */
  private final PersonClassificationService classification;

  @Override
  public List<Long> resolve(PersonSelectionRequest request) {
    if (request == null || request.getSelectionType() == null || request.getSubjectIds() == null) {
      throw invalid("人员选择参数不能为空");
    }
    return switch (request.getSelectionType()) {
      case "ALL" -> resolveAll(request.getSubjectIds());
      case "DEPT" -> resolveDepartments(request.getSubjectIds());
      case "USER" -> resolveUsers(request.getSubjectIds());
      case "POSITION" -> resolvePositions(request.getSubjectIds());
      case "GROUP" -> classification.resolveGroups(request.getSubjectIds());
      default -> throw invalid("不支持的人员选择类型");
    };
  }

  /** 多岗位取并集，停用和跨租户岗位不能被选择。 */
  private List<Long> resolvePositions(List<Long> subjectIds) {
    Set<Long> ids = normalizedPositiveIds(subjectIds, "请选择有效岗位");
    var positions = positionMapper.selectList(new LambdaQueryWrapper<com.sts.org.entity.OrgPosition>()
      .eq(com.sts.org.entity.OrgPosition::getOrganizationId, TenantContext.requireTenantId())
      .eq(com.sts.org.entity.OrgPosition::getStatus, 1).in(com.sts.org.entity.OrgPosition::getPositionId, ids));
    if (positions.size() != ids.size()) throw invalid("岗位不存在、已停用或不属于当前机构");
    var condition = new com.sts.person.dto.PersonClassificationCondition(); condition.setPositionIds(new java.util.ArrayList<>(ids));
    var users = classification.resolve(condition);
    if (users.isEmpty()) throw invalid("所选岗位当前没有启用人员");
    return users.stream().distinct().sorted().toList();
  }

  private List<Long> resolveAll(List<Long> subjectIds) {
    if (!subjectIds.isEmpty()) {
      throw invalid("全员选择不能包含对象ID");
    }
    return loadEnabledUsers(null, null);
  }

  private List<Long> resolveDepartments(List<Long> subjectIds) {
    Set<Long> selectedDeptIds = normalizedPositiveIds(subjectIds, "部门ID不能为空");
    List<SysDept> departments = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                                                        .eq(SysDept::getOrganizationId, TenantContext.requireTenantId())
                                                        .eq(SysDept::getStatus, 1)
                                                        .in(SysDept::getDeptId, selectedDeptIds));
    if (departments.size() != selectedDeptIds.size()) {
      throw invalid("部门不存在、已停用或不属于当前机构");
    }
    Set<Long> expandedDeptIds = new LinkedHashSet<>();
    selectedDeptIds.forEach(deptId -> expandedDeptIds.addAll(deptService.listDescendantIds(deptId)));
    return loadEnabledUsers(expandedDeptIds, null);
  }

  private List<Long> resolveUsers(List<Long> subjectIds) {
    Set<Long> selectedUserIds = normalizedPositiveIds(subjectIds, "用户ID不能为空");
    List<Long> resolved = loadEnabledUsers(null, selectedUserIds);
    if (resolved.size() != selectedUserIds.size()) {
      throw invalid("用户不存在、已停用或不属于当前机构");
    }
    return resolved;
  }

  private List<Long> loadEnabledUsers(Collection<Long> deptIds, Collection<Long> userIds) {
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
      .eq(SysUser::getOrganizationId, TenantContext.requireTenantId())
      .eq(SysUser::getStatus, 1)
      .in(deptIds != null, SysUser::getDeptId, deptIds)
      .in(userIds != null, SysUser::getUserId, userIds);
    return userMapper.selectList(wrapper).stream()
      .map(SysUser::getUserId)
      .distinct()
      .sorted()
      .toList();
  }

  private Set<Long> normalizedPositiveIds(List<Long> ids, String message) {
    Set<Long> normalized = new LinkedHashSet<>();
    for (Long id : ids) {
      if (id == null || id <= 0) {
        throw invalid(message);
      }
      normalized.add(id);
    }
    if (normalized.isEmpty()) {
      throw invalid(message);
    }
    return normalized;
  }

  private BusinessException invalid(String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, INVALID_SELECTION_CODE, message);
  }
}
