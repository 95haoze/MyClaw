import { ChatApiError } from './http'

/** 一个已经按 \n\n 切分并解析好的 SSE 帧。 */
export interface SseEvent {
  event: string
  data: unknown
}

/**
 * 解析单个 SSE 帧（不含结尾的空行）。
 * 注释行（以 ":" 开头）和没有 data 的帧会被忽略。
 */
function parseSseBlock(block: string): SseEvent | undefined {
  if (!block || block.startsWith(':')) return undefined

  let event = 'message'
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }

  if (!dataLines.length) return undefined

  try {
    return { event, data: JSON.parse(dataLines.join('\n')) as unknown }
  } catch {
    throw new ChatApiError('INVALID_STREAM', 'The server returned malformed streaming data.', true)
  }
}

/**
 * 消费一个 text/event-stream 响应体，逐帧回调给调用方。
 * 这里只负责"把字节流切成事件"，不关心业务语义。
 */
export async function consumeSse(
  response: Response,
  signal: AbortSignal,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  if (!response.body) {
    throw new ChatApiError('EMPTY_STREAM', 'The server returned no response stream.', true, response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (value) buffer += decoder.decode(value, { stream: !done })
    if (done) buffer += decoder.decode()
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const parsed = parseSseBlock(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      if (parsed) onEvent(parsed)
      boundary = buffer.indexOf('\n\n')
    }

    if (done) break
  }

  // 流结束但仍被中断，说明是用户主动停止或超时。
  if (signal.aborted) {
    throw new DOMException('Request stopped.', 'AbortError')
  }
}
