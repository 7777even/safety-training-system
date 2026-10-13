/** HTTP 请求成功状态的下界。 */
export const HTTP_STATUS_SUCCESS_MIN = 200;

/** HTTP 请求成功状态的上界（不含）。 */
export const HTTP_STATUS_SUCCESS_MAX = 300;

/** 预留的认证请求头名称。 */
export const AUTHORIZATION_HEADER = 'Authorization';

/** Authorization 请求头中 Bearer 认证方案的前缀。 */
export const BEARER_TOKEN_PREFIX = 'Bearer ';

/** 两端持久化当前登录令牌时使用的统一键名。 */
export const AUTH_TOKEN_STORAGE_KEY = 'safety-training-token';
