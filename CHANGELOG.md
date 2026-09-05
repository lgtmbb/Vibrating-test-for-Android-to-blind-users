# Changelog

Ez a projekt korábban négy, gépi generálásból ("gemini-code-...") származó,
egy .zip-be csomagolt fájlból állt, teljes build-rendszer nélkül. Ez a
verzió az első, ami tényleges, lefordítható Android Studio projektként van
strukturálva.

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
