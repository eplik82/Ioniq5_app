# Lähteülesanne: Ioniq 5 CAN-andmete Android-rakendus

Versioon 0.24 · 2026-10-12

## 1. Eesmärk

Teha Android-rakendus, mis ühendub Hyundai Ioniq 5 (E-GMP platvorm) diagnostika-CAN-võrku
**LELink Bluetooth OBD-II adapteri** kaudu ning näitab auto parameetreid loogiliselt
gruppidena, kaasa arvatud **päikesekatuse (solar roof)** andmeid.

**Sõiduk:** Hyundai Ioniq 5 („Projekt 45”), mudeliaasta 2022 (VIN-i 10. märk `N`), **nelikvedu (AWD, kaks mootorit)**, päikesekatusega.
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
| TPMS | `7A0` → `7A8` (vastas ECU-skaneeringus) | `22C00B` (kinnitada) | Nelja rehvi rõhk ja temperatuur, andurite patarei/olek |
| Tagumine mootor (MCU) | `7E3` → `7EB` | kinnitada | Pöörded, pöördemoment, faasivool, mootori ja inverteri temperatuur |
| Eesmine mootor | kinnitada (kandidaadid `7E6`, `733`, `744`) | kinnitada | Pöörded, pöördemoment, temperatuurid |
| Pidurisüsteem (IEB/ESC) | `7D1` → `7D9` | kinnitada | Rataste kiirused (4×), pidurirõhk |
| Roolivõimendi (MDPS) / ADAS | `7D4` → `7DC` / `7C4`, `7D0` | kinnitada | Roolinurk, rooli pöördemoment, külg- ja pikikiirendus, pöördekiirus (yaw) |
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
Edasised skaneeringud (`testdata/raw/did-scans_2026-10-10_1346.txt`):

| ECU | Vahemik | Tulemus |
|---|---|---|
| `780` infosüsteem | `0100–01FF` | ei ühtegi vastust |
| `7E2` VCU | `0100–01FF` | ei ühtegi vastust (VCU kasutab tõenäoliselt KWP `21xx`) |
| `7C6` näidikuplokk | `B000–B0FF` | `B000`–`B004`; **`B002` baidid 9–11 = odomeeter** (0x0107E9 = 67 561 km) ✓ |
| `7E4` BMS | `0100–01FF` | `0101–0108`, `010A–010C`, `0111`, `0114`, `0130–0138`, `01F2`; **`0101` baidid 41–44 ja 45–48 = kumulatiivne laetud/tühjendatud energia × 0,1 kWh** (9 903,5 / 9 207,6 kWh, klapib Car Scanneriga) ✓ |

Päikesekatuse kogutoodangut (421,80 kWh) ei leitud üheski vastuses. Tõenäoliselt arvutab selle
infosüsteem CAN-siini teadetest ja seda ei saa diagnostikapäringuga lugeda. Lahendus: **rakendus arvutab
ise** päikeseenergia, integreerides hetkevõimsust aja järgi, ning kasutaja saab seadetes sisestada
auto näidu algväärtuseks.

Hetkevõimsuse kandidaat `E011`-st: M–N (paneeli pinge, V) × R (×0,001 A?) annab 20–26 W, mis on
samas suurusjärgus kui kogutoodangu järgi arvutatud keskmine ~40 W. Kinnitamiseks on vaja öist mõõtmist.

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

### 4.2 Rehvid, rool ja mootorid (avastusskaneering 10.10 14:19–14:28)

Fail: `testdata/raw/discovery-stage3_2026-10-10_1420.txt` (kaks skaneeringut: rool otse ja keeratud).

| Andmed | ECU | PID | Baidid ja valem | Kontroll |
|---|---|---|---|---|
| **Rehvirõhud** FL, FR, RL, RR | `7A0` | `22C00B` | 7, 12, 17, 22 × 0,2 psi | 36,2–36,6 psi = 2,50–2,52 bar ✓ |
| **Rehvide temperatuurid** | `7A0` | `22C00B` | 8, 13, 18, 23 − 50 °C | 19–21 °C ✓ |
| **Roolinurk** | `7D4` | `220101` | 10–11 märgiga × 0,1° | −10,5° → 428,2° rooli keerates ✓; positiivne = vasakule ✓ |
| Roolinurk (sama väärtus) | `7D1` | `220104` | 34–35 märgiga × 0,1° | klapib `7D4`-ga ✓ |

Ei leitud: tagamootor `7E3` ei vasta `0100–01FF` ega KWP `21xx` päringutele; `7E6` `01Cx–01Dx` on
seistes kõik nullid (võib olla esimootor). `733` ja `744` ei vasta. Järgmine avastusring (rakenduses
„Avasta (3. etapp)”) otsib `7E3`/`7E6` vahemikest `E000–E0FF` ja `2000–20FF`, VCU-st, pidurisüsteemist,
roolivõimendist, esikaamerast ja radarist. Rataste kiirused ja kiirendused tuleb leida sõidu ajal
(kaasreisija käivitab skaneeringu).

### 4.2a Pidurisüsteem (ESC `7D1` `220104`), sõidulogid 2026-10-11

Kaks sõidulogi (`testdata/raw/drive-log_2026-10-11_1648.txt`, `drive-log_2026-10-11_1703.txt`)
sõidu, pöörete ja foori taga seismisega. Baidid loetakse 0x62 baidist:

| Baidid | Väärtus | Tähendus | Olek |
|---|---|---|---|
| 9–10 | / 256 km/h | sõiduki kiirus (71,26 km/h; võrdne rataste ja kliimamooduli kiirusega) | kasutusel |
| 13, 14, 15, 16 | km/h | rataste kiirused VE, PE, VT, PT (pöördes välimised kiiremad, nt 51/50/50/49 paremale) | kasutusel |
| 7–8 | ~315 × km/h | kiirusega võrdeline, ühik teadmata | kandidaat |
| 41–42 | 0 sõidus, 1420…3710 seistes | piduri rõhk × 0,01 bar? (regeneratsioonil −24 kW oli 0: pidurdus ainult mootoriga) | kandidaat |
| 44–45 | +42 kiirendusel, −32…−38 aeglustusel | pikikiirendus | kandidaat, kalibreerida GPS-iga |
| 37–38, 39–40 | märk sama mis roolinurgal (+43/+45 vasakpöördes) | pöörlemiskiirus ja külgkiirendus | kandidaat, kalibreerida GPS-iga |

Tagamootori pööretest arvutatud kiirus oli ~3 % suurem kui rataste kiirus (73,6 vs 71 km/h).
Rakendus kasutab nüüd ESC kiirust ja arvutab pööretest ainult siis, kui ESC ei vasta.

### 4.2b Päikesekatuse võimsus (pime mõõtmine ilma laadimiseta, 10.10 20:41)

Fail: `testdata/raw/iccu_night_no-charging_2026-10-10_2041.txt`.

| `E011` bait | Päeval (pilves) | AC-laadimise ajal | Pimedas, ei lae |
|---|---|---|---|
| W (25) | 74–81 | 38 | 6 |
| M–N (15–16) ÷ 10 | 164–197 | 95,5 | 275,8 |
| O–P (17–18) ÷ 10 | 647–655 V | 733,4 V | 729,7 V (= aku pinge ✓) |

- **Päikese võimsus = W × 0,5 W** (kandidaat): päeval 37–40 W, mis klapib auto kogutoodangu kasvuga
  (10 Wh / 16 min ≈ 37,5 W); pimedas 3 W (müra). Kinnitada päikesepaistel, kus võimsus peaks olema 100–200 W.
- M–N ei ole paneeli pinge (pimedas suurem kui päeval); tähendus teadmata.
- Auto kogutoodangut (421,8 kWh) diagnostika kaudu ei leitud; rakendus integreerib võimsust ühenduse ajal.

### 4.3 Pardalaadija (OBC) AC-laadimisel (10.10 20:32, pime, auto lukus)

Fail: `testdata/raw/iccu_night_ac-charging_2026-10-10_2032.txt`. Auto laadis AC-ga ~10 kW (BMS −13,3 A, 734,8 V, SOC 85,5 %).

| Andmed | ECU | PID | Baidid ja valem | Kontroll |
|---|---|---|---|---|
| AC faasivoolud L1, L2, L3 | `7E5` | `22E002` | 14–15, 16–17, 18–19 × 0,01 A | 15,82 / 15,82 / 15,58 A |
| AC pinge | `7E5` | `22E002` | 30–31 × 0,01 V | 228,6 V |
| OBC DC pinge | `7E5` | `22E002` | 22–23 × 0,1 V | 734,4 V = BMS ✓ |
| OBC DC vool | `7E5` | `22E002` | 24–25 × 0,01 A | 13,35 A = BMS ✓ |

AC sisend ~10,8 kW, DC väljund 9,8 kW, kasutegur ~91 %.

Päikesekatus pimedas (sama mõõtmine): `E011` M–N = 95,5 V (päeval 164–197 V), R 36 (päeval 123–132),
W 38 (päeval 74–81); O–P = 733,4 V = aku pinge ✓. Kuna ICCU samal ajal laadis, ei ole see puhas
võrdlus: vaja on pimedat mõõtmist ilma laadimiseta.

### 4.4 Kliima, temperatuurid ja soojendused (avastamata)

Car Scanneri HVAC-profiil luges selles autos sise- ja välistemperatuuri, aurusti temperatuuri,
õhuniiskust ja puhuritemperatuure (vt `PARAMEETRID.md` jaotis HVAC), seega on andmed diagnostikaga
loetavad. Aadress ja PID-id tuleb oma logist kinnitada: kogukonna järgi on kliima ECU `7B3`
(`220100`, `220102`), kuid skaneerimisel see `3E 00`-le ei vastanud. Võimalik, et see vastab ainult
siis, kui süüde on sees.

| Andmed | Oletatav allikas | Märkus |
|---|---|---|
| Salongi temperatuur, välistemperatuur | kliima `7B3` `220100` | Car Scanner: sees 22,5…23 °C, väljas 11 °C |
| Aurusti temperatuur, niiskus, puhuritemperatuurid | kliima `7B3` `220100`/`220102` | |
| Seatud temperatuur (juht/kõrvalistuja), AUTO/A/C/ringlus, puhuri kiirus, õhu suund, soojuspumba režiim | kliima `7B3` | avastada |
| Istmete soojendus/ventilatsioon (tase 0–3, iga iste) | istmemoodul `706` (88540-GI200)? kliima `7B3`? | avastada |
| Roolisoojendus | IBU/BCM `7A0` või `770`, rool `7D4`? | avastada |
| Tagaklaasi ja peeglite soojendus, esiklaasi soojendus/defroster | IBU/BCM `7A0`/`770`, kliima `7B3` | avastada |
| Akende asend (avatud/suletud) | IBU `770`/`7A0`, uksemoodulid | avastada, kui loetav |

**Sõidulogi 2026-10-11 16:48 (süüde sees, ~2 min sõitu 12–74 km/h):** `7B3` vastab süüte ajal
nii `220100` kui ka `220102` päringule (`testdata/raw/drive-log_2026-10-11_1648.txt`).
`220100` baidid (loendus 0x62 baidist):

| Bait | Toores | Väärtus | Tähendus | Olek |
|---|---|---|---|---|
| 8 | `7F` | / 2 − 40 = 23,5 °C | salongi temperatuur | kasutusel |
| 9 | `69` | 12,5 °C | välistemperatuur | kinnitatud (kasutaja võrdles auto näiduga 2026-10-11) |
| 10 | `5A`/`5B` | 5…5,5 °C | aurusti temperatuur (Car Scanner 3…6,5) | kasutusel |
| 4, 30, 31 | `94`, `93`, `96` | 34; 33,5; 35 °C | puhurite temperatuurid (juht, jalad) | kandidaat |
| 29 | `7E` | 23 °C | salong ees | kandidaat |
| 28 | `36`→`35` | 54→53 | õhuniiskus %? | kandidaat |
| 32 | `47`…`00` | 71…0 km/h | sõidukiirus (järgis mootori pöörete kiirust) | kasutusel |

`220102` bait 7 (`9E`→`9A`) ja bait 15 (`2D`→`2A`) langesid sõidu ajal aeglaselt. Tähendus on veel
teadmata. Istmete, rooli, klaaside ja peeglite soojenduse andmeid selles logis polnud, sest
lülitusi ja märkeid ei tehtud.

**Kliima avastusskaneering 2026-10-12 17:09 (süüde sees, `testdata/raw/climate-scan_2026-10-12_1709.txt`):**

| ECU | Vastavad DID-id |
|---|---|
| `7B3` kliima | `0100`, `0101`, `0102`, `01A0`, `01A1`, `01A2`, `01A3` |
| `7A0` kere (BCM/IBU) | `B001`–`B012` (18 tk; `B003` sisaldab rehvide andmeid) |
| `770` IBU | `BC01`, `BC03`–`BC09`, `BC10` |
| `706`, `7A1` | ei vastanud |

**Kliima arenduslogi märgetega 2026-10-12 18:02–18:09** (`testdata/raw/climate-devlog_2026-10-12_1802.txt`, GPS eemaldatud):

| Märge | Leitud | Olek |
|---|---|---|
| roolisoojendus | `770` `22BC08` bait 8 bit 6 (`40`) | kasutusel |
| tagaklaasi soojendus | `770` `22BC03` bait 10 bit 1 (`02`); Hyundai lülitab sellega ka peeglid | kasutusel |
| temp 22 / 23,5 / 20 | `7B3` `2201A0` baidid 59 (juht) ja 60 (kõrvalistuja): `10` / `13` / `0C` → 14 + bait × 0,5 °C | kasutusel |
| esiklaasi soojendus | `7B3` `2201A0` bait 5 bit 0 oli 1 ~10 s pärast märget | kandidaat |
| siseõhk | `7B3` `2201A1` bait 16 `01` → `00` | kandidaat |
| puhuri kiirus | `7B3` `2201A2` baidid 20, 22, 24 (väärtused 0–4) | kandidaat |
| istmete soojendus 3-2-1, ventilatsioon | neis 34 päringus muutust polnud | otsida 2. ringi skaneeringuga |

Istmete jaoks on **„Avasta kliima”** nüüd 2. ring: `706` (`22 B0xx`, `22 C0xx`, `21 xx`), `7A6`, `7A3`,
`7A0 22 C0xx`, `770 22 C0xx`. Välisõhu niiskust autol pole (ainult salongi niiskusandur esiklaasil).

Diagnostika nupp **„Kliima arenduslogi”** loeb ainult neid 34 päringut (tsükkel ~4 s), et soojenduste ja
kliima lülitite mõju oleks märgete järgi selgelt näha.

**Avastamise käik (diagnostika → arendajalogi):** süüde sees, auto paigal. Logi algab kõigi ülaltoodud
ECU-de `22xxxx` lugemisega. Seejärel lülitatakse **ükshaaval** sisse ja välja: roolisoojendus,
juhi istme soojendus (tasemed 1–3), kõrvalistuja iste, tagaistmed, istme ventilatsioon, tagaklaasi
(ja peeglite) soojendus, esiklaasi defroster, A/C, ringlus, AUTO, temperatuuri muutmine 0,5 °C
kaupa. Iga lülituse kohta pannakse arendajalogisse märge („rool ON”). Muutunud baidid leitakse
märgete järgi samamoodi nagu päikesekatuse ja rehvide puhul.

Rakendus ainult **loeb** soojenduste ja kliima olekut. Kliimat, soojendusi ega aknaid rakendusest
juhtida ei saa (jaotis 9).

## 5. Ekraanid (loogiline grupeerimine)

1. **Ühendus:** BLE-skaneerimine, LELinki valimine, olek (ühendatud / adapter OK / auto vastab), automaatne taasühendus viimase adapteriga.
2. **Ülevaade (dashboard):** SOC (näidatav ja BMS), aku võimsus kW (+ laadib / − kulutab), aku min/max temperatuur, kiirus, 12 V pinge, sisse- ja väliskliima, päikesekatuse hetkevõimsus.
3. **Kõrgepinge aku:** SOC, SOH, pinge, vool, võimsus, max laadimis- ja tühjendusvõimsus, temperatuurid moodulite kaupa, elementide pinged soojuskaardina (min/max/erinevus esile tõstetud), kumulatiivsed Ah/kWh.
4. **Laadimine:** AC/DC olek, laadimisvõimsus, OBC andmed, laadimiskõver (kW vs SOC) laadimise ajal, laadimisseansi energia ja kestus.
5. **Sõit ja jõuülekanne:** kiirus, käik, pedaalid, mootori pöörded ja temperatuurid, hetkekulu (kWh/100 km), regeneratsioon.
6. **Rehvid ja sillad:** auto **pealtvaade** (kere kontuur, esi- ja tagasild, neli ratast), kõik sildadega
   seotud andmed joonise peal oma kohal:
   - iga ratta juures: rehvirõhk (bar / kPa / psi), rehvi temperatuur, ratta kiirus; hoiatus, kui rõhk
     on alla seatud piiri või rehvide vahe on liiga suur (värviline ratas);
   - esirattad pööratakse joonisel **roolinurga** järgi, roolinurk ka numbrina;
   - iga silla juures: mootori võimsus (kW), pöördemoment (Nm), pöörded, mootori ja inverteri temperatuur;
   - **veojaotus** esi- ja tagasilla vahel (riba ja protsent), arvutatuna kahe mootori pöördemomendist;
   - auto keskel: külg- ja pikikiirendus (g-punkt) ja pöördekiirus;
   - kokku võimsus ja regeneratsioon (negatiivne võimsus teise värviga).
   Värskendus sõidu ajal vähemalt 2 Hz (rool, kiirendused, võimsus), rehvid 0,1 Hz.
   Märkus: BMS-i `0101` „mootor 2” pöörded olid ~115 km/h juures 0, kuigi tagamootor tegi 8766 p/min.
   Kui see on esimootor, on see ühenduse katkestusega (eesmine mootor sõidab vabalt); kontrollida
   kiirendamisel, kui esimootor peab rakenduma.
   **Kinnitatud 2026-10-11:** 25–74 km/h sõidul (ka 44 kW kiirendusel ja −24 kW regeneratsioonil) oli
   „mootor 2” 0 p/min. 12 km/h juures oli see 911 ja tagamootor 910 p/min. Esimootor (vedav sild
   ees) on püsikiirusel lahti ühendatud (EV disconnector) ja ühendub aeglaselt sõites.
7. **Kliima ja soojendused** (vahekaart „Kliima”, teostatud 2026-10-12):
   - **temperatuurid:** salongi temperatuur, välistemperatuur (suurelt), seatud temperatuur juhil ja
     kõrvalistujal, puhurite väljundtemperatuurid, aurusti temperatuur, salongi õhuniiskus;
   - **kliima režiim:** AUTO / käsitsi, A/C sees/väljas, õhuringlus (värske/ringlus), puhuri kiirus,
     õhu suund (nägu/jalad/klaas), soojuspump (küte/jahutus/väljas), eelkliima (kaugjuhtimine);
   - **soojenduste olek salongi pealtvaate joonisel** (sama stiil nagu rehvide vaates): iga istme
     soojendus ja ventilatsioon tasemega 0–3 (pikslitulbad, soojendus oranž ja ventilatsioon sinine),
     roolisoojendus rooli ümber, esiklaasi ja tagaklaasi soojendus, külgpeeglite soojendus, akende
     asend (kui loetav);
   - **soojushaldus:** aku jahutusvedeliku ja sisendi temperatuur, aku küttekeha, mootorite jahutusvedelik;
   - **graafik:** sise- ja välistemperatuur ajas (nt eelkliima või sõidu algusest).
   Ülevaatesse lisatakse salongi ja välistemperatuur ning väikesed ikoonid sisselülitatud
   soojenduste kohta.
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
  - Rehvid ja sillad: sama pealtvaate joonis lihtsustatult (rehvirõhud, roolinurk, veojaotus, võimsus).
  - Päikesekatus: hetkevõimsus, tänane energia.
  - Kliima: salongi ja välistemperatuur, kliima režiim ning soojenduste olek salongi pealtvaatel
    (istmed, rool, klaasid, peeglid).
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
- **Teostus (etapp 5):** `car/MonitorCarAppService` → `DashboardScreen` (NavigationTemplate) + `CarRenderer`
  (SurfaceCallback, joonistab 2× sekundis, kui ekraan on nähtav) + `CarDrawing` (Canvas). Lehed: Ülevaade,
  Aku (180 elemendi ruudustik pinge järgi värvitud), Rattad (pealtvaade, rõhud, temperatuurid, roolinurk,
  mootorite pöörded, rataste kiirused), Laadimine (võimsus, SOC, aeg 80%-ni), Päike (hetkevõimsus
  pikslinäidikuga, ühenduse ajal kogutud energia, ICCU kõrgepinge), Kliima (salongi pealtvaade soojendustega
  nagu telefonis, salongi- ja välistemperatuur, niiskus, seatud temperatuurid, aurusti, puhurid). Ülevaates on
  ka salongi- ja välistemperatuur. Navigatsioonimalli tööriistaribal tohib olla vaid üks tekstiga nupp, seega
  vahetab üks nupp lehti ringiratast. Paigutus on kontrollitud DHU-s Ioniq 5 ekraani suurusega 1920×720.
  Kliima joonis: kui telefonis on fail `Android/data/com.eplik.ioniq5/files/cabin.png` (Ioniq 5 salong
  pealtvaates, 994×496, esiots vasakul), joonistatakse soojendused selle foto peale; muidu kasutatakse
  vektorjoonist. Fotot repos ega APK-s ei ole (Hyundai pilt, repo on avalik), see pannakse telefoni adb-ga. Auto ekraani avamisel ühendutakse viimase adapteriga, kui telefonis
  ühendust pole.

## 6. Mittefunktsionaalsed nõuded

- **Platvorm:** Android 8.0+ (minSdk 26), targetSdk viimane stabiilne. Telefon ja tahvel, püsti- ja põikpaigutus.
- **Tehnoloogia:** Kotlin, Jetpack Compose, Coroutines/Flow. Salvestused CSV-failidena (otse eksporditavad, Room-i asemel), seaded SharedPreferences'is, sõltuvused käsitsi (ilma Hiltita).
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
| 3 | Ülejäänud grupid: laadimine, sõit, **rehvid ja sillad**, **kliima ja soojendused** (jaotis 4.4), päikesekatus (sh PID-ide avastamine TPMS-ist, mootoritest, pidurisüsteemist ja roolivõimendist) | Kõik ekraanid |
| 4 | Logimine, graafikud, CSV eksport, foreground service | Sõidu- ja laadimislogid |
| 5 | Android Auto (Car App Library, DHU testid) | Andmed auto ekraanil |
| 6 | Viimistlus, lokaliseerimine, hoiatused, allkirjastatud APK | v1.0 |

## 9. Ohutus ja piirangud

- Lubatud ainult lugemine: UDS `0x22`, `0x3E` ja `0x19` (veakoodide lugemine, valikuline).
  Keelatud: `0x2E`, `0x2F`, `0x31`, `0x14`, `0x10` (muud kui vaikesessioon), `0x27`. Piirang on koodis, mitte ainult kasutajaliideses.
- Rakendus ei tohi segada auto tööd: pollimissageduse piir ja päringute vaheline paus.
- Kasutaja vastutus: rakendust ei kasutata sõidu ajal juhi poolt (hoiatus esmakäivitusel).

### 9.1 Hoiatused (etapp 6)

Hoiatusi hindab `core/alert/AlertMonitor` igal uuel näidul. Igal hoiatusel on hüsterees, et see ei
vilguks piiri lähedal. Üle 10 min vanad väärtused hoiatuse olekut ei muuda.

| Hoiatus | Sisse | Välja | Raskus |
|---|---|---|---|
| Madal rehvirõhk (iga ratas) | < seadetes piir (vaikimisi 2,2 bar) | ≥ piir + 0,05 bar | hoiatus; kriitiline < piir − 0,3 |
| Rehvirõhkude erinevus | > 0,3 bar | < 0,25 bar | info |
| 12 V aku | < 12,0 V | ≥ 12,2 V | hoiatus; kriitiline < 11,8 V |
| Aku kuum (max temperatuur) | ≥ 45 °C | < 43 °C | hoiatus; kriitiline ≥ 50 °C |
| Aku külm (min temperatuur) | ≤ 0 °C | > 2 °C | info |
| Elementide pingevahe | ≥ 80 mV (ainult voolul < 30 A) | < 60 mV | hoiatus |
| Madal laetus | ≤ seadetes piir (vaikimisi 10 %) | > piir + 2 % | hoiatus; kriitiline ≤ pool piirist |

- **Telefonis:** hoiatused on ekraani ülaosas; puudutus avab vastava vaate (rehvid, aku, ülevaade).
- **Teavitused:** uus hoiatus või kriitiline hoiatus annab telefoni teavituse (kanal „Hoiatused”,
  kõrge tähtsus). Seadetes saab teavitused välja lülitada. Info-tasemel hoiatused teavitust ei anna.
- **Android Autos:** kõige raskem hoiatus on ekraani all olekureal (kriitiline punaselt).

## 10. Otsused

| Küsimus | Otsus |
|---|---|
| Sõiduk | Hyundai Ioniq 5 („Projekt 45”), päikesekatusega |
| Adapter | LELink (BLE) |
| Levitamine | Isiklik kasutus, APK; Play pood pole praegu eesmärk |
| Android Auto | Jah, graafiline (navigatsioonikategooria, Surface), Ioniq 5 stiilis (jaotis 5.1) |
| Aku | 72,6 kWh, 180 elementi (Car Scanneri logi järgi); rakendus tuvastab automaatselt |
| Vedu | Nelikvedu (AWD), kaks mootorit |
| Välisteenused | Praegu ei ole vaja (ABRP, MQTT jms) |
| Lähteandmed | Car Scanner PRO logid (sisseehitatud e-GMP profiil) |
| Kasutajaliidese keel | Eesti ja inglise; kõik kasutajale nähtavad tekstid (ka veateated) on ressurssides, diagnostika toorlogi on eesti keeles |
| APK allkiri | Ühine võti `app/debug.keystore`, et uuendused paigalduksid vana peale. Isiklikuks kasutuseks piisab; Play poodi minekul tuleb teha eraldi salajane väljalaskevõti |
| Versioon | 1.0.x, kus x on CI ehituse number (`versionCode`) |

## 11. Avatud küsimused

1. `7E5`/`22E011` toorvastused päikese käes, varjus ja öösel (jaotis 4.1).
2. Logid muudes olukordades: sõit, AC-laadimine, seismine päikese käes (süüde sees, laadimata).
3. Kliima ja soojendused: süüde sees arendajalogi, kus soojendused ja kliima režiimid lülitatakse
   ükshaaval sisse ja välja, iga lülituse juures märge (jaotis 4.4). Kas `7B3` vastab süüte ajal?
