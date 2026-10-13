import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
const source=await readFile(new URL('../src/libs/statistics-chart.ts',import.meta.url),'utf8');
const url='data:text/javascript;base64,'+Buffer.from(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText).toString('base64');
const {chartSegments,statisticsPng}=await import(url);
test('饼图四分之一与四分之三连续排列，零数据不生成扇区',()=>{
 assert.deepEqual(chartSegments([{label:'线上',value:1},{label:'线下',value:3}]),[{label:'线上',value:1,percent:25,offset:0},{label:'线下',value:3,percent:75,offset:25}]);
 assert.equal(chartSegments([{label:'空',value:0}])[0].percent,0);
});
test('PNG输出包含标题、当前筛选和单位，并等待真实toBlob结果',async()=>{
 const texts=[];const expected=new Blob(['png'],{type:'image/png'});
 globalThis.document={createElement:()=>({getContext:()=>({fillRect(){},fillText(t){texts.push(t);}}),toBlob(cb,type){assert.equal(type,'image/png');cb(expected);}})};
 try {assert.equal(await statisticsPng('参与情况','课程甲 · 开班乙','人',[{label:'参与人数',value:3}],'bar'),expected);assert.ok(texts.includes('课程甲 · 开班乙'));assert.ok(texts.includes('单位：人'));assert.ok(texts.includes('3 人'));}
 finally {delete globalThis.document;}
});
