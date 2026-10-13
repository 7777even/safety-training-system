# 2026-09-15 PC 时间范围录入统一 QA

## 范围

- 培训计划基础信息：培训性质冻结态、计划时间范围。
- 课程开班、课程直播场次：时间范围。
- 考试定义：开放时间、补考时间范围。

## 自动化验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 时间范围定向回归 | 通过，2/2 | `pnpm --dir frontend/pc-web exec node --test tests/schedule-time-range-input.test.mjs` |
| OpenSpec 严格校验 | 通过 | `openspec validate 2026-09-15-unify-schedule-time-range-input --strict` |
| PC Web 类型检查 | 通过 | `pnpm --dir frontend/pc-web exec vue-tsc --noEmit` |
| 受影响源码 ESLint | 通过 | 对四个修改的 Vue 文件执行 `eslint` |
| 差异空白检查 | 通过 | `git diff --check` |

定向回归覆盖：五类时间范围均使用 `datetimerange`、保留 ISO `YYYY-MM-DDTHH:mm:ss`、将范围值映射回既有标量字段，并断言非草稿或历史未标明计划禁用培训性质。

## 聚合既有回归

运行课程、考试、培训计划既有用例及本次用例共 47 项，33 通过、14 失败。失败为既有断言与当前目录结构不一致（例如已不存在的 `course-workspace-state.ts`、`CourseWorkbench.vue`、课程开班页和考试阅卷页路径）及与本次时间控件无关的页面结构断言；本次新增的两项和培训计划 API 用例均通过。未将这些无关失败归为本次修复通过。

## 待人工非生产环境验收

1. 打开执行中培训计划，确认培训性质不可点击；新建或草稿计划仍可切换初培/复审。
2. 对计划、开班、直播、考试开放和补考分别选择、清空、编辑回显一组时间范围；在浏览器 Network 确认请求仍提交原 `startTime/endTime` 或对应开放/补考字段。

未执行真实登录态浏览器验收，也未修改 API、后端、权限或数据库。
