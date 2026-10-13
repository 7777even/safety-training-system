/**
 * 学员侧培训资料预览：按字节判定可渲染格式，再给出页面可直接使用的预览载体。
 *
 * <p>学员侧资料列表按设计**不下发文件主键与文件名**（见 `docs/api/training.openapi.json` 的
 * `TrainingLearnerMaterialVO`），移动端无法按扩展名判定格式，因此与后端一致按内容判定：
 * PDF 交给既有 PDF 组件按字节渲染（沿用共享 `PdfDocumentSource.data`），图片在本端转 Blob URL，
 * 其余格式按移动端既有能力提示不支持，不引入新的播放器或文档引擎。
 */
export type MaterialPreviewKind = 'image' | 'pdf' | 'unsupported';

/** 页面渲染一份资料预览所需的全部载体，均为本地内容，不含可直连地址。 */
export interface MaterialPreviewSource {
  kind: MaterialPreviewKind;
  /** 图片预览地址；仅 `kind === 'image'` 时有效。H5 为本地 Blob URL。 */
  imageUrl: string;
  /** PDF 字节；仅 `kind === 'pdf'` 时有效。 */
  pdfBytes: Uint8Array | null;
}

/** PDF 与常见图片的签名都落在前 12 字节内，避免为此读取整份文件之外的信息。 */
const MATERIAL_SIGNATURE_BYTES = 12;

/** 按字节头判定预览类型，与后端按内容分流的做法一致，不依赖未下发的文件名。 */
export function detectMaterialPreviewKind(bytes: ArrayBuffer): MaterialPreviewKind {
  const head = new Uint8Array(bytes, 0, Math.min(bytes.byteLength, MATERIAL_SIGNATURE_BYTES));
  if (matchesAscii(head, 0, '%PDF')) return 'pdf';
  if (matchesAscii(head, 0, 'GIF8') || matchesAscii(head, 0, 'BM')) return 'image';
  if (matchesAscii(head, 0, 'RIFF') && matchesAscii(head, 8, 'WEBP')) return 'image';
  // JPEG(FF D8 FF) 与 PNG(89 50 4E 47) 为二进制签名，单独比较字节。
  if (head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) return 'image';
  if (head.length >= 4 && head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47) return 'image';
  return 'unsupported';
}

/**
 * 把受控资料字节转换成页面可渲染的预览载体。
 *
 * <p>H5 端图片改用本地 Blob URL（`image` 标签不会携带 Bearer）；其他端沿用既有受控地址约定，
 * 由原生图片组件按端能力处理。
 */
export function buildMaterialPreviewSource(controlledUrl: string, bytes: ArrayBuffer): MaterialPreviewSource {
  const kind = detectMaterialPreviewKind(bytes);
  if (kind === 'image') {
    // #ifdef H5
    return {
      kind,
      imageUrl: globalThis.URL.createObjectURL(new globalThis.Blob([bytes], { type: 'image/*' })),
      pdfBytes: null
    };
    // #endif
    // #ifndef H5
    return { kind, imageUrl: controlledUrl, pdfBytes: null };
    // #endif
  }
  if (kind === 'pdf') {
    return { kind, imageUrl: '', pdfBytes: new Uint8Array(bytes) };
  }
  return { kind, imageUrl: '', pdfBytes: null };
}

/** 释放 H5 Blob URL，避免反复预览时保留已失效对象。 */
export function releaseMaterialPreviewSource(source: MaterialPreviewSource | null): void {
  // #ifdef H5
  if (source?.imageUrl.startsWith('blob:')) globalThis.URL.revokeObjectURL(source.imageUrl);
  // #endif
}

/** 比较指定偏移处的 ASCII 签名，签名不完整时直接判为不匹配。 */
function matchesAscii(head: Uint8Array, offset: number, signature: string): boolean {
  if (head.length < offset + signature.length) return false;
  for (let index = 0; index < signature.length; index += 1) {
    if (head[offset + index] !== signature.charCodeAt(index)) return false;
  }
  return true;
}
