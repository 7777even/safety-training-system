import { AUTHORIZATION_HEADER, BEARER_TOKEN_PREFIX, HTTP_STATUS_SUCCESS_MAX, HTTP_STATUS_SUCCESS_MIN } from '@shared/constants';
import { DEFAULT_REQUEST_TIMEOUT } from '@/constants';

type HttpMethod = 'OPTIONS' | 'GET' | 'HEAD' | 'POST' | 'PUT' | 'DELETE' | 'TRACE' | 'CONNECT';
type TokenProvider = () => string | undefined;
type RequestErrorHandler = (error: unknown) => void;
type RequestData = string | ArrayBuffer | object;
type ResponseType = 'text' | 'arraybuffer';
type RequestInterceptor = (config: RequestConfig<RequestData>) => RequestConfig<RequestData>;
type ResponseInterceptor = (response: unknown) => unknown;
type RequestError = Error & {
  response?: { status: number; data: unknown };
};

export interface RequestConfig<TData extends RequestData = Record<string, unknown>> {
  url: string;
  method?: HttpMethod;
  /** GET/HEAD 请求的查询参数；请求体仍通过 data 传递。 */
  params?: Record<string, unknown>;
  data?: TData;
  header?: Record<string, string>;
  responseType?: ResponseType;
}

let baseURL = import.meta.env.VITE_API_BASE_URL ?? '';
let tokenProvider: TokenProvider | undefined;
let requestErrorHandler: RequestErrorHandler | undefined;
let requestInterceptor: RequestInterceptor | undefined;
let responseInterceptor: ResponseInterceptor | undefined;

/** 设置环境级接口前缀；具体地址必须来自已确认的 API Contract。 */
export function setBaseURL(value: string): void {
  baseURL = value;
}

/** 注入令牌读取函数；认证方案由后续 API Contract 确认。 */
export function setTokenProvider(provider: TokenProvider | undefined): void {
  tokenProvider = provider;
}

/** 提供受控文件下载等原生请求能力读取当前登录令牌。 */
export function getAccessToken(): string | undefined {
  return tokenProvider?.();
}

/** 提供原生文件下载拼接 API 地址。 */
export function getBaseURL(): string {
  return baseURL;
}

/** 注入应用统一错误处理函数，避免页面内堆叠请求错误逻辑。 */
export function setRequestErrorHandler(handler: RequestErrorHandler | undefined): void {
  requestErrorHandler = handler;
}

/** 注入请求拦截器，用于通用请求配置处理，不承载业务参数转换。 */
export function setRequestInterceptor(interceptor: RequestInterceptor | undefined): void {
  requestInterceptor = interceptor;
}

/** 注入响应拦截器，用于通用响应处理，不承载业务结果转换。 */
export function setResponseInterceptor(interceptor: ResponseInterceptor | undefined): void {
  responseInterceptor = interceptor;
}

/**
 * uni.request 的通用 Promise 封装。
 * 不在此处声明业务 URL、参数或返回字段。
 */
export function request<TResponse, TData extends RequestData = Record<string, unknown>>(config: RequestConfig<TData>): Promise<TResponse> {
  const interceptedConfig = requestInterceptor ? requestInterceptor(config) : config;
  const token = tokenProvider?.();
  const header = { ...interceptedConfig.header };

  if (token) {
    header[AUTHORIZATION_HEADER] = `${BEARER_TOKEN_PREFIX}${token}`;
  }

  return new Promise<TResponse>((resolve, reject) => {
    uni.request({
      url: `${baseURL}${interceptedConfig.url}`,
      method: interceptedConfig.method ?? 'GET',
      data: interceptedConfig.params ?? interceptedConfig.data,
      header,
      responseType: interceptedConfig.responseType,
      timeout: DEFAULT_REQUEST_TIMEOUT,
      success: (response) => {
        if (response.statusCode >= HTTP_STATUS_SUCCESS_MIN && response.statusCode < HTTP_STATUS_SUCCESS_MAX) {
          resolve((responseInterceptor ? responseInterceptor(response.data) : response.data) as TResponse);
          return;
        }

        const error = new Error(`Request failed with status ${response.statusCode}`) as RequestError;
        // 保留 HTTP 状态和服务端响应，使应用层能够统一识别登录失效等通用错误。
        error.response = { status: response.statusCode, data: response.data };
        requestErrorHandler?.(error);
        reject(error);
      },
      fail: (error) => {
        requestErrorHandler?.(error);
        reject(error);
      }
    });
  });
}
