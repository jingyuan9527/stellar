<script setup lang="ts">
import { NButton, NResult } from 'naive-ui'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <NResult
      status="404"
      title="404"
      description="抱歉，你访问的页面不存在"
    >
      <template #footer>
        <NButton type="primary" @click="router.replace('/')">返回首页</NButton>
      </template>
    </NResult>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 60vh;
}
</style>
