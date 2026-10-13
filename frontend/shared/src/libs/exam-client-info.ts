/** 考试客户端信息；只存类型摘要，不保存完整 User-Agent。 */
export interface ExamClientInfo<TPlatform extends string = string> { platform: TPlatform; operatingSystem: string; browser: string }
/** 从显式传入的客户端标识解析操作系统与浏览器，不依赖跨端运行环境。 */
export function examClientInfo<TPlatform extends string>(platform: TPlatform, userAgent: string): ExamClientInfo<TPlatform> {
  const operatingSystem = /Android/i.test(userAgent) ? 'Android' : /iPhone|iPad|iPod/i.test(userAgent) ? 'iOS' :
    /Windows/i.test(userAgent) ? 'Windows' : /Macintosh|Mac OS X/i.test(userAgent) ? 'macOS' : /Linux/i.test(userAgent) ? 'Linux' : '未知';
  const browser = /MicroMessenger/i.test(userAgent) ? '微信' : /Edg\//i.test(userAgent) ? 'Edge' : /Firefox\//i.test(userAgent) ? 'Firefox' :
    /Chrome\//i.test(userAgent) ? 'Chrome' : /Safari\//i.test(userAgent) ? 'Safari' : '未知';
  return { platform, operatingSystem, browser };
}
