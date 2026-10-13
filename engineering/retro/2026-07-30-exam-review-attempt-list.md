# Retro：考试批阅任务与作答成绩分页

日期：2026-07-30

## 做得好的地方

- 在实现前明确了批阅任务按 Answer、作答成绩按 Attempt 的不同粒度。
- 复用既有 Reviewer、Candidate、Attempt 和数据范围事实，没有新增表或权限模型。
- 通过 Candidate 快照优先、用户主数据回退提供稳定展示字段，同时不暴露原始快照和敏感信息。

## 问题

- 本机 JDK 禁止 Mockito 默认自附加，需要显式加载已有 Byte Buddy agent。
- Docker 环境不可用，新增聚合 SQL 无法完成真实 MySQL 执行验证。

## 原因

- 测试运行环境未开放 JVM 动态 agent attach。
- Testcontainers 无法发现有效 Docker socket。

## 改进

- 在统一测试环境中固定 Mockito agent 启动参数，避免各次命令重复处理。
- 在具备 Docker 的 CI 或开发机重跑 `ExamAttemptQueryMapperIT`，通过后再关闭 EXAM-08 的 IT 阻塞状态。

