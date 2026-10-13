# Spec Delta：试卷创建工作台

## ADDED Requirements

### Requirement：试卷创建采用阶段式单页工作台

管理员 MUST 在同一试卷工作台中按“基础信息、组卷配置、发布”三个阶段完成创建和编辑。工作台 MUST 使用与新增课程工作台一致的阶段导航和单一内容工作区视觉结构。

#### Scenario：新建试卷保存后继续组卷

- **WHEN** 管理员在基础信息阶段提交符合 Contract 的试卷资料且服务端创建成功
- **THEN** 系统 MUST 使用服务端返回的 `paperId` 替换编辑地址并进入组卷配置阶段
- **AND** 管理员无需返回列表页即可继续配置题目或抽题规则

#### Scenario：未保存试卷禁止进入后续阶段

- **WHEN** 新建试卷尚未成功取得 `paperId`
- **THEN** 系统 MUST 禁用组卷配置和发布阶段
- **AND** MUST 提示先保存基础信息

#### Scenario：发布前先预览

- **WHEN** 管理员尝试发布试卷
- **THEN** 系统 MUST 要求当前组卷配置已成功通过预览
- **AND** 发布成功后 MUST 进入既有版本只读页面

### Requirement：试卷工作台可使用真实接口完成验收

在 `VITE_USE_API_MOCK=false` 且提供非生产、已授权后端环境时，试卷工作台 MUST 使用 `paper.openapi.json` 已定义的真实接口完成创建、组卷、预览、发布和版本回读，不得用 Mock 数据替代真实接口验收。

#### Scenario：真实接口不可访问

- **WHEN** 开发/测试后端不可访问，或没有已授权的测试会话与可用题库数据
- **THEN** 系统 MUST 如实记录真实联调未执行的原因
- **AND** MUST NOT 将 Mock 或静态测试结果记为真实接口验收通过
