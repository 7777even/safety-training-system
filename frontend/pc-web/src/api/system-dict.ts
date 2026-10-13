import type { ApiResponse } from '@shared/types';
import { request } from './request';

/** 字典状态与 Contract 的 0 停用、1 正常保持一致。 */
export type DictStatus = 0 | 1;

/** 通用分页结果。 */
export interface DictPage<TItem> {
  list: TItem[];
  total: number;
}

/** 字典类型记录。 */
export interface DictType {
  dictTypeId: number;
  dictName: string;
  dictType: string;
  status: DictStatus;
  version: number;
  createTime?: string;
}
/** 字典数据记录。 */
export interface DictData {
  dictDataId: number;
  sortOrder?: number;
  dictLabel: string;
  dictValue: string;
  dictType: string;
  status: DictStatus;
  version: number;
  createTime?: string;
}
/** 字典目录。 */
export interface DictCatalog {
  version?: string;
  types?: Record<string, Array<{ label?: string; value?: string }>>;
}

/** 解包统一响应。 */
function unwrapResponse<TData>(response: ApiResponse<TData>): TData {
  if (response.code !== 0) throw new Error(response.message || '请求失败，请稍后重试。');
  return response.data;
}

/** 分页查询字典类型。 */
export async function getDictTypePage(params: Record<string, unknown>): Promise<DictPage<DictType>> {
  return unwrapResponse(await request<ApiResponse<DictPage<DictType>>>({ url: '/api/system/dict/types', method: 'GET', params }));
}
/** 新增字典类型。 */
export async function createDictType(data: Pick<DictType, 'dictName' | 'dictType' | 'status'>): Promise<number> {
  return unwrapResponse(await request<ApiResponse<number>>({ url: '/api/system/dict/types', method: 'POST', data }));
}
/** 修改字典类型。 */
export async function updateDictType(id: number, data: Pick<DictType, 'dictName' | 'dictType' | 'status' | 'version'>): Promise<void> {
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/system/dict/types/${id}`, method: 'PUT', data }));
}
/** 删除字典类型。 */
export async function deleteDictType(id: number): Promise<void> {
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/system/dict/types/${id}`, method: 'DELETE' }));
}
/** 分页查询字典数据。 */
export async function getDictDataPage(params: Record<string, unknown>): Promise<DictPage<DictData>> {
  return unwrapResponse(await request<ApiResponse<DictPage<DictData>>>({ url: '/api/system/dict/data', method: 'GET', params }));
}
/** 新增字典数据。 */
export async function createDictData(data: Pick<DictData, 'dictType' | 'dictLabel' | 'dictValue' | 'sortOrder' | 'status'>): Promise<number> {
  return unwrapResponse(await request<ApiResponse<number>>({ url: '/api/system/dict/data', method: 'POST', data }));
}
/** 修改字典数据。 */
export async function updateDictData(
  id: number,
  data: Pick<DictData, 'dictLabel' | 'dictValue' | 'sortOrder' | 'status' | 'version'>
): Promise<void> {
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/system/dict/data/${id}`, method: 'PUT', data }));
}
/** 删除字典数据。 */
export async function deleteDictData(id: number): Promise<void> {
  unwrapResponse(await request<ApiResponse<null>>({ url: `/api/system/dict/data/${id}`, method: 'DELETE' }));
}
/** 获取字典目录，供其他模块复用。 */
export async function getDictCatalog(): Promise<DictCatalog> {
  return unwrapResponse(await request<ApiResponse<DictCatalog>>({ url: '/api/system/dict/catalog', method: 'GET' }));
}
/** 按类型读取字典数据，供业务模块复用。 */
export async function getDictDataByType(dictType: string): Promise<DictData[]> {
  return unwrapResponse(await request<ApiResponse<DictData[]>>({ url: `/api/system/dict/data/${encodeURIComponent(dictType)}`, method: 'GET' }));
}
