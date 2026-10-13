/** PC/H5共用原生SSE读取；令牌只在Authorization头中传输，组件离开必须调用返回的清理函数。 */
export function subscribeLearnerMessages(url: string, token: string, changed: () => void, failed: (message: string) => void): () => void {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  let resume: (() => void) | undefined;
  /** 断线等待可被退出立即打断，不留下后台重连。 */
  async function reconnect(): Promise<void> {
    await new Promise<void>(resolve => { resume = resolve; timer = setTimeout(resolve, 3000); });
    timer = undefined; resume = undefined;
  }
  /** 流式读取保留半帧与UTF-8分片，SSE注释仅作为心跳。 */
  async function run(): Promise<void> {
    while (!controller.signal.aborted) {
      try {
        const response = await fetch(url, { headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }, signal: controller.signal });
        if (response.status === 401 || response.status === 403) { failed('登录或访问权限已失效，请重新登录'); return; }
        if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('实时连接暂不可用，仍可手动刷新消息');
        const reader = response.body.getReader(); const decoder = new TextDecoder(); let pending = '';
        try {
          while (!controller.signal.aborted) {
            const { value, done } = await reader.read();
            if (done) break;
            pending = (pending + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n');
            let boundary: number;
            while ((boundary = pending.indexOf('\n\n')) >= 0) {
              const frame = pending.slice(0, boundary); pending = pending.slice(boundary + 2);
              if (frame.split('\n').some(line => /^event:\s*messages$/.test(line))) changed();
            }
          }
        } finally { reader.releaseLock(); }
      } catch (error) {
        if (!controller.signal.aborted) failed(error instanceof Error ? error.message : '实时连接暂不可用，仍可手动刷新消息');
      }
      if (!controller.signal.aborted) await reconnect();
    }
  }
  void run();
  return () => { controller.abort(); if (timer) clearTimeout(timer); resume?.(); };
}
