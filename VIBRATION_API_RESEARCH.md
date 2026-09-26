# Android rezgés-API kutatás (Android 4.0 - jelenlegi verziók)

Ez a dokumentum a hivatalos Android fejlesztői dokumentáció (developer.android.com)
alapján összegyűjtött, API-szintenkénti listát ad arról, hogy milyen rezgés-
paraméterek léteznek egyáltalán az Android platformon, és melyik verziótól.
A `SettingsActivity` és a `VibrationCapabilities` osztály ez alapján épül fel.

Alapelv, amit a kód is követ: **soha nem elég tudni, hogy egy API-szinttől
"létezik" egy funkció** - a hardver (a rezgőmotor típusa) ettől függetlenül
támogathatja vagy sem. Ezért mindenhol, ahol van rá futásidejű ellenőrző
metódus, azt hívjuk meg, nem pusztán az `Build.VERSION.SDK_INT`-re
támaszkodunk.

## API 1 (Android 1.0) - API 25 (Android 7.1), tehát Android 4.0-tól is

- `Vibrator.vibrate(long milliseconds)`: egyetlen, fix erősségű rezgés adott
  ideig. Nincs erősségszabályzás - a motor vagy be van kapcsolva, vagy nincs.
- `Vibrator.vibrate(long[] pattern, int repeat)`: be/ki időközök sorozata
  (pl. `[0, 200, 100, 200]` = várj 0ms, rezegj 200ms, várj 100ms, rezegj
  200ms). A `repeat` a minta melyik indexétől ismétlődjön (-1 = nincs
  ismétlés). Ez a legrégebbi, Android 4.0-n is elérhető módja egy
  "pulzáló" mintának.
- `Vibrator.hasVibrator()`: van-e egyáltalán rezgőmotor.
- **Nincs**: erősségszabályzás, előre definiált effektusok, primitívek.

## API 26 (Android 8.0, Oreo)

- Bevezetve: a `VibrationEffect` osztály.
  - `VibrationEffect.createOneShot(long milliseconds, int amplitude)`:
    egyetlen rezgés, immár **állítható erősséggel** (1-255, vagy
    `DEFAULT_AMPLITUDE`).
  - `VibrationEffect.createWaveform(long[] timings, int[] amplitudes, int repeat)`:
    a régi minta-alapú rezgés váltó erősségekkel.
- `Vibrator.hasAmplitudeControl()`: **ez az első API, amivel lekérdezhető,
  hogy a hardver ténylegesen támogatja-e az erősségszabályzást.** Ha nem,
  minden nem-nulla erősségérték egyszerűen "be"-ként viselkedik.
- **Fontos, pontosan dokumentált részlet**: a hivatalos Android haptika
  API-referencia szerint "Non-zero amplitude values are rounded up to 100%
  on devices without amplitude control" - tehát bármilyen 1-255 közti
  erősségérték kérése **biztonságos olyan hardveren is, ami nem támogatja
  az erősségszabályzást**: nem dob kivételt, egyszerűen 100%-ra
  kerekítődik. Emiatt a "Kalapács mód" (lásd lent) nyugodtan kérhet mindig
  255-öt, `hasAmplitudeControl()` előzetes ellenőrzése nélkül - ott
  szándékosan ez a viselkedés (mindig maximális erősség).
- Forrás: [VibrationEffect referencia](https://developer.android.com/reference/kotlin/android/os/VibrationEffect),
  [Android haptics API reference](https://developer.android.com/develop/ui/views/haptics/haptics-apis)

## API 29 (Android 10)

- `VibrationEffect.createPredefined(int effectId)`: előre definiált,
  eszközönként optimalizált rezgésminták: `EFFECT_CLICK`,
  `EFFECT_DOUBLE_CLICK`, `EFFECT_TICK`, `EFFECT_HEAVY_CLICK`. Ha a
  hardverhez nincs optimalizált verzió, a rendszer csendben egy általános
  mintára esik vissza - nincs szükség kézi fallback-re.
- Ezen a szinten **még nincs API** annak lekérdezésére, hogy egy adott
  effektushoz van-e eszközre optimalizált verzió (az `areEffectsSupported`
  csak API 30-tól létezik), tehát Android 10-en ez a támogatottság
  ellenőrizhetetlen - a `VibrationCapabilities` ezt "nem ellenőrizhető"-ként
  jelzi, nem próbál találgatni.

## API 30 (Android 11)

- `Vibrator.areEffectsSupported(int... effectIds)`: minden kért effektusra
  külön-külön visszaadja, hogy `VIBRATION_EFFECT_SUPPORT_YES`,
  `_NO` vagy `_UNKNOWN`.
- `Vibrator.areAllEffectsSupported(int... effectIds)`: összesített
  (AND-olt) eredmény ugyanerre.
- Bevezetve: `VibrationEffect.Composition` - primitívekből összeállítható
  egyedi rezgés. Az API 30-as primitívek: `PRIMITIVE_CLICK`,
  `PRIMITIVE_TICK`, `PRIMITIVE_QUICK_RISE`, `PRIMITIVE_SLOW_RISE`,
  `PRIMITIVE_QUICK_FALL`.
- `Vibrator.arePrimitivesSupported(int... primitiveIds)` /
  `areAllPrimitivesSupported(...)`: **fontos korlátozás** - a hivatalos
  dokumentáció szerint Android 11-en ez az ellenőrzés nem megbízható: csak
  azt teszteli, hogy maga a kompozíció-API elérhető-e, és emiatt minden
  kért azonosítóra "támogatott"-at ad vissza, függetlenül attól, hogy a
  hardver ténylegesen le tudja-e játszani. Megbízható, eszközönkénti
  ellenőrzés csak Android 12-től van. Az alkalmazás ezt a
  `checkReliable` jelzővel mutatja meg a Beállítások képernyőn.
- `VibrationAttributes`: a rezgés "célját" osztályozó attribútum-rendszer
  (pl. értesítés, riasztás, érintési visszajelzés), amivel a rendszer
  eltérően kezelheti (pl. néma módban letilthatja).
- `Composition.addPrimitive(primitiveId, scale, delay)`: minden primitívhez
  külön-külön megadható egy 0.0-1.0 közti erősség-szorzó (`scale`) és egy
  ezredmásodperces késleltetés (`delay`) az előző primitív végétől számítva.
  A hivatalos tervezési ajánlás szerint 50ms alatti rés két primitív közt
  alig érzékelhető, 50ms fölött már jól elkülönül; a `scale` értékeknek
  legalább 1,4-szeres arányban kell eltérniük ahhoz, hogy az erősségkülönbség
  észrevehető legyen. **A "Kiszámíthatatlan mód" ezt a lehetőséget
  használja ki**: lüktetésenként véletlenszerű `scale` és `delay` értékekkel
  állít össze 1-3 primitívből álló kompozíciókat.
- Forrás: [Haptics API reference](https://developer.android.com/develop/ui/views/haptics/haptics-apis),
  [Custom haptic effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects)

## API 31 (Android 12)

- Új primitívek: `PRIMITIVE_THUD`, `PRIMITIVE_SPIN`, `PRIMITIVE_LOW_TICK`.
- `VibratorManager`: több rezgőmotoros készülékekhez (pl. két motor, bal/
  jobb oldal) - `CombinedVibration` osztállyal lehet őket együtt vagy
  külön-külön vezérelni. Ez az alkalmazás egy motort feltételez
  (`VibratorManager.defaultVibrator`), mivel a legtöbb telefonon csak egy
  van, és a teszt célja (visszajelzés-minták tesztelése) nem igényel
  motoronkénti megkülönböztetést.
- `Vibrator.getId()`: az adott rezgőmotor azonosítója (több motor esetén
  hasznos).
- Innentől megbízható a primitívenkénti támogatottság-ellenőrzés (lásd
  fent).

## API 33 (Android 13)

- `Vibrator.getResonantFrequency()` / `getQFactor()`: a rezgőmotor fizikai
  jellemzői (rezonanciafrekvencia Hz-ben, jósági tényező). Ezek
  hardverfüggő, opcionális értékek - sok készülék `NaN`-t ad vissza, ha
  nem jelenti őket. Az alkalmazás ezt "nem elérhető"-ként mutatja, nem
  hibaként.
- Ezek az értékek elsősorban egyedi hullámforma-tervezéshez (envelope-
  alapú effektusokhoz) hasznosak, amit ez az alkalmazás nem implementál
  külön szabályozható beállításként, mivel nem ad kézzelfogható,
  felhasználó által állítható paramétert - csak információs adat.

## API 36 (Android 16) - most már implementálva

- `VibrationEffect.BasicEnvelopeBuilder`: vezérlőpontokból (intenzitás
  0.0-1.0, élesség 0.0-1.0, időtartam ms) felépített, folytonosan változó
  hullámforma-effektus (PWLE - Piecewise-Linear Envelope). A rezgőmotor
  simán átmegy egyik vezérlőpontból a másikba, ellentétben a
  `Composition` primitívek diszkrét "kattanásaival". A pontos szignatúra:
  `addControlPoint(intensity: Float, sharpness: Float, durationMillis: Long)`,
  visszatérési típusa maga a builder (láncolható). A hivatalos példa és
  a keretrendszer elvárása szerint az utolsó vezérlőpontnak 0.0
  intenzitásúnak kell lennie (a lezáráshoz); a kezdő 0-intenzitású pontot a
  keretrendszer automatikusan beszúrja, azt nem kell kézzel megadni.
  `setInitialSharpness(Float)` opcionális, hiányában az első vezérlőpont
  élességét használja.
- `VibrationEffect.WaveformEnvelopeBuilder`: a fejlettebb testvér - itt
  amplitúdó (0.0-1.0) és tényleges frekvencia (Hz) párokkal lehet
  vezérlőpontokat megadni, a rezgőmotor tényleges frekvencia-tartományát
  (`VibratorFrequencyProfile`) figyelembe véve. **Ezt a projekt egyelőre
  nem használja** - a `BasicEnvelopeBuilder` intenzitás/élesség
  párosítása eszközfüggetlenebb és egyszerűbb, és a "Kiszámíthatatlan
  mód" céljára (változatos, félrevezethetetlen mintázat) ez elegendő; a
  `WaveformEnvelopeBuilder` konkrét Hz-értékei csak akkor adnának
  hozzáadott értéket, ha a cél kifejezetten egy adott érzékelt
  "hangmagasság" elérése lenne.
- `Vibrator.areEnvelopeEffectsSupported()`: a támogatottság ellenőrzésére -
  ezt a `VibrationCapabilities` a Beállítások "Eszköz képességei"
  szakaszában is megjeleníti.
- **Hol használja a projekt**: a "Kiszámíthatatlan mód" egyik lehetséges
  "íze" (`TrulyRandomFlavor.ENVELOPE`), amikor `SDK_INT >= 36` és
  `areEnvelopeEffectsSupported()` igazat ad. 1-2 véletlenszerű köztes
  vezérlőpontot épít fel véletlenszerű intenzitással és élességgel, majd
  a kötelező, 0 intenzitású záró ponttal.
- Ehhez a `compileSdk` 34-ről 36-ra, az Android Gradle Plugin 8.5.2-ről
  8.13.0-ra lett emelve (ez az első AGP-verzió, ami hivatalosan
  API 36.1-ig támogatott, és még a régi, megszokott Groovy DSL-lel
  működik - az AGP 9.x már törné a build.gradle jelenlegi szerkezetét).
  A `targetSdk` szándékosan maradt 34-en.
- Forrás: [Create custom haptic effects - Vibration waveform with envelopes](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects#vibration-waveform-with-envelopes),
  [Implement piecewise linear envelope effects (AOSP)](https://source.android.com/docs/core/interaction/haptics/haptics-pwle)

## Natív, ismétlődő hullámforma - determinisztikus mintázatokhoz

A "Rövid-hosszú váltakozás", "Fokozatos erősödés", "Fokozatos gyengülés" és
"Hullámzó intenzitás" módok mindegyike **szándékosan nem véletlenszerű** -
a mintázatnak minden ismétlésnél pontosan ugyanazzal az időzítéssel kell
futnia. Ehhez nem saját, `delay()`-alapú Kotlin-ciklust használnak, hanem a
`VibrationEffect.createWaveform(long[] timings, int[] amplitudes, int
repeat)` API `repeat` paraméterét: ha ez nem -1 (hanem egy érvényes index,
jelen esetben 0), a rendszer a hullámformát a megadott indextől kezdve
**saját maga, natívan ismétli a végtelenségig**, amíg
`Vibrator.cancel()` le nem állítja. Ez két okból jobb egy saját ciklusnál:

1. **Garantáltan azonos időzítés minden körben** - nem a mi
   coroutine-unk (ami ki van téve a JVM/Kotlin ütemező apró, valós idejű
   ingadozásainak) hajtja végre az ismétlést, hanem a platform saját,
   natív rezgésütemezője.
2. **Egyetlen `vibrate()` hívás elég** az egész, akár órákig tartó
   ismétlődő lejátszáshoz - nincs szükség folyamatosan újraébredő
   coroutine-ra.

Forrás: [VibrationEffect.createWaveform referencia](https://developer.android.com/reference/kotlin/android/os/VibrationEffect#createWaveform(long%5B%5D,%20int%5B%5D,%20int)) -
"repeat: The index into the timings array at which to start repeating, or
-1 if you don't want to repeat."

### Sima rámpa/hullám közelítése lépcsőzéssel

A "Fokozatos erősödés/gyengülés" és a "Hullámzó intenzitás" mód nem az
Android 16-os envelope API-t (`BasicEnvelopeBuilder`) használja, hanem sok,
apró (30ms-es) lépésből álló hullámformát épít, amelyben minden lépés
amplitúdója egy kicsit más - ez lépcsőzetesen közelíti a folytonos
görbét, elég finom felbontással ahhoz, hogy simának érződjön. Ennek oka,
hogy ez az API 26-tól (Android 8.0) mindenhol elérhető, szemben az
envelope API-val, ami csak Android 16-tól létezik - így ezek a módok
minden, erősségszabályzással rendelkező készüléken működnek, nem csak a
legújabbakon.

### Valódi, szándékosan éles lépcsőzés (nem közelítés)

A "Lépcsőzetes erősödés" és a "Hirtelen módváltás" mód más esetben pont
az ellenkezőjét akarja: itt a cél kifejezetten a HIRTELEN, interpoláció
nélküli váltás egy-egy diszkrét szint között, nem egy folytonos görbe
közelítése. Ehhez nincs szükség 30ms-es apró lépésekre - elég annyi
`createWaveform` szegmens, ahány szint van (5, illetve 2), mindegyik a
teljes szint-időtartamra állítva, eltérő amplitúdóval. Mivel
`createWaveform` egymást követő, nem-nulla amplitúdójú szegmensei között
nincs kényszerű szünet, a rendszer a motort közvetlenül, szünet nélkül
állítja át a következő szint amplitúdójára - ez pontosan a kért,
"abrupt", átmenet nélküli váltást adja.

## Ismétlődés elkerülése - nem Android-specifikus, de idevágó kutatás

A "Kiszámíthatatlan mód" nem csak az Android rezgés-API-jára épül: a
tényleges élmény javításához a *véletlenszerűség érzékelt minőségét* is meg
kellett vizsgálni, ami már nem Android-specifikus terület, hanem a
játékfejlesztésben (elsősorban hangtervezésben) rég megoldott probléma.

- **A jelenség**: matematikailag helyes, egyenletes eloszlású véletlen
  szám- vagy elemválasztás gyakran "csomósnak" vagy ismétlődőnek *tűnik* az
  embereknek, még akkor is, ha statisztikailag semmi hiba nincs benne (pl.
  10 elemből átlagosan minden 10. választás ismétlődés lenne egy valódi
  egyenletes eloszlásnál - ez embernek "hibásnak" érződik). Ezt kognitív
  pszichológiai kutatás is megerősíti: emberek megbízhatóan kerülik az
  egymást követő ismétlődéseket saját (kézzel generált) "véletlen"
  sorozataikban, még akkor is, ha statisztikailag azoknak elő kellene
  fordulniuk.
- **A bevett megoldás - "Repeat Prevention" / "Avoid Repeating Last N"**:
  játék-hangmotorok (pl. Unity Audio Random Container "Avoid Repeating
  Last" beállítása, a hangkönyvtárakban elterjedt RNGNeeds "Repeat
  Prevention" funkciója) egy közös alapelvet követnek: az utolsó N
  választást kizárják a következő véletlen húzás jelöltjei közül, majd a
  fennmaradó jelöltek közül húznak egyenletesen. Ezt implementálja a
  projekt `RepeatAvoidingPicker<T>` segédosztálya - a "Repick" módszer
  (RNGNeeds terminológiája) egy egyszerűsített, N=1-es változata: ha egy
  elemet két egymást követő húzás választana ki, a második húzás
  kizárólag a többi jelölt közül történik.
- **Alkalmazva**: a Kiszámíthatatlan mód esemény-stratégiájának
  (BURST/SPARSE/ROLLING/PAIRED), a rezgés-ízének (egyéni/előre definiált/
  összetett/envelope) és az előre definiált effektus konkrét azonosítójának
  kiválasztásánál egyaránt.
- Forrás: [Unity - Audio Random Container reference (Avoid Repeating
  Last)](https://docs.unity3d.com/2023.2/Documentation/Manual/AudioRandomContainer-UI.html),
  [RNGNeeds - Repeat Prevention](https://docs.rngneeds.com/documentation/repeat-prevention),
  [An architecturally constrained model of random number generation - emberi
  ismétlődés-kerülés kutatása](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4076660/)

## Amit szándékosan nem tettünk beállítássá

- **Több rezgőmotor egyidejű, külön-külön vezérlése (`CombinedVibration`,
  Android 12+)**: a legtöbb telefonon egyetlen motor van, és a teszt célja
  (visszajelzés-minták demonstrálása) nem igényli motoronkénti
  megkülönböztetést.
- **`WaveformEnvelopeBuilder` (Android 16+, konkrét Hz-frekvenciákkal
  megadott vezérlőpontok)**: a `BasicEnvelopeBuilder` (intenzitás/élesség
  alapú, eszközfüggetlenebb) változata már implementálva van a
  Kiszámíthatatlan módban; a frekvencia-alapú, `VibratorFrequencyProfile`-t
  igénylő verzió továbbra sem egy-két csúszkával leírható felhasználói
  beállítás, hanem egy teljes hullámforma-szerkesztő lenne - ha ez is
  kell, jelezd, külön kiegészítő fejlesztésként megoldható.

## Fő hivatkozások

- https://developer.android.com/reference/kotlin/android/os/VibrationEffect
- https://developer.android.com/reference/android/os/Vibrator
- https://developer.android.com/develop/ui/views/haptics/haptics-apis
- https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects
- https://developer.android.com/develop/ui/views/haptics/haptic-feedback
