package com.sts.admin.mapper;

import com.sts.admin.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends AppBaseMapper<SysRole> {

    @Select("""
        SELECT r.role_code
        FROM us_sys_role r
        INNER JOIN us_sys_user_role ur ON r.role_id = ur.role_id
        WHERE ur.user_id = #{userId}
          AND r.status = 1
          AND r.deleted = 0
        """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT r.*
        FROM us_sys_role r
        INNER JOIN us_sys_user_role ur ON r.role_id = ur.role_id
        INNER JOIN us_sys_role_menu rm ON r.role_id = rm.role_id
        INNER JOIN us_sys_menu m ON rm.menu_id = m.menu_id
        WHERE ur.user_id = #{userId}
          AND m.perms = #{permissionCode}
          AND r.status = 1
          AND r.deleted = 0
          AND m.deleted = 0
        """)
    List<SysRole> selectRolesGrantingPermission(@Param("userId") Long userId,
                                                @Param("permissionCode") String permissionCode);
}
