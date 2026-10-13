# M2 前端表单选择与字典回显整改 QA

## 范围

- 知识点父级选择、知识点编码/名称与题库分类名称必填校验。
- 试卷分类、封面上传、题库分类默认联动、题目编码/题干展示。
- 试卷与考试枚举字段统一采用 `{ label, value }`，只读界面显示中文 label。
- 考试分类、题库、封面、通知范围改为 Contract 数据选择，试卷版本默认带入分类和封面。
- 所属机构切换时清空并重载试卷版本、分类、题库、父级知识点等下游选择，避免跨机构外键残留。
- 同类问题复核覆盖试卷版本/预览、考试人员、批阅、成绩处置和学员考试任务。

## TDD 证据

1. 扩展题库、题目、试卷和考试页面行为测试，首次运行按预期失败。
2. 完成最小实现后，四个目标测试文件 4/4 通过。
3. M2 全部 question/paper/exam 相关测试在 Node 16 的 `structuredClone` 测试兼容垫片下 52/52 通过。

## 验证结果

- `node --test` 目标测试：4/4 通过。
- M2 Node 回归：52/52 通过。
- `pnpm exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `git diff --check`：通过。
- 目标 ESLint：未执行到规则检查；Node 16.19 缺少 ESLint 10 运行所需的 `structuredClone`。
- PC build：未执行到项目编译；Node 16.19 缺少 Vite 8/Rolldown 所需的 `node:util.styleText`。

## Contract 核对

- 未修改 OpenAPI；所有请求字段继续来自现有 Contract。
- `BankCategoryCreateRequest/VO` 没有分类编码字段，故本次只能完成分类名称必填。若要求保存分类编码，需新增
  `categoryCode` 并同步创建、更新、列表/详情响应和 Mock。
- `PaperVersionQuestionVO`、`PaperPreviewVO` 不返回题目编码和题干；本次按最小闭环复用题目详情接口补查，后续可考虑在响应中补字段以消除
  N+1。

## 结论

除题库分类编码 Contract 缺口和 Node 版本导致的 lint/build 环境阻塞外，本轮前端整改通过自动化测试与类型检查。
规格符合性与代码质量双轴 Review 均通过；题库分类编码作为唯一需人工决策的 Contract 缺口保留。
