<script setup lang="ts">
import { NButton, NResult, NIcon } from 'naive-ui'
import { useRouter } from 'vue-router'
import { iconMap } from '@/utils/icons'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="nf-card">
      <div class="nf-glow nf-glow-a" />
      <div class="nf-glow nf-glow-b" />
      <div class="nf-inner">
        <NResult status="404" title="404" description="抱歉，你访问的页面不存在">
          <template #footer>
            <div class="nf-actions">
              <NButton type="primary" round @click="router.replace('/')">
                <template #icon><NIcon><component :is="iconMap.home" /></NIcon></template>
                返回首页
              </NButton>
              <NButton round quaternary @click="router.back()">返回上一页</NButton>
            </div>
          </template>
        </NResult>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 16px;
}

/* 与落地页 Hero 同款视觉语言：渐变底 + 品牌辉光 */
.nf-card {
  position: relative;
  overflow: hidden;
  width: 100%;
  max-width: 560px;
  border-radius: var(--r-xl);
  border: 1px solid var(--c-border);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--c-brand) 10%, var(--c-fill)),
    color-mix(in srgb, var(--c-info) 10%, var(--c-fill))
  );
  padding: 56px 32px;
}

.nf-inner {
  position: relative;
  z-index: 1;
}

.nf-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(48px);
  opacity: 0.5;
}

.nf-glow-a {
  width: 260px;
  height: 260px;
  top: -120px;
  right: -80px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-brand) 30%, transparent), transparent 70%);
}

.nf-glow-b {
  width: 220px;
  height: 220px;
  bottom: -110px;
  left: -70px;
  background: radial-gradient(circle, color-mix(in srgb, var(--c-info) 30%, transparent), transparent 70%);
}

.nf-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .nf-card {
    padding: 40px 20px;
  }
}
</style>