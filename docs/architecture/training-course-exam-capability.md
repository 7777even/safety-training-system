# 开课与考试能力：两处调用

> 长期架构说明。实施以已确认的 `openspec/changes/archive/unify-training-course-exam-capability/` 为准；未确认不得改业务代码。

培训不是另一套开课或考试。开课是一套能力，考试是一套能力。独立管理端和培训计划生成是两个调用方。

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

发布只认对象上的数据，不按「是不是培训」分叉：

- 开课已有 `courseVersionId` 则用它，没有则取课程当前版本。
- 考试有 `planContentId` 则物化为培训来源执行，没有则物化为独立考试执行。同一条 `publish`。

管理端开课/考试列表就是该能力的对象列表，培训生成的开课和考试自然在其中；`planContentId` 是字段。

学员「我的课程 / 我的考试 / 我的培训」是三个入口，读同一套报名或考生事实，查询条件不同。学习与作答仍用报名 ID、Attempt
ID。开考类型由服务端按已有答卷派生，请求不得凭 `attemptType` 创建第二份首次作答；补考走考试补考接口。计划内容
`requiredHours` 是安排学时，发布与预警只合计 COURSE 与 OFFLINE；EXAM/QUESTION_BANK 不得为正、不计入合计。学员 `onlineHours`/
`offlineHours`/`effectiveDurationSeconds`
是获得学时（课程实学；线下合格成绩记内容 `requiredHours`，未评分或不合格为 0；考试/题库为 0），二者不得混用。学员线下场次详情走
`GET /training/learner/offline-sessions/{id}`（`training:learner:task`），不调用管理端场次接口。线下完成只认已评分成绩，无签到
HTTP。培训绑定的课程学习状态变化、考试开考/交卷/成绩发布、线下保存成绩后，同一事务按参训人+内容项刷新逐内容投影；学员培训详情只读投影。计划级
`results/synchronize` 是全量补账，不是看见结果的前置。独立开课/独立考试不刷新培训投影。线下最小口径见
`docs/architecture/offline-session-minimum.md`。

课程章节测验、题库练习属于开课内部考核。计划内容 EXAM、QUESTION_BANK 属于考试能力。两套对象，两处调用，不合并。

线下没有独立开课/考试对标物，仍由培训场次承担。

相关文档：`docs/architecture/training-layer-responsibility.md`（类型/标准/模板/计划各层产物与禁止越界带入）、
`docs/architecture/training-domain-design-readiness-review.md`、
`docs/architecture/course-assessment-and-standalone-exam-design.md`、
`docs/architecture/cover-access-protocol.md`（封面写入、受控读取与学员 `coverUrl`）。
