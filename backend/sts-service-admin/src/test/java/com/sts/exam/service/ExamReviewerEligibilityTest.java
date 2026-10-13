package com.sts.exam.service;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 通过资格服务边界验证本机构、启用和现有阅卷权限。 */
class ExamReviewerEligibilityTest {
  @Test void rejectsForeignDisabledAndUnqualifiedUsers() {
    var users=mock(SysUserMapper.class);var menus=mock(SysMenuMapper.class);var service=new ExamReviewerEligibility(users,menus);var u=new SysUser();u.setOrganizationId(10L);u.setStatus(1);when(users.selectById(9L)).thenReturn(u);when(menus.selectPermsByUserId(9L)).thenReturn(List.of("exam:list"));
    assertThatThrownBy(()->service.requireEligible(10L,List.of(9L))).hasMessageContaining("阅卷权限");
    when(menus.selectPermsByUserId(9L)).thenReturn(List.of("exam:review"));service.requireEligible(10L,List.of(9L));
    u.setStatus(0);assertThatThrownBy(()->service.requireEligible(10L,List.of(9L))).hasMessageContaining("停用");
    u.setStatus(1);u.setOrganizationId(20L);assertThatThrownBy(()->service.requireEligible(10L,List.of(9L))).hasMessageContaining("机构");
  }
  @Test void wildcardAllowsEnabledTenantReviewerAndEmptyClears() {
    var users=mock(SysUserMapper.class);var menus=mock(SysMenuMapper.class);var service=new ExamReviewerEligibility(users,menus);var u=new SysUser();u.setOrganizationId(10L);u.setStatus(1);when(users.selectById(9L)).thenReturn(u);when(menus.selectPermsByUserId(9L)).thenReturn(List.of("*"));service.requireEligible(10L,List.of(9L));service.requireEligible(10L,List.of());
  }
}
