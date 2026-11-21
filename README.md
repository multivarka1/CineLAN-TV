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

This is a **working-source MVP, not a verified APK**. A GitHub Actions runner will download the dependencies and compile the sources. The project was generated in an environment with no Android SDK/Gradle network access, so compilation and real-device playback have **not** been verified. There may be API/dependency compatibility fixes needed based on the build log.

## Build with GitHub Actions

1. Create an empty GitHub repository, e.g. `CineLAN-TV`.
2. Upload **all files and folders** from this ZIP into the repository root (including `.github/workflows/android.yml`).
3. Go to **Actions → Android TV APK → Run workflow**, or push to `main`.
4. Open the workflow run → **Artifacts → CineLAN-TV-debug-apk**.
5. Download ZIP, extract `app-debug.apk`, install on your TV using ADB or a file manager.

Local build (with Android SDK 35, JDK 17, Gradle 8.9):

```bash
gradle :app:assembleDebug
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
