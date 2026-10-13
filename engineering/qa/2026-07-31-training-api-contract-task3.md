# QA：培训 API 契约 Task 3

## 范围

OpenSpec Change `2026-07-31-complete-training-api-contract` Task 3。仅后端与 OpenAPI。

## 已确认规则

- AUDIENCE 来源参训人不支持单人移除。
- provision 只关联既有真实课程开课/考试实例；线下内容幂等创建场次。
- 培训考试使用 `TRAINING + planContentId` Candidate 上下文。
- 结果同步只更新课程、考试、线下分项，不覆盖正式综合结果。

## 验证结果

- TDD Red：目标工作流服务不存在，测试编译失败。
- 目标零 DB：17/17 通过；结果不回退补充用例 6/6 通过。
- Admin 全量零 DB：401/401 通过。
- 定向 MySQL IT：8/8 通过。
- training OpenAPI：67 个 operation，Schema 引用缺失 0。
- OpenSpec strict、JSON 解析和 `git diff --check` 通过。

## Review

- 规格符合性：通过。9 条接口全部实现，身份从登录态派生，未进入未确认能力。
- 代码质量：通过。批量生成与单内容补建均不再把资源定义 ID 冒充执行实例；同步无新正式事实时不清空既有成绩。
