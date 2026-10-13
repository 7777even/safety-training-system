package com.sts.admin.auth.strategy;

import java.util.Map;

public interface LoginStrategy {
    /**
     * 获取登录凭证类型，如 "local", "sms", "oauth2_github"
     */
    String getIdentityType();

    /**
     * 认证逻辑，返回用户主体与本次登录租户。
     *
     * @param params 登录参数，如用户名和密码哈希等
     * @return 认证通过的用户与登录租户
     */
    AuthenticationResult authenticate(Map<String, String> params);
}
