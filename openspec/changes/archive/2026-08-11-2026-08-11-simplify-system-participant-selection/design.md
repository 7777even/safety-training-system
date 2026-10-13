# Design：全系统简化参与人员选择

## 1. 统一原则

```text
全员 / 部门 / 指定人员
        ↓ 当次解析
当前机构启用用户 ID 集合
        ↓ 用户确认或业务提交
各业务人员事实表
```

筛选条件不作为业务范围长期保存。部门包含下级部门；解析时校验部门和人员均属于当前机构、人员账号启用，并对用户 ID 去重。

## 2. 统一人员选择模型

后端提供共享 DTO 和解析服务，不新增通用范围表：

```json
{
  "selectionType": "DEPT",
  "subjectIds": [1001, 1002]
}
```

| `selectionType` | `subjectIds` | 语义            |
|-----------------|--------------|---------------|
| `ALL`           | 必须为空         | 当前机构全部启用用户    |
| `DEPT`          | 至少一个部门 ID    | 部门及其下级部门的启用用户 |
| `USER`          | 至少一个用户 ID    | 指定启用用户        |

不支持混合类型、排除、标签、岗位或嵌套。人员选择分页查询接口继续保留，用于按关键词和部门查看候选人；其 Contract 从
`common-audience.openapi.json` 拆到新的 `person-selection.openapi.json`，路径保持 `/api/common/person-picker`，权限保留
`common:person:list`。

## 3. 课程

### 3.1 指派报名

- 开课报名模式为 `ASSIGNED`；
- 管理员提交统一人员选择，服务端当次解析；
- 对每个用户写入 `sts_course_enrollment`，来源 `ASSIGN`；
- 部门变化不自动影响已生成报名；同开课同用户保持幂等唯一；
- `REQUIRED` 禁止自主退选，`ELECTIVE` 未完成可退选。

### 3.2 开放报名

开课报名模式为 `OPEN`，资格模式为：

- `ALL`：当前机构全员可自主报名，不写白名单；
- `SELECTED`：管理员用部门或人员筛选，保存时展开并写入具体用户白名单。

新增 `sts_course_offering_eligible_user`：

| 字段                          | 说明      |
|-----------------------------|---------|
| `offering_eligible_user_id` | 主键      |
| `organization_id`           | 当前机构    |
| `course_offering_id`        | 开课 ID   |
| `user_id`                   | 具体可报名用户 |
| 审计、逻辑删除字段                   | 沿用项目规范  |

唯一键为 `(course_offering_id,user_id,deleted)`。`sts_course_offering` 新增 `eligibility_type=ALL/SELECTED`，删除
`audience_id`、`is_self_enroll_allowed`、`is_external_user_allowed`；`enrollment_type` 仅允许 `ASSIGNED/OPEN`。

可报名列表和自主报名同时校验开课发布、启用、有效期、当前机构、未报名和 ALL/具体白名单。替换 SELECTED 名单采用事务内先删后插；
部门变化不自动刷新白名单。

### 3.3 直播

删除 `sts_course_live_session.audience_id` 及 DTO/VO/快照字段。直播属于课程内容项，学员访问资格继承有效课程报名，不建立直播人员名单。

## 4. 考试

- `POST /exam/exams/{examId}/candidates/bind-audience` 替换为 `POST /exam/exams/{examId}/candidates/bind-selection`；
- 请求体使用统一人员选择，解析后逐人写 `sts_exam_candidate`；
- 删除 `sts_exam_candidate.audience_id`；`assignment_source` 删除 `AUDIENCE`，直接选择使用 `ASSIGN`；
- 删除 `sts_exam_policy.result_notify_audience_id`。本期成绩通知如启用，只面向该考试实际考生，不另建通知范围；通知发送能力不在本
  Change 新增。

课程和培训上下文生成的考生继续绑定各自报名/参训人员，不经过人员选择解析器。

## 5. 培训

- 培训计划创建/更新删除 `audienceId`；
- `bind-audience` 替换为 `bind-selection`，解析后逐人写 `sts_training_participant`；
- 删除 `sts_training_plan.audience_id` 和 `sts_training_participant.audience_id`；
- `join_source` 删除 `AUDIENCE`，直接选择统一记录为 `ASSIGN`；
- `participant_org_id` 从被选用户的当前机构事实派生，客户端不得提交或伪造。

课程、考试、线下执行实例继续消费已经确定的 `sts_training_participant`。

## 6. 问卷

- 问卷投放实例创建删除 `audienceId` 和 `resultViewAudienceId`；
- 参与人绑定接口使用统一人员选择，逐人写 `sts_survey_participant`；
- 删除 `sts_survey_instance.audience_id/result_view_audience_id` 和 `sts_survey_participant.audience_id`；
- 问卷提交必须找到当前登录用户对应的参与人，不再在提交时隐式创建参与人；
- 结果和统计由问卷管理权限及当前机构隔离控制，不另建结果查看人员范围。

匿名问卷只表示结果展示不显示人员信息，不等于允许未绑定外部人员提交。

## 7. 删除公共参与范围

删除：

- `com.sts.audience` 中 Audience Controller/Service/Resolver/Mapper/Entity/DTO/VO；
- `/api/common/audiences/**`、`common:audience:*`；
- `docs/api/common-audience.openapi.json` 中范围接口；人员选择拆分后删除该文件；
- 初始化菜单中的参与范围菜单和功能权限；
- `sts_common_audience_member`、`sts_common_audience_rule`、`sts_common_audience`。

`PersonPickerController/Service/DTO/VO` 移至独立 `com.sts.person`（暂定包名），不依赖被删除表，保留
`/api/common/person-picker` 和 `common:person:list`。

## 8. 数据库 migration

新增单个版本化 migration，顺序如下：

1. 创建 `sts_course_offering_eligible_user`；
2. 为 `sts_course_offering` 增加 `eligibility_type`，开发期无历史数据，不回填业务数据；
3. 删除各业务表上的 audience/通知范围列及相关索引；
4. 删除参与范围菜单的角色关联、菜单和 `common:audience:*` 权限；
5. 按 member → rule → audience 顺序删除三张公共表。

同步当前 `backend/sql/schema-*.sql`、Entity、Mapper、自定义 SQL、OpenAPI 和测试 schema。历史 migration 保留，不改写 checksum。

## 9. API Contract

更新：

- `course.openapi.json`
- `exam.openapi.json`
- `training.openapi.json`
- `survey.openapi.json`
- 新增 `person-selection.openapi.json`
- 删除 `common-audience.openapi.json`

所有人员绑定请求复用同一选择语义，但各 Controller 在自己的 OpenAPI 中完整声明请求 schema、中文字段说明和 example。接口不接受
`organizationId`，服务端从登录租户派生。

这是破坏性 API Contract 和数据库变更，实施前必须人工确认。仅后端；前端以 OpenAPI 为准另开。

## 10. 测试策略

### 零 DB

- 统一解析器覆盖 ALL/DEPT/USER、下级部门、去重、禁用用户和跨机构拒绝。
- 课程指派最终写具体报名；开放 SELECTED 只允许白名单用户报名。
- 考试、培训、问卷选择后逐人写领域事实且重复幂等。
- 问卷未绑定用户不能提交；直播资格继承课程报名。

### MySQL IT

- 新白名单表映射、唯一键、逻辑删除和同机构约束。
- 所有删除列、索引、菜单权限和三张公共表确实不存在。
- 课程报名、考试考生、培训参训人、问卷参与人仍可真实落库并受租户隔离。
- 当前 schema 可从空库顺序构建；退役 migration 在旧结构夹具上执行成功。

## 11. 部署与回滚

- 系统未上线且没有业务历史数据，不设计兼容或数据转换。
- 执行 migration 前仍需备份数据库；DROP 表和列不可由代码回滚恢复。
- 回滚需要恢复 migration 前备份并回退应用版本。
- 若实施前发现共享环境存在真实业务数据，立即暂停并提交人工决策。

