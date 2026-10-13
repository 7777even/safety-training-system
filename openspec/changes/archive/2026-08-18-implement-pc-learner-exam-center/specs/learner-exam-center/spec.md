# 学员考试中心规格增量

## ADDED Requirements

### Requirement: 学员考试中心完整呈现本人任务

系统 MUST 使用当前登录身份分页读取考试任务，并支持已确认的状态、名称和上下文筛选。任务卡 MUST 显示服务端返回的业务状态与可见成绩，不得展示内部 ID。

#### Scenario：学员筛选本人独立考试

- **WHEN** 学员以任务状态、考试名称或上下文类型筛选
- **THEN** 页面向 `GET /api/exam/learner/tasks` 传递对应 `query.*` 参数和分页参数
- **AND** 使用响应中的 `total` 驱动分页

### Requirement: 学员考试中心使用前台门户学习中心样式

系统 MUST 将 `/portal/exams` 作为学员门户页面实现，MUST 以 `/portal/class` 的学习中心横幅和网格任务卡为视觉基准，MUST NOT 使用后台管理的筛选表单、表格工作区、侧栏、面包屑、tabs-card 或 `admin-*` 页面容器。

#### Scenario：学员进入考试中心

- **WHEN** 学员访问 `/portal/exams`
- **THEN** 页面显示学习中心式蓝色横幅和考试任务网格卡
- **AND** 页面不显示后台管理式查询表单或工作区布局

### Requirement: 课程来源考试不走独立开考接口

系统 MUST 将 `contextType=COURSE` 的任务导航到“我的课程”，MUST NOT 调用公开的独立考试开考接口。

#### Scenario：学员点击课程来源考试

- **WHEN** 课程来源任务可操作
- **THEN** 系统导航至 `/portal/class`
- **AND** 不请求 `POST /api/exam/attempts/start`

### Requirement: 结果内容遵从服务端可见性裁剪

系统 MUST 仅在正式成绩可见时展示成绩概览；题目级答案、正确性、得分和解析仅在对应字段返回时展示。

#### Scenario：已发布成绩但答案详情被裁剪

- **GIVEN** 服务端返回正式成绩但未返回正确答案或解析字段
- **WHEN** 学员查看结果
- **THEN** 页面显示成绩概览
- **AND** 不显示标准答案、正确性或解析占位内容

### Requirement: 交卷后可回看本人答案

系统 MUST 在作答提交后允许学员查看本人已提交的题干、选项和答案。正式成绩未发布时，系统 MUST NOT 显示得分、正确性、标准答案或解析。

#### Scenario：学员交卷后等待成绩发布

- **WHEN** 学员手动或超时提交试卷且正式成绩尚不可见
- **THEN** 系统显示本人每题已提交答案和等待成绩说明
- **AND** 不显示标准答案、对错、得分或解析
