import { ChatApiError } from '../api'

/** 后端错误码 → 面向用户的中文提示。 */
const CODE_MESSAGES: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: '用户名或密码错误',
  AGENT_MAX_ITERATIONS: 'Agent 多次调用工具后仍未完成。请缩小任务范围，或换一种方式描述后重试。',
  TOOL_EXECUTION_FAILED: '工具连续执行失败。请检查工具参数、权限和服务状态后重试。',
  MODEL_TIMEOUT: '模型响应超时，请稍后重试或缩短问题。',
  MODEL_RATE_LIMIT: '模型服务当前请求过多，请稍后再试。',
  MODEL_ERROR: '模型服务暂时不可用，请稍后重试。',
  SERVER_BUSY: '服务器当前任务较多，请稍后重试。',
  INVALID_REQUEST: '请求内容不正确，请检查输入。',
  PERSISTENCE_ERROR: '回答生成成功前保存失败，请稍后重试。',
  INTERNAL_ERROR: '服务处理失败，请稍后重试。',
  EMPTY_STREAM: '服务端没有返回数据流，请检查后端服务。',
  STREAM_ERROR: '流式请求失败，请稍后重试。',
  INCOMPLETE_STREAM: '连接提前断开，已保留收到的部分回答。',
  INVALID_STREAM: '服务端返回了无法解析的流式数据。',
  HTTP_ERROR: '接口请求失败，请检查后端服务和网络连接。',
}

/**
 * 把任意异常翻译成一句可以直接展示给用户的中文。
 * 覆盖了网络断开、主动中止、后端错误码和未知异常四种情况。
 */
export function describeChatError(value: unknown): string {
  if (value instanceof ChatApiError) {
    return CODE_MESSAGES[value.code] ?? value.message
  }
  if (value instanceof DOMException && value.name === 'AbortError') {
    return '已停止生成。'
  }
  if (value instanceof TypeError) {
    return '无法连接后端服务，请确认服务已启动，并检查网络或代理配置。'
  }
  if (value instanceof Error) {
    return value.message
  }
  return '请求失败，请稍后重试。'
}
