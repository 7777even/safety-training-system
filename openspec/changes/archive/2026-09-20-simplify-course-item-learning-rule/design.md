# Design：课程内容项材料学习规则最小化闭环

## D1：以内容项字段作为唯一写入接口

管理端和调用方只写 `CourseItem.minimumStudySeconds`。API 字段名为兼容既有调用方保持不变，中文统一称为
“要求学习时长”。ASSET 必须大于 0，其他类型必须为 0。

## D2：删除浅层配置模块

删除机构课程设置模块和独立材料规则模块。两者不再提供接口、菜单、权限或前端入口；独立材料规则表删除，机构课程设置表及历史值保留并统一标注为暂时隐藏，不得作为当前业务规则依据。课程级
`isSpeedPlayAllowed`、`isWatermarkEnabled` 继续作为学员端唯一倍速和水印来源。

## D3：数据迁移保持当前有效门槛

迁移 ASSET 时按以下顺序写回 `minimum_study_seconds`：

1. 自定义材料规则 `required_seconds > 0`；
2. 内容项已有 `minimum_study_seconds > 0`；
3. 本机构 `default_document_page_seconds > 0`；
4. 固定兜底 15 秒。

非 ASSET 内容项统一归零。完成回填后删除独立材料规则表；机构课程设置表只作为暂时隐藏的历史资产保留。

## D4：旧快照只读兼容

新发布快照只序列化 `minimumStudySeconds`。解析旧快照时仍接受 `materialRule.source/requiredSeconds`，
并保持“CUSTOM → 内容项值 → DEFAULT”的原完成门槛；兼容字段不进入新快照和公开接口。

## D5：菜单与权限同步删除

空库种子和线上树移除菜单 302、1311、1312；存量库先删除 `us_sys_role_menu` 关联，再将对应菜单逻辑删除。
后端 Controller 删除后，权限一致性测试不得再发现 `course:setting:view/edit`。
