/** 后台侧栏菜单项，内容完全由当前用户的 profile 菜单树生成。 */
export interface AdminNavigationItem {
  title: string;
  path: string;
  icon: string;
  children?: AdminNavigationItem[];
}

/** 后台顶层模块，包含模块点击时应进入的首个可访问页面。 */
export interface AdminModuleNavigationItem extends AdminNavigationItem {
  landingPath: string;
  children: AdminNavigationItem[];
}
