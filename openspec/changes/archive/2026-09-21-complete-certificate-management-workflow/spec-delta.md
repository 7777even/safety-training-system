# Spec Delta：证书模板设计与签发规则管理闭环

## ADDED Requirements

### Requirement：证书模板设计 SHALL 使用可覆盖草稿和不可变发布版本

#### Scenario：首次保存模板设计

- **GIVEN** 当前租户的模板尚无设计草稿
- **WHEN** 管理员提交底图、画布、文字/变量元素、坐标和字体样式
- **THEN** 系统 SHALL 创建 `currentVersionNo + 1` 的 DRAFT
- **AND** 模板详情 SHALL 返回完整 DRAFT 及元素
- **AND** SHALL NOT 修改任何既有 PUBLISHED/ARCHIVED 版本。

#### Scenario：重复保存未发布设计

- **GIVEN** 模板已经存在 DRAFT
- **WHEN** 管理员再次保存设计
- **THEN** 系统 SHALL 在同一 DRAFT 上替换设计内容
- **AND** SHALL 保持同一草稿版本号
- **AND** 模板乐观锁不一致时 SHALL 返回 409，SHALL NOT 静默覆盖。

#### Scenario：发布模板草稿

- **GIVEN** 模板具有合法底图和元素设计
- **WHEN** 管理员发布模板
- **THEN** 系统 SHALL 原子地将当前 DRAFT 固化为 PUBLISHED
- **AND** 旧当前发布版本 SHALL 转为 ARCHIVED
- **AND** `currentVersionNo` SHALL 指向新发布版本
- **AND** 已被证书引用的历史版本和元素 SHALL 保持不变。

### Requirement：模板详情 SHALL 支持设计工作台可靠回显

#### Scenario：读取具有草稿和发布版本的模板

- **GIVEN** 当前租户模板同时具有当前发布版本和下一版本草稿
- **WHEN** 管理员读取模板详情
- **THEN** 响应 SHALL 同时返回模板元数据、当前发布版本和草稿
- **AND** 每个版本 SHALL 返回底图、画布、版本状态和按顺序排列的完整元素
- **AND** SHALL NOT 返回其他租户的模板、版本或元素。

### Requirement：证书 PNG SHALL 使用已发布模板的真实底图与样式

#### Scenario：预览或签发证书

- **GIVEN** 已发布模板具有可读取图片底图及 TEXT/VARIABLE 元素
- **WHEN** 系统预览模板或为达标学员签发证书
- **THEN** 生成 PNG SHALL 使用真实底图
- **AND** SHALL 按坐标、字体、字号和颜色绘制静态文字及变量值
- **AND** 预览与正式签发 SHALL 复用相同渲染语义。

#### Scenario：背景文件不可用

- **GIVEN** 背景文件不存在或不是可解码图片
- **WHEN** 管理员保存或发布模板设计
- **THEN** 系统 SHALL 拒绝请求
- **AND** SHALL NOT 使用默认占位背景伪装成功。

### Requirement：被签发规则引用的模板 SHALL NOT 删除

#### Scenario：删除仍被规则使用的模板

- **GIVEN** 当前租户存在未删除签发规则引用模板
- **WHEN** 管理员删除该模板
- **THEN** 系统 SHALL 返回 409
- **AND** SHALL 保留模板及其版本、元素。

### Requirement：签发规则 SHALL 支持详情与受控更新

#### Scenario：读取规则详情

- **GIVEN** 当前租户存在签发规则
- **WHEN** 管理员按规则 ID 查询
- **THEN** 系统 SHALL 返回完整规则配置、乐观锁版本、模板名称和来源名称
- **AND** 其他租户相同 ID SHALL 按不存在处理。

#### Scenario：更新规则

- **GIVEN** 管理员拥有 `certificate:rule:edit` 且提交版本与当前记录一致
- **WHEN** 更新模板、来源、自动签发或启用状态
- **THEN** 系统 SHALL 验证模板和来源可绑定后更新规则
- **AND** 重复配置或并发版本冲突 SHALL 返回 409
- **AND** SHALL NOT 修改历史已签发证书。

### Requirement：签发规则来源 SHALL 通过统一候选接口选择

#### Scenario：查询可绑定来源

- **GIVEN** 管理员指定 `COURSE`、`EXAM` 或 `TRAINING`
- **WHEN** 分页查询规则来源候选
- **THEN** 系统 SHALL 只返回当前租户中满足已确认发布状态的来源
- **AND** SHALL 返回统一的来源类型、ID、编码、名称和状态
- **AND** SHALL NOT 返回草稿、取消、归档或结束后不可新增绑定的来源。

#### Scenario：绕过候选直接提交无效来源

- **GIVEN** 客户端提交其他租户、未发布、已结束或不存在的来源 ID
- **WHEN** 创建或更新签发规则
- **THEN** 服务端 SHALL 拒绝请求
- **AND** SHALL NOT 依赖前端候选列表保障正确性。

## MODIFIED Requirements

### Requirement：证书必须按来源合格结果自动生成真实文件

修改为：证书模板管理必须支持底图和 TEXT/VARIABLE
元素的草稿保存、详情回显、发布与安全删除；模板发布版本不可变。课程、理论考试和培训签发规则必须支持创建、详情、更新、删除及当前租户可绑定来源选择。学员达标后仍按启用规则自动、幂等签发真实
PNG，且 PNG 必须使用已发布模板的真实底图与样式。

## REMOVED Requirements

无。本 Change 不恢复 M4 已删除的撤销、重发、任意手工签发、到期管理或公开版本历史能力。
