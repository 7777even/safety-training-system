import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
/** 运行真实人员配置组件边界，范围由服务端展开。 */
async function methods(names,context) {
 const source=await readFile(new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue',import.meta.url),'utf8');const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
 const ast=ts.createSourceFile('panel.ts',script,ts.ScriptTarget.Latest,true);const js=ts.transpileModule(ast.statements.filter(n=>ts.isFunctionDeclaration(n)&&names.includes(n.name?.text)).map(n=>n.getText(ast)).join('\n'),{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(js+'\n({'+names.join(',')+'})',context);
}
test('岗位和用户组使用正确中文单位，评卷范围不伪造成指定人员',async()=>{
 const state={reviewerSelection:{},selectedReviewers:{}};const c=await methods(['rangeSummary','selectReviewers'],state);
 assert.equal(c.rangeSummary({selectionType:'POSITION',subjectIds:[3,4]}),'已选 2 个岗位');assert.equal(c.rangeSummary({selectionType:'GROUP',subjectIds:[5]}),'已选 1 个用户组');c.selectReviewers({selectionType:'GROUP',subjectIds:[5]},[{userId:9}]);assert.equal(state.selectedReviewers.value.length,0);assert.equal(state.reviewerSelection.value.selectionType,'GROUP');
});
test('保存提交两种范围并回读真实评卷人数，不提交组ID作为用户ID',async()=>{
 const calls=[];const reviewerSelection={value:{selectionType:'GROUP',subjectIds:[5]}};const reviewerIds={value:[]};
 const c=await methods(['save'],{selectedUsers:{value:[]},actualCandidateCount:{value:2},selection:{value:{selectionType:'POSITION',subjectIds:[3]}},reviewerSelection,reviewerIds,candidateTotal:{value:2},requiresReviewer:{value:true},saving:{},props:{examId:7},bindExamSelection:async(id,p)=>calls.push(['candidate',p]),replaceExamReviewers:async(id,p)=>calls.push(['reviewer',p]),load:async()=>{calls.push(['load']);reviewerIds.value=[9,10];},emit:(...x)=>calls.push(x),ElMessage:{success:()=>{},warning:()=>{}}});
 await c.save();assert.equal(calls[1][1].selection.selectionType,'GROUP');assert.equal(calls[1][1].reviewerUserIds,undefined);assert.equal(calls[3][2],2);assert.equal(reviewerSelection.value,undefined);
});
test('评卷资格失败不宣布人员配置成功，不清掉未提交条件',async()=>{
 const events=[];const selected={value:{selectionType:'GROUP',subjectIds:[5]}};const saving={value:false};
 const c=await methods(['save'],{selectedUsers:{value:[]},actualCandidateCount:{value:2},selection:{value:undefined},candidateTotal:{value:2},reviewerSelection:selected,reviewerIds:{value:[]},requiresReviewer:{value:true},saving,props:{examId:7},replaceExamReviewers:async()=>{throw new Error('评卷人没有阅卷权限');},emit:(...x)=>events.push(x),ElMessage:{success:()=>events.push('success'),warning:()=>{}}});
 await assert.rejects(c.save(),/阅卷权限/);assert.equal(events.length,0);assert.equal(selected.value.selectionType,'GROUP');assert.equal(saving.value,false);
});

test('同一面板保存考生后单改评卷不重新展开旧范围，筛选零人不阻止保存',async()=>{
 let bindings=0;const selection={value:{selectionType:'GROUP',subjectIds:[5]}};const reviewerSelection={value:undefined};const total={value:2};const users={value:[]};
 const c=await methods(['save'],{selection,selectedUsers:users,reviewerSelection,reviewerIds:{value:[9]},candidateTotal:total,actualCandidateCount:{value:2},requiresReviewer:{value:true},saving:{},props:{examId:7},bindExamSelection:async()=>{bindings++;},replaceExamReviewers:async()=>{},load:async()=>{},emit:()=>{},ElMessage:{success:()=>{},warning:()=>{throw new Error('筛选不能阻止配置');}}});
 await c.save();total.value=0;reviewerSelection.value={selectionType:'POSITION',subjectIds:[3]};await c.save();assert.equal(bindings,1);assert.equal(selection.value,undefined);
});
