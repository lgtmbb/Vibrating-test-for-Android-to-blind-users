# Changelog

Ez a projekt korábban négy, gépi generálásból ("gemini-code-...") származó,
egy .zip-be csomagolt fájlból állt, teljes build-rendszer nélkül. Ez a
verzió az első, ami tényleges, lefordítható Android Studio projektként van
strukturálva.

## [1.6.0]

### Hozzáadva
- **Új, 6. mód: "Kalapács mód"** - a lehető legerősebb rezgés, folyamatosan,
  de nagyon apró (25-60ms) szünetekkel megszakítva, mint amikor valaki
  ismételten lesújt egy kalapáccsal. Amikor a készülék támogatja, az
  `EFFECT_HEAVY_CLICK` előre definiált effektust használja (ezt kifejezetten
  erős, hirtelen "ütés" érzetre tervezték, gyakran gyártó által hangolva);
  ha nem elérhető, egy 255-ös (maximális) erősségű, rövid impulzusra esik
  vissza. Ellentétben a Kiszámíthatatlan móddal, itt az erősség szándékosan
  NEM véletlenszerű - mindig maximális -, csak az ütések apró időzítése
  kap enyhe, emberi jellegű ingadozást.
- `VIBRATION_API_RESEARCH.md` kiegészítve egy pontosan dokumentált
  részlettel: nem nulla erősségérték biztonságosan kérhető
  erősségszabályzás nélküli hardveren is (100%-ra kerekítődik, nem dob
  kivételt) - ez teszi lehetővé, hogy a Kalapács mód mindig 255-öt
  kérjen, előzetes `hasAmplitudeControl()` ellenőrzés nélkül.

### Változott
- A gombok újraszámozva: ...5. Kiszámíthatatlan, 6. Kalapács, majd
  Leállítás, majd 7. Beállítások.

## [1.5.0]

### Hozzáadva
- **Négy különböző esemény-stratégia a Kiszámíthatatlan módban**, hogy ne
  csak az egyes értékek, hanem a véletlenszerűség JELLEGE is változzon:
  - **BURST**: 1-3 gyors lüktetés apró résekkel, majd közepes szünet
    (ez volt eddig az egyetlen viselkedés).
  - **SPARSE**: hosszú (1,5-5s) csend, majd egyetlen, "meglepetésszerű"
    lüktetés.
  - **ROLLING**: 4-9 apró lüktetésből álló, majdnem folyamatos "hullám".
  - **PAIRED**: két lüktetés rövid réssel ("kop-kop"), majd hosszabb
    szünet.
- **Ismétlődés elkerülése (`RepeatAvoidingPicker`)**: a stratégia-, íz- és
  előre definiált effektus-választás mostantól nem választja ki kétszer
  egymás után ugyanazt. Ez a játék-hangtervezésben bevett, dokumentált
  "Repeat Prevention" / "Avoid Repeating Last N" technika (Unity Audio
  Random Container, RNGNeeds könyvtár) alkalmazása - lásd
  `VIBRATION_API_RESEARCH.md` új szakaszát a hozzá tartozó kutatással
  (miért érződik a matematikailag helyes, egyenletes véletlen mégis
  "csomósnak" az embereknek).

### Változott
- A Kiszámíthatatlan mód belső felépítése átalakítva: a lüktetés-lejátszás
  logikája kiemelve egy önálló `playOnePulse` függvénybe, amit mind a négy
  stratégia újrahasznosít.

## [1.4.0]

### Javítva
- A "Kiszámíthatatlan mód" jelzett hibája: még amplitúdó-szabályzás
  nélküli (a legtöbb olcsóbb, ERM-motoros) készüléken is érezhetően
  változatos maradjon. Eddig ilyen hardveren minden lüktetés
  `VibrationEffect.DEFAULT_AMPLITUDE`-del futott (ez helyes, a hardver
  valódi korlátja - nem hiba), de emiatt a mód monotonnak tűnhetett. Mostantól
  az "egyéni hullámforma" íz lüktetésenként véletlenszerűen választ egy
  sima, folytonos impulzus és egy apró (2-6 szegmenses) mikro-lüktetés-
  sorozat között - ez a hullámforma SZERKEZETÉT teszi véletlenszerűvé,
  ami erősségszabályzás nélkül is másképp érződik.

### Hozzáadva
- **Envelope-effektus (Android 16, API 36) mint új "íz" a Kiszámíthatatlan
  módban**: `VibrationEffect.BasicEnvelopeBuilder`-rel felépített,
  folytonosan változó intenzitású/élességű rezgés, véletlenszerű
  vezérlőpontokkal. Csak akkor kerül a lehetséges ízek közé, ha
  `Vibrator.areEnvelopeEffectsSupported()` igazat ad az adott készüléken.
  A támogatottság (vagy annak hiánya) a Beállítások "Eszköz képességei"
  szakaszában is megjelenik.
- `VIBRATION_API_RESEARCH.md` kiegészítve az envelope API pontos
  szignatúrájával és forrásaival; a korábban "egyelőre nem implementálva"
  jelzés törölve, mivel ez a verzió már használja.

### Változott
- `compileSdk` 34-ről 36-ra emelve (az envelope API eléréséhez),
  `targetSdk` szándékosan maradt 34-en. Android Gradle Plugin 8.5.2-ről
  8.13.0-ra emelve (ez az első hivatalosan API 36.1-ig támogatott AGP,
  ami még nem igényli az AGP 9.x törő DSL-váltását).
- A CI workflow-ban a Gradle wrapper verziója 8.7-ről 8.13-ra emelve (ezt
  az AGP 8.13.0 megköveteli), és egy explicit `sdkmanager` lépés
  hozzáadva az Android 36-os platform/build-tools telepítéséhez, mert a
  futtatógép előre telepített SDK-ja nem feltétlenül tartalmazza még.

## [1.3.0]

### Átnevezve
- A korábbi "Kiszámíthatatlan mód" (3. gomb) mostantól **"Következetlen
  mód"** (Inconsistent Mode) néven fut. A működése nem változott: a
  Beállításokban megadott hossz/szünet körül, a "Kiszámíthatatlanság
  mértéke" csúszka által vezérelt mértékben szór.

### Hozzáadva
- **Új, 5. mód: "Kiszámíthatatlan mód"** (Truly Unpredictable), ami jóval
  erősebben véletlenszerű, mint a Következetlen mód, szándékosan úgy, hogy
  ne alakuljon ki felismerhető minta:
  - Ciklusonként 1-3 lüktetésből álló "eseményeket" játszik le, a
    lüktetések közt apró, véletlenszerű réssel.
  - Minden egyes lüktetés függetlenül, egymástól elválasztva
    véletlenszerűsíti: az erősséget (ha a hardver támogatja), a hosszt, és
    a rezgés "típusát" (egyéni hullámforma / előre definiált effektus /
    összetett primitívek - amelyik éppen elérhető a készüléken).
  - Összetett primitívek választásakor a primitívek számát, sorrendjét, az
    egyes primitívek erősség-szorzóját (`scale`) és a köztük lévő
    késleltetést (`delay`) is véletlenszerűsíti, az
    `VibrationEffect.Composition.addPrimitive(id, scale, delay)` API
    kihasználásával.
  - A ciklusok közti szünet lényegesen szélesebb, teljesen független
    tartományban véletlenszerű, mint a Következetlen módé.
  - Szándékosan nem használja a Beállítások duration/pause/
    kiszámíthatatlanság-mérték/rezgéstípus értékeit - ezek csak a
    Következetlen módot vezérlik -, hogy a szórás mértéke felülről ne
    legyen korlátozva.
- `VIBRATION_API_RESEARCH.md` kiegészítve: a `Composition.addPrimitive`
  `scale`/`delay` paramétereinek tervezési ajánlásaival, és egy új
  szakasszal az Android 16 (API 36) hullámforma-envelope API-król
  (`BasicEnvelopeBuilder`, `WaveformEnvelopeBuilder`), amit a projekt
  jelenleg (a célközönség eszközparkja és a compileSdk miatt) még nem
  használ, de dokumentál a jövőbeli bővítéshez.

### Változott
- A gombok újraszámozva: 1. Állandó, 2. Pulzáló, 3. Következetlen, 4.
  Vákuum, 5. Kiszámíthatatlan, majd Leállítás, majd 6. Beállítások.
- A Beállítások képernyőn az erősség/hossz/szünet/kiszámíthatatlanság-
  mérték/rezgéstípus leírásai pontosítva, hogy egyértelmű legyen: ezek csak
  az Állandó, Pulzáló és Következetlen módot érintik, az új Kiszámíthatatlan
  módot nem.

## [1.2.0]

### Hozzáadva
- **Beállítások képernyő** (a főképernyő ötödik, utolsó gombja), amely a
  kutatott rezgés-API-felületet (lásd `VIBRATION_API_RESEARCH.md`)
  szabályozható beállításokká alakítja:
  - Rezgés erőssége (1-255, csak ha a hardver támogatja - `hasAmplitudeControl()`).
  - Rezgés hossza és a szünet hossza ezredmásodpercben, saját mezőkben.
  - Kiszámíthatatlanság mértéke (0-100%) a Kiszámíthatatlan módhoz.
  - Rezgéstípus: egyéni hullámforma / előre definiált effektus (Android
    10+) / összetett primitívek (Android 11+, Android 12-től megbízható
    támogatottság-ellenőrzéssel).
  - **Eszköz képességei** szakasz: minden fenti funkcióról kiírja, hogy az
    adott telefonon elérhető-e, és ha nem, miért (API-szint hiánya vagy
    hardverkorlát), beleértve a rezonanciafrekvenciát és jósági tényezőt is
    (Android 13+, ha a hardver jelenti).
- `VibrationCapabilities`: futásidejű képesség-lekérdező réteg, amely
  soha nem következtet Android-verzióból, mindig a Vibrator saját
  ellenőrző metódusait hívja (`hasAmplitudeControl`, `areEffectsSupported`,
  `arePrimitivesSupported`, stb.).
- `VibrationSettings`: minden fenti paraméter és a két viselkedési
  kapcsoló (képernyőzár utáni folytatás, gomb-ismétlési viselkedés) közös,
  `SharedPreferences`-re épülő tárolója.
- **"Gomb ismételt megnyomása újraindítja a rezgést" kapcsoló** (a
  Beállítások képernyőn, részletes leírással): alapértelmezésben
  bekapcsolva (ez az eddigi viselkedés - minden gombnyomás újraindít).
  Kikapcsolva: ha egy mód gombját nyomják meg úgy, hogy az a mód már fut,
  a gomb nem csinál semmit (nem indítja újra a rezgést), csak egy rövid
  TalkBack-bemondást ad ("Már fut ez a mód..."). Ehhez a
  `VibrationService` egy `currentActiveMode` állapotot tart nyilván, amit
  a `MainActivity` minden gombnyomáskor leellenőriz.
- `VIBRATION_API_RESEARCH.md`: külön dokumentum, amely API-szintenként
  (Android 4.0-tól a jelenlegi verziókig) összefoglalja, milyen
  rezgés-paraméterek léteznek egyáltalán a platformon, hivatalos
  fejlesztői dokumentációra hivatkozva.

### Változott
- Az Állandó, Pulzáló és Kiszámíthatatlan mód mostantól a Beállítások
  képernyőn megadott hosszt, szünetet és rezgéstípust használja a korábbi
  fix, kódba írt értékek helyett. A Vákuum mód szándékosan kivétel: mindig
  az egyéni hullámformát használja, mert a fokozatos erősödés a lényege.

## [1.1.0]

### Hozzáadva
- `VibrationService`: a rezgés-ciklusokat az Activity-től független
  szolgáltatásban futtatja.
- "Folytatás képernyőzár után is" jelölőnégyzet a főképernyőn, amely
  `SharedPreferences`-ben megmarad az újraindítások között. Bejelölve a
  szolgáltatás `specialUse` típusú előtér-szolgáltatássá lép elő (állandó
  értesítéssel és Leállítás gombbal), és `PARTIAL_WAKE_LOCK`-ot tart, hogy a
  mintázat időzítése pontos maradjon képernyőzár alatt.
- Negyedik rezgésmód ("Vákuum / szívó mód") bekötve a felhasználói
  felületre – a kód már megvolt a forrásban, de nem volt elérhető gomb
  hozzá.
- `strings.xml`: minden korábban kódba ágyazott szöveg (feliratok,
  `contentDescription`-ök, bemondások) áthelyezve erőforrásfájlba.
- Új jogosultságok: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `WAKE_LOCK`, `POST_NOTIFICATIONS` (utóbbi Android 13+ eszközökön a
  szolgáltatás értesítésének megjelenítéséhez szükséges, futásidőben
  kérve).
- Teljes Gradle build-struktúra (`build.gradle`, `settings.gradle`,
  `gradle.properties`), mivel korábban csak a nyers forrásfájlok léteztek,
  build-rendszer nélkül.
- Vektorgrafikus alkalmazásikon és állapotsor-ikon (korábban egyáltalán
  nem volt ikonfájl a projektben, csak hivatkozás rá a manifestben).

### Javítva
- `findViewById<Button>(R.btnConsistent)` és a másik három azonos hibás
  hivatkozás `R.id.btnConsistent` (stb.) formára javítva – e nélkül a
  projekt nem fordult volna le.

### Változott
- A rezgés-vezérlő logika átkerült a `MainActivity`-ből a
  `VibrationService`-be; a `MainActivity` mostantól csak a felhasználói
  felületet kezeli, és `Intent`-eken keresztül vezérli a szolgáltatást.

## [1.0.0] (a kiindulási állapot, változtatás előtt)
- Három rezgésmód (Állandó, Pulzáló, Kiszámíthatatlan) és Leállítás gomb,
  az Activity saját `CoroutineScope`-jában futtatva.
- `contentDescription` minden gombon, `announceForAccessibility()` hívások
  módváltáskor.
- Negyedik ("Vákuum") mód kódja jelen volt, de UI-hoz nem kötve.
- Hiányzó build-rendszer, hiányzó ikon, hibás `R.id` hivatkozások – ebben
  az állapotban a projekt nem lett volna fordítható.
