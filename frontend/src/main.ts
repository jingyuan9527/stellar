import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupDiscreteApi } from './utils/discrete'
import 'virtual:uno.css'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

setupDiscreteApi()

app.use(router)
app.mount('#app')
