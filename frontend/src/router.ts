import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import PlayerDetailPage from './PlayerDetailPage.vue'
import TeamDetailPage from './TeamDetailPage.vue'
import ChampionDetailPage from './ChampionDetailPage.vue'
import MatchesPage from './MatchesPage.vue'
import MatchDetailPage from './MatchDetailPage.vue'
import PlayerComparePage from './PlayerComparePage.vue'
import CollectionsPage from './CollectionsPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: App },
    { path: '/players/:playerId', name: 'player-detail', component: PlayerDetailPage, props: true },
    { path: '/teams/:teamId', name: 'team-detail', component: TeamDetailPage, props: true },
    { path: '/champions/:championId', name: 'champion-detail', component: ChampionDetailPage, props: true },
    { path: '/matches', name: 'matches', component: MatchesPage },
    { path: '/matches/:matchId', name: 'match-detail', component: MatchDetailPage, props: true },
    { path: '/compare', name: 'player-compare', component: PlayerComparePage },
    { path: '/collections', name: 'collections', component: CollectionsPage },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
