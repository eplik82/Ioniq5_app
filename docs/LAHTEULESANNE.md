# Lähteülesanne: Ioniq 5 CAN-andmete Android-rakendus

Versioon 0.6 (mustand) · 2026-10-10

## 1. Eesmärk

Teha Android-rakendus, mis ühendub Hyundai Ioniq 5 (E-GMP platvorm) diagnostika-CAN-võrku
**LELink Bluetooth OBD-II adapteri** kaudu ning näitab auto parameetreid loogiliselt
gruppidena, kaasa arvatud **päikesekatuse (solar roof)** andmeid.

**Sõiduk:** Hyundai Ioniq 5 („Projekt 45”), mudeliaasta 2022 (VIN-i 10. märk `N`), päikesekatusega.
Aku on Car Scanneri logi järgi **72,6 kWh, 180 elementi**: elemendid 1–180 näitavad ~3,76 V,
elemendid 181–192 ~0 V ning aku pinge 677–696 V ÷ 180 = 3,76–3,87 V elemendi kohta.
(Esialgne eeldus oli 77,4 kWh, kuid sellel oleks 192 töötavat elementi.) Rakendus tuvastab aku
variandi ise elementide arvu järgi ja seadetes saab seda muuta.

**Kasutus:** esialgu ainult isiklikuks kasutuseks (APK paigaldatakse ise, mitte Play poe kaudu).
Telefonirakenduse kõrval on **Android Auto** tugi (jaotis 5.1). Kasutajaliides on **eesti ja inglise keeles**.

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
| Adapter | LELink (esimene põlvkond, BLE 4.0, ELM327-ühilduv). Bluetoothi nimi on **`OBDBLE`**; rakendus otsib vaikimisi seda nime, kuid lubab valida ka mõne muu seadme. GATT-teenus `FFE0`, karakteristik `FFE1` (notify + write). UUID-d kinnitatakse 1. etapis. |
| Ühendus | Bluetooth Low Energy. Klassikalist Bluetoothi (SPP) toetada arhitektuuris, aga v1-s pole see kohustuslik. |
| CAN | ISO 15765-4, 11-bit ID, 500 kbit/s (`ATSP6`). |
| Protokoll | UDS teenus `0x22` (ReadDataByIdentifier). ISO-TP mitmekaadrilised vastused koostab ELM327. |
| ELM-i algväärtustus | `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP6`, `ATCAF1`, ECU vahetamiseks `ATSH<id>` ja vajadusel `ATCRA<id>`/`ATFCSH`. |
| Pollimine | Järjekord ECU-de kaupa; igal grupil oma sagedus (nt BMS 1 Hz, rehvid 0,1 Hz). Pollitakse ainult parasjagu nähtaval ekraanil olevaid gruppe, logimise ajal kõiki logitavaid. |
| Unerežiim | Kui süüde on väljas ja ECU-d ei vasta, peatab rakendus pollimise ja ei hoia autot ärkvel (12 V aku kaitse). |

## 4. Andmeallikad (ECU-d ja PID-id)

Allolev loend põhineb kogukonna teadaolevatel andmetel (EVNotify, OVMS, Car Scanneri profiilid).
Kasutaja Car Scanner PRO logid on peamine lähteandmestik: nende järgi kontrollitakse PID-e ja
dekodeerimisvalemeid (vt jaotis 7). Esimesest logist leitud ~410 parameetrit on loetletud failis
[`PARAMEETRID.md`](PARAMEETRID.md). Logis vastasid juhtplokid **BMS, VCMS** (laadimise juhtimine),
**ICCU** (OBC + 12 V muundur), **MCU** (mootor, õli- ja veepump), **VCS/VCU, HVAC** ja **ADAS**.
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

### 4.1 Päikesekatuse andmed

Car Scanneris on kasutaja **ise loodud** (kontrollimata) päikesekatuse PID-id:

| Parameeter | ECU | PID | Car Scanneri valem | Väärtus logis (DC-laadimine, 10.10 ~12:10) |
|---|---|---|---|---|
| Solar roof power | `7E5` → `7ED` | `22E011` | `((K*256)+L)/100` W | 150,07–150,08 W (peaaegu muutumatu: valem tõenäoliselt vale) |
| Solar to High Voltage | `7E5` | `22E011` | `Q*2` W | 112 W |
| Solar roof voltage | `7E5` | `22E011` | `((M*256)+N)/10` V | 279–303 V |

Car Scanneri tähed: `A` on esimene bait pärast vastuse SID-i ja DID-i (`62 E0 11`), seega
`K` = 11. andmebait. Rakenduse `SignalDefinition` loeb baite vastuse algusest, st `K` → `startByte = 13`.

Kontrollimine:

1. **Toorvastus:** Car Scanneri terminalis `ATSH7E5` ja `22E011`; vastus salvestada (ekraanipilt
   või tekst) päikese käes, varjus ja öösel. Võrrelda, millised baidid valgusega muutuvad.
2. **Võrdlus auto enda näiduga:** infosüsteemis näidatav päikeselaadimise statistika.
3. Kui `7E5`/`22E011` ei sobi: Bluetooth HCI snoop log, ECU-skaneerimine (`3E 00` / `22 F190`
   aadressidele `0x700–0x7FF`) ja tundmatute ECU-de DID-skaneerimine koos valguse võrdlusega.

Rakendusse ehitatakse diagnostikakonsool ja skanner (jaotis 5.9), nii et kontroll käib sama rakendusega.

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

### 5.1 Android Auto

Android Auto rakendus tehakse Google'i **Car App Library** abil. Sama andmeallikas (repository)
teenindab nii telefoni kui ka auto ekraani; BLE-ühendus ja pollimine käivad telefonis
foreground service'is.

- **Ekraanid auto ekraanil:**
  - Ülevaade: SOC, aku võimsus (kW), aku temperatuur, päikesekatuse hetkevõimsus, 12 V pinge.
  - Aku: SOC, SOH, min/max elemendipinge, temperatuurid.
  - Laadimine: võimsus, SOC, hinnanguline lõpuaeg.
  - Rehvid: nelja rehvi rõhk.
  - Päikesekatus: hetkevõimsus, tänane energia.
- **Graafiline kuva (nõue):** auto ekraanil on päris graafilised näidikud, mitte tekstiloendid.
  Selleks kasutatakse Car App Library **navigatsioonikategooriat**, mis annab joonistuspinna
  (`SurfaceCallback`). Näidikud joonistatakse ise Canvasele, nupud ja menüü on `NavigationTemplate`
  ActionStripis. See sobib isiklikuks kasutuseks; Play poodi rakendus nii ei läheks.
- **Kujundus:** sobitub Ioniq 5 enda näidikuploki ja infosüsteemi stiiliga:
  - tume taust, valged ja helehallid õhukesed kirjad, sinakas-türkiissed aktsendid;
  - „parametric pixel” motiiv (ruudukujulised pikslid) SOC-i ja võimsuse näidikutes;
  - minimalistlikud ringikujulised ja kaarnäidikud, palju tühja ruumi;
  - öö- ja päevarežiim vastavalt auto valgusandurile (Car App Library `isDarkMode`);
  - Hyundai logosid ega kaubamärke ei kasutata, ainult stiili.
  Sama kujunduskeel on ka telefonirakenduses.
- **Ekraanide paigutus:** kohandub eri suurustega auto ekraanidele; Ioniq 5 12,3" laiekraan on peamine sihtmärk.
- **Värskendus:** joonistuspinnal uuendatakse näidikuid sujuvalt (kuni ~10 kaadrit sekundis);
  andmed tulevad sama kiiresti, kui autost pollitakse. Sõidu ajal ei ole kerimist ega pikka teksti.
- **Paigaldus:** isiklikuks kasutuseks paigaldatakse APK ise ja Android Autos lülitatakse
  arendajarežiimis sisse „Unknown sources”.
- **Testimine:** Desktop Head Unit (DHU) emulaatoriga arvutis, seejärel päris autos.

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

- **Car Scanner PRO logid:** kasutaja ekspordib logid (CSV). Car Scanneris on kasutusel sisseehitatud
  ühendusprofiil „Hyundai e-GMP: IONIQ 5 (2022-2025) / IONIQ 6 / EV6 / …” ilma kohandatud PID-ideta.
  Sisseehitatud profiili PID-e Car Scanner ei ekspordi, seega võetakse logidest parameetrite
  nimed ja väärtused ning PID-id ja valemid sobitatakse nende järgi.
  Need pannakse repo kausta `testdata/carscanner/` **ilma GPS-veergudeta** (repo on avalik).
  VIN-i repo ei salvestata. Nende järgi
  - kontrollitakse, et rakenduse dekodeeritud väärtused on samad mis Car Scanneris;
  - koostatakse PID-konfiguratsiooni algversioon;
  - toidetakse simulaatorit, et arendada UI-d ilma autota.
- Ühiktestid iga PID-i dekodeerimisvalemile päris autost salvestatud vastuste põhjal.
- Simulaatorrežiim (võlts-transport), mis mängib salvestatud seansi uuesti läbi; sellega saab UI-d (ka Android Auto) arendada ilma autota.
- Päris autoga testid: seisab, sõidab, AC-laadimine, DC-laadimine, päikese käes seismine.

## 8. Etapid

| Etapp | Sisu | Tulemus |
|---|---|---|
| 0 | Projekti alus: Gradle, Compose, arhitektuuri skelett, CI (build + testid) | Käivituv tühi rakendus |
| 1 | BLE + ELM327 kiht, diagnostikakonsool, ECU/DID-skanner, seansi salvestamine | Toorandmed autost; päikesekatuse ECU avastamine |
| 2 | PID-konfiguratsioon, dekodeerimine, BMS ja ülevaade | SOC, võimsus, temperatuurid, elementide pinged |
| 3 | Ülejäänud grupid: laadimine, sõit, rehvid, kliima, päikesekatus | Kõik ekraanid |
| 4 | Logimine, graafikud, CSV eksport, foreground service | Sõidu- ja laadimislogid |
| 5 | Android Auto (Car App Library, DHU testid) | Andmed auto ekraanil |
| 6 | Viimistlus, lokaliseerimine, hoiatused, allkirjastatud APK | v1.0 |

## 9. Ohutus ja piirangud

- Lubatud ainult lugemine: UDS `0x22`, `0x3E` ja `0x19` (veakoodide lugemine, valikuline).
  Keelatud: `0x2E`, `0x2F`, `0x31`, `0x14`, `0x10` (muud kui vaikesessioon), `0x27`. Piirang on koodis, mitte ainult kasutajaliideses.
- Rakendus ei tohi segada auto tööd: pollimissageduse piir ja päringute vaheline paus.
- Kasutaja vastutus: rakendust ei kasutata sõidu ajal juhi poolt (hoiatus esmakäivitusel).

## 10. Otsused

| Küsimus | Otsus |
|---|---|
| Sõiduk | Hyundai Ioniq 5 („Projekt 45”), päikesekatusega |
| Adapter | LELink (BLE) |
| Levitamine | Isiklik kasutus, APK; Play pood pole praegu eesmärk |
| Android Auto | Jah, graafiline (navigatsioonikategooria, Surface), Ioniq 5 stiilis (jaotis 5.1) |
| Aku | 72,6 kWh, 180 elementi (Car Scanneri logi järgi); rakendus tuvastab automaatselt |
| Välisteenused | Praegu ei ole vaja (ABRP, MQTT jms) |
| Lähteandmed | Car Scanner PRO logid (sisseehitatud e-GMP profiil) |
| Kasutajaliidese keel | Eesti ja inglise |

## 11. Avatud küsimused

1. `7E5`/`22E011` toorvastused päikese käes, varjus ja öösel (jaotis 4.1).
2. Logid muudes olukordades: sõit, AC-laadimine, seismine päikese käes (süüde sees, laadimata).
