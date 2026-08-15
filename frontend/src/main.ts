import { createApp } from 'vue'
import Root from './Root.vue'
import { router } from './router'
import { initLocale } from './i18n'
import { initTheme } from './theme'
import './style.css'

initTheme()
initLocale()

createApp(Root).use(router).mount('#app')
