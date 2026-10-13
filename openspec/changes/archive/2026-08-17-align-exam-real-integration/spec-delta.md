# Spec Delta：考试真实联调与契约对齐

## ADDED Requirements

### Requirement：考试创建表单遵循详情页布局

考试创建和草稿详情必须复用 `admin-detail-page`、`admin-detail-form`、`admin-detail-page__form-scroll` 与 `sticky-actions`。
常规字段必须双列排列；长文本和策略分组必须整行排列；页面主体不得因表单增长产生独立长滚动条。

#### Scenario：管理员打开新建考试

- **WHEN** 管理员进入新建考试页
- **THEN** 常规基础字段以双列呈现
- **AND** 创建与取消操作固定在详情卡底部

### Requirement：考试考生绑定使用当前人员选择契约

考试模块不得调用 `/api/common/audiences` 或 `/candidates/bind-audience`。管理员绑定考生时，必须使用当前人员选择器构造
`BindSelectionRequest` 并调用 `/api/exam/exams/{examId}/candidates/bind-selection`。

#### Scenario：管理员保存考生配置

- **WHEN** 管理员选定当前考试机构内的人员并保存
- **THEN** 前端向 `bind-selection` 提交 `selection`、`contextType=STANDALONE` 和 `contextId=0`
- **AND** 成功后重新读取考生分页数据
- **AND** 不发起任何受众接口请求

### Requirement：考试闭环以真实接口验收

关闭考试 Mock 后，考试创建、考生配置、发布、学员作答、教师批阅、成绩发布和学员成绩查看必须逐段通过当前 OpenAPI 验收。

#### Scenario：已发布成绩的学员查看

- **GIVEN** 已提交的考试作答已由已分配教师批阅，且成绩已发布
- **WHEN** 对应学员读取本人的考试任务与作答详情
- **THEN** 可查看正式成绩、及格状态和发布时间
- **AND** 答案详情与解析仅按当前 Contract 策略可见

### Requirement：新建考试只选择试卷最新发布版本

新建考试必须对每份试卷仅展示版本号最高的 `PUBLISHED` 版本。已发布的历史版本必须保留，以确保既有考试和版本历史可追溯，但不得作为新建考试的常规选择项。

#### Scenario：试卷具有多个已发布版本

- **GIVEN** 一份试卷存在版本 4 和版本 6，且二者均为 `PUBLISHED`
- **WHEN** 管理员新建考试并打开试卷版本选择器
- **THEN** 该试卷仅显示版本 6
- **AND** 版本 4 仍可在试卷版本历史中回读

### Requirement：考生选择完整映射人员选择契约

考试考生绑定页面必须让管理员选择 `PersonSelectionRequest.selectionType` 的全部、部门或指定人员模式，并提交与模式相匹配的 `subjectIds`。独立考试的 `contextType` 和 `contextId` 必须由页面固定，不得让用户手工输入。

#### Scenario：管理员按部门绑定考生

- **WHEN** 管理员选择“按部门”并确认至少一个部门
- **THEN** 页面提交 `selectionType=DEPT` 与所选部门 ID 组成的 `subjectIds`
- **AND** 提交固定的 `contextType=STANDALONE`、`contextId=0`

### Requirement：作答端按现有策略记录反作弊事件

作答端在发生页面隐藏、窗口失焦或超时交卷时，必须按当前 OpenAPI 记录最小反作弊事件；不得新增摄像头、人脸或未确认的防作弊能力。

#### Scenario：学员作答时切出页面

- **WHEN** 当前可编辑作答页面进入隐藏或失焦状态
- **THEN** 页面调用反作弊事件记录接口，包含事件类型、发生时间、累计次数和处置动作

### Requirement：考试详情提供统一的三步工作台

考试详情必须提供“设置考试信息、人员配置、发布考试”三步导航，并且只复用当前已有的详情、人员配置与发布 Contract。

#### Scenario：草稿考试按步骤完成配置并发布

- **GIVEN** 管理员打开一份草稿考试详情
- **WHEN** 管理员点击“人员配置”步骤后返回详情，再点击“发布考试”步骤
- **THEN** 页面分别进入既有人员配置页并调用既有发布接口，发布成功后步骤状态更新为完成

### Requirement：学员结果页遵从字段可见性裁剪

学员结果页必须只在服务端返回标准答案或解析字段时显示其对应内容；不得以占位文本暗示被策略隐藏的答案详情。

#### Scenario：成绩已发布但答案详情被隐藏

- **GIVEN** 成绩已发布且服务端未返回 `standardAnswer`、正确选项或 `analysisSnapshot`
- **WHEN** 学员查看结果
- **THEN** 页面显示正式成绩
- **AND** 不显示标准答案和解析区块

## MODIFIED Requirements

无。

## REMOVED Requirements

无。
