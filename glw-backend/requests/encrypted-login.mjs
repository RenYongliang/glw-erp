// Node 22+; run: node requests/encrypted-login.mjs
// Optional env: API_BASE_URL, LOGIN_USERNAME, LOGIN_PASSWORD. Defaults are local mock credentials.
// The successful response includes secrets: use this script only in a private development terminal.
import { webcrypto } from 'node:crypto'
const base = process.env.API_BASE_URL || 'http://localhost:8080'
const keyResponse = await fetch(`${base}/api/users/login-key`, { cache: 'no-store' })
if (!keyResponse.ok) throw new Error(`Public key request failed: ${keyResponse.status}`)
const { data: key } = await keyResponse.json()
const subtle = webcrypto.subtle
const publicKey = await subtle.importKey('spki', Buffer.from(key.publicKey, 'base64'), { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt'])
const aes = await subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
const iv = webcrypto.getRandomValues(new Uint8Array(12))
const plain = new TextEncoder().encode(JSON.stringify({ username: process.env.LOGIN_USERNAME || 'admin', password: process.env.LOGIN_PASSWORD || 'Admin123!', remember: false }))
const ciphertext = await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: new TextEncoder().encode(`${key.keyId}.${key.challenge}`) }, aes, plain)
const raw = await subtle.exportKey('raw', aes)
const encryptedKey = await subtle.encrypt('RSA-OAEP', publicKey, raw)
plain.fill(0)
new Uint8Array(raw).fill(0)
const b64 = value => Buffer.from(value).toString('base64')
const response = await fetch(`${base}/api/users/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ keyId: key.keyId, challenge: key.challenge, encryptedKey: b64(encryptedKey), iv: b64(iv), ciphertext: b64(ciphertext) }) })
console.log(JSON.stringify(await response.json(), null, 2))
if (!response.ok) process.exitCode = 1
