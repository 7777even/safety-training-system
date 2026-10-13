/** 问卷扫码仅使用二维码库的浏览器图片生成能力。 */
declare module 'qrcode' {
  const QRCode: {
    toDataURL(text: string, options?: { width?: number; margin?: number }): Promise<string>;
  };
  export default QRCode;
}
