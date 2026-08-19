<script setup lang="ts">
/**
 * 渲染错误边界：捕获子树渲染/生命周期异常，展示友好占位页而非白屏，
 * 并把错误上报到后端（落 sys_log 模块=前端错误）。
 * 使用：<ErrorBoundary><RouterView /></ErrorBoundary>
 */
import { onErrorCaptured, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { reportClientError } from '@/utils/clientError'

const hasError = ref(false)
const errorMessage = ref('')

// 路由切换时复位错误态：错误多由具体页面渲染引发，换页应回到正常渲染，
// 避免 ErrorBoundary 包住 RouterView 后一次渲染错误卡死整个应用的导航。
const route = useRoute()
watch(() => route.fullPath, () => {
  hasError.value = false
  errorMessage.value = ''
})

onErrorCaptured((err: unknown, _instance: unknown, info: string) => {
  hasError.value = true
  errorMessage.value = err instanceof Error ? err.message : String(err)
  reportClientError({
    message: `[组件边界] ${errorMessage.value}`,
    stack: `${err instanceof Error ? err.stack || '' : String(err)}\ninfo: ${info}`,
    source: 'boundary',
  })
  window.$message?.error('页面渲染出错，已恢复兜底视图')
  return false // 阻止继续向上传播
})

function reload() {
  location.reload()
}
</script>

<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-box">
      <div class="error-icon">⚠️</div>
      <h3>页面出错了</h3>
      <p class="error-message">{{ errorMessage }}</p>
      <p class="error-tip">错误已自动记录，刷新后重试。</p>
      <NButton type="primary" size="small" @click="reload">刷新页面</NButton>
    </div>
  </div>
  <slot v-else />
</template>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 24px;
}
.error-box {
  text-align: center;
  padding: 32px 40px;
  border-radius: 12px;
  background: var(--n-color, var(--c-fill-2));
  max-width: 480px;
}
.error-icon {
  font-size: 40px;
  margin-bottom: 12px;
}
.error-box h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.error-message {
  font-size: 13px;
  opacity: 0.8;
  word-break: break-all;
  margin: 0 0 8px;
}
.error-tip {
  font-size: 12px;
  opacity: 0.6;
  margin: 0 0 16px;
}
</style>