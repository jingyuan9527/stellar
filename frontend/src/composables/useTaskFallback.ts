import { onBeforeUnmount, watch, type Ref } from 'vue'
import { useAiNotifyStore } from '@/store/aiNotify'

/**
 * AI 生成任务 loading 兜底。
 *
 * 背景：图片/视频生成完成通知依赖 SSE 长连接，`generating` 只在收到 SSE 通知时复位；
 * 一旦断线或通知在重连间隙丢失，按钮会永久卡在 loading。
 *
 * 兜底策略（与 SSE 并存，SSE 正常时完全不干预）：
 * 1. 生成中若 SSE 断线（connected=false）→ 启动低频轮询，每轮先 `refresh()` 拉服务端
 *    状态再判 `isSettled()`，检测到本次任务已结束即回调 `onSettled(true)`；
 * 2. SSE 重连后停止轮询，交还给 SSE 推送；
 * 3. 无论 SSE 状态，超过 `timeoutMs` 强制复位（任务超时/兜底失败，引导用户查历史）。
 *
 * 注意：`isSettled` 必须反映**服务端最新**状态（直查单任务接口，或配合 `refresh()` 刷新
 * 本地历史后再读），不能只读过期的本地缓存，否则轮询会永远判不满、只剩超时兜底。
 */
export function useTaskFallback(opts: {
  /** 生成中标志（复位也由本函数负责置 false） */
  generating: Ref<boolean>
  /** 本次任务是否已结束（completed/failed）——建议直查服务端单任务接口 */
  isSettled: () => boolean | Promise<boolean>
  /** 检测到任务已结束 / 超时时的处理（页面负责刷历史、提示、弹抽屉） */
  onSettled: (viaFallback: boolean) => void
  /** 每轮轮询前置触发的状态刷新（重新拉取历史/状态）；若 isSettled 已直查单任务接口可省略 */
  refresh?: () => void | Promise<void>
  /** 断线轮询间隔，默认 15s */
  intervalMs?: number
  /** 强制超时兜底，默认 10 分钟 */
  timeoutMs?: number
}) {
  const aiNotifyStore = useAiNotifyStore()
  const intervalMs = opts.intervalMs ?? 15000
  const timeoutMs = opts.timeoutMs ?? 10 * 60 * 1000
  let pollTimer: number | null = null
  let timeoutTimer: number | null = null
  let polling = false

  function stopPoll() {
    if (pollTimer !== null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function stopTimeout() {
    if (timeoutTimer !== null) {
      clearTimeout(timeoutTimer)
      timeoutTimer = null
    }
  }

  function startPoll() {
    stopPoll()
    pollTimer = window.setInterval(async () => {
      // 防重入：上一轮异步刷新/判断未结束时跳过
      if (polling) return
      if (!opts.generating.value) {
        stopPoll()
        return
      }
      polling = true
      try {
        if (opts.refresh) await opts.refresh()
        if (await opts.isSettled()) {
          stopPoll()
          stopTimeout()
          opts.generating.value = false
          opts.onSettled(true)
        }
      } catch {
        // 刷新/判断异常：本轮跳过，下轮重试，不提前复位
      } finally {
        polling = false
      }
    }, intervalMs)
  }

  // SSE 连接状态变化：断线且生成中 → 兜底轮询接管；重连 → 停轮询交还 SSE
  watch(
    () => aiNotifyStore.connected,
    (connected) => {
      if (connected) {
        stopPoll()
      } else if (opts.generating.value) {
        startPoll()
      }
    },
  )

  // 生成状态变化：开始 → 若已断线立即轮询 + 启动超时兜底；结束 → 收尾
  watch(opts.generating, (generating) => {
    if (generating) {
      if (!aiNotifyStore.connected) startPoll()
      stopTimeout()
      timeoutTimer = window.setTimeout(() => {
        stopPoll()
        opts.generating.value = false
        opts.onSettled(true)
      }, timeoutMs)
    } else {
      stopPoll()
      stopTimeout()
    }
  })

  onBeforeUnmount(() => {
    stopPoll()
    stopTimeout()
  })
}
