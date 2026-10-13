# Design：课程中心选择器优化

## 既有契约与身份边界

- 素材列表使用 `asset.openapi.json` 的分页接口；列表行已有名称、类型、处理状态和启停状态，不能为列表密度额外逐条读取详情。
- 素材详情仍仅用于：跨分页已选项回显校验、用户点击预览。
- 人员选择使用 `org.openapi.json` 已有的人员分页接口，查询必须带 `query.organizationId`。
- 参与范围使用 `common-audience.openapi.json`；规则枚举为 `INCLUDE/EXCLUDE` 与 `ALL/DEPT/TAG/POSITION/USER/AUDIENCE`
  ，解析模式为 `DYNAMIC/FROZEN`。
- `frontend/pc-web/src/utils/tenant-context.ts` 已定义：具备 `system:tenant:list` 的用户可跨机构，否则使用会话
  `userInfo.organizationId`。本 Change 只复用该既有判断，不定义新的 superAdmin 识别方式。

## 交互设计

### 素材选择

打开或翻页时只调用一次素材分页接口。点击素材名称时再读取该素材详情并在预览区展示；选择本页素材不读取详情。已有选中 ID
打开时只按选中数量读取详情，用于回显和当前可用性校验。

### 机构范围

普通管理员不显示机构选择控件，人员列表、参与范围引用选项和新建参与范围均固定当前会话机构。superAdmin
才显示机构选择控件；切换机构时清空已选人员、参与范围规则对象并重新加载下游数据。

### 枚举展示

参与范围弹窗复用 `constants/enum-options.ts` 的中文选项，不将英文 Contract 枚举直接显示给用户。

## 风险与取舍

- 素材列表不再显示仅详情接口才有的文件大小、时长；以“按需预览详情”换取避免 N+1 请求。
- 若普通管理员会话缺少 `organizationId`，禁用保存/查询并提示重新登录，而不是默认为机构 1。
