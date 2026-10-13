import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
async function client(request) {
 const source=await readFile(new URL('../src/api/training-change.ts',import.meta.url),'utf8');const ast=ts.createSourceFile('api.ts',source,ts.ScriptTarget.Latest,true);
 const js=ts.transpileModule(ast.statements.filter(ts.isFunctionDeclaration).map(n=>n.getText(ast).replace('export ','')).join('\n'),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(js+'\n({savePlanChange,submitPlanChange,decidePlanChange})',{request});
}
test('变更保存使用独立草稿，不写生效计划',async()=>{const calls=[];const c=await client(async r=>{calls.push(r);return {code:0,data:7};});assert.equal(await c.savePlanChange(5,{baseVersion:2,changeType:'BASIC',basic:{planName:'调整'}}),7);assert.equal(calls.length,1);assert.equal(calls[0].url,'/api/training/plans/5/changes');assert.equal(calls[0].data.baseVersion,2);});
test('提交使用变更版本，指定另一审批人',async()=>{const calls=[];const c=await client(async r=>{calls.push(r);return {code:0,data:null};});await c.submitPlanChange(5,7,3,9);assert.equal(calls[0].url,'/api/training/plans/5/changes/7/submit');assert.equal(calls[0].data.version,3);assert.equal(calls[0].data.approverUserId,9);});
test('生效被拒绝必须保留失败提示，不能当作批准成功',async()=>{const c=await client(async()=>({code:8351,message:'已有学习事实，无法安全同步'}));await assert.rejects(c.decidePlanChange(5,7,true,'同意'),/无法安全同步/);});

async function functions(names,context) {
 const source=await readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanChangePanel.vue',import.meta.url),'utf8');
 const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];const ast=ts.createSourceFile('panel.ts',script,ts.ScriptTarget.Latest,true);
 const js=ts.transpileModule(ast.statements.filter(n=>ts.isFunctionDeclaration(n)&&names.includes(n.name?.text)).map(n=>n.getText(ast)).join('\n'),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(js+'\n({'+names.join(',')+'})',context);
}
test('完整变更弹窗深拷贝阶段、评价与人员，不污染原计划',async()=>{
 const plan={trainingPlanId:5,planCode:'P5',planName:'原计划',version:2,trainingNature:'INITIAL',stages:[{stageName:'原阶段',contents:[{contentName:'原内容'}]}],evaluationForms:[{formVersionId:3}]};
 const payload={value:undefined};const full={get value(){return payload.value?.full;}};
 const c=await functions(['open'],{props:{plan,participants:[{userId:9}]},editing:{},editorReadonly:{},tab:{},annualChanged:{},annualId:{},payload,full,fullModel:{value:undefined},editorVisible:{}});
 c.open('FULL');payload.value.full.stages[0].contents[0].contentName='改后';payload.value.full.forms[0].formVersionId=7;payload.value.full.participantSelection.subjectIds.push(10);
 assert.equal(plan.stages[0].contents[0].contentName,'原内容');assert.equal(plan.evaluationForms[0].formVersionId,3);assert.equal(payload.value.baseVersion,2);
});
test('保存失败保留草稿和弹窗且不报告生效成功',async()=>{
 const messages=[];const visible={value:true};const saving={value:false};const draft={baseVersion:2,changeType:'BASIC',basic:{planName:'新名称'}};
 const c=await functions(['save'],{Error,payload:{value:draft},editorReadonly:{value:false},saving,full:{value:null},props:{plan:{trainingPlanId:5}},editing:{},editorVisible:visible,savePlanChange:async()=>{throw new Error('版本冲突');},ElMessage:{success:x=>messages.push('success'),error:x=>messages.push(x)}});
 await c.save();assert.equal(visible.value,true);assert.equal(saving.value,false);assert.equal(messages.length,1);assert.match(messages[0],/版本冲突/);assert.equal(draft.basic.planName,'新名称');
});
