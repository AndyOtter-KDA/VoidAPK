# Void - Privacy-First Encrypted Messenger

Void is a modern, privacy-centric communication platform designed to securely facilitate direct, end-to-end (E2E) encrypted conversations and ephemeral notes. Built directly on cryptographic principles, Void removes the necessity of standard trust anchors—eliminating logins, server-managed accounts, tracking, and the collection of personally identifiable information.

---

## Technical Architecture

### How It Works
Void facilitates identity management entirely on the edge (the local physical device):
- **On-Device Key Generation:** A cryptographic key pair (ECDH P-256) is generated dynamically when you launch the application. This key pair is securely kept inside the hardware-backed Android Keystore.
- **Display ID Generation:** Your public key is hashed using SHA-256 to derive a permanent 19-character identifier formatted as: `XXXX-XXXX-XXXX-XXXX`. This Display ID serves as your unique handshake address.
- **BIP39 Recovery Phrase:** An offline 12-word BIP39 mnemonic recovery phrase is constructed on-device using 128 bits of entropy plus a checksum. This recovery phrase is the single authority required to rebuild your private identity on another device.
- **Zero-Account Registry:** Because no cloud-managed directories or standard user profiles exist on a server, if you lose your physical device and your BIP39 phrase, your identity is lost forever by design.

---

### End-To-End (E2E) Encryption
Void provides uncompromising communication security using modern hybrid cryptography:
- **Diffie-Hellman Key Exchange:** When establishing a communication terminal with another node, both devices securely exchange ECDH P-256 public keys. Using these keys, each node locally derives a shared symmetric key without ever transmitting it across the network.
- **Symmetric Cipher:** Every text payload is encrypted locally with AES-256-GCM before transport.
- **Zero-Knowledge Transport:** The server layer functions exclusively as a dumb relay for ciphertext, possessing zero access to private keys or cleartext payloads.

---

### Local Persistence & Room Database
All active communications, temporary message history, local contacts list, and configuration secrets are retained locally using a securely encrypted relational SQLite abstraction layer powered by **Jetpack Room Database**. 

---

## Core Features

- **No Personal Identifiers:** Zero onboarding fields. No emails, phone numbers, location metadata, or server-side account registries are used.
- **Self-Destructing Messages:** Real-time messages can be configured to self-destruct after they are read. Customizable intervals range from 5 seconds to 5 minutes, completely wiping logs from both devices and database records.
- **Self-Destructing Notes:** Ephemeral notes are encrypted locally with a single-use key. The cipher content is dispatched to the server, and the symmetric key is wrapped in a high-privacy short code. Once the recipient uses the short code, the cleartext is destroyed from memory and the ciphertext is purged from the database.
- **Peer-to-Peer Adding:** Initiate conversations directly. You must input the recipient's exact 19-character Display ID to begin a secure handshake. No automated user discovery or contact syncing takes place.
- **Transfer and Backup:** Migrate your secure key pair to a new terminal using a temporary 6-digit transfer code (which encrypts and uploads your identifier with a 10-minute TTL) or via an encrypted, password-protected local backup file.

---

## Proprietary Service & Infrastructure Notice

> [!NOTE]
> **Firebase Proprietary Notice:** The cloud notification and message synchronization relay infrastructure for Void is built using **Google Firebase** (including Cloud Firestore and Firebase Cloud Messaging), which is a proprietary, closed-source cloud service. However, because all message contents, identities, and notes are cryptographically sealed with AES-256-GCM on-device *prior* to server ingress, no entity (including Google, the server operator, or developers) can read your communication payloads.

---

## License

Void is distributed as open-source software under the terms of the **GNU General Public License v3.0**. See the accompanying license files for details.

---

*Nothing stays. Nothing lingers. Nothing remains.*

**Void.**
