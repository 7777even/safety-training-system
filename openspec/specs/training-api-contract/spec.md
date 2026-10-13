# training-api-contract Specification

## Purpose

TBD - created by archiving change 2026-07-31-complete-training-api-contract. Update Purpose after archive.
## Requirements
### Requirement: 已有培训与评价接口具有完整机器可读契约

系统 MUST 在 `docs/api/*.openapi.json` 中完整登记已有培训、评价表单和评价结果 Controller 的所有对外 operation。契约 MUST
与实际请求、响应、分页、枚举、权限和业务错误一致。

#### Scenario: 前端读取已有接口契约

- **GIVEN** 培训 Controller 已存在 57 个对外 operation
- **WHEN** 调用方读取 `training.openapi.json`
- **THEN** 契约登记全部 57 个 operation
- **AND** 每个 operation 使用具体请求和响应 Schema
- **AND** 每个字段具有中文说明和合法 example

#### Scenario: 评价表单使用数据库枚举

- **GIVEN** 数据库将评价表单类型定义为 `COMPOSITE/EXPERT_INSPECTION`
- **WHEN** 调用方读取评价表单创建、更新或查询契约
- **THEN** `formType` 只允许这两个值
- **AND** 契约不得展示 `COURSE/TEACHER/TRAINING` 等未定义值

#### Scenario: 业务错误可被调用方稳定处理

- **GIVEN** 请求可能触发参数、鉴权、权限、资源不存在、版本或状态冲突
- **WHEN** 调用方读取对应 operation
- **THEN** 契约声明 400、401、403、404、409 及实际业务码
- **AND** 模板内容类型错误包含业务码 `8038`

### Requirement: 模板分类完整树

系统 MUST 为有权限的调用方返回其可见范围内的完整模板分类树，不得要求调用方从分页结果猜测跨页父子关系。

#### Scenario: 读取分类树

- **GIVEN** 当前用户具有模板查询权限并可访问目标机构
- **WHEN** 查询模板分类树
- **THEN** 系统返回包含 ID、父 ID、名称、排序、状态和 children 的完整层级
- **AND** 同级节点按排序值和 ID 稳定排序

#### Scenario: 隔离不可见分类

- **GIVEN** 分类属于当前用户不可访问的机构
- **WHEN** 当前用户查询分类树
- **THEN** 系统不得返回该分类或借其泄漏不可见层级信息

### Requirement: 评价表单版本历史可发现

系统 MUST 允许调用方从评价表单 ID 获取其可见版本摘要和真实版本 ID。

#### Scenario: 选择已发布版本

- **GIVEN** 评价表单存在多个发布版本
- **WHEN** 有权限的调用方查询表单版本历史
- **THEN** 系统返回每个版本的 `formVersionId`、版本号、发布状态、发布人、发布时间和版本说明
- **AND** 结果按版本号稳定排序

### Requirement: 整体替换与发布执行乐观并发控制

系统 MUST 在标准规则、模板阶段等聚合子集合整体替换和资源发布前原子校验聚合根版本与状态。版本或状态冲突 MUST 返回 HTTP
409，且不得产生部分删除或部分写入。

#### Scenario: 拒绝旧版本整体替换

- **GIVEN** 客户端提交的版本已经落后于数据库版本
- **WHEN** 客户端整体替换标准规则或模板阶段
- **THEN** 系统返回 HTTP 409
- **AND** 原有子集合保持不变

#### Scenario: 拒绝覆盖已发布内容

- **GIVEN** 标准、模板或评价版本已经发布
- **WHEN** 客户端尝试覆盖其不可变内容
- **THEN** 系统返回 HTTP 409
- **AND** 已发布内容保持不变

#### Scenario: 已发布计划结构和名单只读

- **GIVEN** 培训计划已经发布
- **WHEN** 客户端尝试修改计划结构或参训名单
- **THEN** 系统返回 HTTP 409
- **AND** 结构与名单保持不变

### Requirement: 发布规则能够引用同批新建评价项目

系统 MUST 允许评分规则通过项目业务编码引用同一次发布请求中新建的评价项目，并由后端映射数据库 ID。

#### Scenario: 发布带项目规则的新版本

- **GIVEN** 发布请求包含项目 `evaluationItemCode=A`
- **AND** 评分规则引用 `evaluationItemCode=A`
- **WHEN** 系统发布新版本
- **THEN** 系统先创建项目并取得其 ID
- **AND** 评分规则保存该真实项目 ID
- **AND** 调用方无需提交尚未生成的 `evaluationItemId`

### Requirement: 执行人与教师身份来自登录态

系统 MUST 从受信任登录态派生发布人、评分人和当前教师身份，不得使用客户端传入值冒充当前执行人。

#### Scenario: 查询本人教师任务

- **GIVEN** 当前登录用户是教师 A
- **WHEN** 用户查询教师任务
- **THEN** 系统只返回教师 A 被分配的任务
- **AND** 客户端不能通过提交教师 B 的用户 ID 查看教师 B 的任务

#### Scenario: 记录发布和确认审计

- **GIVEN** 当前登录用户执行发布或线下评分
- **WHEN** 操作成功
- **THEN** 对应审计用户 ID 取自当前登录态
- **AND** 客户端提交的同名身份字段不得覆盖该值

### Requirement: 培训执行入口可追溯且幂等

系统 MUST 按 planContentId 创建或复用计划专属执行实例，不得关联来源资源的既有执行，也不得用资源 ID 作为
executionId。COURSE 的 executionId MUST 指向 CourseOffering，EXAM/QUESTION_BANK MUST 指向计划专属
AssessmentExecution，OFFLINE MUST 指向 OfflineSession。重复生成或补建 MUST NOT 产生重复执行关系。完整执行闭环见
`training-execution-loop`。

#### Scenario: 查询计划执行关系

- **GIVEN** 已发布计划的内容已生成执行实例
- **WHEN** 有权限的管理人员查询计划执行关系
- **THEN** 系统返回计划内容、执行类型和真实执行实例 ID
- **AND** COURSE 返回 CourseOffering ID，EXAM/QUESTION_BANK 返回 AssessmentExecution ID，OFFLINE 返回 OfflineSession ID

#### Scenario: 单内容补建

- **GIVEN** 某计划内容尚无执行关联
- **WHEN** 管理人员请求补建该内容
- **THEN** 系统按 planContentId 创建或复用计划专属实例
- **AND** 重复请求返回同一业务结果，不创建重复关联

### Requirement: 教师和学员只能处理本人培训上下文

系统 MUST 根据登录态、参训关系、教师分配和计划内容关系限制教师任务、学员培训任务、考试启动和结果读取。

#### Scenario: 教师读取本人场次任务

- **GIVEN** 当前用户是目标线下场次的教师
- **WHEN** 查询场次任务详情
- **THEN** 系统返回该场次及当前可处理动作
- **BUT** 非该场次教师不得读取教师处理详情

#### Scenario: 学员从培训内容启动考试

- **GIVEN** 当前用户是目标培训参训人
- **AND** 目标内容属于其计划且为考试内容
- **WHEN** 学员启动考试
- **THEN** 系统进入计划专属 AssessmentExecution 的 Candidate/Attempt
- **AND** 不得复用来源考试的 Candidate
- **AND** 按培训计划、参训人和计划内容隔离作答及成绩

### Requirement: 培训结果只同步正式可追溯事实

系统 MUST 只从课程、考试和线下的正式业务事实同步参训人逐内容结果投影。同步 MUST 幂等；投影 MUST 分别保存来源域
`sourceStatus` 和培训域 `resultStatus`，并可追溯到计划内容和执行实例。计划结束时 MUST 冻结投影和最终结果快照。

#### Scenario: 重复同步正式结果

- **GIVEN** 某参训人的课程、考试和线下正式结果未发生变化
- **WHEN** 管理人员重复执行结果同步
- **THEN** 参训结果保持一致
- **AND** 不产生重复来源或覆盖更晚的正式事实

#### Scenario: 拒绝未发布成绩

- **GIVEN** 考试已有内部评分但尚未正式发布
- **WHEN** 系统同步培训结果
- **THEN** 未发布评分不得作为正式培训结果来源

#### Scenario: 已发布计划不可正常结束

- **GIVEN** 计划处于 PUBLISHED 且尚未显式 start
- **WHEN** 调用方请求结束计划
- **THEN** 契约对应 `POST /api/training/plans/{trainingPlanId}/end` 返回 HTTP 409 且业务码 `8163`
- **AND** 不执行结果同步、冻结或状态更新

### Requirement: 线下培训公开契约不得暴露签到语义

培训与运维 OpenAPI MUST 删除签到窗口、签退要求、签到规则、签到记录错误码、签到来源类型和待确认签到统计。系统 MUST 删除
`training:attendance:list/edit/confirm` 权限及对应菜单种子。线下场次生命周期状态与启用状态 MUST
保持既有契约，创建或更新场次不得绕过专用状态流转接口。

#### Scenario: 前端读取线下相关 OpenAPI

- **WHEN** 前端读取培训内容配置、线下场次创建/更新/查询和教师工作台 Schema
- **THEN** 所有请求、响应、required 列表、字段说明和示例均不包含签到语义
- **AND** 场次名称、教师、地点、起止时间、状态及计划/阶段/内容关联仍按适用接口保留

#### Scenario: 初始化权限菜单

- **WHEN** 系统校验或初始化菜单权限
- **THEN** 菜单和角色授权均不包含 `training:attendance:list/edit/confirm`
- **AND** 其他线下场次、教师任务和成绩权限保持不变

