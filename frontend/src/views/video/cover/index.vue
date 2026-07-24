<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { NButton, NSpace, NSlider, NTabs, NTabPane } from 'naive-ui'
import CoverCanvas from './CoverCanvas.vue'
import ContentTab from './tabs/ContentTab.vue'
import BackgroundTab from './tabs/BackgroundTab.vue'
import TextStyleTab from './tabs/TextStyleTab.vue'
import EffectsTab from './tabs/EffectsTab.vue'
import ExportTab from './tabs/ExportTab.vue'
import VideoModals from '../components/VideoModals.vue'
import { useCoverStore } from '../store/cover'
import { useUIStore } from '../store/ui'
import { ratios } from '../lib/ratios'

const coverStore = useCoverStore()
const uiStore = useUIStore()

const containerRef = ref<HTMLDivElement | null>(null)
const canvasCompRef = ref<{ getEl: () => HTMLElement | null } | null>(null)
const zoom = ref(1)

const cfg = computed(() => ratios[coverStore.state.ratio])

function fit() {
  const c = containerRef.value
  if (!c) return
  const availW = c.clientWidth - 48
  const availH = c.clientHeight - 56
  if (availW <= 0 || availH <= 0) return
  const z = Math.min(availW / cfg.value.width, availH / cfg.value.height, 1)
  zoom.value = Math.max(0.1, Math.round(z * 100) / 100)
}

function getCanvasEl() {
  return canvasCompRef.value?.getEl() ?? null
}

watch(() => coverStore.state.ratio, fit, { flush: 'post' })
onMounted(fit)
</script>

<template>
  <div class="cover-page">
    <div class="toolbar">
      <NSpace size="small">
        <NButton size="small" @click="uiStore.openModal('drafts')">封面草稿</NButton>
        <NButton size="small" @click="uiStore.openModal('clear')">清空缓存</NButton>
      </NSpace>
    </div>

    <div class="cover-grid">
      <div ref="containerRef" class="preview-pane">
        <div class="zoom-toolbar">
          <NSpace size="small">
            <NButton size="small" @click="fit">适应窗口</NButton>
            <NButton size="small" @click="zoom = 1">100%</NButton>
          </NSpace>
          <NSlider
            :value="Math.round(zoom * 100)"
            :min="10"
            :max="200"
            :format-tooltip="(v: number) => `${v}%`"
            style="flex: 1; max-width: 200px"
            @update:value="(v: number) => (zoom = v / 100)"
          />
        </div>
        <div class="preview-container">
          <div :style="{ width: `${cfg.width * zoom}px`, height: `${cfg.height * zoom}px` }">
            <div :style="{ transform: `scale(${zoom})`, transformOrigin: 'center' }">
              <CoverCanvas ref="canvasCompRef" />
            </div>
          </div>
        </div>
      </div>

      <div class="tabs-pane">
        <NTabs type="line" animated>
          <NTabPane name="content" tab="内容">
            <ContentTab />
          </NTabPane>
          <NTabPane name="background" tab="背景">
            <BackgroundTab />
          </NTabPane>
          <NTabPane name="text" tab="文字">
            <TextStyleTab />
          </NTabPane>
          <NTabPane name="effects" tab="特效">
            <EffectsTab />
          </NTabPane>
          <NTabPane name="export" tab="导出">
            <ExportTab :get-canvas-el="getCanvasEl" />
          </NTabPane>
        </NTabs>
      </div>
    </div>

    <VideoModals />
  </div>
</template>

<style scoped>
.cover-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: calc(100vh - 128px);
  min-height: 400px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.cover-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 380px;
  grid-template-rows: 1fr;
  gap: 16px;
  min-height: 0;
}

.preview-pane {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  padding: 16px;
  min-height: 0;
  overflow: hidden;
  background-color: rgba(128, 128, 128, 0.04);
  background-image:
    linear-gradient(rgba(128, 128, 128, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(128, 128, 128, 0.06) 1px, transparent 1px);
  background-size: 24px 24px;
  border: 1px solid rgba(128, 128, 128, 0.12);
}

.zoom-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.preview-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  min-height: 0;
}

.tabs-pane {
  height: 100%;
  overflow-y: auto;
  min-height: 0;
  padding: 0 4px;
}

@media (max-width: 768px) {
  .cover-page {
    height: auto;
    min-height: 0;
  }
  .cover-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
  }
  .preview-pane {
    min-height: 360px;
  }
  .tabs-pane {
    height: auto;
  }
}
</style>
