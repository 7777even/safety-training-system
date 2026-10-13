package com.sts.person.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.person.dto.PersonPickerQuery;
import com.sts.person.vo.PersonPickerItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonPickerServiceImpl implements PersonPickerService {
  private final SysUserMapper userMapper;
  private final SysDeptMapper deptMapper;
  private final SysDeptService deptService;
  /** 与下发使用相同岗位/用户组展开边界。 */
  private final PersonSelectionService selectionService;

  @Override
  public PageResult<PersonPickerItemVO> pagePersons(PageRequest<PersonPickerQuery> request) {
    PersonPickerQuery query = request.getQuery();
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
      .eq(SysUser::getOrganizationId, TenantContext.requireTenantId())
      .eq(SysUser::getStatus, 1);
    if (query != null && query.getDeptId() != null) {
      List<Long> deptIds = deptService.listDescendantIds(query.getDeptId());
      if (deptIds.isEmpty()) {
        return new PageResult<>(List.of(), 0);
      }
      wrapper.in(SysUser::getDeptId, deptIds);
    }
    if (query != null && query.getPositionId() != null) {
      var selection = new com.sts.person.dto.PersonSelectionRequest(); selection.setSelectionType("POSITION");
      selection.setSubjectIds(java.util.List.of(query.getPositionId()));
      wrapper.in(SysUser::getUserId, selectionService.resolve(selection));
    }
    if (query != null && query.getGroupId() != null) {
      var selection = new com.sts.person.dto.PersonSelectionRequest(); selection.setSelectionType("GROUP");
      selection.setSubjectIds(java.util.List.of(query.getGroupId()));
      wrapper.in(SysUser::getUserId, selectionService.resolve(selection));
    }
    if (query != null && StringUtils.hasText(query.getKeyword())) {
      String keyword = query.getKeyword().trim();
      wrapper.and(w -> w.like(SysUser::getUserCode, keyword)
        .or().like(SysUser::getUserName, keyword)
        .or().like(SysUser::getPhone, keyword));
    }
    wrapper.orderByDesc(SysUser::getCreateTime);
    Page<SysUser> page = userMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    Set<Long> deptIds = page.getRecords().stream().map(SysUser::getDeptId)
      .filter(Objects::nonNull).collect(Collectors.toSet());
    Map<Long, SysDept> departments = deptIds.isEmpty() ? Map.of()
      : deptMapper.selectList(new LambdaQueryWrapper<SysDept>().in(SysDept::getDeptId, deptIds))
      .stream().collect(Collectors.toMap(SysDept::getDeptId, Function.identity()));
    return new PageResult<>(page.getRecords().stream().map(user -> toVO(user, departments)).toList(), page.getTotal());
  }

  private PersonPickerItemVO toVO(SysUser user, Map<Long, SysDept> departments) {
    PersonPickerItemVO vo = new PersonPickerItemVO();
    vo.setUserId(user.getUserId());
    vo.setUserCode(user.getUserCode());
    vo.setUserName(user.getUserName());
    vo.setOrganizationId(user.getOrganizationId());
    vo.setDeptId(user.getDeptId());
    SysDept dept = user.getDeptId() == null ? null : departments.get(user.getDeptId());
    vo.setDeptName(dept == null ? null : dept.getDeptName());
    vo.setMemberType(user.getMemberType());
    vo.setJobTitle(user.getJobTitle());
    vo.setStatus(user.getStatus());
    return vo;
  }
}
