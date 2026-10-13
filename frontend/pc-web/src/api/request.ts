import axios from 'axios';
import type { AxiosRequestConfig } from 'axios';
import { AUTHORIZATION_HEADER, BEARER_TOKEN_PREFIX } from '@shared/constants';

type TokenProvider = () => string | undefined;
type RequestErrorHandler = (error: unknown) => void;
type ApiError = Error & {
  code: number;
  response?: { status?: number; data?: unknown };
};

let tokenProvider: TokenProvider | undefined;
let requestErrorHandler: RequestErrorHandler | undefined;
let targetTenantId: number | undefined;

/** 监管租户视角错误码的统一前端提示，避免各页面重复维护文案。 */
export const TARGET_TENANT_ERROR_MESSAGES: Record<number, string> = {
  4061: '当前用户不是监管租户，不能切换企业视角',
  4062: '当前用户没有监管聚合查询权限',
  4063: '目标企业租户不存在或已停用',
  4064: '当前请求不允许切换企业租户视角',
  4065: '企业只读视角已失效，请重新选择企业'
};

/** 设置监管只读视角的目标企业租户。 */
export function setTargetTenantId(organizationId: number | undefined): void {
  targetTenantId = organizationId;
}

/** 清除监管只读视角，避免退出后沿用旧租户上下文。 */
export function clearTargetTenantId(): void {
  targetTenantId = undefined;
}

/** 注入令牌读取函数；认证方案由后续 API Contract 确认。 */
export function setTokenProvider(provider: TokenProvider | undefined): void {
  tokenProvider = provider;
}

/** 为需要通过原生媒体请求读取受控文件的组件提供当前 Bearer Token。 */
export function getAccessToken(): string | undefined {
  return tokenProvider?.();
}

/** 注入应用统一错误处理函数，避免页面内堆叠请求错误逻辑。 */
export function setRequestErrorHandler(handler: RequestErrorHandler | undefined): void {
  requestErrorHandler = handler;
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? ''
});

/** 判断响应是否为统一 API Result 外壳，避免把普通响应数据误判为业务错误。 */
function isApiResponse(data: unknown): data is { code: number; message?: string } {
  return typeof data === 'object' && data !== null && 'code' in data && typeof data.code === 'number';
}

/** 将业务失败响应转换为可被调用方捕获的错误，并保留原始响应供全局提示使用。 */
function createApiError(
  code: number,
  message: string | undefined,
  response?: { status?: number; data?: unknown }
): ApiError {
  const error = new Error(TARGET_TENANT_ERROR_MESSAGES[code] ?? message ?? '请求失败，请稍后重试。') as ApiError;
  error.code = code;
  error.response = response;
  return error;
}

http.interceptors.request.use((config) => {
  const token = tokenProvider?.();

  if (token) {
    config.headers.set(AUTHORIZATION_HEADER, `${BEARER_TOKEN_PREFIX}${token}`);
  }

  const method = (config.method ?? 'GET').toUpperCase();
  // 当前只读视角仅服务监管培训记录下钻，不向工作台、统计或预警请求传播目标租户。
  if (targetTenantId !== undefined && config.url?.startsWith('/api/regulation/training-plans') && ['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    config.headers.set('X-Target-Tenant-Id', String(targetTenantId));
  }

  return config;
});

http.interceptors.response.use(
  (response) => {
    if (isApiResponse(response.data) && response.data.code !== 0) {
      const error = createApiError(response.data.code, response.data.message, response);
      requestErrorHandler?.(error);
      return Promise.reject(error);
    }

    return response.data;
  },
  (error: unknown) => {
    requestErrorHandler?.(error);
    return Promise.reject(error);
  }
);

/**
 * PC Web 通用请求入口。
 * 不在此处声明业务 URL、参数或返回字段。
 */
export function request<TResponse>(config: AxiosRequestConfig): Promise<TResponse> {
  return http.request<unknown, TResponse>(config);
}
