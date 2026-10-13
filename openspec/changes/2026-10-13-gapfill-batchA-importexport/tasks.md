# 补漏 A 批任务（已定稿，Owner：待分配，建议 Codex）

|状态|编号|输入|输出/前后端影响|API/数据影响|验收与测试|
|---|---|---|---|---|---|
|[~] T1|11|用户管理/组织|单/多组织 CSV 导入导出、错误行定位|新增 `sys:user:import`/`sys:user:export` 与权限点|后端+前端已实现；解析器单测 5/5 绿；后端 `sts-service-admin` 编译通过；DB-IT/frontend-build 待环境可用（Docker 不可用、pc-web node_modules 未安装）|
|[_] T2|16|考试安排/考生|文件导入考生名单入口|新增 `exam:exam:import` 与权限点；复用 candidate 写入|与绑定等价、重复/非法反馈、权限；单测、前端行为测试|
|[_] T3|38|培训成员|批量导入(`joinSource=IMPORT`)、名单 CSV 导出|新增 `training:participant:import/export` 与权限点|重复加入幂等、导出字段、扫码延期不阻塞；单测、前端行为测试|

说明：38 的扫码加入(`joinSource=QR`)列为本期延期子能力，本批不实现、不影响 11/16/38 导入导出勾选。
勾选表示开发完成，待真实验收后关闭原清单对应编号。同步 OpenAPI/数据库资产/QA/Retro/双轴 Review。

## T1 实现记录（2026-10-13）
后端：`UserImportParser`（纯逻辑，单测 5/5 通过）、`SysUserService.exportUsers/importUsers`、`SysUserController` 两端点；`system-admin.openapi.json`（两路径 + `UserImportResult` schema）；`menus.json` 新增 1006/1007 权限种子；迁移 `V20261013100000__add_user_import_export_menus.sql`。`sts-service-admin` 主源码与测试源码均编译通过（JDK 21）。
前端：`system-user.ts` 新增 `exportUsers`(Blob)/`importUsers`(multipart)；`user/index.vue` 新增导出按钮、导入弹窗（拖拽上传 + 模板下载 + 逐行失败明细）。默认初始密码 123456（SHA-256 后由 PasswordEncoder 再编码，与现有约定一致）；导入按部门编码归属当前租户；多组织导出由操作者切换租户上下文分别导出（单租户会话模型下的可行最小形态）。未改动 `INIT_SCHEMA_VERSION` 基线——本迁移为叠加式新增权限，Flyway 在全部库执行。
待验证：DB-IT（Docker 不可用）、pc-web 类型检查/构建（node_modules 未安装）。
