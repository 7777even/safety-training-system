# 奖惩佐证预览 Retro

- 起因：工作台仅显示“已上传”，奖惩记录只有 `evidenceFileId`，系统文件 URL 为私有地址，缺少可按业务记录授权读取的入口。
- 处理：新增按奖惩记录读取佐证的受控接口，复用现有预览器；上传未保存时使用本地文件预览。未新增任意文件 ID 读取权限。
- 验证：后端服务与 HTTP 定向测试、PC 类型与 ESLint、OpenAPI JSON 与 OpenSpec strict 通过。`description` / `occurred_date` 表约束冲突按用户确认的可选口径同步迁移与 init；佐证/清空字段 MySQL IT 2/2、升级迁移 1/1、Admin init 1/1 通过。完整奖惩 IT 的既有计划乐观锁用例单独失败；真实登录态及对象存储文件预览待 Task 3。
- 后续：管理员用图片、PDF、Office 分别核对列表与表单预览；检查无佐证、删除文件和已结束计划的行为。
