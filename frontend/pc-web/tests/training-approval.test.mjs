import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
async function client(request) {
 const source=await readFile(new URL('../src/api/training-approval.ts',import.meta.url),'utf8');
 const ast=ts.createSourceFile('api.ts',source,ts.ScriptTarget.Latest,true);
 const js=ts.transpileModule(ast.statements.filter(ts.isFunctionDeclaration).map(n=>n.getText(ast).replace('export ','')).join('\n'),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(js+'\n({submitPlanApproval,decidePlanApproval,bindAnnualPlan,saveAnnualPlan})',{request});
}
test('审批提交绑定读取版本和指定审批人，不能触发发布',async()=>{
 const calls=[];const c=await client(async r=>{calls.push(r);return {code:0,data:null};});
 await c.submitPlanApproval(5,3,9);assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/training/plans/5/approvals');assert.equal(calls[0].data.version,3);assert.equal(calls[0].data.approverUserId,9);
});
test('驳回意见透传，权限失败不能报告成功',async()=>{
 const calls=[];const c=await client(async r=>{calls.push(r);return {code:8337,message:'仅指定审批人可审批'};});
 await assert.rejects(c.decidePlanApproval(5,false,'内容缺失'),/指定审批人/);assert.equal(calls[0].data.approved,false);assert.equal(calls[0].data.comment,'内容缺失');
});
test('年度关联和文件编辑保留各自版本',async()=>{
 const calls=[];const c=await client(async r=>{calls.push(r);return {code:0,data:10};});
 await c.bindAnnualPlan(5,3,2);await c.saveAnnualPlan({planYear:2026,planName:'年度计划',fileInfoId:8,version:1},10);
 assert.equal(calls[0].data.version,3);assert.equal(calls[0].data.annualPlanId,2);assert.equal(calls[1].method,'PUT');assert.equal(calls[1].data.version,1);
});

async function componentFunctions(file, names, context, prefix='') {
 const source=await readFile(new URL(file,import.meta.url),'utf8');
 const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
 const ast=ts.createSourceFile('component.ts',script,ts.ScriptTarget.Latest,true);
 const js=ts.transpileModule(ast.statements.filter(n=>ts.isFunctionDeclaration(n) && names.includes(n.name?.text)).map(n=>n.getText(ast)).join('\n'),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(prefix+js+'\n({'+names.join(',')+'})',context);
}
test('名单添加与批量绑定成功后重新读取计划版本，再刷新名单',async()=>{
 const calls=[];const plan={value:{trainingPlanId:5,version:2}};
 const c=await componentFunctions('../src/views/admin/business-center/training/plan/workspace/index.vue',['assignParticipant','handleParticipantSelection'],{
  plan,selectedUserId:{value:8},assignTrainingPlanParticipant:async()=>calls.push('assign'),bindTrainingPlanParticipantSelection:async()=>calls.push('bind'),
  loadPlan:async()=>{calls.push('plan');plan.value.version++;},refreshExecutionFacts:async()=>calls.push('facts'),ElMessage:{success:()=>{}}
 });
 await c.assignParticipant();await c.handleParticipantSelection({selectionType:'GROUP',subjectIds:[1]});
 assert.deepEqual(calls,['assign','plan','facts','bind','plan','facts']);assert.equal(plan.value.version,4);
});
test('审批读取失败保持锁定，旧请求不能覆盖新计划',async()=>{
 const states=[];const history={value:[]};let resolveOld;let first=true;
 const props={plan:{trainingPlanId:5,version:2}};
 const c=await componentFunctions('../src/views/admin/business-center/training/plan/components/PlanApprovalPanel.vue',['load'],{
  props,loading:{value:false},error:{value:''},emit:(...args)=>states.push(args),history,linked:{value:null},annuals:{value:[]},approvers:{value:[]},annualId:{value:null},
  pending:{value:false},approved:{value:false},canAnnualList:{value:false},canEdit:{value:false},
  getPlanApprovals:async()=>{if(first){first=false;return new Promise(resolve=>{resolveOld=resolve;});}throw new Error('读取失败');},getLinkedAnnualPlan:async()=>null
 },'let sequence=0;');
 const old=c.load();props.plan={trainingPlanId:6,version:1};await c.load();resolveOld([{approvalStatus:'APPROVED',planVersion:2}]);await old;
 assert.equal(history.value.length,0);assert.equal(states.length,2);assert.equal(states[1][1],true);assert.equal(states[1][2],false);
});
