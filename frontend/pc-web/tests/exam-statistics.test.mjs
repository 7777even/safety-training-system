import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const compile = source => `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText).toString('base64')}`;
const { examClientInfo } = await import(compile(await readFile(new URL('../../shared/src/libs/exam-client-info.ts',import.meta.url),'utf8')));
test('客户端只保存类型摘要，Edge和微信不误归Chrome',()=>{
  assert.deepEqual(examClientInfo('PC','Windows Chrome/132 Edg/132'),{platform:'PC',operatingSystem:'Windows',browser:'Edge'});
  assert.equal(examClientInfo('H5','Android Chrome/132 MicroMessenger/8').browser,'微信');
  assert.equal(examClientInfo('H5','').operatingSystem,'未知');
});
test('人员分页与导出共享筛选，导出选中不变为全部',async()=>{
  const calls=[];
  globalThis.batch5Request=async options=>{calls.push(options);return options.responseType==='blob'?new Blob(['xlsx']):{data:{list:[],total:0}};};
  try {
    const source=(await readFile(new URL('../src/api/exam-statistics.ts',import.meta.url),'utf8')).replace(/import \{ request \} from [^;]+;/,'const request = options => globalThis.batch5Request(options);');
    const api=await import(compile(source));
    await api.getExamStatisticsPeople(3,{pageNum:2,pageSize:20,learnerKeyword:'张',absentOnly:true});
    await api.exportExamStatistics(3,{learnerKeyword:'张',absentOnly:true},[7,8]);
    assert.deepEqual(calls[0].params,{pageNum:2,pageSize:20,learnerKeyword:'张',absentOnly:true});
    assert.deepEqual(calls[1].params,{learnerKeyword:'张',absentOnly:true,candidateIds:[7,8]});
    assert.equal(calls[1].responseType,'blob');
    assert.equal(calls[1].paramsSerializer.indexes,null);
  } finally { delete globalThis.batch5Request; }
});
