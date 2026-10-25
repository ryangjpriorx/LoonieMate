# Release signing key

`release.jks.enc` is LoonieMate's signing key, locked with AES-256 (OpenSSL, PBKDF2, 600,000 rounds).
It can only be unlocked with the `SIGNING_PASSPHRASE` secret, which lives in the repository's
GitHub Actions secrets and with the project owner, never in this repository.

Every LoonieMate release must be signed with this same key. Android only installs an update over
an existing copy when the signatures match, and the in-app updater refuses any file that isn't
signed with it.
