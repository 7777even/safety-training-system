import test from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';import ts from 'typescript';
import { ref, reactive } from 'vue';
const compile=s=>`data:text/javascript;base64,${Buffer.from(ts.transpileModule(s,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText).toString('base64')}`;
const home=compile(await readFile(new URL('../../shared/src/utils/learner-home.ts',import.meta.url),'utf8'));
const historySource=(await readFile(new URL('../../shared/src/utils/learner-history.ts',import.meta.url),'utf8')).replace("'./learner-home'",JSON.stringify(home));
const {historyTarget,historyStatus}=await import(compile(historySource));

test('更正失败原因显示在仍打开的弹窗内，不能被遮罩隐藏',async()=>{
  const vue=await readFile(new URL('../src/views/admin/result/archive/components/ArchiveHistoryPanel.vue',import.meta.url),'utf8');
  const dialog=vue.match(/<ElDialog\b[^>]*>([\s\S]*?)<\/ElDialog>/)?.[1];
  assert.ok(dialog,'更正使用弹窗');
  assert.match(dialog,/<ElAlert\b[^>]*v-if="error"[^>]*:title="error"/,'失败提示必须在弹窗内容中可见');
});
test('历史原入口涵盖六类来源，结束记录不依赖当前任务列表',()=>{
  for(const platform of ['PC','H5']) {
    assert.ok(historyTarget({kind:'COURSE',resourceId:1},platform).includes(platform==='PC'?'/learning/1':'enrollmentId=1'));
    assert.ok(historyTarget({kind:'COURSE_QUIZ',resourceId:2,itemId:3},platform).includes(platform==='PC'?'/learning/2':'enrollmentId=2'));
    assert.ok(historyTarget({kind:'FORMAL_EXAM',resourceId:4,parentId:5,attemptId:6,action:'VIEW_RESULT'},platform).includes(platform==='PC'?'/result/6':'examAttemptId=6'));
    assert.ok(historyTarget({kind:'TRAINING',resourceId:7},platform).includes(platform==='PC'?'/training/7':'trainingParticipantId=7'));
    for(const kind of ['PRACTICE','SIMULATION']) assert.ok(historyTarget({kind,resourceId:8},platform).includes(platform==='PC'?'/practice/8':'/practice/attempt?id=8'));
  }
  assert.throws(()=>historyTarget({kind:'FORMAL_EXAM',resourceId:0,parentId:5},'PC'),/不可用/);
  assert.equal(historyStatus('WITHDRAWN'),'已退出');assert.equal(historyStatus('PENDING_PUBLISH'),'待发布');
});
test('本人详情请求不发送用户身份，管理更正明确发送当前展示版本',async()=>{
  const path=new URL('../src/api/learner-history.ts',import.meta.url);let seen;
  const source=(await readFile(path,'utf8')).replace("import { request } from './request';",'const request = async (request) => globalThis.__historyRequest(request);');
  globalThis.__historyRequest=async request=>{seen=request;return {code:0,data:{version:4}};};
  try {const api=await import(compile(source));await api.getMyHistoryDetail('COURSE',1);assert.equal(seen.url,'/api/learner/history/COURSE/1');assert.equal(seen.data,undefined);
    await api.appendHistoryCorrection(7,'COURSE',1,{version:3,changes:{hours:2},reason:'核实'});assert.equal(seen.method,'POST');assert.deepEqual(seen.data,{version:3,changes:{hours:2},reason:'核实'});
  } finally {delete globalThis.__historyRequest;}
});
test('PC历史日期选中后清空仍能查询全部日期，不抛异常',async()=>{
  const vue=await readFile(new URL('../src/views/user-center/history/index.vue',import.meta.url),'utf8');
  let params;
  globalThis.__historyFixture={ref,reactive,onMounted:()=>{},useRouter:()=>({}),DEFAULT_LIST_PAGE_SIZE:20,
    getMyHistory:async p=>{params=p;return {list:[],total:0};}};
  try {
    const script=vue.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;\r?\n/gm,'');
    const page=await import(compile('const {ref,reactive,onMounted,useRouter,getMyHistory,DEFAULT_LIST_PAGE_SIZE}=globalThis.__historyFixture;\n'+script+'\nexport {query,load,error};'));
    page.query.dates=['2026-10-08','2026-10-09'];await page.load();assert.equal(params['query.fromDate'],'2026-10-08');
    page.query.dates=null;params=undefined;await page.load();assert.equal(page.error.value,'');assert.equal(params['query.fromDate'],undefined);assert.equal(params['query.toDate'],undefined);
  } finally {delete globalThis.__historyFixture;}
});
