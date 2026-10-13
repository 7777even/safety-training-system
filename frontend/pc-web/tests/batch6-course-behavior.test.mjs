import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';
import ts from 'typescript';

// 提取真实页面方法执行，验证跳转/请求行为而非仅检查源码字符串。
async function pageMethods(path, names, context) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8');
  const script = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile('page.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const methods = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.includes(node.name?.text));
  assert.equal(methods.length, names.length);
  const js = ts.transpileModule(methods.map(node => node.getText(ast)).join('\n'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  return vm.runInNewContext(js + `\n({${names.join(',')}})`, context);
}

test('PC结业结果可回看，显式补测才启动第二次作答', async () => {
  const routes = []; const starts = [];
  const methods = await pageMethods('../src/views/portal/class/learning.vue', ['enterCourseExam'], {
    tasks: { value: { enrollmentId: 10 } }, isExamItem: () => true,
    router: { push: async route => routes.push(route) },
    startCourseExamAttempt: async (...args) => { starts.push(args); return { examAttemptId: 22 }; },
    ElMessage: { error: message => assert.fail(message) }, Date
  });
  const item = { courseItemId: 2, itemTitle: '结业测评', learnable: true, canRetake: true, scorePublishStatus: 'EFFECTIVE', publishedExamAttemptId: 21 };
  await methods.enterCourseExam(item);
  assert.equal(routes[0].name, 'PortalCourseAssessmentResult'); assert.equal(starts.length, 0);
  await methods.enterCourseExam(item, true);
  assert.equal(starts.length, 1); assert.equal(starts[0][0], 10); assert.equal(starts[0][1], 2);
  assert.equal(routes[1].params.attemptId, 22); assert.equal(routes[1].name, 'PortalCourseAssessmentAttempt');
});

test('H5显式补测复用课程开考接口，普通回看不重新开考', async () => {
  const routes = []; const starts = [];
  const methods = await pageMethods('../../mobile-app/src/pages/course/learning.vue', ['openTask'], {
    tasks: { value: { enrollmentId: 10 } }, isAssessmentItem: () => true,
    navigateToAssessment: (...args) => routes.push(args), enterLive: () => assert.fail('非直播'),
    startCourseExamAttempt: async (...args) => { starts.push(args); return { examAttemptId: 22 }; },
    COURSE_ASSESSMENT_TASK_STATUS: { WAITING_RESULT: 'WAITING_RESULT' }, Date,
    uni: { showToast: () => assert.fail('请求失败') }, getAssessmentExecutionErrorMessage: String
  });
  const item = { courseItemId: 2, itemType: 'QUIZ', learnable: true, scorePublishStatus: 'EFFECTIVE', publishedExamAttemptId: 21 };
  methods.openTask(item); assert.equal(routes[0][1], 21); assert.equal(starts.length, 0);
  methods.openTask(item, true); await Promise.resolve(); await Promise.resolve();
  assert.equal(starts.length, 1); assert.equal(routes[1][1], 22); assert.equal(routes[1][2], false);
});

test('直播跳转等待后端登记，失败关闭窗口，不产生学习秒数请求', async () => {
  const popup = { location: {}, close: () => { popup.closed = true; } }; const calls = [];
  let fail = false;
  const methods = await pageMethods('../src/views/portal/class/learning.vue', ['enterLive'], {
    activeItem: { value: { courseItemId: 2 } }, enrollmentId: { value: 10 },
    window: { open: () => popup, location: { assign: () => assert.fail('已有窗口') } },
    participateCourseLive: async (...args) => { calls.push(args); if (fail) throw new Error('场次取消'); return { liveUrl: 'https://example.com/live' }; },
    refreshTaskProgress: async () => {}, ElMessage: { error: () => {} }
  });
  await methods.enterLive(); assert.deepEqual(calls[0], [10, 2]); assert.equal(popup.location.href, 'https://example.com/live'); assert.equal(popup.opener, null);
  fail = true; await methods.enterLive(); assert.equal(popup.closed, true);
});

test('PC分类切换回首页，查询沿用分类和原有分页参数', async () => {
  const requests = []; const context = {
    offeringKeyword: { value: " 安全 " }, selectedCategoryId: { value: 7 }, offeringPageNum: { value: 3 }, offeringPageSize: { value: 20 },
    loading: { value: false }, offerings: { value: [] }, offeringTotal: { value: 0 }, courses: { value: [] },
    getLearnerOfferingPage: async params => { requests.push(params); return { list: [], total: 0 }; }, loadCourseCovers: async () => {}
  };
  const methods = await pageMethods('../src/views/portal/class/index.vue', ['changeOfferingCategory', 'loadOfferings'], context);
  await methods.changeOfferingCategory(); assert.equal(requests[0].pageNum, 1); assert.equal(requests[0]['query.categoryId'], 7); assert.equal(requests[0]['query.keyword'], '安全');
});

test('H5名称与分类组合筛选复用现有查询，空关键词恢复全部', async () => {
  const requests = []; const keyword = { value: ' 安全 ' };
  const methods = await pageMethods('../../mobile-app/src/pages/course/index.vue', ['loadOfferings'], {
    offeringKeyword: keyword, offeringLoadVersion: 0, offeringsLoading: { value: false },
    categories: { value: [] }, categoryOptions: { value: [{ id: 7 }] }, categoryIndex: { value: 0 },
    offeringCoverUrls: { value: {} }, offerings: { value: [] },
    getLearnerCourseCategories: async () => [], revokeProtectedImageUrl: () => {},
    getLearnerOfferingPage: async params => { requests.push(params); return { list: [], total: 0 }; },
    loadOfferingCovers: async () => {}
  });
  await methods.loadOfferings();
  assert.equal(requests[0]['query.keyword'], '安全'); assert.equal(requests[0]['query.categoryId'], 7);
  keyword.value = ' ';
  await methods.loadOfferings(); assert.equal(requests[1]['query.keyword'], undefined);
});
