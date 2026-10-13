/** 课程学习水印的跨端统一口径：把水印文字编码成可直接用于 CSS `background-image` 的平铺图案。 */

/** 单个平铺单元的像素尺寸；移动端按同一数值换算为 rpx，实现等比缩放。 */
const TILE_WIDTH = 240;
const TILE_HEIGHT = 170;
const FONT_SIZE = 17;
const ROTATE_DEGREES = -24;
/**
 * 深色主体是主可见色：水印只落在页面留白与卡片留白这类浅色面上，
 * 深色主体在这种底色上最清晰，因此浓度要够「明显」但不至于糊住文字。
 */
const BODY_COLOR = '#0f172a';
const BODY_OPACITY = 0.32;
const OUTLINE_COLOR = '#ffffff';
/** 浅色描边只在深色底上起作用（浅色底上白描边不可见），保留它是为了万一落到深色留白时不丢字。 */
const OUTLINE_OPACITY = 0.6;
/**
 * 两行错位排布，避免单一方向的重复图案过于规律而容易被整体裁除。
 * 坐标是「旋转后外框左上角」落点，再折算回文字锚点，保证两行都不被平铺单元裁切：
 * `y = 外框顶部 + 文字宽度 × sin(24°)`（rotate(-24°) 会让文字从锚点向右上方延伸）。
 */
const ANCHOR_Y_FROM_BOX_TOP = Math.sin((Math.abs(ROTATE_DEGREES) * Math.PI) / 180);
const LINES = [
  { x: 18, y: 18 },
  { x: 128, y: 92 }
];

export type WatermarkTile = {
  /** 可直接赋给 CSS `background-image` 的数据地址。 */
  image: string;
  /** 平铺单元宽度（px），移动端可按同数值换算 rpx 缩放。 */
  width: number;
  /** 平铺单元高度（px），移动端可按同数值换算 rpx 缩放。 */
  height: number;
};

/** 随机水印最小重建间隔（毫秒）：不定时出现，与最大间隔之间均匀随机。 */
export const WATERMARK_MIN_REFRESH_INTERVAL_MS = 45_000;
/** 随机水印最大重建间隔（毫秒）。 */
export const WATERMARK_MAX_REFRESH_INTERVAL_MS = 90_000;

/** 随机水印参数区间：在固定平铺口径上随机化单元尺寸、旋转角与两行锚点。 */
const RANDOM_TILE_WIDTH_RANGE: [number, number] = [200, 280];
const RANDOM_TILE_HEIGHT_RANGE: [number, number] = [150, 200];
const RANDOM_FONT_SIZE_RANGE: [number, number] = [15, 19];
/** 基准旋转 -24°，在 ±10° 内随机，保持斜向水印的辨识度。 */
const RANDOM_ROTATE_RANGE: [number, number] = [-34, -14];
const RANDOM_ANCHOR_X_RANGE: [number, number] = [12, 48];
const RANDOM_ANCHOR_Y_RANGE: [number, number] = [14, 30];
const RANDOM_LINE_GAP_RANGE: [number, number] = [46, 70];

/** 在闭区间内取均匀随机整数。 */
function randomBetween(range: [number, number], random: () => number): number {
  return range[0] + Math.floor(Math.min(0.999999, Math.max(0, random())) * (range[1] - range[0] + 1));
}

/**
 * 生成随机化的水印平铺单元：单元尺寸、旋转角、字号与两行锚点均随机，
 * 配合不定时重建实现「不定时不定位置」的员工编号水印，使整体录屏拼接失效。
 * 文字转义与固定平铺一致；考试与课程作答页继续使用 {@link buildWatermarkTile}。
 */
export function buildRandomWatermarkTile(text: string, random: () => number = Math.random): WatermarkTile {
  const content = escapeXml(text.trim()) || '学员';
  const tileWidth = randomBetween(RANDOM_TILE_WIDTH_RANGE, random);
  const tileHeight = randomBetween(RANDOM_TILE_HEIGHT_RANGE, random);
  const fontSize = randomBetween(RANDOM_FONT_SIZE_RANGE, random);
  const rotateDegrees = randomBetween(RANDOM_ROTATE_RANGE, random);
  // 文字宽度估算与固定平铺同口径：中日韩字符按 1 个字宽、其余按 0.56 字宽。
  const textWidth = [...content]
    .reduce((total, char) => total + (/[\u4e00-\u9fa5\uff00-\uffef]/.test(char) ? 1 : 0.56), 0) * fontSize;
  const firstLineY = randomBetween(RANDOM_ANCHOR_Y_RANGE, random);
  const lines = [
    { x: randomBetween(RANDOM_ANCHOR_X_RANGE, random), y: firstLineY },
    { x: randomBetween(RANDOM_ANCHOR_X_RANGE, random), y: firstLineY + randomBetween(RANDOM_LINE_GAP_RANGE, random) }
  ];
  const rendered = lines
    .map((line) => {
      // 旋转后文字从锚点向右上方延伸，锚点纵坐标按旋转角折算，并夹在单元内避免裁切。
      const anchorY = Math.min(line.y + textWidth * Math.sin((Math.abs(rotateDegrees) * Math.PI) / 180), tileHeight - fontSize);
      const anchorText = anchorY.toFixed(1);
      return `<text x="${line.x}" y="${anchorText}" font-size="${fontSize}" font-weight="600" letter-spacing="1"`
        + ` fill="${BODY_COLOR}" fill-opacity="${BODY_OPACITY}" stroke="${OUTLINE_COLOR}" stroke-opacity="${OUTLINE_OPACITY}"`
        + ` stroke-width="1" paint-order="stroke fill" transform="rotate(${rotateDegrees} ${line.x} ${anchorText})">${content}</text>`;
    })
    .join('');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${tileWidth}" height="${tileHeight}">`
    + `<g font-family="PingFang SC, Microsoft YaHei, Helvetica Neue, Arial, sans-serif">${rendered}</g></svg>`;
  return {
    image: `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`,
    width: tileWidth,
    height: tileHeight
  };
}

/** 在重建间隔区间内取一个均匀随机的间隔（毫秒）。 */
export function nextWatermarkDelayMs(random: () => number = Math.random): number {
  const ratio = Math.min(1, Math.max(0, random()));
  return Math.round(WATERMARK_MIN_REFRESH_INTERVAL_MS + ratio * (WATERMARK_MAX_REFRESH_INTERVAL_MS - WATERMARK_MIN_REFRESH_INTERVAL_MS));
}

/** 转义 XML 保留字符，避免用户名中的 `&`、`<` 破坏 SVG 结构。 */
function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * 生成水印平铺单元的 SVG 数据地址。
 * 文字被转成背景图而不是 DOM 节点：页面与卡片留白区域随滚动长度变化，
 * 逐行铺 DOM 会让节点数量随高度增长，图片平铺则与高度无关、零布局开销。
 */
export function buildWatermarkTile(text: string): WatermarkTile {
  const content = escapeXml(text.trim()) || '学员';
  // 估算文字宽度：中日韩字符按 1 个字宽、其余按 0.56 字宽计算，用于把两行推进平铺单元内部。
  const textWidth = [...content]
    .reduce((total, char) => total + (/[\u4e00-\u9fa5\uff00-\uffef]/.test(char) ? 1 : 0.56), 0) * FONT_SIZE;
  const lines = LINES.map(
    (line) => {
      const anchorY = (line.y + textWidth * ANCHOR_Y_FROM_BOX_TOP).toFixed(1);
      return `<text x="${line.x}" y="${anchorY}" font-size="${FONT_SIZE}" font-weight="600" letter-spacing="1"`
        + ` fill="${BODY_COLOR}" fill-opacity="${BODY_OPACITY}" stroke="${OUTLINE_COLOR}" stroke-opacity="${OUTLINE_OPACITY}"`
        + ` stroke-width="1" paint-order="stroke fill" transform="rotate(${ROTATE_DEGREES} ${line.x} ${anchorY})">${content}</text>`;
    }
  ).join('');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${TILE_WIDTH}" height="${TILE_HEIGHT}">`
    + `<g font-family="PingFang SC, Microsoft YaHei, Helvetica Neue, Arial, sans-serif">${lines}</g></svg>`;
  return {
    image: `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`,
    width: TILE_WIDTH,
    height: TILE_HEIGHT
  };
}
