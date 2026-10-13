import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
import { computed, nextTick, reactive, ref, watch } from 'vue';

/** 运行两端请求适配器，验证身份不由前端指定且错误不会伪装成功。 */
for (const app of ['pc-web', 'mobile-app']) {
  test(`${app}个人练习请求严格使用本人接口`, async () => {
    const requests = [];
    const source = await readFile(new URL(`../../${app}/src/api/learner-practice.ts`, import.meta.url), 'utf8');
    const output = ts.transpileModule(source.replace(/import\s*\{\s*request\s*\}\s*from\s*['"]\.\/request['"];?/, 'const request = (...args) => globalThis.__practiceRequest(...args);'), { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
    globalThis.__practiceRequest = async config => { requests.push(config); return { code: 0, data: { sessionId: 9 } }; };
    const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${app}`);
    assert.deepEqual(await api.startPractice({ candidateId: 1, mode: 'PRACTICE', clientToken: 'token' }), { sessionId: 9 });
    await api.savePracticeAnswer(9, 4, { selected: ['A'], blanks: {} });
    await api.setWrongDisposition(2, 'MASTERED');
    await api.getFavoriteQuestions({ pageNum: 1, pageSize: 20, 'query.keyword': '安全' });
    assert.deepEqual(requests.map(x => [x.method, x.url]), [
      ['POST', '/api/question/learner/sessions'],
      ['PUT', '/api/question/learner/sessions/9/items/4/answer'],
      ['PUT', '/api/question/learner/wrong-questions/2/disposition'],
      ['GET', '/api/question/learner/favorite-questions']
    ]);
    assert.ok(requests.every(x => !x.data || !('userId' in x.data)));
    assert.equal(requests.at(-1).params['query.keyword'], '安全');
    globalThis.__practiceRequest = async () => ({ code: 7092, message: '无权访问' });
    await assert.rejects(api.getPractice(9), /无权访问/);
  });
}

/** 两端考试保留课程领域，收藏/关注写入均使用本人接口。 */
for (const app of ['pc-web', 'mobile-app']) {
  test(`${app}个人中心保留领域入口与收藏关注请求`, async () => {
    const requests = [];
    const source = await readFile(new URL(`../../${app}/src/api/learner-center.ts`, import.meta.url), 'utf8');
    const output = ts.transpileModule(source.replace(/import\s*\{\s*request\s*\}\s*from\s*['"]\.\/request['"];?/, 'const request = (...args) => globalThis.__centerRequest(...args);'), { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
    globalThis.__centerRequest = async config => { requests.push(config); return { code: 0, data: { examAttemptId: 9 } }; };
    const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${app}`);
    await api.getPersonalAssessments({ 'query.status': 'ENDED' });
    await api.getPersonalAttemptHistory({ enrollmentId: 3, itemId: 4 });
    assert.equal(await api.startPersonalCourseAttempt(3, 4), 9);
    await api.setCourseFavorite(5, true); await api.setCourseFavorite(5, false);
    await api.getPersonalTeachers({ 'query.keyword': '老师' });
    await api.setTeacherFollow(6, true); await api.setTeacherFollow(6, false);
    assert.deepEqual(requests.map(x => [x.method, x.url]), [
      ['GET', '/api/exam/learner/assessment-tasks'], ['GET', '/api/exam/learner/attempt-history'],
      ['POST', '/api/course/learning/enrollments/3/items/4/attempts'],
      ['PUT', '/api/course/learning/favorite-courses/5'], ['PUT', '/api/course/learning/favorite-courses/5'],
      ['GET', '/api/course/learning/teachers'], ['PUT', '/api/course/learning/teacher-follows/6'], ['PUT', '/api/course/learning/teacher-follows/6']
    ]);
    assert.deepEqual(requests[3].data, { favorite: true }); assert.deepEqual(requests[4].data, { favorite: false });
    assert.deepEqual(requests[6].data, { followed: true }); assert.deepEqual(requests[7].data, { followed: false });
    assert.equal(requests[2].data.platform, app === 'pc-web' ? 'PC' : 'H5');
    globalThis.__centerRequest = async () => ({ code: 5092, message: '无权访问' });
    await assert.rejects(api.setTeacherFollow(6, true), /无权访问/);
    await assert.rejects(api.setCourseFavorite(5, true), /无权访问/);
  });
}

test('未反馈不能推断为错题，标准答案按冻结结构展示', async () => {
  const source = await readFile(new URL('../../shared/src/utils/learner-practice.ts', import.meta.url), 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
  const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}`);
  const questions = [{ itemId: 1, answer: null, evaluationResult: null, favorite: true }, { itemId: 2, answer: { selected: ['A'], blanks: {} }, evaluationResult: 'AUTO_WRONG', favorite: false }];
  assert.deepEqual(api.filterPracticeQuestions(questions, 'UNANSWERED').map(q => q.itemId), [1]);
  assert.deepEqual(api.filterPracticeQuestions(questions, 'WRONG').map(q => q.itemId), [2]);
  assert.deepEqual(api.filterPracticeQuestions(questions, 'FAVORITE').map(q => q.itemId), [1]);
  assert.equal(api.practiceStandardAnswer('{"blanks":[{"blankNo":1,"answers":["安全","平安"]}]}'), '第1空：安全 / 平安');
  assert.equal(api.practiceStandardAnswer('{"correctOptions":["A","C"]}'), 'A、C');
  assert.equal(api.practiceFeedback(null), '');
  assert.equal(api.practiceRecordTime('2026-10-08T10:05:30'), '2026-10-08 10:05:30');
  assert.equal(api.practiceRecordTime(null), '尚未提交');
  assert.equal(api.practiceRecordDuration(125), '2分5秒');
  assert.equal(api.practiceRecordDuration(3600), '1小时0分0秒');
  assert.equal(api.practiceSourceLabel('COURSE_QUIZ'), '课程测试');
});

/** 执行两端真实页面脚本，验证收藏分类加载和取消后的列表回显。 */
for (const app of ['pc-web', 'mobile-app']) {
  test(`${app}我的收藏分类读取题目，关注课程只读取课程`, async () => {
    const file = app === 'pc-web' ? 'views/user-center/courses/index.vue' : 'pages/favorite-course/index.vue';
    const source = await readFile(new URL(`../../${app}/src/${file}`, import.meta.url), 'utf8');
    const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?$/gm, '');
    const calls = []; const stops = []; const route = reactive({ name: 'LearnerCourseFavorites', query: {} }); let saved = true;
    globalThis.__favoriteView = {
      computed, ref, watch: (...args) => { const stop = watch(...args); stops.push(stop); return stop; },
      onMounted: () => {}, onLoad: fn => fn({ tab: 'favorites' }), onShow: () => {},
      useRoute: () => route, useRouter: () => ({}), DEFAULT_LIST_PAGE_SIZE: 20, LEARNER_LIST_PAGE_SIZE: 20,
      getFavoriteCourses: async () => { calls.push('courses'); return { list: [], total: 0 }; },
      getFavoriteCourseCatalog: async () => { calls.push('catalog'); return { list: [], total: 0 }; },
      getFavoriteQuestions: async () => { calls.push('questions'); return { list: saved ? [{ questionId: 5, stem: '检查安全装置' }] : [], total: saved ? 1 : 0 }; },
      setQuestionFavorite: async (id, favorite) => { assert.equal(id, 5); assert.equal(favorite, false); saved = false; },
      ElMessage: { error: message => assert.fail(message) }, uni: { showToast: ({ title }) => assert.fail(title) }
    };
    const bindings = `const { ${Object.keys(globalThis.__favoriteView).join(', ')} } = globalThis.__favoriteView;`;
    const output = ts.transpileModule(`${bindings}\n${script}\nexport { title, questionTab, favoriteTab, questions, load, removeQuestion };`, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
    let api;
    try {
      // 执行页面响应式行为，不模拟DOM，也不把这里当真实浏览器验收。
      api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#favorite-${app}`);
      await api.load(); assert.equal(calls.at(-1), 'courses');
      api.favoriteTab.value = 'QUESTIONS'; await nextTick(); await api.load();
      assert.equal(api.questionTab.value, true); assert.equal(calls.at(-1), 'questions');
      assert.equal(api.questions.value[0].stem, '检查安全装置');
      await api.removeQuestion(5); assert.deepEqual(api.questions.value, []);
      if (app === 'pc-web') route.name = 'LearnerFollowedCourses'; else api.title.value = '关注课程';
      await nextTick(); await api.load(); assert.equal(api.questionTab.value, false); assert.equal(calls.at(-1), 'courses');
    } finally { stops.forEach(stop => stop()); delete globalThis.__favoriteView; }
  });
}
