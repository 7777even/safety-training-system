# Design：开课、报名与学习事实

## Context

- 前置：草稿编排 + 发布版本已归档交付。
- DDL：`02_asset_course.sql` 开课/报名/进度/会话/评价五表（已评审）。
- 步进：`course-module-task-plan` 步骤 5；可拆三批交付。
- 阻塞项（原待确认）本 Change **按推荐默认关闭**（与直播规则确认方式一致），见 §Open Questions。

## Goals / Non-Goals

**Goals:**

1. 开课草稿→发布（冻结当时 `current_version_id`）→结束/取消。
2. 指派报名 + 开放自助报名；选修可退选；同开课同用户唯一。
3. 学习会话幂等上报，汇总内容项进度与报名进度，按完成规则判定 COMPLETED。
4. 每报名一条课程评价。
5. 补齐「有有效开课则禁止课程 content-mode 切换」。

**Non-Goals:**

- `sts_common_audience*` 动态解析（audience_id 可空，暂不校验成员）。
- 培训计划 `plan_content_id` 驱动、IMPORT/TRAINING 报名来源完整链路。
- 学员端前端、直播学习时长的平台侧自动采集（会话由客户端上报）。
- 步骤 6 考试内容项。

## Decisions

### D1：完成规则 `completion_rule`（推荐默认）

允许值：

| 值                    | 含义                                                     |
|----------------------|--------------------------------------------------------|
| `ALL_REQUIRED_ITEMS` | 快照中全部 `is_required=1` 的内容项 `progress_status=COMPLETED` |
| `ALL_ITEMS`          | 快照中全部内容项均为 COMPLETED                                   |

缺省创建开课时：`ALL_REQUIRED_ITEMS`。

内容项完成（ASSET/LIVE）推荐默认：

- 累计 `study_seconds` ≥ 快照材料规则 `required_seconds`（DEFAULT 时用发布时写入快照的生效值）。
- 音视频类：另需 `last_position_seconds / 素材时长` ≥ 机构设置 `defaultMediaCompletionRatio`（时长未知时仅按秒数）。
- LIVE：会话有效秒数累计达到 `required_seconds` 或机构 `defaultLinkLiveSeconds`（写入快照的生效值）。
- 离页：`end_reason=SCREEN_LEAVE` 的会话秒数是否计入，由材料规则 `is_screen_leave_paused` 决定——为 1 时该会话
  `valid_study_seconds` 客户端应已扣除，服务端信任并封顶校验。

过期：开课 `end_time` 已过且未完成 → 报名可置 `EXPIRED`（定时或访问时惰性更新）。

### D2：报名与退选（推荐默认）

- `enrollment_type=ASSIGNED`：仅管理员指派（`ASSIGN`）；`is_self_enroll_allowed` 强制 0。
- `OPEN` / `LINK`：`is_self_enroll_allowed=1` 时学员可 `SELF` 报名；LINK 本阶段与 OPEN 相同（链接令牌暂不做独立表）。
- 退选：仅 `course_type=ELECTIVE` 且 `learning_status` 为 `NOT_STARTED` 或 `LEARNING` 允许学员自助退选（逻辑删除报名及相关进度/未完成会话）；
  `REQUIRED` 禁止自助退选。
- 管理员可移除未完成报名。

### D3：开课发布与版本冻结

- 开课 `publish_status`：`DRAFT → PUBLISHED` 时必须：课程存在且课程 `publish_status` 为 `PUBLISHED`（非 OFFLINE）、
  `current_version_id` 非空；写入 `course_version_id=current_version_id`，此后该字段不可变。
- `PUBLISHED → ENDED | CANCELLED`；取消后不可再报名。
- 课程 `content-mode` 切换：若存在同课程下 `publish_status in (DRAFT,PUBLISHED)` 的开课 → 拒绝（补步骤 3 守卫）。

### D4：学习会话幂等

- 唯一键 `(course_enrollment_id, client_trace_id, deleted)`。
- 重复上报相同 `client_trace_id`：返回已有会话，不重复累加秒数。
- `end_reason` 允许：`COMPLETED` / `USER_EXIT` / `SCREEN_LEAVE` / `TIMEOUT` / `ERROR`。

### D5：进度与评价

- 会话结束后更新/创建 `sts_course_item_progress`，再重算报名 `progress_percent`、`total_study_seconds`、`learning_status`。
- 评价：报名 `COMPLETED` 后可提交；`uk_enrollment_deleted` 一报名一条。

### D6：权限（管理端）

| 权限                                               | 用途      |
|--------------------------------------------------|---------|
| `course:offering:{list,add,edit,delete,publish}` | 开课      |
| `course:enrollment:{list,assign,remove}`         | 报名管理    |
| `course:learning:view`                           | 管理端学习详情 |

学员侧接口：登录即可（操作本人报名/会话）；后续再挂学员角色权限码。

### D7：错误码段

`5060-5089` 开课/报名/学习。

## Risks / Trade-offs

- [无 audience 解析] → 开放报名等于机构内登录用户均可；多机构后接入。
- [完成口径为推荐默认] → 产品可后续改枚举与算法，快照已含材料规则降低漂移。
- [步骤 5 体量大] → Task 拆三批，单次循环最多连续 3 Task。

## Migration Plan

1. `V20260722050000__create_course_offering_learning_tables.sql`（五表）。
2. 同步 `schema-course.sql`。
3. 回滚：停 API；表只追加。

## Open Questions

| 事项     | 状态                          |
|--------|-----------------------------|
| 学习完成口径 | **已按推荐默认关闭**（D1）            |
| 选修退选   | **已按推荐默认关闭**（D2）            |
| 学习详情字段 | 管理端返回报名进度+分项进度+最近会话摘要（够用即止） |
| 直播有效学时 | 客户端上报会话秒数（D1/D4），不做平台拉流计时   |
