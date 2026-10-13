import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
const source = await readFile(new URL('../../shared/src/libs/course-chapters.ts', import.meta.url), 'utf8');
const { orderCourseChapters } = await import('data:text/javascript;base64,' + Buffer.from(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText).toString('base64'));
test('目录前序排列保留全部内容及深度，旧快照默认根级', () => {
  const rows = orderCourseChapters([{ chapterId: 3, parentId: 2 }, { chapterId: 4 }, { chapterId: 2, parentId: 1 }, { chapterId: 1, parentId: 0 }]);
  assert.deepEqual(rows.map(c => [c.chapterId, c.depth]), [[4, 0], [1, 0], [2, 1], [3, 2]]);
});
test('较深目录不丢失或重复内容', () => {
  const rows = orderCourseChapters(Array.from({ length: 100 }, (_, i) => ({ chapterId: i + 1, parentId: i })));
  assert.equal(rows.length, 100);
  assert.equal(rows.at(-1).depth, 99);
});
