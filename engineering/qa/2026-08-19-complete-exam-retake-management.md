# 考试补考管理与成绩状态展示 QA

日期：2026-08-19  
OpenSpec Change：`2026-08-19-complete-exam-retake-management` Task 1～3

## 验收结果

- 考试策略支持仅不及格、不及格或未参加、仅未参加三种补考范围；新增考试默认范围为仅不及格人员。
- 成绩处置页从考试情况和成绩列表均携带 `examCandidateId`；开补考仅调用既有接口，使用本次操作内复用的幂等令牌。
- 开补考不提交考试时间、时长、试卷、范围、次数或成绩；资格和窗口由接口返回的业务结果最终判定。
- 作答状态、发布方式、发布结果均已映射为中文，包含 `PUBLISHED`、`SINGLE`、`FAILED`。

## 自动化验证

- 定向 Node 测试：28/28 通过。
- `pnpm typecheck`（`frontend/pc-web`）：通过。
- 受影响的生产源码 ESLint：通过。
- `git diff --check`（本 Change 相关文件）：通过。

## 未执行项

- 未执行真实环境联调：当前未提供可使用的非生产环境地址和考试角色测试账号。
- 测试文件的单独 ESLint 未纳入结论：现有 ESLint 环境未声明 Node 的 `URL`、`Buffer` 全局变量；生产源码 ESLint 已通过，本次未调整全局 lint 配置。
