# OpenAPI 契约编写规范

编制日期：2026-07-24
修订：2026-07-24（强化：接口/字段注释、example、按 Controller 分类）

适用范围：所有写入或修改 `docs/api/*.openapi.json` 的工作（Cursor / Claude / Codex / 人工）。

样板文件（优先对齐）：`docs/api/asset.openapi.json`  
同风格已落地：`docs/api/org.openapi.json`

运行期包装与分页语义见：`docs/architecture/api-standard.md`。  
本文件只规定 **OpenAPI 文档怎么写**，不替代 HTTP Result / 错误状态映射。

---

## 0. 四条铁律（缺一不可）

| # | 铁律 | 不合格表现 |
|---|------|------------|
| 1 | **按 Controller 分类** | 根级 `tags` 未按 Controller 建分组；operation 无 tags 或 tags 用业务名平铺（如「成员」「标签」） |
| 2 | **每个接口有注释** | 缺少 `summary` 或 `description` |
| 3 | **每个字段有注释** | schema property / parameter 缺少中文 `description` |
| 4 | **每个接口有 example** | 有 body 无 request example，或成功响应无 `code/message/data` example；查询参数无 `schema.example` |

禁止把同一文件写成「路径大杂烩、无 Controller 分组」的平铺清单。Swagger / Redoc 的分组来源是 **根级 `tags` + operation.`tags`**，不是路径书写顺序。

---

## 1. 何时必须更新

下列任一发生时，必须同步对应 `docs/api/<domain>.openapi.json`，不得只改代码：

1. 新增 / 修改 / 删除 Controller 对外路径或 Method
2. 请求 / 响应字段增删改、枚举变化、必填变化
3. 权限码（`@SaCheckPermission`）变化
4. 业务错误码或失败语义变化
5. 分页参数、路径参数、查询参数绑定方式变化

禁止：

- 只写摘要、省略 example、省略字段 description
- 前端或调用方自行发明未进 Contract 的字段
- 用 Markdown 另起一套平行接口说明替代 OpenAPI
- 不按 Controller 建 `tags`、把接口平铺成无分类列表

---

## 2. 文件与域划分

| 文件                               | 域                                 |
|----------------------------------|-----------------------------------|
| `system-admin.openapi.json`      | 认证与系统管理（用户/角色/部门/菜单等）             |
| `asset.openapi.json`             | 素材                                |
| `course.openapi.json`            | 课程                                |
| `org.openapi.json`               | 机构成员 / 标签 / 岗位                    |
| `person-selection.openapi.json`  | 当前机构候选人员分页选择                      |
| `question.openapi.json`          | 知识点 / 题库 / 题目版本 / 导入 / 错题状态       |
| `paper.openapi.json`             | 试卷组卷 / 预览 / 发布                    |
| `exam.openapi.json`              | 考试定义 / 作答 / 批阅 / 成绩 / 补考          |
| `training.openapi.json`          | 培训类型 / 标准 / 模板 / 计划 / 执行实例 / 线下成绩 |
| `evaluation-form.openapi.json`   | 评价表单定义 / 不可变版本 / 评分规则             |
| `survey.openapi.json`            | 问卷定义 / 投放 / 作答 / 统计               |
| `evaluation-result.openapi.json` | 评价记录 / 综合成绩计算批次                   |
| `archive.openapi.json`           | 培训档案 / 归档版本 / 导出任务                |
| `certificate.openapi.json`       | 证书模板 / 签发规则 / 签发撤销重发              |
| `ops.openapi.json`               | 运营工作台 / 统计 / 下钻 / 导出任务            |
| `regulation.openapi.json`        | 监管汇总 / 预警规则 / 检测 / 处置流水           |

新增业务域时：新建独立 `<domain>.openapi.json`，并在 `docs/AGENTS.md` 登记。跨域只改“被扩展的那份”契约，并在 description 中互相引用。

---

## 3. 按 Controller 分类（强制，禁止平铺）

### 3.1 根级 `tags` = Controller 目录

文件顶部必须声明本域涉及的全部 Controller，**一个 Controller 一个 tag**：

```json
"tags": [
  {
    "name": "OrgTagController",
    "description": "机构标签管理（对应 com.sts.org.controller.OrgTagController）"
  },
  {
    "name": "OrgPositionController",
    "description": "机构岗位管理（对应 OrgPositionController）"
  },
  {
    "name": "OrgMemberController",
    "description": "机构成员档案管理（对应 OrgMemberController）"
  }
]
```

要求：

1. `name` **必须等于** Java Controller 简单类名（如 `OrgMemberController`），不得用中文或业务简称单独作 tag 名。
2. 每个 tag 必须有中文 `description`，说明该 Controller 职责。
3. 新增 Controller 时先在根级 `tags` 登记，再写其 paths。
4. 同一 operation 只挂 **一个** Controller tag（禁止多 Controller 混挂）。

### 3.2 operation 必须挂到 Controller

每个 HTTP operation 的 `tags` 必须为：

```json
"tags": ["OrgMemberController"]
```

禁止：

- 省略 `tags`
- 使用 `["成员"]`、`["org-member"]`、`["CRUD"]` 等非 Controller 类名平铺标签
- 把多个无关 Controller 的接口混在同一 tag 下「凑一组」

### 3.3 书写与阅读方式

OpenAPI 的 `paths` 键按 URL 存放是规范格式；**分类靠 tags，不靠把文档改成 Markdown 章节平铺**。

编写与 Review 时按 Controller 检查：

1. 列出该 Controller 的全部映射方法
2. 确认根级 `tags` 已声明
3. 确认每个方法都有完整注释 + example，且 `tags` 指向该 Controller

读者在 Swagger UI / Redoc 中应按左侧 **Controller 分组**浏览，而不是无分类的接口长列表。

---

## 4. 每个接口必须有注释（强制）

每个 HTTP operation 必须包含：

| 项 | 要求 |
|----|------|
| `summary` | 一句话中文标题（接口注释短标题） |
| `description` | 接口注释正文：权限码、关键业务规则、主要错误码 |
| `tags` | **唯一** Controller 类名 |
| `security` | `BearerAuth`（公开接口除外并在 description 标明） |
| `x-permission` | 与 `@SaCheckPermission` 完全一致 |
| `parameters` / `requestBody` | 有则写全；分页用 `pageNum`/`pageSize`/`query.*` |
| `responses.200` | `ResultEnvelope` + `data` schema；带 **example** |
| `responses.401` / `403` | 引用公共 response |

业务对象封面受控 GET 除外：成功 `200` 为原始图片 `binary`（含 `Content-Type` / `ETag` example），不包 Result；失败 404 仍用
Result 外壳与业务码。设计见 `cover-access-protocol.md`。

`description` 推荐句式：

```text
权限：`org:member:add`。校验用户主部门属于目标机构。错误码：4026 用户主部门不属于目标机构；4027 成员已存在。
```

缺少 `summary` 或 `description` 的 operation 视为未完成。

---

## 5. 每个字段必须有注释（强制）

### 5.1 Schema 对象

每个 `components.schemas` 中的对象：

1. 对象本身有 `description`
2. `required` 与后端 `@NotNull` / `@NotBlank` / 业务必填对齐
3. **每个 property 必须有中文 `description`（字段注释）**
4. 标量与枚举字段必须有 `example`；对象/数组可用嵌套 example 或由上层 request/response example 覆盖
5. 枚举写 `enum`，并在 description 中解释每个取值含义
6. 可空字段标 `nullable: true`
7. ID 使用 `type: integer, format: int64`
8. 时间使用 `format: date-time` 或 `date`
9. 分页结果单独建 `PageResultXxxVO`，`list` + `total`

### 5.2 参数字段

每个 path / query / header parameter 必须有：

- 中文 `description`
- `schema`（含类型）
- `schema.example`（或 parameter 级 `example`）

禁止：只列类型、无中文说明的「裸字段」；禁止参数无 example。

---

## 6. 每个接口必须有 example（强制）

1. **有请求体**：必须有 `requestBody.content.application/json.example`
2. **成功响应**：必须有 `responses.200.content.application/json.example`
3. **查询/路径参数**：每个参数必须有 example（见 §5.2）
4. example 使用真实业务语义数据，禁止 `foo` / `bar` / `string` / `test`
5. 成功 example 外壳固定：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

6. 创建类接口 `data` 一般为新建主键（int64）
7. 无返回体接口 `data` 可为 `null`
8. 分页 example 的 `data` 为 `{ "list": [ ... ], "total": 1 }`

无 body 的 GET/DELETE：可无 requestBody example，但 **响应 example 与参数 example 仍必须有**。

---

## 7. 文档结构其余强制项

每个 OpenAPI 文件至少包含：

1. `openapi: 3.0.3`
2. `info.title` / `info.version` / `info.description`（说明鉴权方式、域边界、**按 Controller tags 分组阅读**）
3. `servers`：`url: /api`
4. `tags`：按 Controller 分类目录（见 §3）
5. `paths`：路径以 `/api/...` 开头，与网关/context-path 对外路径一致
6. `components.securitySchemes.BearerAuth`
7. `components.schemas.ResultEnvelope`
8. 常用失败响应：`Unauthorized`、`Forbidden`；写操作建议含 `BadRequest`
9. 域内业务错误码说明 schema（如 `OrgBusinessErrorCodes`），供联调参考

---

## 8. 与 Controller 对齐检查清单

写完或改完 OpenAPI 后，**按 Controller 逐个**勾选（不要按整文件路径平铺扫一眼了事）：

- [ ] 根级 `tags` 已声明该 Controller，且有中文 description
- [ ] 该 Controller 每个映射方法都有 operation，且 `tags` 仅含该类名
- [ ] 每个 operation 有 `summary` + `description`（接口注释）
- [ ] 每个参数 / schema 字段有中文 `description`（字段注释）
- [ ] 每个参数有 example；有 body 则有 request example；必有成功响应 example
- [ ] 路径、Method 与映射注解一致（注意类上 `@RequestMapping` 前缀）
- [ ] `x-permission` 与 `@SaCheckPermission` 一致
- [ ] 请求字段与 Create/Update/Query DTO 一致；响应与 VO 一致
- [ ] 分页参数名为 `pageNum`、`pageSize`、`query.字段名`
- [ ] 错误码与 `BusinessException` 业务码一致，并写入 description 或错误码 schema

---

## 9. 最小合格样例（摘录）

根级分类：

```json
"tags": [
  {
    "name": "OrgMemberController",
    "description": "机构成员档案管理"
  }
]
```

接口（注释 + Controller 分类 + example）：

```json
{
  "post": {
    "summary": "创建机构成员",
    "description": "权限：`org:member:add`。校验用户主部门属于目标机构。错误码：4026、4027。",
    "tags": ["OrgMemberController"],
    "security": [{ "BearerAuth": [] }],
    "x-permission": "org:member:add",
    "requestBody": {
      "required": true,
      "content": {
        "application/json": {
          "schema": { "$ref": "#/components/schemas/OrgMemberCreateRequest" },
          "example": {
            "organizationId": 1,
            "userId": 100,
            "memberType": "INTERNAL",
            "employeeNo": "E1001",
            "status": 1
          }
        }
      }
    },
    "responses": {
      "200": {
        "description": "操作成功。",
        "content": {
          "application/json": {
            "example": { "code": 0, "message": "success", "data": 31 }
          }
        }
      }
    }
  }
}
```

字段注释：

```json
{
  "employeeNo": {
    "type": "string",
    "description": "工号；非空时机构内唯一，空值允许多条。",
    "example": "E1001",
    "nullable": true
  }
}
```

---

## 10. AI 执行要求（Cursor / Claude / Codex）

1. 实现或修改对外 API 时，**同一次交付内**更新 OpenAPI；不得留到「以后补文档」。
2. 新增域优先复制 `asset.openapi.json` / `org.openapi.json` 的结构；先登记 Controller `tags`，再写该 Controller 下接口。
3. 交付自检口令：**分类（Controller）→ 接口注释 → 字段注释 → example**；任一项缺失视为未完成。
4. 只改 schema、不改 path example，或 tags 非 Controller 类名，视为未完成。
5. Review 时把「是否按本规范写全」作为独立检查项。
6. 本规范与 `AGENTS.md` §10、OpenSpec、后端 `AGENTS.md` 同时生效；冲突时以更具体的本文件与已确认 OpenSpec 为准。
7. 当 `git config user.name` 为 **`lims`** 时：后端交付以完整 OpenAPI 作为给前端的契约即可，不要求改前端 Mock/页面；OpenSpec 设计与 Task 不含前端（见 `AGENTS.md` §17.1）。
