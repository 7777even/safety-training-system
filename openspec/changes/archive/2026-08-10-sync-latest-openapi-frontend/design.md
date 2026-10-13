# 设计

## 系统用户与人员选择器

`UserVO.deptId/deptName` 使用可空类型；用户列表对未分配部门显示中文占位。人员选择器删除 `orgMemberId/employeeNo`，增加
`jobTitle`，同时更新真实 API 类型、组件展示和 Mock。

## M2 业务租户上下文

题库、试卷、考试、评价表单的业务请求不再传递 `organizationId` 或 `query.organizationId`
。响应中的组织字段只读保留；页面使用会话租户和后端租户隔离，不展示普通租户的组织选择器。

## 素材预览

`hls`、`mp3`、`pdf`、`excel`、`image` 继续按格式渲染；`original` 显示原文件下载入口，不默认在线解析。
