import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const menuTreeUrl = new URL('../src/utils/menu-tree.ts', import.meta.url);

/** 将无运行时依赖的菜单树工具转译为可由 Node 直接执行的模块。 */
async function loadMenuTreeUtils() {
  const source = await readFile(menuTreeUrl, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022
    }
  });

  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('父菜单 ID 不会让新增且未授权的叶子菜单自动选中', async () => {
  const { getSelectedLeafMenuIds } = await loadMenuTreeUtils();
  const menuTree = [
    {
      menuId: 1,
      menuName: '系统管理',
      children: [
        {
          menuId: 2,
          menuName: '用户管理',
          children: [
            { menuId: 3, menuName: '查询' },
            { menuId: 4, menuName: '新增用户' }
          ]
        },
        { menuId: 5, menuName: '角色管理' }
      ]
    }
  ];

  assert.deepEqual(getSelectedLeafMenuIds(menuTree, [1, 2, 3, 5]), [3, 5]);
});

test('全部叶子菜单授权时保留全部叶子 ID，供树自动显示父节点全选', async () => {
  const { getSelectedLeafMenuIds } = await loadMenuTreeUtils();
  const menuTree = [
    {
      menuId: 1,
      menuName: '系统管理',
      children: [
        { menuId: 2, menuName: '用户管理' },
        { menuId: 3, menuName: '角色管理' }
      ]
    }
  ];

  assert.deepEqual(getSelectedLeafMenuIds(menuTree, [1, 2, 3]), [2, 3]);
});
