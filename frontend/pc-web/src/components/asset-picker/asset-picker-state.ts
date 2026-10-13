/** 素材选择器只依赖素材主键，保留完整对象供确认时返回。 */
export interface SelectableAsset {
  assetId: number;
}

/** 合并当前页选择，单选替换、多选跨分页累加并按主键去重。 */
export function mergeAssetSelection<TAsset extends SelectableAsset>(
  current: TAsset[],
  incoming: TAsset[],
  mode: 'single' | 'multiple'
): TAsset[] {
  if (mode === 'single') return incoming.slice(-1);
  const selected = new Map(current.map((asset) => [asset.assetId, asset]));
  incoming.forEach((asset) => selected.set(asset.assetId, asset));
  return Array.from(selected.values());
}

/** 从已选素材中移除指定主键。 */
export function removeAssetSelection<TAsset extends SelectableAsset>(current: TAsset[], assetId: number): TAsset[] {
  return current.filter((asset) => asset.assetId !== assetId);
}
