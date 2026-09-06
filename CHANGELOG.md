# Changelog

Ez a projekt korábban négy, gépi generálásból ("gemini-code-...") származó,
egy .zip-be csomagolt fájlból állt, teljes build-rendszer nélkül. Ez a
verzió az első, ami tényleges, lefordítható Android Studio projektként van
strukturálva.

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
