# QA：M2 前端 Task 3 考试详情与人员配置

日期：2026-07-28
Change：`2026-07-27-implement-m2-frontend-question-exam` Task 3

## 验证结果

- TDD：先新增 Mock 切换、详情读取及独立页面测试；实现前因 `exam` Mock 与独立页面不存在而失败，完成后 Node 定向测试 5/5 通过。
- API：`VITE_USE_API_MOCK` 默认使用 `src/mock/exam.ts`；设为 `false` 时沿用相同请求参数走 `/api/exam/*` 真实分支。
- PC：`pnpm eslint src/api/exam.ts src/mock/exam.ts src/views/admin/business-center/exams/exam --max-warnings=0`、
  `pnpm typecheck`、`pnpm build` 均通过。
- 差异：`git diff --check` 通过。

## 覆盖范围

- 考试列表、新建页、详情/发布页和人员配置页为独立路由页面；复杂操作不使用 Dialog 或 Drawer。
- 新建时选择 `paperVersionId`；已有考试编辑不提交或更换该字段；发布后只读展示完整 `ExamVO` 字段。
- 考生绑定固定使用 `contextType='STANDALONE'`、`contextId=0`，且只选择已冻结的 PARTICIPANT 范围；保存后读取候选人列表展示服务端结果。
- 批阅人从已确认的人员选择接口选择，至少一人；替换固定使用 `assignmentSource='USER'`。

## 已确认的 Contract 取舍

- `ExamVO` 未返回 `policy`：策略只在新建时提交；详情页不回显或更新策略，避免覆盖服务端未知值。
- Contract 未提供读取批阅人接口：人员配置页不回显当前批阅人，保存会整体替换，并在页面明确提示。
- 上述两项记录为后续 OpenAPI 优化，不在当前 Task 擅自扩展 API。

## 双轴 Review

- 规格符合性：通过。页面、请求和 Mock 字段来自 `exam.openapi.json`；不再将未声明的字符串字段限制为前端自定义枚举。
- 代码质量：通过。仅修改考试 API、Mock、页面、测试与过程记录；无新增依赖，中文注释、错误反馈、Mock 标记和 scoped Less 符合前端约束。

## 未完成的验收

真实后端环境尚不可用。鉴权、实际参与范围冻结、服务端校验和真实数据联调须在 M2 Task 6 中以 `VITE_USE_API_MOCK=false` 执行；Mock
不作为真实闭环证据。
