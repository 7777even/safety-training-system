# Tasks：全系统简化参与人员选择

> 状态：已完成（2026-08-11）。
>
> Owner：lims

## Task 1：完成课程具体人员报名与开放白名单

- 状态：已完成
- Owner：lims
- 输入：现有人员选择、课程开课/报名/直播、已确认 design §1～3。
- 输出：统一人员选择解析；指派报名最终绑定具体用户；开放报名支持全员或具体用户白名单；直播继承课程报名。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：破坏性调整课程开课和报名选择 Contract；新增 `person-selection.openapi.json`；直播删除 audience 字段。
- 数据影响：新增开课可报名用户表；开课增加资格类型并删除 audience/自助/外部字段；直播删除 audience 字段；同步 migration。
- 验收标准：
  - [x] 先观察人员解析、指派具体报名和开放白名单测试在旧实现下失败。
  - [x] 部门只用于当次展开，最终 `sts_course_enrollment` 或白名单只保存具体 userId。
  - [x] ASSIGNED 只能管理员指派；OPEN 只能全员或白名单用户自主报名。
  - [x] REQUIRED/ELECTIVE 退选和内容完成规则保持不变。
  - [x] 直播不再保存范围，学员资格继承有效课程报名。
  - [x] migration、当前 schema、Entity 和 course/person-selection OpenAPI 同步。
- 测试方式：课程和人员解析零 DB；课程 Mapper/租户/报名/白名单 MySQL IT；OpenAPI 检查。

## Task 2：完成考试、培训和问卷具体人员绑定

- 状态：已完成
- Owner：lims
- 输入：Task 1；考试考生、培训参训人、问卷参与人和已确认 design §4～6。
- 输出：考试、培训、问卷统一使用人员选择并写领域人员事实；不再读写 audience 数据。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：`bind-audience` 替换为 `bind-selection`；删除相关 audience/通知范围字段；更新三份 OpenAPI。
- 数据影响：删除 exam/training/survey 的 audience 和结果通知范围列、索引；同步 migration、schema 和 Entity。
- 验收标准：
  - [x] 考试选择后逐人创建考生快照，重复绑定幂等，不保留 audience 来源。
  - [x] 培训选择后逐人创建参训人，人员机构由服务端派生。
  - [x] 问卷选择后逐人创建参与人，未绑定用户不能提交。
  - [x] 结果查看只使用业务权限和当前机构隔离，不存在结果可见范围字段。
  - [x] 三个域的零 DB、Mapper 和租户边界 IT 通过，OpenAPI 与实现一致。
- 测试方式：考试/培训/问卷零 DB；各域 Mapper 与租户边界 MySQL IT；OpenAPI JSON/`$ref` 检查。

## Task 3：退役公共参与范围并完成全系统验收

- 状态：已完成
- Owner：lims
- 输入：Task 1～2；公共 Audience 实现、菜单权限、三张表和全系统当前文档。
- 输出：公共参与范围后端/API/权限/表完整删除；全量验证、双轴 Review、QA、Retro 和进度记录完成。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：删除 `/api/common/audiences/**` 和 `common:audience:*`；保留独立人员选择接口。
- 数据影响：migration 最后删除 member/rule/audience 三表和菜单角色关联；无历史数据转换，恢复依赖备份。
- 验收标准：
  - [x] `com.sts.audience` 仅完成 PersonPicker 迁出后整体删除，不保留空壳或桩。
  - [x] 当前 schema、生产代码、OpenAPI、菜单和权限不存在 audience 活动引用。
  - [x] 退役 migration 真库 IT 验证列、索引、菜单和三张表删除顺序。
  - [x] Admin 全量零 DB 和 MySQL IT 全部通过。
  - [x] 规格符合性和代码质量 Review 均为“通过”。
  - [x] QA、Retro、tasks.md、路线图和进度表记录实际验证、破坏性变化与备份恢复边界。
- 测试方式：Admin 全量 test/integration-test；migration IT；全局残余扫描；OpenAPI/OpenSpec strict validate；
  `git diff --check`。

## 完成记录

- TDD：人员解析、课程白名单、考试/培训/问卷具体人员绑定均先观察旧实现编译或行为测试失败，再完成最小实现。
- 零 DB：`mvn -pl sts-service-admin -am test`，560/560 通过。
- MySQL IT：`mvn -pl sts-service-admin -am -Pintegration-test test`，189/189 通过，包含退役 migration 专项。
- 数据边界：未连接或修改现有业务数据库；migration 只在 Testcontainers 临时数据库执行，用户后续自行初始化。
