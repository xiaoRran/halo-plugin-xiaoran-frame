import './styles/theme.css'
import { definePlugin } from '@halo-dev/console-shared'
import HomeView from './views/HomeView.vue'
import { IconPlug } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/xiaoran-space',
        name: 'XiaoranSpace',
        component: HomeView,
        meta: {
          title: '小染空间',
          searchable: true,
          menu: {
            name: '小染空间',
            group: '小染空间',
            icon: markRaw(IconPlug),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
