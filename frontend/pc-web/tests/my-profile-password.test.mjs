import test from 'node:test'; import assert from 'node:assert/strict'; import {readFile} from 'node:fs/promises'; import {createHash} from 'node:crypto'; import ts from 'typescript';
const compile=s=>`data:text/javascript;base64,${Buffer.from(ts.transpileModule(s,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText).toString('base64')}`;
const sha=compile((await readFile(new URL('../../shared/src/libs/sha256.ts',import.meta.url),'utf8')).replace("'js-sha256'",JSON.stringify(import.meta.resolve('js-sha256'))));
const source=(await readFile(new URL('../../shared/src/libs/my-password.ts',import.meta.url),'utf8')).replace("'./sha256'",JSON.stringify(sha));
const {prepareMyPassword}=await import(compile(source));
test('确认密码不一致或为空时不产生改密请求',async()=>{await assert.rejects(prepareMyPassword('old','new','different'),/不一致/);await assert.rejects(prepareMyPassword('','new','new'),/请填写/);});
test('双端改密只传两个SHA256摘要，不发送确认值或明文',async()=>{const data=await prepareMyPassword('old-password','new-password','new-password');assert.deepEqual(data,{oldPassword:createHash('sha256').update('old-password').digest('hex'),newPassword:createHash('sha256').update('new-password').digest('hex')});});
