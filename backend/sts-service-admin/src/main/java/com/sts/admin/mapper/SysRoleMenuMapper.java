package com.sts.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.sts.admin.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysRoleMenuMapper extends AppBaseMapper<SysRoleMenu> {

  /**
   * 统计仍指向未删除角色的菜单授权数。
   * <p>
   * 菜单是全局定义，占用检查必须跨租户；已软删或不存在的角色留下的孤儿关联不计入。
   */
  @InterceptorIgnore(tenantLine = "true")
  @Select("""
    SELECT COUNT(*)
    FROM us_sys_role_menu rm
    INNER JOIN us_sys_role r ON r.role_id = rm.role_id AND r.deleted = 0
    WHERE rm.menu_id = #{menuId}
    """)
  Long countLiveRoleBindingsByMenuId(@Param("menuId") Long menuId);
}
