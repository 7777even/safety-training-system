# Spec Delta：培训题库练习与公共抽题闭环

本文件满足仓库 L3/L4 Change 的根级 Spec Delta 要求。OpenSpec `spec-driven` schema 的机器合并输入按 capability
分文件保存，以下文件是本 Change 的完整规范正文：

- [公共抽题能力](specs/question-draw-engine/spec.md)：新增场景无关的候选池、全局唯一匹配、候选不足诊断和种子复现要求。
- [培训执行闭环](specs/training-execution-loop/spec.md)：修改培训题库练习业务语义、发布冻结、执行隔离和结果投影，并新增规则级预检与两级题池快照要求。
- [学员作答与结果](specs/learner-exam-reading/spec.md)：修改判分/发布要求对培训题库练习的适用边界，新增“不判定”答案和不评分练习结果要求。

## 语义变更摘要

1. 培训计划题库练习从“计分并判断通过”改为“全部作答并提交即完成”。
2. 可自动判断题给出对错；不可自动判断题只记录作答，不批阅、不算错、不进正确率分母。
3. 题库可含且规则可抽简答题，未命中或 `RECORD_ONLY` 简答不再触发整库 `8127`。
4. 发布预检与真实抽题共用候选过滤和全局唯一匹配，候选不足使用结构化 `8166`。
5. 计划发布冻结候选题版本池，生成执行复制运行池，Attempt 冻结本次随机结果。
6. 公共抽题引擎只复用抽题机械能力，不统一培训、课程、试卷和考试的评分策略。

## 归档要求

归档时必须以三个 capability spec 的完整 `ADDED/MODIFIED` Requirement 为准合并到 `openspec/specs/`；不得只用本摘要替代规范正文。
