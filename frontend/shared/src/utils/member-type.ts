/**
 * 成员身份（INTERNAL / EXTERNAL / REGULATOR）的界面中文口径。
 * 两端的唯一来源：PC 用户管理的下拉选项与本人资料只读展示、移动端本人资料都从这里取，
 * 页面里不要再各写一份本地字典。
 */
export const MEMBER_TYPE_ENTRIES = [
  { label: '内部成员', value: 'INTERNAL' },
  { label: '外部成员', value: 'EXTERNAL' },
  { label: '监管成员', value: 'REGULATOR' }
] as const;

/**
 * 存储的成员身份编码转界面中文。
 * 未识别的编码原样保留（而不是显示成占位符），便于发现契约新增身份未被翻译。
 */
export function memberTypeLabel(value: string | null | undefined): string {
  if (!value) return '—';
  return MEMBER_TYPE_ENTRIES.find((item) => item.value === value)?.label ?? value;
}
