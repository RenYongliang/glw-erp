// Offline demo server simulation only. Real backend private keys never enter the browser.
import {
  encodeBase64,
  decodeBase64,
  type LoginEnvelope,
  type LoginCredentials,
} from './loginEncryption.ts'
let keys: Promise<CryptoKeyPair> | undefined
let keyId = ''
const challenges = new Map<string, number>()
function pair() {
  return (keys ??= crypto.subtle.generateKey(
    {
      name: 'RSA-OAEP',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256',
    },
    false,
    ['encrypt', 'decrypt'],
  ))
}
export async function mockLoginKey() {
  keyId ||= crypto.randomUUID()
  for (const [challenge, end] of challenges) if (end <= Date.now()) challenges.delete(challenge)
  if (challenges.size >= 1000) throw new Error('Too many challenges')
  const challenge = crypto.randomUUID(),
    end = Date.now() + 120000
  challenges.set(challenge, end)
  return {
    keyId,
    challenge,
    expiresAt: new Date(end).toISOString(),
    publicKey: encodeBase64(await crypto.subtle.exportKey('spki', (await pair()).publicKey)),
  }
}
export async function mockDecryptLogin(body: LoginEnvelope): Promise<LoginCredentials> {
  const end = challenges.get(body.challenge)
  challenges.delete(body.challenge)
  if (body.keyId !== keyId || !end || end <= Date.now()) throw new Error('Invalid challenge')
  const raw = await crypto.subtle.decrypt(
    { name: 'RSA-OAEP' },
    (await pair()).privateKey,
    decodeBase64(body.encryptedKey),
  )
  try {
    const aes = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['decrypt'])
    const plaintext = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: decodeBase64(body.iv),
        additionalData: new TextEncoder().encode(`${body.keyId}.${body.challenge}`),
        tagLength: 128,
      },
      aes,
      decodeBase64(body.ciphertext),
    )
    try {
      return JSON.parse(new TextDecoder().decode(plaintext))
    } finally {
      new Uint8Array(plaintext).fill(0)
    }
  } finally {
    new Uint8Array(raw).fill(0)
  }
}
