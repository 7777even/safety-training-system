import type { MenuTreeItem } from '@shared/types';

/**
 * 从角色接口返回的菜单 ID 中提取当前树内已授权的叶子节点 ID。
 * 父节点仅用于层级归属，树的全选与半选状态由叶子节点授权情况自动计算。
 */
export function getSelectedLeafMenuIds(menuTree: MenuTreeItem[], selectedMenuIds: number[]): number[] {
  const selectedMenuIdSet = new Set(selectedMenuIds);
  const leafMenuIds: number[] = [];

  function collectLeafMenuIds(nodes: MenuTreeItem[]): void {
    nodes.forEach((menu) => {
      const children = menu.children ?? [];

      if (children.length > 0) {
        collectLeafMenuIds(children);
      } else if (selectedMenuIdSet.has(menu.menuId)) {
        leafMenuIds.push(menu.menuId);
      }
    });
  }

  collectLeafMenuIds(menuTree);
  return leafMenuIds;
}
