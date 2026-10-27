# Sending builds to testers (Firebase App Distribution)

Every push to `main` runs `.github/workflows/firebase-distribution.yml`:

```
git push  ->  GitHub Actions  ->  build + test + lint  ->  sign debug APK  ->  Firebase App Distribution
                                                                                   |
                                                       tester group "me"  <--------+  email with install link
```

The app does **not** use the Firebase SDK; App Distribution only needs the APK and the app id.

## One-time setup

The workflow needs three **repository secrets**. Add them at GitHub: repo > *Settings* > *Secrets and variables* >
*Actions* > *New repository secret*. Nothing secret is ever committed (`*.keystore` is git-ignored).

| Secret | What it is |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | The **whole contents** of a Firebase service-account JSON key. |
| `CI_KEYSTORE_BASE64` | The signing keystore, base64-encoded. |
| `CI_KEYSTORE_PASSWORD` | The keystore password (store and key share it). |

(The Firebase app id is not secret; it sits in the workflow file.)

### 1. Service-account key

Firebase console > *Project settings* > *Service accounts* > *Generate new private key*, or Google Cloud console >
*IAM & Admin* > *Service accounts* > *Keys*. Copy the file's contents to the clipboard and paste into the secret:

```bash
pbcopy < path/to/service-account.json
```

Prefer a **dedicated** account with only the *Firebase App Distribution Admin* role over the default
`firebase-adminsdk-...` account, which can do much more than CI needs.

### 2. Signing key

Debug builds are signed with a key that CI creates fresh on every run unless we supply one, and Android refuses to
update an installed app when the signature changes. So CI always signs with one fixed key:

```bash
keytool -genkeypair -keystore ci-debug.keystore -storetype PKCS12 -alias ci \
  -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Mock GPS Lab CI debug"
base64 -i ci-debug.keystore | pbcopy      # -> CI_KEYSTORE_BASE64
```

Use the password you chose for `CI_KEYSTORE_PASSWORD`. The alias must stay `ci` (see `app/build.gradle.kts`).
Keep a backup of the keystore outside the repo: if it is lost, generate a new one and testers must uninstall once.

### 3. Testers

Firebase console > *App Distribution* > *Testers & Groups*. The workflow sends to the group with alias `me`; add or
remove people there. A new tester gets an invitation email first and must accept it.

## What a tester does

1. Open the email on the phone, tap the link, accept the invitation (first time only).
2. Install the build. Android may ask to allow installs from the browser or the App Distribution app.
3. Mock location still needs Developer Options and *Select mock location app*; see the README's *Phone setup*.

## Good to know

- **Version code = GitHub run number** (`-PciVersionCode`), so each distributed build is newer than the last. Local
  builds stay at 1.
- **Local vs CI installs don't mix.** `./gradlew installDebug` signs with your machine's debug key, Firebase builds
  with the CI key. Switching from one to the other needs an uninstall first.
- **Manual run:** Actions tab > *Distribute to Firebase* > *Run workflow*.
- **Release notes** are the commit message of the push.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Run fails at *Check repository secrets are set* | A secret is missing or misspelled; the error names it. |
| Upload step: HTTP 403 / "permission denied" | The service account lacks the *Firebase App Distribution Admin* role (IAM). Also check the App Distribution API is enabled. |
| Build fails with a keystore / password error | `CI_KEYSTORE_BASE64` and `CI_KEYSTORE_PASSWORD` come from different keystores, or the alias is not `ci`. |
| Run is green but no email | The group alias isn't `me`, the group is empty, or the invitation was never accepted. Check spam. |
| Phone: "App not installed / package conflicts" | An app signed with another key is installed. Uninstall it, then install the Firebase build. |
