# Tasks：填空题按空位作答

- [x] T1 后端（Owner：Codex）：从冻结答案快照计算安全 `blankCount`，同步 `ExamAttemptQuestionVO` 和 OpenAPI；输入为冻结题目，输出不泄露答案的作答快照；以学员快照单测验证多空数量与脱敏。
- [x] T2 前端 PC（Owner：Codex）：填空题逐空输入、回填、暂存和已答状态；依赖 T1 的 `blankCount`；以定向回归和 typecheck 验收。
- [x] T3 前端移动端（Owner：Codex）：独立考试与课程考核答题页同步逐空输入、回填、暂存和已答状态；依赖 T1 的 `blankCount`；以定向回归和 typecheck 验收。
- [x] T4 验证（Owner：Codex）：后端定向单测、PC/移动端定向测试、OpenAPI JSON 校验和 `git diff --check`。
