<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { usePasskey } from '@/composables/usePasskey'
import type { PasskeyCredential } from '@/types'
import { VButton, VCard, VEmpty, VLoading, VModal, VSpace, Dialog, Toast } from '@halo-dev/components'
import RiKey2Line from '~icons/ri/key-2-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEdit2Line from '~icons/ri/edit-2-line'
import RiShieldKeyholeLine from '~icons/ri/shield-keyhole-line'
import RiTimeLine from '~icons/ri/time-line'
import RiCheckboxCircleLine from '~icons/ri/checkbox-circle-line'

const {
  loading,
  error,
  credentials,
  isSupported,
  fetchCredentials,
  registerPasskey,
  deleteCredential,
  updateCredentialName
} = usePasskey()

const showRegisterModal = ref(false)
const showEditModal = ref(false)
const registerDisplayName = ref('')
const editingCredential = ref<PasskeyCredential | null>(null)
const editDisplayName = ref('')

const webAuthnSupported = computed(() => isSupported())

onMounted(() => {
  fetchCredentials()
})

const handleRegister = async () => {
  try {
    await registerPasskey(registerDisplayName.value || undefined)
    Toast.success('Passkey 注册成功')
    showRegisterModal.value = false
    registerDisplayName.value = ''
  } catch (e) {
    Toast.error(error.value || '注册失败')
  }
}

const handleDelete = (credential: PasskeyCredential) => {
  Dialog.warning({
    title: '确认删除',
    description: `确定要删除 Passkey "${credential.spec.displayName || '未命名'}" 吗？删除后将无法使用该 Passkey 登录。`,
    confirmText: '删除',
    cancelText: '取消',
    async onConfirm() {
      try {
        await deleteCredential(credential.metadata.name)
        // 手动从列表中移除
        const index = credentials.value.findIndex(c => c.metadata.name === credential.metadata.name)
        if (index > -1) {
          credentials.value.splice(index, 1)
        }
        Toast.success('Passkey 已删除')
      } catch {
        Toast.error(error.value || '删除失败')
      }
    },
  })
}

const handleEdit = (credential: PasskeyCredential) => {
  editingCredential.value = credential
  editDisplayName.value = credential.spec.displayName || ''
  showEditModal.value = true
}

const handleSaveEdit = async () => {
  if (!editingCredential.value) return
  try {
    await updateCredentialName(editingCredential.value.metadata.name, editDisplayName.value)
    Toast.success('已更新名称')
    showEditModal.value = false
    editingCredential.value = null
  } catch (e) {
    Toast.error(error.value || '更新失败')
  }
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '从未使用'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const getTransportLabel = (transport: string) => {
  const labels: Record<string, string> = {
    internal: '内置认证器',
    usb: 'USB',
    nfc: 'NFC',
    ble: '蓝牙',
    hybrid: '混合',
  }
  return labels[transport] || transport
}
</script>

<template>
  <div class="passkey-list">
    <!-- Not Supported Warning -->
    <div v-if="!webAuthnSupported" class="passkey-list__warning">
      <VCard title="不支持 Passkey">
        <template #description>
          您的浏览器不支持 WebAuthn/Passkey。请使用支持的现代浏览器（Chrome、Firefox、Safari、Edge）。
        </template>
      </VCard>
    </div>

    <template v-else>
      <!-- Header -->
      <div class="passkey-list__header">
        <div class="passkey-list__header-info">
          <h3 class="passkey-list__title">
            <RiShieldKeyholeLine class="passkey-list__title-icon" />
            Passkey 管理
          </h3>
          <p class="passkey-list__description">
            Passkey 是一种更安全、更便捷的登录方式，可以使用指纹、面容或设备 PIN 码进行身份验证。
          </p>
        </div>
        <VButton v-if="credentials.length > 0" type="secondary" @click="showRegisterModal = true">
          <template #icon>
            <RiKey2Line />
          </template>
          添加 Passkey
        </VButton>
      </div>

      <!-- Loading State -->
      <VLoading v-if="loading && !credentials.length" class="passkey-list__loading" />

      <!-- Error State -->
      <div v-else-if="error" class="passkey-list__error">
        <VCard title="加载失败">
          <template #description>{{ error }}</template>
          <template #actions>
            <VButton type="secondary" @click="fetchCredentials">重试</VButton>
          </template>
        </VCard>
      </div>

      <!-- Empty State -->
      <VEmpty v-else-if="!credentials.length" title="暂无 Passkey" message="添加一个 Passkey 来开启更安全的登录体验">
        <template #actions>
          <VButton type="primary" @click="showRegisterModal = true">
            <template #icon>
              <RiKey2Line />
            </template>
            添加 Passkey
          </VButton>
        </template>
      </VEmpty>

      <!-- Credentials List -->
      <div v-else class="passkey-list__items">
        <div v-for="credential in credentials" :key="credential.metadata.name" class="passkey-list__item">
          <div class="passkey-list__item-icon">
            <RiKey2Line />
          </div>
          <div class="passkey-list__item-content">
            <div class="passkey-list__item-name">
              {{ credential.spec.displayName || '未命名 Passkey' }}
            </div>
            <div class="passkey-list__item-meta">
              <span class="passkey-list__item-meta-item">
                <RiTimeLine />
                创建于 {{ formatDate(credential.spec.createdAt) }}
              </span>
              <span v-if="credential.spec.lastUsedAt" class="passkey-list__item-meta-item">
                <RiCheckboxCircleLine />
                最后使用 {{ formatDate(credential.spec.lastUsedAt) }}
              </span>
              <span v-if="credential.spec.transports?.length" class="passkey-list__item-meta-item">
                {{ credential.spec.transports.map(getTransportLabel).join(', ') }}
              </span>
            </div>
          </div>
          <div class="passkey-list__item-actions">
            <button class="action-btn" @click="handleEdit(credential)">
              <RiEdit2Line />
            </button>
            <button class="action-btn action-btn--danger" @click="handleDelete(credential)">
              <RiDeleteBinLine />
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- Register Modal -->
    <VModal v-model:visible="showRegisterModal" title="添加 Passkey" :width="480" @close="registerDisplayName = ''">
      <div class="passkey-register-modal">
        <p class="passkey-register-modal__description">
          为您的 Passkey 设置一个名称，以便于识别不同的设备或认证器。
        </p>
        <div class="passkey-register-modal__field">
          <label for="displayName">名称（可选）</label>
          <input id="displayName" v-model="registerDisplayName" type="text" placeholder="例如：MacBook 指纹、iPhone 面容"
            class="passkey-register-modal__input" />
        </div>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="showRegisterModal = false">取消</VButton>
          <VButton type="secondary" :loading="loading" @click="handleRegister">
            开始注册
          </VButton>
        </VSpace>
      </template>
    </VModal>

    <!-- Edit Modal -->
    <VModal v-model:visible="showEditModal" title="编辑 Passkey" :width="480" @close="editingCredential = null">
      <div class="passkey-register-modal">
        <div class="passkey-register-modal__field">
          <label for="editDisplayName">名称</label>
          <input id="editDisplayName" v-model="editDisplayName" type="text" placeholder="输入 Passkey 名称"
            class="passkey-register-modal__input" />
        </div>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="showEditModal = false">取消</VButton>
          <VButton type="secondary" :loading="loading" @click="handleSaveEdit">
            保存
          </VButton>
        </VSpace>
      </template>
    </VModal>
  </div>
</template>

<style lang="scss" scoped>
.passkey-list {
  padding: 1.25rem;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 1.5rem;
    gap: 1rem;

    @media (max-width: 640px) {
      flex-direction: column;
    }
  }

  &__header-info {
    flex: 1;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 1.125rem;
    font-weight: 600;
    margin: 0 0 0.5rem 0;
  }

  &__title-icon {
    width: 1.25rem;
    height: 1.25rem;
    color: #4f46e5;
  }

  &__description {
    color: #6b7280;
    font-size: 0.875rem;
    margin: 0;
  }

  &__loading {
    padding: 3rem 0;
  }

  &__warning,
  &__error {
    margin-top: 1rem;
  }

  &__items {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    margin-top: 1.5rem;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 1rem;
    background: white;
    border: 1px solid #e5e7eb;
    border-radius: 0.5rem;
    transition: all 0.2s;

    &:hover {
      border-color: #d1d5db;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
    }
  }

  &__item-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 2.5rem;
    height: 2.5rem;
    background: #eef2ff;
    border-radius: 0.5rem;
    color: #4f46e5;
    flex-shrink: 0;

    svg {
      width: 1.25rem;
      height: 1.25rem;
    }
  }

  &__item-content {
    flex: 1;
    min-width: 0;
  }

  &__item-name {
    font-weight: 500;
    color: #111827;
    margin-bottom: 0.25rem;
  }

  &__item-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 1rem;
    font-size: 0.75rem;
    color: #6b7280;
  }

  &__item-meta-item {
    display: flex;
    align-items: center;
    gap: 0.25rem;

    svg {
      width: 0.875rem;
      height: 0.875rem;
    }
  }

  &__item-actions {
    display: flex;
    gap: 0.5rem;
    flex-shrink: 0;
  }
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: white;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #d1d5db;
    color: #374151;
    background: #f9fafb;
  }

  svg {
    width: 16px;
    height: 16px;
  }

  &--danger {
    color: #dc2626;
    border-color: #fecaca;
    background: #fef2f2;

    &:hover {
      background: #fee2e2;
      border-color: #fca5a5;
    }
  }
}

.passkey-register-modal {
  &__description {
    color: #6b7280;
    font-size: 0.875rem;
    margin: 0 0 1rem 0;
  }

  &__field {
    label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: #374151;
      margin-bottom: 0.5rem;
    }
  }

  &__input {
    width: 100%;
    padding: 0.5rem 0.75rem;
    border: 1px solid #d1d5db;
    border-radius: 0.375rem;
    font-size: 0.875rem;
    transition: all 0.2s;

    &:focus {
      outline: none;
      border-color: #4f46e5;
      box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
    }

    &::placeholder {
      color: #9ca3af;
    }
  }
}
</style>
