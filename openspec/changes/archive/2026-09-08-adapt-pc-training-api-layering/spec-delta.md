# Spec Delta：PC Web 培训接口分层适配

## MODIFIED Requirements

### Requirement：培训计划模板复制

PC Web MUST 在新建计划时将 `sourceTemplateId` 原样提交给创建接口，并在成功后读取服务端复制的阶段内容。对已创建草稿，PC Web MUST 先获得用户确认，再调用 apply-template 覆盖复制；不得读取模板详情后自行构造并提交计划阶段。

#### Scenario：草稿从模板覆盖复制

- **WHEN** 管理员为草稿计划确认更换来源模板
- **THEN** 页面提交计划版本和模板 ID 到 apply-template，并重载计划详情

### Requirement：模板与标准边界

PC Web MUST 将标准规则显示为检查提示，不得自动生成模板阶段或内容。模板保存和发布 MUST NOT 因缺少本期执行配置而被前端阻止。

#### Scenario：无执行配置模板

- **WHEN** 管理员保存只含课程引用的模板课表且未配置 executionConfig
- **THEN** PC Web 允许提交，计划复制后由计划工作台补齐本期配置

### Requirement：培训来源进入

PC Web MUST 使用培训 enter 返回值进入课程、考试/题库或线下场次；课程与考试管理对象的培训来源以 `planContentId` 是否为空标识。
