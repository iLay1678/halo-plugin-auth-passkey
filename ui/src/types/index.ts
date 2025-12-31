export interface PasskeyCredential {
  metadata: {
    name: string
    creationTimestamp?: string
  }
  spec: {
    username: string
    credentialId: string
    publicKey: string
    signatureCount: number
    displayName?: string
    aaguid?: string
    discoverable: boolean
    userVerified: boolean
    backupEligible: boolean
    backedUp: boolean
    transports?: string[]
    createdAt: string
    lastUsedAt?: string
  }
}

export interface RegistrationOptions {
  challenge: string
  rp: {
    id: string
    name: string
  }
  user: {
    id: string
    name: string
    displayName: string
  }
  excludeCredentials: string[]
  authenticatorSelection: {
    authenticatorAttachment?: string
    residentKey: string
    userVerification: string
  }
  timeout: number
  pubKeyCredParams: Array<{
    type: string
    alg: number
  }>
}

export interface AuthenticationOptions {
  challenge: string
  rpId: string
  timeout: number
  allowCredentials: string[]
  userVerification: string
  sessionId: string
}
