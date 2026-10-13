package com.sts.person.service;

import com.sts.admin.entity.SysDept;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysDeptMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.PersonSelectionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonSelectionServiceImplTest {

  private SysUserMapper userMapper;
  private SysDeptMapper deptMapper;
  private SysDeptService deptService;
  private PersonSelectionServiceImpl service;
  private PersonClassificationService classification;
  private com.sts.org.mapper.OrgPositionMapper positionMapper;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(1L);
    userMapper = mock(SysUserMapper.class);
    deptMapper = mock(SysDeptMapper.class);
    deptService = mock(SysDeptService.class);
    classification = mock(PersonClassificationService.class);
    positionMapper = mock(com.sts.org.mapper.OrgPositionMapper.class);
    service = new PersonSelectionServiceImpl(userMapper, deptMapper, deptService, positionMapper, classification);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void all_resolvesEnabledUsersInCurrentOrganization() {
    when(userMapper.selectList(any())).thenReturn(List.of(user(3L), user(2L)));

    List<Long> result = service.resolve(request("ALL", List.of()));

    assertThat(result).containsExactly(2L, 3L);
  }

  @Test
  void dept_expandsDescendantsAndDeduplicatesUsers() {
    when(deptMapper.selectList(any())).thenReturn(List.of(dept(10L)));
    when(deptService.listDescendantIds(10L)).thenReturn(List.of(10L, 11L));
    when(userMapper.selectList(any())).thenReturn(List.of(user(8L), user(8L), user(9L)));

    List<Long> result = service.resolve(request("DEPT", List.of(10L)));

    assertThat(result).containsExactly(8L, 9L);
  }

  @Test
  void user_rejectsMissingDisabledOrCrossOrganizationUser() {
    when(userMapper.selectList(any())).thenReturn(List.of(user(8L)));

    assertThatThrownBy(() -> service.resolve(request("USER", List.of(8L, 9L))))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(4111));
  }

  @Test void groupSelectionSupportsCurrentMembers() {
    when(classification.resolveGroups(List.of(1L))).thenReturn(List.of(7L,8L),List.of(9L));
    assertThat(service.resolve(request("GROUP",List.of(1L)))).containsExactly(7L,8L);
    assertThat(service.resolve(request("GROUP",List.of(1L)))).containsExactly(9L);
  }
  @Test void foreignOrDisabledPositionCannotResolvePeople() {
    when(positionMapper.selectList(any())).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolve(request("POSITION",List.of(1L)))).isInstanceOf(BusinessException.class);
    org.mockito.Mockito.verifyNoInteractions(classification);
  }
  @Test void positionResolvesEnabledPeopleWithDuplicatesRemoved() {
    var position = new com.sts.org.entity.OrgPosition(); position.setPositionId(1L);
    when(positionMapper.selectList(any())).thenReturn(List.of(position));
    when(classification.resolve(any())).thenReturn(List.of(9L,7L,7L));
    assertThat(service.resolve(request("POSITION",List.of(1L,1L)))).containsExactly(7L,9L);
  }
  private static PersonSelectionRequest request(String type, List<Long> ids) {
    PersonSelectionRequest request = new PersonSelectionRequest();
    request.setSelectionType(type);
    request.setSubjectIds(ids);
    return request;
  }

  private static SysUser user(Long id) {
    SysUser user = new SysUser();
    user.setUserId(id);
    user.setOrganizationId(1L);
    user.setStatus(1);
    return user;
  }

  private static SysDept dept(Long id) {
    SysDept dept = new SysDept();
    dept.setDeptId(id);
    dept.setOrganizationId(1L);
    dept.setStatus(1);
    return dept;
  }
}
