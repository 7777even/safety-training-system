/** 移动端 Vite 环境变量定义。 */
interface ImportMetaEnv {
  readonly VITE_USE_API_MOCK?: string;
  readonly VITE_API_BASE_URL?: string;
}

/** 移动端构建期环境变量入口。 */
interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*?url' {
  const source: string;
  export default source;
}
