# Releasing

Releases are signed APKs published as GitHub Releases, so you can install
them on a phone without the Play Store. Pushing a version tag does the work;
the steps below set it up once.

## One-time setup

### 1. Create the signing key

Every release must be signed with the same key, or phones won't accept it as
an update. Create it once, on your own machine, and choose its passwords
yourself:

```bash
keytool -genkeypair -v -keystore ~/sholto-release.jks -alias sholto -keyalg RSA -keysize 4096 -validity 10000
```

Back the file and its passwords up somewhere safe. If the key is lost, no
future release can update an installed copy. Never commit it: `*.jks` and
`keystore.properties` are in `.gitignore`.

### 2. Sign local release builds (optional)

Create `keystore.properties` in the project root:

```properties
storeFile=/Users/you/sholto-release.jks
storePassword=…
keyAlias=sholto
keyPassword=…
```

Then `./gradlew assembleRelease` writes a signed
`app/build/outputs/apk/release/app-release.apk`. Without the file, release
builds are unsigned.

### 3. Give GitHub Actions the key

Add four repository secrets (Settings → Secrets and variables → Actions, or
with `gh secret set`):

| Secret | Value |
|---|---|
| `SHOLTO_KEYSTORE_BASE64` | the key file, base64-encoded: `base64 -i ~/sholto-release.jks` |
| `SHOLTO_KEYSTORE_PASSWORD` | the keystore password |
| `SHOLTO_KEY_ALIAS` | `sholto` |
| `SHOLTO_KEY_PASSWORD` | the key password |

## Each release

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`, and
   merge to `main` as usual.
2. Tag `main` with the version name and push the tag:

   ```bash
   git tag v1.0.0 origin/main
   git push origin v1.0.0
   ```

3. The **Release** workflow checks that the tag matches `versionName`, runs the
   lint and tests, builds the signed APK and publishes it as a GitHub Release
   named after the tag.
4. On the phone, open the release page, download the APK and allow your
   browser to install apps when asked.

The release build is shrunk with R8. Before tagging, it's worth installing a
release build and running one full combined-mode workout on the bag.

## Play Store (later)

To use the Play Console's internal testing track instead, build an app bundle
with `./gradlew bundleRelease` and upload
`app/build/outputs/bundle/release/app-release.aab`. Play App Signing will ask
whether to use this key as the upload key.
