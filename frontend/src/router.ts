import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import PlayerDetailPage from './PlayerDetailPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: App },
    { path: '/players/:playerId', name: 'player-detail', component: PlayerDetailPage, props: true },
  ],
})
