import { definePlugin } from '@halo-dev/ui-shared'
import PasskeyList from './components/PasskeyList.vue'
import { markRaw } from 'vue'
import type { UserProfileTab } from '@halo-dev/ui-shared'

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    'uc:user:profile:tabs:create': (): UserProfileTab[] => {
      return [
        {
          id: 'passkey',
          label: '通行密钥',
          component: markRaw(PasskeyList),
          priority: 30,
        },
      ]
    },
  },
})
