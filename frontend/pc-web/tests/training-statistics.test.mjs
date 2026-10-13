import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
/** 运行真实 API 封装，只替换统一传输层，验证下钻和分页参数不丢失。 */
async function api(file,calls) {
  globalThis.batch5TrainingRequest=async options=>{calls.push(options);return {data:{list:[],total:0}};};
  const source=(await readFile(new URL(`../src/api/${file}.ts`,import.meta.url),'utf8')).replace(/import \{ request \} from [^;]+;/,'const request = options => globalThis.batch5TrainingRequest(options);');
  const js=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`);
}

test('无部门班级下钻保留计划、模式、关键字与分页',async()=>{
  const calls=[];try {const client=await api('training-class-statistics',calls);const filters={trainingPlanId:9,unassignedOnly:true,mode:'OFFLINE',learnerKeyword:'张',pageNum:2,pageSize:10};await client.getTrainingClassPeople(filters);assert.equal(calls[0].url,'/api/training/statistics/classes/people');assert.deepEqual(calls[0].params,filters);}finally{delete globalThis.batch5TrainingRequest;}
});

test('完成名单与计划分页使用确认的接口及相同筛选',async()=>{
  const calls=[];try {const client=await api('training-statistics',calls);await client.getTrainingPlanStatistics({planName:'计划',pageNum:3,pageSize:10});const filters={completedOnly:true,learnerKeyword:'王',pageNum:2,pageSize:10};await client.getTrainingProgressPeople(8,filters);assert.equal(calls[0].url,'/api/training/statistics/plans');assert.equal(calls[0].params.pageNum,3);assert.equal(calls[1].url,'/api/training/statistics/plans/8/people');assert.deepEqual(calls[1].params,filters);}finally{delete globalThis.batch5TrainingRequest;}
});

test('全部课程记录不附加单课程或单计划限制',async()=>{
  const calls=[];try {const client=await api('course-statistics',calls);await client.getCourseStatisticsPeople({learnerKeyword:'王',pageNum:2,pageSize:10});assert.equal(calls[0].url,'/api/course/statistics/people');assert.deepEqual(calls[0].params,{learnerKeyword:'王',pageNum:2,pageSize:10});}finally{delete globalThis.batch5TrainingRequest;}
});

test('统计契约覆盖实际请求URL、权限及可解析的模型引用',async()=>{
  for(const domain of ['exam','course','training']) {
    const document=JSON.parse(await readFile(new URL(`../../../docs/api/${domain}.openapi.json`,import.meta.url),'utf8'));
    const schemas=document.components.schemas;const tags=new Set(document.tags.map(tag=>tag.name));
    for(const [path,methods] of Object.entries(document.paths)) {
      if(!path.includes('/statistics'))continue;
      assert.ok(path.startsWith('/api/'),path);
      for(const operation of Object.values(methods)) {
        assert.ok(operation.summary&&operation.description&&operation['x-permission']);assert.ok(operation.tags.every(tag=>tags.has(tag)));
        assert.ok(operation.security?.length);for(const requirement of operation.security)for(const name of Object.keys(requirement))assert.ok(document.components.securitySchemes[name],`${path}:unknown security ${name}`);
        for(const param of operation.parameters??[])assert.ok(param.description && 'example' in param.schema,`${path}:${param.name}`);
        const walk=value=>{if(!value||typeof value!=='object')return;if(value.$ref?.startsWith('#/components/schemas/'))assert.ok(schemas[value.$ref.split('/').at(-1)],value.$ref);Object.values(value).forEach(walk);};walk(operation);
      }
    }
    if(domain==='training')assert.equal(document.paths['/api/training/statistics/classes'].get.tags[0],'TrainingClassStatisticsController');
  }
});
