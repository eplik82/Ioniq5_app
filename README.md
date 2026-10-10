# Ioniq 5 Monitor

Android-rakendus, mis loeb Hyundai Ioniq 5 andmeid LELink (ELM327, Bluetooth LE) OBD-II adapteri
kaudu ning näitab neid telefonis ja Android Autos, kaasa arvatud päikesekatuse andmeid.
Rakendus ainult loeb andmeid ega kirjuta autosse midagi.

- Lähteülesanne: [docs/LAHTEULESANNE.md](docs/LAHTEULESANNE.md)
- Car Scanneri logist leitud parameetrid: [docs/PARAMEETRID.md](docs/PARAMEETRID.md)

## Ülesehitus

| Moodul | Sisu |
|---|---|
| `core` | Puhas Kotlin: transpordi liides, ELM327 (AT-käsud, ISO-TP), UDS (ainult lugemisteenused), PID-ide dekodeerimine. Testitav ilma telefoni ja autota. |
| `app` | Android-rakendus (Jetpack Compose): kasutajaliides eesti ja inglise keeles, hiljem BLE-transport, taustteenus ja Android Auto. |

## Ehitamine

Vaja on JDK 17 ja Android SDK-d (API 35).

```
./gradlew :core:test          # tuuma testid
./gradlew :app:assembleDebug  # APK: app/build/outputs/apk/debug/
```

GitHub Actions ehitab iga push'i peale testid ja debug-APK.

**Uusim APK (püsiv link):** https://github.com/eplik82/Ioniq5_app/releases/download/apk/ioniq5-monitor.apk

Kõik ehitused on allkirjastatud sama debug-võtmega (`app/debug.keystore`), nii et uue versiooni saab
paigaldada vana peale.
