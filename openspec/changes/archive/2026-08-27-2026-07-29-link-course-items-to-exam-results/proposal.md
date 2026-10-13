# Proposal：课程考试内容项绑定与正式成绩回接

## 背景

课程内容项、DTO、VO 与版本快照已含 `examId`，但课程 API 契约仍停留在“仅 ASSET / LIVE”，服务端也未校验考试资源的发布状态、启用状态或跨机构
USE 授权。成绩发布目前只更新考试 Attempt/Candidate，未基于课程报名与内容项回写学习进度和课程完成态。

## 目标

1. 为 EXAM / PRACTICE / QUESTION_BANK 内容项建立完整的考试资源引用契约和服务端可用性校验。
2. 学员从课程冻结内容项进入考试时，由服务端创建或定位带 courseOfferingId、courseItemId、courseEnrollmentId 的课程上下文
   Candidate。
3. 正式成绩发布后幂等回写对应 `course_item_progress`，并重新计算同一报名的课程完成态。

## 范围

- 修改 `course.openapi.json`、`exam.openapi.json` 及对应 Controller/DTO/VO/Service。
- 增加课程考试进入接口；禁止前端直接提交课程上下文或 userId 创建 Candidate。
- 对课程项创建/更新增加考试存在、启用、发布和 EXAM 资源 USE 授权校验。
- 执行锁定、课程版本快照、课程上下文隔离、成绩发布幂等回写。
- 仅后端；前端以 OpenAPI 为准另开。

## 非目标范围

- 前端页面、Mock、联调。
- 数据库结构变更；复用既有 `sts_course_item.exam_id`、`sts_exam_candidate` 课程字段与 `sts_course_item_progress`。
- 修改考试判分、补考成绩取值或课程版本发布语义。

## 已确认业务规则

- EXAM 仅允许引用 ONLINE。
- PRACTICE 仅允许引用 SPECIAL_PRACTICE。
- QUESTION_BANK 仅允许引用 BANK_PRACTICE，且其 questionBankId 必须与课程内容项来源题库一致。
- 三类内容项只有正式发布且及格才完成；OFFLINE 不属于本 Change。
