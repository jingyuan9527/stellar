import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupDiscreteApi } from './utils/discrete'
import { setupCrossTabSync } from './views/video/lib/sync'
import 'virtual:uno.css'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

setupDiscreteApi()
setupCrossTabSync()

app.use(router)
app.mount('#app')
