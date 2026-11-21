# CineLAN TV — Android TV + SMB + LibVLC (MVP)

A standalone Android TV app that connects to `10.0.11.1` with a username/password, browses the SMB shares `abc` and `video`, and opens video files in LibVLC.

## Already implemented

- TV launcher, landscape dark UI, D-pad focus and scrolling
- SMB2/3 login (username, password, optional domain)
- Verify access to **both** SMB shares
- Browse nested directories and filter video extensions
- Direct LibVLC playback from `smb://` URL
- Basic D-pad controls: OK pause, left/right seek ±10 s, Back exit
- GitHub Actions debug APK build and downloadable artifact
- Credentials held only in process memory, never in repository/preferences

## Important assumptions

`\\10.0.11.1\abc` and `\\10.0.11.1\video` must be **separate SMB shares**. The app treats them as `smb://10.0.11.1/abc/` and `/video/`. If those are directories in another share, adjust `SmbRepository` and the connection UI. SMB2 or SMB3 must be enabled. Your Android TV must have network access to `10.0.11.1:445` (typically within your ISP/local network).

The debug APK is built with JDK 17, Gradle 8.9 and Android SDK 35. SMB access and playback still require verification on a real Android TV connected to the provider network.

## Build with GitHub Actions

1. Create an empty GitHub repository, e.g. `CineLAN-TV`.
2. Upload **all files and folders** from this ZIP into the repository root (including `.github/workflows/android.yml`).
3. Go to **Actions → Android TV APK → Run workflow**, or push to `main`.
4. Open the workflow run → **Artifacts → CineLAN-TV-debug-apk**.
5. Download ZIP, extract `app-debug.apk`, install on your TV using ADB or a file manager.

Local build (with Android SDK 35, JDK 17, Gradle 8.9):

```bash
./gradlew :app:assembleDebug
```

`app/build/outputs/apk/debug/app-debug.apk` is the expected output.

## Security and restrictions

- No credentials are hard-coded or committed, and no password is persisted.
- LibVLC receives credentials in the SMB URL held only in memory. While convenient for MVP, URL credentials can potentially appear in diagnostic logs in underlying playback libraries. Avoid sharing diagnostic logs and consider a secure SMB-to-local-stream bridge for a production release.
- This app deliberately does not bypass provider authorization or access restrictions.
- To keep the prototype simple, targetSdk is 28 and cleartext traffic is enabled for the local-network SMB stack. Update targetSdk and network policy for production.
- Some provider streams/codecs and complex passwords may need further validation on the real TV device.
- For bundled LibVLC, follow the upstream LGPL license requirements before distribution.

## Not in MVP yet

Poster metadata, automatic classification, fast scan/caching, resume history, IPTV, subtitles/audio picker, secure persistent accounts, thumbnails, and a premium streaming UI are planned enhancements.

## Troubleshooting

- **Permission denied / STATUS_LOGON_FAILURE:** Verify account and password; if a Windows/AD domain is needed, fill in Domain.
- **STATUS_BAD_NETWORK_NAME:** `abc` / `video` might be ordinary folders rather than separate SMB shares.
- **No route / timeout:** TV and SMB server must be reachable on the same allowed network; check TCP port 445.
- **Folder opens, video does not:** SMBJ and LibVLC use separate SMB implementations. Inspect codec and LibVLC SMB auth; test a simple MP4 first.


## Install and connect

Enable installation from the chosen source on Android TV, then install the debug APK with a file manager or `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Open CineLAN TV, enter the SMB server IP (default 10.0.11.1), username, password and optional domain, then select Connect. Both `abc` and `video` must be accessible as shares.

Use the D-pad to select directories and files. During playback use OK to pause or resume, left and right to seek, and Back to exit.

## Known limitations

Real-device playback and SMB authentication have not been validated. LibVLC receives an in-memory URL containing credentials and may expose it in underlying diagnostic logs. Avoid sharing logs. Domain authentication in LibVLC, special characters in passwords, audio track and subtitle selection need device validation or implementation. The debug build is not a production release.
