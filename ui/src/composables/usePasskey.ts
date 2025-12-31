import { ref } from 'vue'
import axios from 'axios'
import type { PasskeyCredential, RegistrationOptions, AuthenticationOptions } from '@/types'

const apiClient = axios.create({
  baseURL: '/apis/api.passkey.halo.run/v1alpha1',
  withCredentials: true,
})

// Base64URL encode/decode utilities
function base64UrlEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function base64UrlDecode(str: string): ArrayBuffer {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

export function usePasskey() {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const credentials = ref<PasskeyCredential[]>([])

  // Check if WebAuthn is supported
  const isSupported = (): boolean => {
    return (
      window.PublicKeyCredential !== undefined &&
      typeof window.PublicKeyCredential === 'function'
    )
  }

  // Fetch user's passkeys
  const fetchCredentials = async () => {
    loading.value = true
    error.value = null
    try {
      const response = await apiClient.get<{ credentials: PasskeyCredential[] }>('/credentials')
      // Map backend response to frontend format
      credentials.value = (response.data.credentials || []).map((cred: any) => ({
        metadata: {
          name: cred.name,
          creationTimestamp: cred.createdAt,
        },
        spec: {
          username: '',
          credentialId: cred.credentialId,
          publicKey: '',
          signatureCount: 0,
          displayName: cred.displayName,
          aaguid: '',
          discoverable: false,
          userVerified: false,
          backupEligible: false,
          backedUp: cred.backedUp || false,
          transports: cred.transports || [],
          createdAt: cred.createdAt,
          lastUsedAt: cred.lastUsedAt,
        },
      }))
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.data?.message) {
        error.value = e.response.data.message
      } else {
        error.value = e instanceof Error ? e.message : '获取凭证失败'
      }
      console.error('Failed to fetch credentials:', e)
    } finally {
      loading.value = false
    }
  }

  // Register a new passkey
  const registerPasskey = async (displayName?: string) => {
    if (!isSupported()) {
      throw new Error('WebAuthn is not supported in this browser')
    }

    loading.value = true
    error.value = null

    const origin = window.location.origin

    try {
      // Step 1: Get registration options from server
      const optionsResponse = await apiClient.post<RegistrationOptions>('/registration/options', {
        displayName,
        origin,
      })
      const options = optionsResponse.data

      // Step 2: Convert options for WebAuthn API
      const publicKeyCredentialCreationOptions: PublicKeyCredentialCreationOptions = {
        challenge: base64UrlDecode(options.challenge),
        rp: {
          id: options.rp.id,
          name: options.rp.name,
        },
        user: {
          id: base64UrlDecode(options.user.id),
          name: options.user.name,
          displayName: options.user.displayName,
        },
        pubKeyCredParams: options.pubKeyCredParams.map((param) => ({
          type: param.type as PublicKeyCredentialType,
          alg: param.alg,
        })),
        timeout: options.timeout,
        authenticatorSelection: {
          authenticatorAttachment: options.authenticatorSelection.authenticatorAttachment as AuthenticatorAttachment | undefined,
          residentKey: options.authenticatorSelection.residentKey as ResidentKeyRequirement,
          userVerification: options.authenticatorSelection.userVerification as UserVerificationRequirement,
        },
        excludeCredentials: options.excludeCredentials.map((id) => ({
          type: 'public-key' as PublicKeyCredentialType,
          id: base64UrlDecode(id),
        })),
        attestation: 'none',
      }

      // Step 3: Call WebAuthn API
      const credential = (await navigator.credentials.create({
        publicKey: publicKeyCredentialCreationOptions,
      })) as PublicKeyCredential

      if (!credential) {
        throw new Error('Failed to create credential')
      }

      const attestationResponse = credential.response as AuthenticatorAttestationResponse

      // Step 4: Send response to server
      const registrationData = {
        credentialId: base64UrlEncode(credential.rawId),
        attestationObject: base64UrlEncode(attestationResponse.attestationObject),
        clientDataJSON: base64UrlEncode(attestationResponse.clientDataJSON),
        transports: attestationResponse.getTransports?.() || [],
        displayName,
        origin,
      }

      await apiClient.post('/registration/verify', registrationData)

      // Refresh credentials list
      await fetchCredentials()

      return true
    } catch (e: unknown) {
      if (e instanceof DOMException) {
        if (e.name === 'NotAllowedError') {
          error.value = '注册已取消或被拒绝'
        } else if (e.name === 'InvalidStateError') {
          error.value = '该认证器已注册'
        } else if (e.name === 'AbortError') {
          error.value = '操作已取消'
        } else if (e.name === 'TimeoutError' || e.message.includes('timed out')) {
          error.value = '操作超时，请重试'
        } else {
          error.value = '操作失败，请重试'
        }
      } else if (axios.isAxiosError(e) && e.response?.data?.message) {
        error.value = e.response.data.message
      } else {
        error.value = e instanceof Error ? e.message : '注册失败'
      }
      console.error('Registration failed:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Authenticate with passkey
  const authenticateWithPasskey = async (username?: string) => {
    if (!isSupported()) {
      throw new Error('WebAuthn is not supported in this browser')
    }

    loading.value = true
    error.value = null

    const origin = window.location.origin

    try {
      // Step 1: Get authentication options from server
      const optionsResponse = await apiClient.post<AuthenticationOptions>('/authentication/options', {
        username,
        origin,
      })
      const options = optionsResponse.data

      // Step 2: Convert options for WebAuthn API
      const publicKeyCredentialRequestOptions: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlDecode(options.challenge),
        rpId: options.rpId,
        timeout: options.timeout,
        userVerification: options.userVerification as UserVerificationRequirement,
        allowCredentials:
          options.allowCredentials.length > 0
            ? options.allowCredentials.map((id) => ({
                type: 'public-key' as PublicKeyCredentialType,
                id: base64UrlDecode(id),
              }))
            : undefined,
      }

      // Step 3: Call WebAuthn API
      const credential = (await navigator.credentials.get({
        publicKey: publicKeyCredentialRequestOptions,
      })) as PublicKeyCredential

      if (!credential) {
        throw new Error('Failed to get credential')
      }

      const assertionResponse = credential.response as AuthenticatorAssertionResponse

      // Step 4: Send response to server for verification
      const authenticationData = {
        sessionId: options.sessionId,
        credentialId: base64UrlEncode(credential.rawId),
        authenticatorData: base64UrlEncode(assertionResponse.authenticatorData),
        clientDataJSON: base64UrlEncode(assertionResponse.clientDataJSON),
        signature: base64UrlEncode(assertionResponse.signature),
        userHandle: assertionResponse.userHandle
          ? base64UrlEncode(assertionResponse.userHandle)
          : null,
        origin,
      }

      const response = await apiClient.post('/authentication/verify', authenticationData)

      return response.data
    } catch (e: unknown) {
      if (e instanceof DOMException) {
        if (e.name === 'NotAllowedError') {
          error.value = '认证已取消或被拒绝'
        } else if (e.name === 'AbortError') {
          error.value = '操作已取消'
        } else if (e.name === 'TimeoutError' || e.message.includes('timed out')) {
          error.value = '操作超时，请重试'
        } else {
          error.value = '认证失败，请重试'
        }
      } else if (axios.isAxiosError(e) && e.response?.data?.message) {
        error.value = e.response.data.message
      } else {
        error.value = e instanceof Error ? e.message : '认证失败'
      }
      console.error('Authentication failed:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Delete a passkey
  const deleteCredential = async (name: string) => {
    loading.value = true
    error.value = null
    try {
      await apiClient.delete(`/credentials/${name}`)
      await fetchCredentials()
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.data?.message) {
        error.value = e.response.data.message
      } else {
        error.value = e instanceof Error ? e.message : '删除凭证失败'
      }
      console.error('Failed to delete credential:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  // Update passkey display name
  const updateCredentialName = async (name: string, displayName: string) => {
    loading.value = true
    error.value = null
    try {
      await apiClient.put(`/credentials/${name}`, { displayName })
      await fetchCredentials()
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.data?.message) {
        error.value = e.response.data.message
      } else {
        error.value = e instanceof Error ? e.message : '更新凭证失败'
      }
      console.error('Failed to update credential:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    credentials,
    isSupported,
    fetchCredentials,
    registerPasskey,
    authenticateWithPasskey,
    deleteCredential,
    updateCredentialName,
  }
}
