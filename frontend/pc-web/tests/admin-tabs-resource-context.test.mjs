import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const tabStateUrl = new URL('../src/layouts/admin-tab-state.ts', import.meta.url);

/** 将无运行时依赖的页签状态工具转译为 Node 可执行模块。 */
async function loadTypeScriptModule(url) {
  const source = await readFile(url, 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('课程和考试工作台标签优先显示进入时已知的资源名称', async () => {
  const tabState = await loadTypeScriptModule(tabStateUrl);
  const examRoute = {
    path: '/admin/business-center/exams/exam/6',
    fullPath: '/admin/business-center/exams/exam/6?stage=assignment&tabTitle=%E5%AE%89%E5%85%A8%E7%94%9F%E4%BA%A7%E8%80%83%E8%AF%95',
    params: { examId: '6' },
    query: { stage: 'assignment', tabTitle: '安全生产考试' }
  };
  const courseRoute = {
    path: '/admin/business-center/course/center',
    fullPath: '/admin/business-center/course/center?courseId=10&stage=content&tabTitle=%E5%AE%89%E5%85%A8%E5%9F%B9%E8%AE%AD%E8%AF%BE%E7%A8%8B',
    params: {},
    query: { courseId: '10', stage: 'content', tabTitle: '安全培训课程' }
  };

  assert.equal(tabState.getAdminTabTitle(examRoute, '考试工作台'), '安全生产考试 · 人员配置');
  assert.equal(tabState.getAdminTabTitle(courseRoute, '课程中心'), '安全培训课程 · 内容编排');
});

test('同一路由记录切换题目或试卷资源时，详情页面按资源参数重建', async () => {
  const layout = await readFile(new URL('../src/layouts/AdminLayout.vue', import.meta.url), 'utf8');

  assert.match(layout, /const contentViewKey = computed\(\(\) => `\$\{String\(route\.name \?\? route\.path\)\}:\$\{JSON\.stringify\(route\.params\)\}`\);/);
  assert.match(layout, /<RouterView :key="contentViewKey" \/>/);
});

test('页签容器宽度变化后重新计算左右滑动按钮状态', async () => {
  const layout = await readFile(new URL('../src/layouts/AdminLayout.vue', import.meta.url), 'utf8');

  assert.match(layout, /let tabScrollResizeObserver: globalThis\.ResizeObserver \| undefined;/);
  assert.match(layout, /tabScrollResizeObserver = new globalThis\.ResizeObserver\(updateTabScrollState\);/);
  assert.match(layout, /tabScrollResizeObserver\.observe\(tabScrollContainer\.value\);/);
  assert.match(layout, /tabScrollResizeObserver\?\.disconnect\(\);/);
});
