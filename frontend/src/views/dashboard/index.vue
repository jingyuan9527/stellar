<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NCard, NGrid, NGridItem, NIcon, NStatistic, NEmpty } from 'naive-ui'
import { useAuthStore } from '@/store/auth'
import { iconMap } from '@/utils/icons'
import { getAiUsageStats } from '@/api/ai'
import type { AiUsageStats } from '@/types/api'

const authStore = useAuthStore()
const stats = ref<AiUsageStats | null>(null)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const statCards = computed(() => [
  { label: '总 Token 消耗', value: stats.value?.totalTokens ?? 0, icon: 'sparkles', color: '#18a058' },
  { label: '今日 Token', value: stats.value?.todayTokens ?? 0, icon: 'info', color: '#2080f0' },
  { label: '总调用次数', value: stats.value?.totalCalls ?? 0, icon: 'grid', color: '#f0a020' },
  { label: '今日调用', value: stats.value?.todayCalls ?? 0, icon: 'list', color: '#d03050' },
])

function renderStatIcon(name: string, color: string) {
  const Icon = iconMap[name]
  if (!Icon) return null
  return h(NIcon, { size: 28, color }, { default: () => h(Icon) })
}

const maxTrendTokens = computed(() => {
  if (!stats.value?.dailyTrend?.length) return 1
  return Math.max(1, ...stats.value.dailyTrend.map((d) => d.tokens))
})

async function loadStats() {
  try {
    stats.value = await getAiUsageStats()
  } catch {
    // 错误已由拦截器提示
  }
}

onMounted(loadStats)
</script>

<template>
  <div class="dashboard">
    <NCard class="welcome-card" :bordered="false">
      <div class="welcome">
        <div class="welcome-text">
          <h2>{{ greeting }}，{{ authStore.userInfo?.nickname || '用户' }} 👋</h2>
          <p>Stellar 个人实验沉淀池 · AI token 消费看板</p>
        </div>
        <div class="welcome-icon">
          <NIcon size="64" color="#18a058">
            <component :is="iconMap.grid" />
          </NIcon>
        </div>
      </div>
    </NCard>

    <NGrid :x-gap="16" :y-gap="16" :cols="4" responsive="screen" item-responsive>
      <NGridItem
        v-for="item in statCards"
        :key="item.label"
        span="4 m:2 l:1"
      >
        <NCard :bordered="false" class="stat-card">
          <div class="stat">
            <div class="stat-icon">
              <component :is="renderStatIcon(item.icon, item.color)" />
            </div>
            <NStatistic :label="item.label" :value="item.value" />
          </div>
        </NCard>
      </NGridItem>
    </NGrid>

    <NCard title="近 7 日 Token 消耗趋势" :bordered="false">
      <div v-if="stats?.dailyTrend?.length" class="trend">
        <div v-for="d in stats.dailyTrend" :key="d.date" class="trend-item">
          <div class="trend-bar-wrap">
            <div
              class="trend-bar"
              :style="{ height: (d.tokens / maxTrendTokens * 100) + '%' }"
            ></div>
          </div>
          <div class="trend-label">{{ d.date.slice(5) }}</div>
          <div class="trend-val">{{ d.tokens }}</div>
        </div>
      </div>
      <NEmpty v-else description="暂无数据" />
    </NCard>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 22px;
}

.welcome-text p {
  margin: 0;
  color: #888;
}

.stat {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.1);
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 220px;
  padding: 0 4px;
}

.trend-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
}

.trend-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bar {
  width: 70%;
  max-width: 48px;
  background: var(--primary-color, #18a058);
  border-radius: 6px 6px 0 0;
  min-height: 2px;
  transition: height 0.3s;
}

.trend-label {
  font-size: 12px;
  opacity: 0.6;
}

.trend-val {
  font-size: 12px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .trend {
    height: 160px;
    gap: 6px;
  }
  .trend-label,
  .trend-val {
    font-size: 10px;
  }
}
</style>
