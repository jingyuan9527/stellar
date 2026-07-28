/**
 * 统一时间格式化工具：把后端返回的多种时间形式（ISO 带 T / 空格分隔 / 带时区偏移 / 毫秒时间戳）
 * 一律解析后按用户本地时区输出为 `YYYY-MM-DD HH:mm:ss`。
 *
 * 不再用字符串截断（s.replace('T',' ').slice(0,19)）：截断式在后端改用带时区的 ISO
 * 时会原样暴露 UTC 时间，跨时区用户看到的并非本地时间；统一走 Date 解析可避免该问题。
 */
export function formatTime(input?: string | number | null): string {
  if (input == null || input === '') return ''
  // 部分引擎（旧 Safari）不识别空格分隔的日期串，统一转成 ISO 的 T 分隔再解析
  const raw = typeof input === 'string' ? input.replace(' ', 'T') : input
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => n.toString().padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}
