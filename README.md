
Encrypted messenger with self-destructing messages and notes.
No email. No phone number. No cloud accounts.

---

## HOW IT WORKS

Void creates your identity entirely on your device. A cryptographic key
pair is generated and stored in your device's hardware-backed secure
storage. Your public key becomes your permanent Display ID. Your private
key never leaves your device.

You choose a username. This is how other people find you. Your Display ID
is how they verify it is really you.

There is no signup. No registration. No server-side accounts. If you lose
your device and your recovery phrase, your identity is gone forever. That
is by design.

---

## IDENTITY AND KEY MANAGEMENT

Void uses a hybrid cryptographic wallet-style architecture for identity
management.

Your identity is an ECDH P-256 key pair generated on-device and stored
in the Android Keystore. This is the same elliptic curve cryptography
used by modern cryptocurrency wallets.

Your 12-word recovery phrase is generated using the BIP39 standard.
BIP39 is the same mnemonic encoding used by Bitcoin and other
cryptocurrency wallets to back up and restore private keys. Your 12 words
encode 128 bits of entropy plus a checksum, allowing you to recover your
full identity on any device.

This means:

- Your identity is a cryptographic key, not an account.
- Your recovery phrase is a standard BIP39 mnemonic.
- Your Display ID is derived from your public key using SHA-256.
- No server ever sees your private key or recovery phrase.

If you have used a cryptocurrency wallet, you already understand how
Void works. Your identity is your wallet. Your recovery phrase is your
seed. Lose them, and access is gone forever.

---

## ENCRYPTION

Every message is end-to-end encrypted.

When you start a chat, both devices generate an elliptic curve key pair
(ECDH P-256). The public keys are exchanged through the server. Each
device then derives a shared AES-256 key using Elliptic Curve
Diffie-Hellman. This key never touches the server.

Messages are encrypted with AES-256-GCM before leaving your device. The
server only ever sees ciphertext. It cannot read your messages.

Self-destructing notes use a random AES-256 key generated on the sender's
device. The encrypted note is stored on the server. The decryption key is
shared as part of a short code. Once the recipient reads the note, it is
deleted from the server.

---

## ADDING CONTACTS

To start a chat, you need the other person's Display ID. This is a
19-character identifier in the format:

    XXXX-XXXX-XXXX-XXXX

You enter their Display ID. The app creates an encrypted channel. No
username search. No contact discovery. You must know who you are
talking to.

Always verify the Display ID matches the person you intend to chat with.
Usernames can be changed. Display IDs are permanent.

---

## SELF-DESTRUCTING MESSAGES

Messages can be set to self-destruct after being read. Timer options
range from 5 seconds to 5 minutes. Once the timer expires, the message
is deleted from both devices and from the server.

Self-destructing notes work differently. You create a note, set it to
self-destruct after reading, and receive a short code. Give this code
to the recipient. They open it on the website. The note is shown once,
then destroyed.

---

## BACKUP AND TRANSFER

You can transfer your identity to a new device using a 6-digit transfer
code. The old device encrypts your identity with the code and uploads it
temporarily. The new device enters the code and retrieves it. The
transfer code expires after 10 minutes.

You can also export an encrypted backup file to your device storage,
protected by a password you choose.

A 12-word BIP39 recovery phrase is shown once when you create your
identity. Write it down. Store it safely. This is the same standard used
by cryptocurrency wallets. It is the only way to recover your identity
if all other methods fail. Void cannot recover it for you.

---

## PRIVACY

Void does not collect your email address, phone number, name, location,
or any personal information. There are no analytics. No advertising.
No tracking.

The server stores encrypted message payloads, your chosen username, and
your public key. It does not store message history in readable form. It
does not know who you are.

---

## LICENSE

GNU General Public License v3.0

---

    Nothing stays.
    Nothing lingers.
    Nothing remains.

    Void.
