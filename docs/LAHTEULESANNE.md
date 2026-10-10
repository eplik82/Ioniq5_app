# Lähteülesanne: Ioniq 5 CAN-andmete Android-rakendus

Versioon 0.12 (mustand) · 2026-10-10

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
| ICCU (OBC + 12 V muundur + **päikesekatus**) | `7E5` → `7ED` (kinnitada) | kinnitada, päikesekatus `22E011` | AC laadimise pinge ja vool, OBC temperatuur, LDC (12 V) vool, 12 V aku olek, päikesekatuse võimsus ja pinge |

### 4.1 Päikesekatuse andmed

Päikesekatuse andmed tulevad **ICCU-moodulist** (Integrated Charging Control Unit: pardalaadija,
12 V muundur ja päikesekatuse laadimine ühes plokis). ICCU diagnostikaaadress on tõenäoliselt
`7E5` (vastus `7ED`); see klapib ka kasutaja Car Scanneri päikesekatuse PID-ide päisega. Aadress
kinnitatakse Car Scanneri „ECU identifiers” vaatest või rakenduse skanneriga (`22 F190`, `22 F18C`).

Car Scanneris on kasutaja **ise loodud** (kontrollimata) päikesekatuse PID-id:

| Parameeter | ECU | PID | Car Scanneri valem | Väärtus logis (DC-laadimine, 10.10 ~12:10) |
|---|---|---|---|---|
| Solar roof power | `7E5` → `7ED` | `22E011` | `((K*256)+L)/100` W | 150,07–150,08 W (peaaegu muutumatu: valem tõenäoliselt vale) |
| Solar to High Voltage | `7E5` | `22E011` | `Q*2` W | 112 W |
| Solar roof voltage | `7E5` | `22E011` | `((M*256)+N)/10` V | 279–303 V |

Car Scanneri tähed: `A` on esimene bait pärast vastuse SID-i ja DID-i (`62 E0 11`), seega
`K` = 11. andmebait. Rakenduse `SignalDefinition` loeb baite vastuse algusest, st `K` → `startByte = 13`.

**Esimene toorvastus** (Car Scanneri terminal, 10.10 13:03, päev, pilves;
`testdata/raw/iccu_7E5_22E011_2026-10-10_1303.txt`): ICCU vastab, vastuse pikkus 49 baiti.
Rakenduse ISO-TP kokkupanek ja baitide asukohad on selle vastusega testitud (`SolarRoofSampleTest`).

| Parameeter | Baidid | Toorväärtus | Car Scanneri valemiga |
|---|---|---|---|
| Solar roof power | K, L (13–14) | `3A 4E` | 149,26 W (logis 150,07: püsib ~150 juures, võib olla piir või muu suurus) |
| Solar roof voltage | M, N (15–16) | `06 6D` | 164,5 V (logis 279–303 V) |
| Solar to High Voltage | Q (19) | `39` | 114 W (logis 112 W) |

**Auto enda näit samal ajal** (infosüsteemi „Solar roof” vaade): päikesekatuse näidik 1/3, aku 47%.
(Sama vaate „3,5 km Elektrum” on navigatsiooni kaugus lähima laadijani, mitte päikeselt saadud läbisõit:
hiljem näitas sama koht „9,9 km Alexela”.)

Kandidaadid, mida järgmiste vastustega kontrollida:

| Kandidaat | Baidid | Toorväärtus | Tõlgendus | Põhjendus |
|---|---|---|---|---|
| Päikesekatuse tase (näidik 1/3) | E, F (7–8) või 29–31 | `01` | tase 1 | Peab muutuma koos näidikuga |
| Kogunenud energia (loendur) | O, P (17–18) või R–U (20–23) | `19 91`, `7B 7F D2 51` | teadmata | Suured väärtused, võivad olla loendurid |

**Teine vastus** (`…_sample2.txt`, sama päev, pilves, **auto sõitis**) võrreldes esimesega:

| Bait | 1. → 2. | Märkus |
|---|---|---|
| J (12) | 120 → 119 | aeglane muutus (temperatuur?) |
| K, L (13–14) | 149,26 → 149,24 W | peaaegu konstantne: tõenäoliselt mitte hetkevõimsus |
| M, N (15–16) | 164,5 → 184,4 V | muutub kiiresti: tõenäoliselt päikesepaneeli pinge |
| P (18), R (20), T (22), W (25) | väikesed muutused | võivad olla vool, võimsus või temperatuurid |
| Q (19) | 114 → 114 | muutumatu |
| V (24) | 34 → 34 | muutumatu (3,5 km oli navigatsiooni kaugus, mitte selle bait) |

**Rakenduse DID-skaneering** (10.10 13:18–13:20, pilves; `testdata/raw/iccu_7E5_did-scan_E000-E0FF_2026-10-10_1320.txt`):
ICCU vastab DID-idele `E001`, `E002`, `E003`, `E011`, `E021`, `E031`, `E032`, `E033`, `E034`, `E041`;
kõigile teistele vahemikus `E000–E0FF` tuleb `7F 22 31` (requestOutOfRange).

`E011` neli vastust kõrvuti:

| Mõõtmine | J | K–L ÷100 | M–N ÷10 | O–P ÷10 | Q | R | V | W |
|---|---|---|---|---|---|---|---|---|
| 13:03 | 120 | 149,26 | 164,5 | 654,5 | 57 | 123 | 34 | 77 |
| sõit | 119 | 149,24 | 184,4 | 651,8 | 57 | 127 | 34 | 75 |
| 13:18 | 117 | 149,28 | 166,4 | 647,2 | 57 | 126 | 34 | 74 |
| 13:19 | 117 | 149,33 | 197,2 | 649,0 | 57 | 132 | 34 | 81 |

Järeldused (esialgsed):
- **O–P ÷ 10 = kõrgepinge aku pinge** (647–655 V): 180 elementi × ~3,6 V sobib SOC 47%-ga.
  Sama suurusjärk (`0x1965`, `0x1968`, `0x1995`) on ka `E001`, `E002`, `E003` vastustes.
- **M–N ÷ 10 = tõenäoliselt päikesepaneeli pinge** (164–197 V, muutub kiiresti).
- K–L püsib ~149,3 juures ega ole hetkevõimsus; Q (57) ja V (34) ei muutunud.
- `E041` on **sündmuste ajalugu läbisõiduga**: kirjed `kood(2) + läbisõit(4) × 0,1 km`, nt
  `0043 000A4CBA` = kood 0x43, 67 500,2 km (odomeeter näitas 13:27 67 533 km). Koodid 0x43 ja 0x48.
  Tõenäoliselt laadimis- või rikkesündmused, mitte päikesekatus.
- `E033` sisaldab samuti läbisõite: `00087CFD` = 55 628,5 km, `0008BC4B` = 57 249,1 km (sündmuste ajalugu).

**Auto näidud 10.10 13:23–13:27:** päikesekatuse kogutoodang (Energy information → „Total cumulative
power generation”) **421,79 kWh**; odomeeter 67 533–67 534 km; aku 38%.
Kell 13:39 näitas sama vaade **421,80 kWh**: 10 Wh 16 minutiga ehk keskmiselt ~40 W (pilves).
See kinnitab, et `E011` K–L (~149) ja Q×2 (114 W) ei ole hetkevõimsus.

Väärtust 421,79 kWh (nt 42 179, 421 790 või 4 217 900) ICCU `E0xx` vastustes **ei leidu**. Kogutoodang
võib olla mõnes teises ICCU DID-vahemikus või teises juhtplokis (nt infosüsteem või VCMS); ECU-skaneerimine on tehtud (vt `PARAMEETRID.md`).
ICCU vahemikud `0100–01FF`, `1000–10FF`, `B000–B0FF`, `C000–C0FF` ei andnud ühtegi vastust; `F1xx` sisaldab ainult
identifikaatoreid: tarkvara **NE1A039**, osa 364011XAB0, `F180` = „NEICCU50”, **valmistatud 2023-09-13**
(ICCU on vahetatud), tarkvara uuendatud 2024-09-06.
Järgmine samm: otsida 421,80 kWh infosüsteemist (`780`), näidikuplokist (`7C6`), VCU-st (`7E2`) ja BMS-ist (`7E4`).

Pilvise päeva kohta tunduvad 149 W ja 114 W liiga suured (näidik 1/3, katuse tippvõimsus ~200 W);
need ei pruugi olla hetkevõimsus. Ühe vastuse põhjal ei saa valemeid kinnitada; vaja on võrdlust
päikese käes, varjus ja öösel ning auto näidu (km, tase) üleskirjutamist iga vastuse juures.

Kontrollimine:

1. **Toorvastus:** Car Scanneri terminalis `ATSH7E5` ja `22E011`; vastus salvestada (ekraanipilt
   või tekst) päikese käes, varjus ja öösel. Võrrelda, millised baidid valgusega muutuvad.
2. **Võrdlus auto enda näiduga:** infosüsteemis näidatav päikeselaadimise statistika.
3. Kui `22E011` valemid ei sobi: ICCU DID-skaneerimine (eelkõige vahemik `E000–E0FF`, kus on ka
   teised ICCU andmed) päikese käes ja varjus; baidid, mis muutuvad valgusega, on päikesekatuse andmed.
4. Kui ka see ei anna tulemust: Bluetooth HCI snoop log Car Scanneri lugemisest.

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
