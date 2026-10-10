# Lähteülesanne: Ioniq 5 CAN-andmete Android-rakendus

Versioon 0.1 (mustand) · 2026-10-10

## 1. Eesmärk

Teha Android-rakendus, mis ühendub Hyundai Ioniq 5 (E-GMP platvorm) diagnostika-CAN-võrku
**LELink Bluetooth OBD-II adapteri** kaudu ning näitab auto parameetreid loogiliselt
gruppidena, kaasa arvatud **päikesekatuse (solar roof)** andmeid.

Rakendus loeb andmeid ainult lugemisrežiimis. See ei kirjuta ECU-desse midagi, ei kustuta
veakoode ega muuda seadeid (vt jaotis 9).

## 2. Kasutusjuhud

1. Juht ühendab LELinki OBD-pessa, avab rakenduse, rakendus leiab adapteri ja ühendub.
2. Avalehel on kiirülevaade: SOC, aku temperatuur, võimsus (kW), kiirus, 12 V aku, sisse- ja väliskliima.
3. Kasutaja valib grupi (aku, laadimine, sõit, rehvid, kliima, päikesekatus jne) ja näeb detailandmeid.
4. Kasutaja salvestab sõidu või laadimise logi ning ekspordib selle CSV-failina.
5. Edasijõudnud kasutaja saadab diagnostikakonsoolis toorpäringu (nt `22 0101` aadressile `7E4`) ja näeb vastust.

## 3. Riistvara ja side

| Teema | Nõue / eeldus |
|---|---|
| Adapter | LELink (BLE, ELM327-ühilduv). GATT-teenus `FFE0`, karakteristik `FFE1` (notify + write). Tuleb kinnitada konkreetse mudeli peal (LELink vs LELink²). |
| Ühendus | Bluetooth Low Energy. Klassikalist Bluetoothi (SPP) toetada arhitektuuris, aga v1-s pole see kohustuslik. |
| CAN | ISO 15765-4, 11-bit ID, 500 kbit/s (`ATSP6`). |
| Protokoll | UDS teenus `0x22` (ReadDataByIdentifier). ISO-TP mitmekaadrilised vastused koostab ELM327. |
| ELM-i algväärtustus | `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP6`, `ATCAF1`, ECU vahetamiseks `ATSH<id>` ja vajadusel `ATCRA<id>`/`ATFCSH`. |
| Pollimine | Järjekord ECU-de kaupa; igal grupil oma sagedus (nt BMS 1 Hz, rehvid 0,1 Hz). Pollitakse ainult parasjagu nähtaval ekraanil olevaid gruppe, logimise ajal kõiki logitavaid. |
| Unerežiim | Kui süüde on väljas ja ECU-d ei vasta, peatab rakendus pollimise ja ei hoia autot ärkvel (12 V aku kaitse). |

## 4. Andmeallikad (ECU-d ja PID-id)

Allolev loend põhineb kogukonna teadaolevatel andmetel (EVNotify, OVMS, Car Scanneri profiilid).
**Iga aadress ja dekodeerimisvalem tuleb 1. etapis päris autoga üle kontrollida.**
PID-id ja valemid hoitakse rakenduses eraldi konfiguratsioonifailis (JSON), et neid saaks
parandada ja lisada ilma koodi muutmata.

| ECU | Päring → vastus | PID | Peamised parameetrid |
|---|---|---|---|
| BMS (aku haldus) | `7E4` → `7EC` | `220101` | BMS SOC, max laadimis- ja tühjendusvõimsus, aku vool ja pinge, moodulite temperatuurid, sisse- ja väljavoolu temperatuur, min/max elemendipinge, 12 V aku pinge, kumulatiivne laetud/tühjendatud Ah ja kWh, tööaeg, laadimise olek (AC/DC) |
| BMS | `7E4` | `220102`, `220103`, `220104`, `22010A`, `22010B`, `22010C` | Elementide pinged (elementide arv sõltub aku mahust: 58 kWh = 144, 72,6 kWh = 180, 77,4/84 kWh = 192) |
| BMS | `7E4` | `220105` | SOH, näidatav SOC, elementide pingeerinevus, lisamoodulite temperatuurid |
| BMS | `7E4` | `220106` | Jahutusvedeliku temperatuur, aku küttekeha olek |
| VCU / VMCU | `7E2` → `7EA` | `2101`, `2102` | Kiirus, käik (P/R/N/D), gaasipedaal, piduripedaal, mootori pöörded |
| Mootorikontrollerid (taga/ees) | kinnitada | kinnitada | Mootori ja inverteri temperatuurid, pöördemoment |
| Näidikuplokk (cluster) | `7C6` → `7CE` | `22B002` | Läbisõit |
| TPMS | `7A0` → `7A8` | `22C00B` | Nelja rehvi rõhk ja temperatuur |
| Kliima (FATC) | `7B3` → `7BB` | `220100` | Sisetemperatuur, välistemperatuur, ventilaator, soojuspumba olek |
| OBC / ICCU (laadija, 12 V muundur) | kinnitada | kinnitada | AC laadimise pinge ja vool, OBC temperatuur, LDC (12 V) vool |
| **Päikesekatus** | **teadmata** | **teadmata** | Toodetud võimsus (W), päeva ja koguenergia (Wh), katuse olek, kuhu energia suunatakse (12 V / kõrgepinge aku) |

### 4.1 Päikesekatuse andmed: avastusetapp

Päikesekatuse kontrolleri diagnostikaaadressi ja PID-e ei ole avalikult dokumenteeritud.
Seetõttu on 1. etapis eraldi ülesanne:

1. **ECU-skaneerimine:** saata `3E 00` (TesterPresent) või `22 F190` (VIN) igale aadressile
   vahemikus `0x700–0x7FF` ja koostada vastanud ECU-de nimekiri (sh `22 F187`/`F18C` osanumber).
2. **DID-skaneerimine:** vastanud tundmatutel ECU-del käia läbi DID-vahemikud (nt `0100–01FF`, `B000–B0FF`, `C000–C0FF`, `E000–E0FF`).
3. **Võrdlus:** salvestada vastused päikese käes ja varjus (või kaetud katusega). Baidid, mis muutuvad koos valgusega, on päikesekatuse kandidaadid.
4. **Valideerimine:** võrrelda leitud väärtusi auto infosüsteemis näidatava päikeselaadimise statistikaga.

Rakendusse ehitatakse selleks diagnostikakonsool ja skanner (jaotis 5.9), nii et avastustöö
käib sama rakendusega.

## 5. Ekraanid (loogiline grupeerimine)

1. **Ühendus:** BLE-skaneerimine, LELinki valimine, olek (ühendatud / adapter OK / auto vastab), automaatne taasühendus viimase adapteriga.
2. **Ülevaade (dashboard):** SOC (näidatav ja BMS), aku võimsus kW (+ laadib / − kulutab), aku min/max temperatuur, kiirus, 12 V pinge, sisse- ja väliskliima, päikesekatuse hetkevõimsus.
3. **Kõrgepinge aku:** SOC, SOH, pinge, vool, võimsus, max laadimis- ja tühjendusvõimsus, temperatuurid moodulite kaupa, elementide pinged soojuskaardina (min/max/erinevus esile tõstetud), kumulatiivsed Ah/kWh.
4. **Laadimine:** AC/DC olek, laadimisvõimsus, OBC andmed, laadimiskõver (kW vs SOC) laadimise ajal, laadimisseansi energia ja kestus.
5. **Sõit ja jõuülekanne:** kiirus, käik, pedaalid, mootori pöörded ja temperatuurid, hetkekulu (kWh/100 km), regeneratsioon.
6. **Rehvid:** auto skeem nelja rehvi rõhu ja temperatuuriga, hoiatus alla seatud piiri.
7. **Kliima ja soojushaldus:** sise- ja väliskliima, aku jahutusvedelik, soojuspump, aku küttekeha.
8. **Päikesekatus:** hetkevõimsus (W), tänane ja koguenergia, ajaloograafik (päev/nädal), hinnanguline lisaläbisõit (km).
9. **Diagnostika (edasijõudnud):** toorpäringu konsool (ECU aadress + päring), ECU- ja DID-skanner, päringute ja vastuste logi. Ainult lugemisteenused, vt jaotis 9.
10. **Logid:** salvestatud sõidud ja laadimised, graafikud, CSV eksport ja jagamine.
11. **Seaded:** ühikud, pollimissagedus, aku variant (elementide arv), keel (eesti/inglise), hoiatuspiirid, tume/hele teema.

Iga parameetri juures on ühik, värskendamise aeg ja märge, kui väärtus on aegunud (ECU ei vastanud).

## 6. Mittefunktsionaalsed nõuded

- **Platvorm:** Android 8.0+ (minSdk 26), targetSdk viimane stabiilne. Telefon ja tahvel, püsti- ja põikpaigutus.
- **Tehnoloogia:** Kotlin, Jetpack Compose, Coroutines/Flow, Room (logid), DataStore (seaded), Hilt (DI).
- **Arhitektuur:** kihid `transport` (BLE/SPP) → `elm327` (AT-käsud, vastuste parsimine, ISO-TP) → `protocol` (UDS, PID-konfiguratsioon, dekodeerimine) → `repository` → UI (MVVM). Transport on liidese taga, et testida ilma autota (simulaator / salvestatud vastuste taasesitus).
- **Load:** `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+), vanematel `ACCESS_FINE_LOCATION`. Taustlogimiseks foreground service koos teavitusega.
- **Töökindlus:** ühenduse katkemisel automaatne taasühendus, rakendus ei jookse kokku vigase või puuduva vastuse peale.
- **Jõudlus:** ülevaate värskendus vähemalt 1 Hz; pollimine kohandub adapteri kiirusega.
- **Keel:** kasutajaliides eesti ja inglise keeles.
- **Andmed:** kõik andmed jäävad seadmesse, internetiühendust ei ole vaja.

## 7. Testimine

- Ühiktestid iga PID-i dekodeerimisvalemile päris autost salvestatud vastuste põhjal.
- Simulaatorrežiim (võlts-transport), mis mängib salvestatud seansi uuesti läbi; sellega saab UI-d arendada ilma autota.
- Päris autoga testid: seisab, sõidab, AC-laadimine, DC-laadimine, päikese käes seismine.

## 8. Etapid

| Etapp | Sisu | Tulemus |
|---|---|---|
| 0 | Projekti alus: Gradle, Compose, arhitektuuri skelett, CI (build + testid) | Käivituv tühi rakendus |
| 1 | BLE + ELM327 kiht, diagnostikakonsool, ECU/DID-skanner, seansi salvestamine | Toorandmed autost; päikesekatuse ECU avastamine |
| 2 | PID-konfiguratsioon, dekodeerimine, BMS ja ülevaade | SOC, võimsus, temperatuurid, elementide pinged |
| 3 | Ülejäänud grupid: laadimine, sõit, rehvid, kliima, päikesekatus | Kõik ekraanid |
| 4 | Logimine, graafikud, CSV eksport, foreground service | Sõidu- ja laadimislogid |
| 5 | Viimistlus, lokaliseerimine, hoiatused, väljalase (APK / Play pood) | v1.0 |

## 9. Ohutus ja piirangud

- Lubatud ainult lugemine: UDS `0x22`, `0x3E` ja `0x19` (veakoodide lugemine, valikuline).
  Keelatud: `0x2E`, `0x2F`, `0x31`, `0x14`, `0x10` (muud kui vaikesessioon), `0x27`. Piirang on koodis, mitte ainult kasutajaliideses.
- Rakendus ei tohi segada auto tööd: pollimissageduse piir ja päringute vaheline paus.
- Kasutaja vastutus: rakendust ei kasutata sõidu ajal juhi poolt (hoiatus esmakäivitusel).

## 10. Avatud küsimused

1. Mis on "projektr5"? Kas see on konkreetne projekt, mudeliaasta, varustustase või mõni muu sõiduk?
2. Auto mudeliaasta ja aku maht (58 / 72,6 / 77,4 / 84 kWh)? Kas on Ioniq 5 N?
3. Milline LELinki mudel täpselt (LELink BLE, LELink²)?
4. Kas rakendus on isiklikuks kasutuseks (APK) või Play poodi?
5. Kas on vaja Android Auto toe või kodukoha/pilve ühendust (nt ABRP, Home Assistant / MQTT)?
6. Kas on varasemaid logisid või Car Scanneri profiile, mida saaks lähteandmetena kasutada?
7. Kas kasutajaliidese keel peab olema ainult eesti, ainult inglise või mõlemad?
