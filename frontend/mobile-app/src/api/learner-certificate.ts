import type { ApiResponse } from '@shared/types';
import { getAccessToken, getBaseURL, request } from './request';

export type CertificateSourceType = 'COURSE' | 'EXAM' | 'TRAINING';

export interface LearnerCertificate {
  certificateId: number;
  organizationId: number;
  certificateNo: string;
  holderUserId: number;
  sourceType: CertificateSourceType;
  sourceId: number;
  /** 来源课程、考试或培训计划的当前名称。 */
  sourceName?: string | null;
  certificateStatus: string;
  issuedTime: string;
  certificateFileId: number;
}

/** 分页结果外壳，与后端 ResultPageResultCertificateVO 的 data 对齐。 */
export interface CertificatePage {
  list: LearnerCertificate[];
  total: number;
}

/** 解包真实 Result，页面可将后端权限、状态和业务错误原样反馈给学员。 */
function unwrap<T>(response: ApiResponse<T>): T {
  if (response.code !== 0) throw new Error(response.message || '证书请求失败，请稍后重试。');
  return response.data;
}

/** 本人证书分页查询，持有人由登录态派生，不传人员主键。 */
export async function getMyCertificates(pageNum = 1, pageSize = 20): Promise<CertificatePage> {
  return unwrap(
    await request<ApiResponse<CertificatePage>>({
      url: '/api/certificate/my-certificates',
      method: 'GET',
      params: { pageNum, pageSize }
    })
  );
}

/** 本人证书文件受控读取相对路径；非持有人返回 403/9416。 */
export function getMyCertificateFileUrl(certificateId: number): string {
  return `/api/certificate/my-certificates/${certificateId}/file`;
}

/** 读取本人证书文件为二进制，供 H5 Blob 预览使用。 */
export async function getMyCertificateFile(certificateId: number): Promise<ArrayBuffer> {
  return request<ArrayBuffer>({
    url: getMyCertificateFileUrl(certificateId),
    method: 'GET',
    responseType: 'arraybuffer'
  });
}

/** 构造本人证书受控下载请求，下载前由后端校验持有人身份。 */
export function downloadMyCertificateFile(certificateId: number): { url: string; header: Record<string, string> } {
  const token = getAccessToken();
  return {
    url: `${getBaseURL()}${getMyCertificateFileUrl(certificateId)}`,
    header: token ? { Authorization: `Bearer ${token}` } : {}
  };
}
