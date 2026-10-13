package com.sts.person.service;
import com.sts.admin.mapper.*;
import com.sts.admin.system.service.SysDeptService;
import com.sts.admin.tenant.TenantContext;
import com.sts.person.dto.*;
import com.sts.common.api.PageRequest;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
/** 分类候选查询失败时不能退化为全员返回。 */
class PersonPickerServiceImplTest {
  @Test void emptyOrInvalidGroupCannotFallBackToAllUsers() {
    TenantContext.setTenantId(1L);
    try {
      var users = mock(SysUserMapper.class); var selection = mock(PersonSelectionService.class);
      var service = new PersonPickerServiceImpl(users,mock(SysDeptMapper.class),mock(SysDeptService.class),selection);
      when(selection.resolve(any())).thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST,4120,"用户组当前没有人员"));
      var query = new PersonPickerQuery(); query.setGroupId(7L);
      assertThatThrownBy(() -> service.pagePersons(PageRequest.of(1,20,query))).isInstanceOf(BusinessException.class);
      verify(selection).resolve(argThat(s -> "GROUP".equals(s.getSelectionType()) && s.getSubjectIds().equals(java.util.List.of(7L))));
      verifyNoInteractions(users);
    } finally { TenantContext.clear(); }
  }
}
