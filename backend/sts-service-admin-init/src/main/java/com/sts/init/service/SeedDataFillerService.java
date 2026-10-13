package com.sts.init.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.init.config.InitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeedDataFillerService {

    /**
     * 不向企业/培训机构管理员角色分发「机构管理」入口（9532）及其按钮权限。
     * <p>
     * 该菜单挂在系统管理模块下，但机构是租户主数据本身，只由 superAdmin 或监管租户维护
     * （见 {@code OrganizationServiceImpl#requireOrgManager}）。绑给企业管理员会形成点击必然
     * {@code 403 / 4048} 的死入口；监管租户走 9810 模块下的 9814 独立入口，不受此处影响。
     */
    private static final Set<Long> TENANT_ADMIN_EXCLUDED_MENU_IDS = Set.of(9532L, 1381L, 1382L, 1383L);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InitProperties initProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Transactional
    @SuppressWarnings("unchecked")
    public void fillSeedData() {
        log.info("Starting seed data population from separate JSON configurations in data folder...");

        try {
            // 1. 读取 data 目录下的 JSON 文件
            List<Map<String, Object>> roles;
            try (InputStream is = new ClassPathResource("data/roles.json").getInputStream()) {
                roles = objectMapper.readValue(is, List.class);
            }

            List<Map<String, Object>> menus;
            try (InputStream is = new ClassPathResource("data/menus.json").getInputStream()) {
                menus = objectMapper.readValue(is, List.class);
            }

            List<Map<String, Object>> configs;
            try (InputStream is = new ClassPathResource("data/configs.json").getInputStream()) {
                configs = objectMapper.readValue(is, List.class);
            }

            Map<String, Object> dicts;
            try (InputStream is = new ClassPathResource("data/dicts.json").getInputStream()) {
                dicts = objectMapper.readValue(is, Map.class);
            }

            List<Map<String, Object>> users;
            try (InputStream is = new ClassPathResource("data/users.json").getInputStream()) {
                users = objectMapper.readValue(is, List.class);
            }

            List<Map<String, Object>> dictTypes = (List<Map<String, Object>>) dicts.get("dictTypes");
            List<Map<String, Object>> dictData = (List<Map<String, Object>>) dicts.get("dictData");

          // 种子数据归属示例企业租户；superAdmin 是账号级全局旁路，不受租户约束但仍需一个归属值。
          final String seedOrgCode = "DEMO";
            Long defaultOrganizationId = jdbcTemplate.query(
              "SELECT organization_id FROM sts_org_organization WHERE org_code = ? AND deleted = 0 LIMIT 1",
              rs -> rs.next() ? rs.getLong("organization_id") : null,
              seedOrgCode
            );
            if (defaultOrganizationId == null) {
              throw new IllegalStateException("示例企业租户未初始化，无法写入角色/部门种子数据");
            }

            // 2. 初始化配置的角色 (us_sys_role)
            // 主键由数据库自增生成；存在性以机构内 role_code + deleted=0 判定。
            log.info("Initializing system roles...");
            for (Map<String, Object> role : roles) {
                String roleName = (String) role.get("name");
                String roleCode = (String) role.get("code");
                Integer status = (Integer) role.get("status");
                int isBuiltin = "superAdmin".equals(roleCode) ? 1 : 0;

                Long roleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_role WHERE organization_id = ? AND role_code = ? AND deleted = 0",
                    Long.class, defaultOrganizationId, roleCode
                );
                if (roleCount == null || roleCount == 0) {
                    log.info("Creating role dynamically: {} ({})", roleName, roleCode);
                    jdbcTemplate.update(
                      "INSERT INTO us_sys_role (organization_id, role_name, role_code, role_level, is_builtin, status, description) "
                        + "VALUES (?, ?, ?, 'ADMIN', ?, ?, '')",
                        defaultOrganizationId, roleName, roleCode, isBuiltin, status != null ? status : 1
                    );
                }
            }

            // 3. 动态初始化配置的用户、对应组织结构、以及安全账号凭证 (100% 幂等)
            // 主键统一交由数据库自增生成，外键通过 KeyHolder 回取真实自增 ID 关联，
            // 存在性判定一律走业务唯一键 + deleted=0，可安全在含有其他数据的库上重复执行。
            for (Map<String, Object> user : users) {
                String username = (String) user.get("userCode");
                String realName = (String) user.get("userName");
                String plaintextPassword = (String) user.get("plaintextPassword");
                String deptName = (String) user.get("deptName");
                // 替换根部门占位符
                if ("ROOT_DEPT".equals(deptName)) {
                    deptName = initProperties.getRootDeptName();
                }
                String phone = (String) user.get("phone");
                Integer status = (Integer) user.get("status");
                List<String> roleCodes = (List<String>) user.get("roleCodes");

                // A. 校验并动态生成组织部门 (us_sys_dept)
                Long deptId = jdbcTemplate.query(
                    "SELECT dept_id FROM us_sys_dept WHERE dept_name = ? AND deleted = 0",
                    rs -> rs.next() ? rs.getLong("dept_id") : null,
                    deptName
                );
                if (deptId == null) {
                    log.info("Creating department dynamically: {}", deptName);
                    String deptCode = "DEPT_" + deptName.replaceAll("\\s+", "_");
                    deptId = insertReturningKey(
                        "INSERT INTO us_sys_dept (organization_id, parent_id, ancestors, dept_code, dept_name, dept_type, sort_order, leader, phone, description, status) "
                            + "VALUES (?, 0, '0', ?, ?, 'DEPARTMENT', 1, 'Admin', ?, '', 1)",
                        defaultOrganizationId,
                        deptCode,
                        deptName,
                        phone
                    );
                }

                // B. 校验并初始化用户主体 (us_sys_user)
              // 账号在租户内唯一，存在性判定必须带租户条件。
                Long targetUserId = jdbcTemplate.query(
                  "SELECT user_id FROM us_sys_user WHERE organization_id = ? AND user_code = ? AND deleted = 0",
                    rs -> rs.next() ? rs.getLong("user_id") : null,
                  defaultOrganizationId, username
                );
                if (targetUserId == null) {
                    log.info("Creating user dynamically: {}", username);
                    targetUserId = insertReturningKey(
                      "INSERT INTO us_sys_user (organization_id, user_code, user_name, phone, status, dept_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                      defaultOrganizationId, username, realName, phone, status != null ? status : 1, deptId
                    );
                }

                // C. 初始化安全认证凭证 (us_sys_auth_identity)
                Long identityCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_auth_identity WHERE user_id = ? AND identity_type = 'local' AND deleted = 0",
                    Long.class, targetUserId
                );
                if (identityCount == null || identityCount == 0) {
                    log.info("Creating auth credential for user: {}...", username);
                    // 前端 SHA-256 离散：front_hash = SHA-256(plaintext)
                    String frontHash = getSha256(plaintextPassword);
                    // 后端 BCrypt 慢哈希编码
                    String dbHash = passwordEncoder.encode(frontHash);

                    jdbcTemplate.update(
                        "INSERT INTO us_sys_auth_identity (user_id, identity_type, identifier, credential) " +
                            "VALUES (?, 'local', ?, ?)",
                        targetUserId, seedOrgCode + ":" + username, dbHash
                    );
                }

                // D. 绑定角色 (us_sys_user_role)
                if (roleCodes != null && !roleCodes.isEmpty()) {
                    for (String roleCode : roleCodes) {
                        Long roleId = jdbcTemplate.query(
                            "SELECT role_id FROM us_sys_role WHERE role_code = ? AND deleted = 0",
                            rs -> rs.next() ? rs.getLong("role_id") : null,
                            roleCode
                        );
                        if (roleId != null) {
                            // us_sys_user_role 为物理删除中间表，无 deleted 列，按 (user_id, role_id) 判定
                            Long userRoleCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM us_sys_user_role WHERE user_id = ? AND role_id = ?",
                                Long.class, targetUserId, roleId
                            );
                            if (userRoleCount == null || userRoleCount == 0) {
                                log.info("Binding user {} to role {}...", username, roleCode);
                                jdbcTemplate.update(
                                    "INSERT INTO us_sys_user_role (user_id, role_id) VALUES (?, ?)",
                                    targetUserId, roleId
                                );
                            }
                        }
                    }
                }
            }

            // 4. 全量树形菜单与按钮初始化 (us_sys_menu)
            // 菜单/按钮使用固定业务 ID（前端路由与权限点依赖其稳定），故显式插入 menu_id，
            // 存在性按主键 menu_id 判定（不加 deleted 过滤：软删后主键仍占用，避免重插撞 PK）。
            log.info("Initializing system menus...");
            for (Map<String, Object> menu : menus) {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_menu WHERE menu_id = ?", Long.class, menu.get("id")
                );
                if (count == null || count == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO us_sys_menu (menu_id, parent_id, menu_name, path, component, perms, icon, sort_order, is_visible, menu_type, module_id) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                        menu.get("id"),
                        menu.get("pid"),
                        menu.get("name"),
                        menu.get("path"),
                        menu.get("comp"),
                        menu.get("perms"),
                        menu.get("icon"),
                        menu.get("sort"),
                            menu.get("type"),
                            menu.get("moduleId")
                    );
                }
            }

            // 5. 内置参数配置初始化 (us_sys_config)
            // 使用固定业务 ID，按主键或业务唯一键 config_key 判定存在性。
            log.info("Initializing system global configurations...");
            for (Map<String, Object> config : configs) {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_config WHERE config_id = ? OR config_key = ?",
                    Long.class, config.get("id"), config.get("key")
                );
                if (count == null || count == 0) {
                    jdbcTemplate.update(
                        "INSERT INTO us_sys_config (config_id, config_name, config_key, config_value, config_type) " +
                            "VALUES (?, ?, ?, ?, ?)",
                        config.get("id"), config.get("name"), config.get("key"), config.get("val"), config.get("type")
                    );
                }
            }

            // 6. 默认数据字典初始化 (us_sys_dict_type & us_sys_dict_data)
            // 使用固定业务 ID，按主键或业务唯一键判定存在性。
            log.info("Initializing data dictionary types...");
            for (Map<String, Object> dt : dictTypes) {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_dict_type WHERE dict_type_id = ? OR dict_type = ?",
                    Long.class, dt.get("id"), dt.get("type")
                );
                if (count == null || count == 0) {
                    jdbcTemplate.update(
                        "INSERT INTO us_sys_dict_type (dict_type_id, dict_name, dict_type, status) " +
                            "VALUES (?, ?, ?, 1)",
                        dt.get("id"), dt.get("name"), dt.get("type")
                    );
                }
            }

            log.info("Initializing data dictionary values...");
            for (Map<String, Object> dd : dictData) {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM us_sys_dict_data WHERE dict_data_id = ?",
                    Long.class, dd.get("id")
                );
                if (count == null || count == 0) {
                    jdbcTemplate.update(
                        "INSERT INTO us_sys_dict_data (dict_data_id, sort_order, dict_label, dict_value, dict_type, status) " +
                            "VALUES (?, ?, ?, ?, ?, 1)",
                        dd.get("id"),
                        dd.get("sort"),
                        dd.get("label"),
                        dd.get("val"),
                        dd.get("type")
                    );
                }
            }

            // 7. 绑定“管理员”角色与所有菜单权限 (us_sys_role_menu)
            log.info("Binding 'admin' role with all menus/permissions...");
            Long adminRoleId = jdbcTemplate.query(
                "SELECT role_id FROM us_sys_role WHERE role_code = 'admin' AND deleted = 0",
                rs -> rs.next() ? rs.getLong("role_id") : null
            );
            if (adminRoleId != null) {
                for (Map<String, Object> menu : menus) {
                    // 示例企业管理员只绑定企业菜单：监管模块（9810）由监管租户角色单独授权，
                    // 「机构管理」及按钮权限属租户主数据维护，同样不向企业管理员分发。
                    Long menuId = ((Number) menu.get("id")).longValue();
                    if (((Number) menu.get("moduleId")).longValue() == 9810L
                        || TENANT_ADMIN_EXCLUDED_MENU_IDS.contains(menuId)
                        // 更正权限只定义，不向既有或初始化管理员角色自动授予。
                        || "archive:history:correct".equals(menu.get("perms"))
                        || "archive:history:correction:list".equals(menu.get("perms"))) {
                        continue;
                    }
                    Long exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM us_sys_role_menu WHERE role_id = ? AND menu_id = ?",
                        Long.class, adminRoleId, menuId
                    );
                    if (exists == null || exists == 0) {
                        jdbcTemplate.update(
                            "INSERT INTO us_sys_role_menu (role_id, menu_id) VALUES (?, ?)",
                            adminRoleId, menuId
                        );
                    }
                }
            }

            log.info("Successfully populated all platform seed data!");
        } catch (Exception e) {
            log.error("Failed to parse and fill seed data: {}", e.getMessage(), e);
            throw new RuntimeException("Init filler failed", e);
        }
    }

    /**
     * 执行 INSERT 并回取数据库生成的自增主键，用于外键关联。
     */
    private Long insertReturningKey(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key returned for insert: " + sql);
        }
        return key.longValue();
    }

    private String getSha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encoded) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
