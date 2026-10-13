# Play kaudu paigaldamine (Android Auto jaoks)

Android Auto näitab Car App Library rakendusi (meie rakendus on navigatsioonikategoorias) ainult
siis, kui need on paigaldatud usaldusväärsest allikast ehk Play kaudu. Arendaja seade
„Unknown sources” neile ei kehti ja `adb install -i com.android.vending` ei aita: Android Auto
küsib Play teenuselt, kas rakendus tuli Play'st. Telefoni logis on siis näha:

```
CAR.VALIDATOR: Package DENIED; failed all other checks [com.eplik.ioniq5]
```

Allikas: <https://developer.android.com/training/cars/testing> („To test your app in real
vehicles, it must be installed from a trusted source”). Ülevaatust pole vaja: rakendus läheb Play
poodi ainult sinu kontole (sisemine jagamine või sisemine testimine).

## Mis on valmis

- **AAB:** iga push'i peale ehitab CI Play jaoks allkirjastatud faili. Püsiv link:
  <https://github.com/eplik82/Ioniq5_app/releases/download/apk/ioniq5-monitor.aab>
- **Üleslaadimisvõti:** `C:\Claude\Ioniq5_keys\upload.jks`, parool samas kaustas failis
  `PAROOL.txt`. CI saab need GitHubi saladustest (`UPLOAD_KEYSTORE_BASE64`,
  `UPLOAD_KEYSTORE_PASSWORD`). Võti ei ole repos. Kui see kaob, saab Play Console'is
  üleslaadimisvõtme lähtestada.
- **Sihttase API 36** (Play nõue alates 31.08.2026).
- **Privaatsuspoliitika:** <https://github.com/eplik82/Ioniq5_app/blob/HEAD/docs/PRIVAATSUSPOLIITIKA.md>
- Versiooninumber kasvab iga CI ehitusega ise (`versionCode` = ehituse number).

## 1. Rakenduse loomine Play Console'is

<https://play.google.com/console> → **Loo rakendus**:

- Nimi: `Ioniq 5 Monitor` (sisemises testis nime ei kontrollita)
- Vaikekeel: eesti
- Rakendus, tasuta
- Nõustu tingimustega → **Loo rakendus**

## 2. Kiire proov: sisemine rakenduse jagamine (internal app sharing)

Sellega näeb kõige kiiremini, kas Android Auto rakendust usaldab.

1. Play Console'is vasakul **Testimine → Sisemine rakenduse jagamine** (Internal app sharing).
   Lisa üleslaadijaks oma e-posti aadress ja luba allalaadimine kõigile, kellel on link.
2. Laadi üles `ioniq5-monitor.aab` (link ülal) ja kopeeri saadud link.
3. **Telefonis:** Play pood → profiilipilt → Seaded → Teave → toksa 7 korda real
   „Play poe versioon”. Seejärel Seaded → Üldine → lülita sisse **„Sisemine rakenduse jagamine”**.
4. **Eemalda praegune Ioniq5 Monitor.** Play allkirjastab rakenduse teise võtmega, nii et vana
   peale see ei paigaldu. Salvestused ja seaded lähevad kaduma.
5. Ava link telefonis ja paigalda.
6. Android Auto seaded → **Kohanda käivitajat**: Ioniq5 Monitor peaks olema loendis.

## 3. Püsiv lahendus: sisemine testimine (internal testing)

Uuendused tulevad Play poe kaudu nagu tavalisel rakendusel.

1. **Testimine → Sisemine testimine → Testijad**: loo loend ja lisa oma Google'i konto e-post.
2. **Loo väljalase**. Esimesel korral küsitakse **Play rakenduste allkirjastamist** (Play App
   Signing): vali Google'i loodud võti ja nõustu.
3. Laadi üles `ioniq5-monitor.aab` → **Salvesta** → **Vaata üle** → **Alusta levitamist**.
4. Kui Play Console nõuab enne levitamist rakenduse sisu deklaratsioone
   (**Poliitika → Rakenduse sisu**), siis vastused:
   - Privaatsuspoliitika: link ülal
   - Rakendusele juurdepääs: kõik funktsioonid on saadaval ilma sisselogimiseta
   - Reklaamid: ei
   - Sisu hinnang: küsimustik, kõigile vastus „ei” (tööriist / utiliit)
   - Sihtrühm: 18+
   - Andmeohutus: rakendus **ei kogu ega jaga** andmeid (kõik jääb telefoni, internetiluba pole)
   - Reklaami-ID: ei kasuta
   - Esiplaaniteenus (`connectedDevice`): rakendus loeb ühendatud OBD-II Bluetooth-adapterist
     auto andmeid ka siis, kui ekraan on kustunud. Kui küsitakse videot, piisab lühikesest
     ekraanisalvestusest, kus rakendus on adapteriga ühendatud ja teavitusribal on laetus.
5. Kopeeri **testijate link** („Liitu Androidis”), ava see telefonis, nõustu testimisega ja
   paigalda rakendus Play poest.

Uue versiooni jaoks: Sisemine testimine → **Loo uus väljalase** → laadi üles uusim
`ioniq5-monitor.aab`. Telefon uuendab rakenduse Play kaudu.

## Pärast Play paigaldust

- **Ära paigalda enam debug-APK-d** (`ioniq5-monitor.apk`) Play versiooni peale: allkirjad ei
  klapi ja paigaldus ebaõnnestub. Debug-APK on edaspidi ainult arendamiseks.
- Kui rakendus ikka Android Autos ei ilmu, ühenda telefon arvutiga ja vaata logi:
  `adb logcat | grep CAR.VALIDATOR`, samal ajal ava telefonis „Kohanda käivitajat”.
