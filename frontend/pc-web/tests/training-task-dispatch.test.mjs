import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
/** 运行真实组件计算属性，验证转任务与部分安排提示。 */
async function panel(props) {
 const source=await readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue',import.meta.url),'utf8');
 const script=source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];const ast=ts.createSourceFile('panel.ts',script,ts.ScriptTarget.Latest,true);
 const names=['allContentArranged','arrangementSummaryText','arrangeButtonText','taskDispatchHint'];
 const declarations=ast.statements.filter(n=>ts.isVariableStatement(n)&&n.declarationList.declarations.some(d=>names.includes(d.name.getText(ast)))).map(n=>n.getText(ast)).join('\n');
 const js=ts.transpileModule(declarations,{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText;
 return vm.runInNewContext(js+'\n({arrangeButtonText,arrangementSummaryText,taskDispatchHint})',{props,computed:f=>({get value(){return f();}})});
}
test('无安排计划明确转为任务，部分安排保留继续处理提示',async()=>{
 const props={publishStatus:'PUBLISHED',contentCount:3,arrangedContentCount:0};const c=await panel(props);
 assert.equal(c.arrangeButtonText.value,'转为培训任务');assert.match(c.taskDispatchHint.value,/开始培训/);
 props.arrangedContentCount=1;assert.equal(c.arrangeButtonText.value,'继续安排培训内容');assert.equal(c.arrangementSummaryText.value,'已安排 1/3，请继续处理');
});
test('全部安排后提示开始培训，进行中不误提示再次转任务',async()=>{
 const props={publishStatus:'PUBLISHED',contentCount:2,arrangedContentCount:2};const c=await panel(props);
 assert.equal(c.arrangementSummaryText.value,'已安排完成');assert.match(c.taskDispatchHint.value,/开始培训/);
 props.publishStatus='RUNNING';assert.match(c.taskDispatchHint.value,/任务详情/);assert.doesNotMatch(c.taskDispatchHint.value,/再次/);
});
