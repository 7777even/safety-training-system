# Design：一套开课能力、一套考试能力，两处调用

## Context

开课与考试各是一套核心能力。独立管理端和培训生成是两个调用方。当前培训 Adapter 插入已发布对象，旁路正式发布。

目标形态：

```text
                    ┌─ 管理端：创建时不带版本（发布时取 current）
开课能力 ───────────┤
create → 配阅卷人 → publish → enroll
                    └─ 培训生成：创建时带冻结版本和 planContentId

                    ┌─ 管理端：独立考试草稿
考试能力 ───────────┤
create → 配策略/阅卷人 → publish → bindCandidates
                    └─ 培训生成：计划专属考试草稿（带冻结快照）
```

培训 Adapter 是调用方，不是第二套生命周期。

## Goals / Non-Goals

**Goals:**

1. `publishOffering` / `publishExam` 各一条规则，用对象上已有字段工作。
2. 培训生成调用 `CourseOfferingService` / `ExamService` 的创建、发布、人员接口。
3. 管理端开课/考试列表包含培训生成的对象，因为它们就是开课/考试。
4. 学员三个入口是同一事实的不同查询条件。

**Non-Goals:**

- 合并课程内测验与计划考试。
- 教师统一收件箱、前端、存量回迁草稿。

## Decisions

### D1 开课发布只有一条版本规则

**规则**：若开课已有 `courseVersionId`，发布必须使用该版本，不得改写；若为空，取课程 `currentVersionId`
写入后再发布。随后一律：校验版本属于该课程且可用、生成测验执行、断言阅卷人（5068）、置 `PUBLISHED`。

独立开课创建时不写版本，发布走「取 current」。培训生成创建草稿时写入计划冻结版本，发布走「已有则用」。依据是版本已定或未定。

阅卷人：开课草稿上的原 API。谁创建的开课都一样。缺阅卷人时发布失败，开课保持 DRAFT。

培训生成顺序：`createDraft` →（可被管理端配阅卷人）→ `publishOffering` → `enroll(source=TRAINING)`。

### D2 考试发布只有一条执行物化

**规则**：`publish(examId)` 根据**这张考试**的字段物化或刷新 `AssessmentExecution`：

| 考试上的数据             | 执行上的结果                                    |
|--------------------|-------------------------------------------|
| `planContentId` 空  | `STANDALONE_EXAM` / `STANDALONE_PRACTICE` |
| `planContentId` 非空 | `TRAINING_EXAM` / `TRAINING_PRACTICE`     |
| `examType`         | `assessmentKind`、试卷或题库资源                  |
| `ExamPolicy`       | 策略快照                                      |

独立考试与计划专属考试都走 `create` + 写策略/阅卷人 + `publish`。`ensureTraining` 不再作为发布旁路；它与
`refreshStandalone` 收成同一个「按考试物化执行」。

培训生成：`create`（带 planContentId、冻结试卷/题库/策略）→ 写阅卷人 → `publish` → `bindCandidates(source=TRAINING)`。

### D3 管理列表就是开课/考试列表

培训生成的开课/考试必须出现在管理端开课/考试列表，否则阅卷人、详情、批阅无法使用这套能力。

`planContentId` 出现在列表项和详情上，便于识别来源；查询可按该字段过滤。默认返回全部。

学员入口与管理列表不是一回事：管理员管的是开课/考试对象；学员「我的课程」是独立学习入口。

### D4 学员三个入口 = 同一事实、不同查询

| 入口   | 读的事实                 | 条件                         |
|------|----------------------|----------------------------|
| 我的课程 | `CourseEnrollment`   | 来源不是 TRAINING              |
| 我的考试 | `ExamCandidate` + 执行 | `sourceType` 不是 TRAINING_* |
| 我的培训 | 参训人 + 计划内容           | 计划状态可见性（既有）                |

学习/作答仍用报名 ID、Attempt ID。这是入口编排，不是给核心能力打补丁。「我的课程」已按来源划分；「我的考试」同样按来源划分。

### D5 人员接口是报名/考生能力，培训是调用方

`enroll` / `bindSelection` 接受人员与 `source`。培训生成调用它们写入 TRAINING。Adapter 不再直接 `insert` 报名/考生（或仅作为
Service 内部实现，对外仍是该能力）。

开课约束：独立指派开课允许 ASSIGN 来源报名；培训创建的开课允许 TRAINING
来源报名（创建时定好）。这是开课自己的报名规则，与「开放报名才允许自助报名」同类。开课更新、结束、取消仍走开课能力；计划侧读取开课状态。本期培训生成只写入
TRAINING 来源报名。

### D6 课程内考核与计划考试仍是两套对象

课程章节测验属于**开课能力内部**；计划 EXAM/QUESTION_BANK 属于**考试能力**。培训 COURSE 调用开课，培训 EXAM 调用考试。名称在契约里分开写。

### D7 批阅走对象所属能力

课程测验阅卷走开课；考试阅卷走考试。培训班不另建收件箱。对象在管理列表里可见，教师就能找到任务。

## 调用关系

```text
TrainingExecutionProvisioner
  COURSE:
    CourseOfferingService.create(草稿, courseVersionId=冻结, planContentId)
    CourseOfferingService.publish(offeringId)     // 同一条发布
    CourseEnrollmentService.assign(..., TRAINING)
  EXAM / QUESTION_BANK:
    ExamService.create(草稿, 冻结策略, planContentId)
    ExamService.replaceReviewers(...)
    ExamService.publish(examId)                   // 同一条发布
    ExamService.bindSelection(..., TRAINING)
```

独立管理端调用同一组 Service，不传冻结版本 / 不传 planContentId。

## 契约

- 开课/考试发布说明写清：已有版本则用已有，否则取当前。
- 开课/考试列表与详情带 `planContentId`；默认列出全部。
- 我的考试说明：本入口为独立考试任务；培训考试从培训入口进入。
- 生成执行：调用开课/考试发布；5068 时开课仍为草稿。

无 migration。

## Risks / Trade-offs

- [培训开课出现在开课列表] → 这是目标。用 `planContentId` 识别来源。
- [管理员改培训开课时间导致与计划阶段不一致] → 开课时间以开课为准；计划阶段是生成时的编排。
- [管理员在培训开课上再指派 ASSIGN 人员] → 本期用开课允许来源约束，培训开课只接受 TRAINING。
- [存量已是 PUBLISHED 的培训开课] → 保持现状；新生成走草稿再发布。
- [执行物化合并] → 用考试字段推导 sourceType，单测锁定独立考试发布结果。

## Migration Plan

1. 先把发布与执行物化收成一条，培训改为调用。
2. 列表暴露 `planContentId`，学员考试入口按来源查询。
3. 回滚无 DDL。

## Open Questions

无。待人工确认后实施。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
