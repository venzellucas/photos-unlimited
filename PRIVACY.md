# Privacy notes

Photos Unlimited is an independent Android app. It has no project-owned account or
photo-upload server and includes no advertising or analytics SDK.

## Data used by the app

- **Account credential:** exchanged with Google and encrypted with an Android
  Keystore AES-GCM key in private app storage. It is excluded from Android backup
  and is not logged. The account email is stored locally to identify the active account.
- **Photo and video files:** read from your selected folders and sent directly to
  Google's endpoints. Original embedded metadata, including location when permitted,
  is part of those bytes. Files stay on the device.
- **Upload history:** a private database tracks local media versions, outcomes,
  retries, and remote identifiers, scoped to the connected account.
- **Storage inventory:** an optional scan caches library metadata and continuation
  cursors in a separate private database. The scan itself does not download or delete media.
- **Free up storage (only when you start it):** for each item you choose, the app
  downloads the original from Google (or reads it from the phone), keeps a copy in
  `Pictures/Photos Unlimited/Pending upload/` on the phone, deletes the original in
  Google Photos, and uploads the copy again. Copies are named with the file's SHA-1 and
  a short one-way hash of the account (not the address) and are removed once Google
  confirms the replacement. A private journal records each step. If another app removes
  a phone photo involved in this, the app writes the verified copy back to its folder.
- **Profile photo:** an optional profile lookup requests read-only profile and email
  scopes separately from upload authorization. The app checks that the verified
  email matches the connected account, downloads the image from an allowed Google
  image host, and caches a small copy in private storage. A failed lookup does not
  trigger a new login or prevent backup.

Google receives requests, account identifiers, uploaded content, and ordinary
connection information under its own policies. Profile images are downloaded
without forwarding the account's authorization token to the image host.

## Sign-in and controls

The Google setup page runs in a restricted WebView with no JavaScript bridge or
file access. The app reads the setup token for exchange, not password fields, and
clears WebView cookies when leaving sign-in. A browser setup-token fallback is
available. Tokens and credentials must never be shared in issues or messages.

You choose folders and can pause backup, change network/charging preferences, or
disconnect. Disconnect removes the saved credential, profile cache, and account's
storage inventory and stops future jobs. An in-flight Google request may have
completed before cancellation. Google-side session revocation is managed in your
Google account. The local upload ledger is retained for history; clearing app data
or uninstalling removes the app's local data, except unfinished Free up copies in
`Pictures/Photos Unlimited/Pending upload/`, which stay so no photo is lost and can be
finished by a later install or removed by you.

## Website

The static website includes no analytics code or third-party fonts. GitHub Pages
provides hosting and handles its own connection logs. See
[GitHub Pages data collection](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages#data-collection).
