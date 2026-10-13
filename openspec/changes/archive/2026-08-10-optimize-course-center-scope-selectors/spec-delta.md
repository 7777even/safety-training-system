# Spec Delta：课程中心选择器

## ADDED Requirements

### Requirement：素材选择按需加载详情

课程素材选择器 MUST 使用素材分页结果渲染列表，不得对当前页每一项发起详情请求。仅在已选项回显校验或用户明确点击预览时请求对应详情。

### Requirement：默认本机构数据范围

普通管理员 MUST 使用登录会话机构查询和保存人员与参与范围；只有具备既有租户管理权限的用户 MAY 选择其他机构。切换机构 MUST
清空已选下游对象。

### Requirement：参与范围枚举中文化

参与范围规则和解析模式的可见选项 MUST 使用中文 `label` 与 Contract `value` 的映射，不得直接显示英文枚举值。
