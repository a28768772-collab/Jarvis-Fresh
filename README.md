# Jarvis Ultimate — Android starter
Android Kotlin project, voice command + Hindi TTS + explicit Accessibility-based taps and scrolls. This is a functional **starter**, not a fully autonomous agent.

## GitHub APK
1. Create a new GitHub repository.
2. Upload **contents of this ZIP** at the repository root (keep `.github/workflows/build-apk.yml`).
3. Commit to `main`. Open Actions → Build Jarvis APK → Run workflow (or wait for push).
4. Download the `Jarvis-debug-APK` artifact, unzip it, and install `app-debug.apk` on your Android phone after reviewing the permissions.

## Setup
Open Jarvis, allow microphone, tap Accessibility settings and explicitly enable Jarvis Touch Control. Press microphone and speak, or type a command.
Examples: `यूट्यूब खोलो`, `सेटिंग खोलो`, `नीचे स्क्रॉल`, `ऊपर स्क्रॉल`, `क्लिक खोजें`, `होम`, `बैक`.

## Limits / safety
- Touch actions operate only when accessibility is enabled, and depend on visible screen content; some apps block accessibility.
- This project does NOT bypass lock screens, read passwords, or automatically send messages/payments.
- Voice recognition may require internet depending on Android speech provider. Commands and TTS can work offline if device language resources are installed.
- No AI API is embedded. For a future online AI brain, use a secure server proxy and a strict allowlist of user-confirmed actions; NEVER commit API keys to GitHub or put a secret key in an APK.
- Android build uses Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and compile SDK 35.
