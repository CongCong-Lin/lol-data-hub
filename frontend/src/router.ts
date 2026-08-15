import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import PlayerDetailPage from './PlayerDetailPage.vue'
import TeamDetailPage from './TeamDetailPage.vue'
import ChampionDetailPage from './ChampionDetailPage.vue'
import MatchDetailPage from './MatchDetailPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: App },
    { path: '/players/:playerId', name: 'player-detail', component: PlayerDetailPage, props: true },
    { path: '/teams/:teamId', name: 'team-detail', component: TeamDetailPage, props: true },
    { path: '/champions/:championId', name: 'champion-detail', component: ChampionDetailPage, props: true },
    { path: '/matches/:matchId', name: 'match-detail', component: MatchDetailPage, props: true },
    /* 旧独立页面路由：重定向到首页对应视图，保持收藏夹/旧链接可用 */
    { path: '/matches', redirect: () => ({ path: '/', query: { view: 'matches' } }) },
    { path: '/compare', redirect: () => ({ path: '/', query: { view: 'compare' } }) },
    { path: '/collections', redirect: () => ({ path: '/', query: { view: 'collections' } }) },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
