export interface LoginKey {
  keyId: string
  publicKey: string
  challenge: string
  expiresAt: string
}
export interface LoginCredentials {
  username: string
  password: string
  remember?: boolean
}
export interface LoginEnvelope {
  keyId: string
  challenge: string
  encryptedKey: string
  iv: string
  ciphertext: string
}
export const encodeBase64 = (bytes: ArrayBuffer | Uint8Array) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes)))
export const decodeBase64 = (value: string) => Uint8Array.from(atob(value), (c) => c.charCodeAt(0))

export async function encryptLogin(
  key: LoginKey,
  credentials: LoginCredentials,
): Promise<LoginEnvelope> {
  if (!globalThis.crypto?.subtle) throw new Error('登录加密需要 HTTPS 或 localhost 安全环境')
  if (
    !key.keyId ||
    !key.challenge ||
    !Number.isFinite(Date.parse(key.expiresAt)) ||
    Date.parse(key.expiresAt) <= Date.now()
  )
    throw new Error('登录密钥已过期，请重新登录')
  const publicKey = await crypto.subtle.importKey(
    'spki',
    decodeBase64(key.publicKey),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const aes = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const plaintext = new TextEncoder().encode(JSON.stringify(credentials))
  const raw = await crypto.subtle.exportKey('raw', aes)
  try {
    const ciphertext = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv,
        additionalData: new TextEncoder().encode(`${key.keyId}.${key.challenge}`),
        tagLength: 128,
      },
      aes,
      plaintext,
    )
    const encryptedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, raw)
    return {
      keyId: key.keyId,
      challenge: key.challenge,
      encryptedKey: encodeBase64(encryptedKey),
      iv: encodeBase64(iv),
      ciphertext: encodeBase64(ciphertext),
    }
  } finally {
    plaintext.fill(0)
    new Uint8Array(raw).fill(0)
  }
}
