package com.sts.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.sts.admin.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends AppBaseMapper<SysMenu> {

    @Select("""
        SELECT DISTINCT m.perms
        FROM us_sys_menu m
        INNER JOIN us_sys_role_menu rm ON m.menu_id = rm.menu_id
        INNER JOIN us_sys_user_role ur ON rm.role_id = ur.role_id
        INNER JOIN us_sys_role r ON ur.role_id = r.role_id
        WHERE ur.user_id = #{userId}
          AND m.deleted = 0
          AND r.deleted = 0
          AND r.status = 1
          AND m.is_visible = 1
          AND m.perms IS NOT NULL
          AND m.perms <> ''
        """)
    List<String> selectPermsByUserId(@Param("userId") Long userId);

  /**
   * 查询用户可见菜单树（含向上补齐的父级）。
   * <p>
   * <b>豁免租户插件</b>：插件无法区分 {@code WITH RECURSIVE} 的 CTE 别名与真实表，
   * 会向递归引用 {@code menu_tree t} 注入 {@code t.organization_id}，而该结果集并无此列，
   * 导致 SQL 语法错误。隔离性由 {@code ur.user_id} 精确定位保证：
   * 用户只能取到自己绑定的角色，而角色与用户同租户由用户/角色写入时的一致性校验保证。
   */
  @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE menu_tree AS (
            -- 1. 初始集：直接授权的菜单和按钮
            SELECT m.*
            FROM us_sys_menu m
            INNER JOIN us_sys_role_menu rm ON m.menu_id = rm.menu_id
            INNER JOIN us_sys_user_role ur ON rm.role_id = ur.role_id
            INNER JOIN us_sys_role r ON ur.role_id = r.role_id
            WHERE ur.user_id = #{userId}
              AND m.deleted = 0
              AND r.deleted = 0
              AND r.status = 1
              AND m.is_visible = 1

            UNION

            -- 2. 递归部分：向上查找父级（目录、模块）
            SELECT p.*
            FROM us_sys_menu p
            INNER JOIN menu_tree t ON t.parent_id = p.menu_id
            WHERE p.deleted = 0
              AND p.is_visible = 1
        )
        SELECT DISTINCT * FROM menu_tree
        ORDER BY sort_order ASC, menu_id ASC
        """)
    List<SysMenu> selectAccessibleMenusByUserId(@Param("userId") Long userId);
}
