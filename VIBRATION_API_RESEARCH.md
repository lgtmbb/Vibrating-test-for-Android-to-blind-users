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

## Samsung (S25): "az intenzitás nem állítható" - diagnózis és javítás

**Tünet**: Samsung Galaxy S25-ön az erősség nem volt állítható.

**Dokumentált okok, amiket a kód mostantól kezel** (a tényleges S25-ös
viselkedést innen, eszköz nélkül nem tudtam kipróbálni - ezért nem egyetlen
találgatott javítás, hanem választható módszerek kerültek be):
- **A kapcsoló le volt tiltva.** A Beállítások csak akkor engedte az
  egyéni erősséget, ha `Vibrator.hasAmplitudeControl()` igazat adott. Ha
  egy készülék (a Samsung-fórumokon és az Android-dokumentációban is
  szereplő eszközöknél előfordul) hamisat jelent, a vezérlő szürke maradt.
  Most mindig elérhető.
- **Az `AUTO` mód csendben kitöltési tényezőre váltott**, ha a hardver
  nem jelzett amplitúdó-támogatást - ez ott is gyenge/azonos érzetet adott,
  ahol a motor valójában követi az amplitúdót. Új "Intenzitás-módszer":
  Automatikus / Amplitúdó kényszerítve / Kitöltési tényező (on/off arány).
  Az utolsó minden hardveren érezhető, mert nem az amplitúdó-támogatásra
  épül; a kényszerített amplitúdó pedig kipróbálható akkor is, ha a
  készülék rosszul jelent. Az explicit erősségű `createOneShot` hívások
  is kitöltési tényezős emulációra váltanak, ha az amplitúdó-mód ki van
  kapcsolva (20ms-es periódus, on/off arány = kért erősség).
- **Rendszer-szintű skálázás.** Az Android a felhasználó "Rezgés
  erőssége" beállításai (hívás / értesítés / érintés / média) szerint
  skálázza az app rezgését a `VibrationAttributes`/`AudioAttributes`
  felhasználási osztály alapján; alacsony beállításnál az amplitúdó-
  különbségek összenyomódnak. Az app addig nem adott meg osztályt. Új
  Beállítás: "Rezgés felhasználási osztálya" (Ébresztő alapértelmezett,
  Kisegítő lehetőségek, Értesítés, Érintés, Média, Nincs megadva), és
  minden rezgés ezen keresztül (`play()`) szól.
- **Háttér/képernyőzár.** Az Android haptika-dokumentációja szerint a
  `VibrationAttributes` háttérből indított rezgésnél kötelező, mert háttérben
  csak "figyelemfelkeltő" rezgés támogatott - ez a lezárt képernyős hibát is
  magyarázhatja. Az alapértelmezett Ébresztő osztály ezt fedi.

Forrás: [Android haptics API reference](https://developer.android.com/develop/ui/views/haptics/haptics-apis),
[VibrationAttributes](https://developer.android.com/reference/android/os/VibrationAttributes),
[Samsung Developer Forum: hasAmplitudeControl, createWaveform](https://forum.developer.samsung.com/t/hasamplitudecontrol-createwaveform-api/30327)

## Csomag-aláírás és telepítési hiba - kritikus javítás

Ez a szakasz nem rezgés-API-kutatás, hanem egy súlyos, a projekt
kiadási folyamatát érintő hiba dokumentálása, mert a jövőben is
releváns marad.

**A tünet**: egy korábbi verzió telepítése után egy újabb verzió
telepítése "az alkalmazás nincs telepítve" hibával elutasításra került,
a Play Protect figyelmeztetéseknél megszokott "mégis telepítés" opció
nélkül.

**Az ok**: a `debug` build-típusnak nem volt explicit megadva
`signingConfig`. Ilyenkor az Android Gradle Plugin egy alapértelmezett,
`~/.android/debug.keystore` útvonalon várt kulcsot használ - ha ez a
fájl nem létezik (és egy friss GitHub Actions futtatógépen sosem
létezik, mert minden futás egy vadonatúj, üres virtuális gépen indul),
az AGP **automatikusan generál egyet, futásonként újat**. Emiatt a v1.0.0-tól
kezdve minden egyes kiadott `.apk` **más-más, egyszer használatos
aláírással** rendelkezett. Android biztonsági okból nem engedi, hogy egy
már telepített csomagot egy eltérő aláírású verzió felülírjon - innen a
hiba.

**A javítás**: egy állandó, 30 évig érvényes debug-kulcsot generáltam
(`app/debug.keystore`, `keytool -genkeypair`), és az `app/build.gradle`
`signingConfigs.debug` blokkjában erre mutatunk explicit módon, a
jól ismert, nyilvános Android debug-kulcs alapértelmezett jelszavaival
(`android`/`android`, alias `androiddebugkey` - ezek nem titkosak, minden
Android debug-kulcs ugyanezt használja, mert debug build soha nem kerül
éles disztribúcióba). Egy `signingConfigs` blokkban `debug` néven
definiált konfiguráció automatikusan felülírja az AGP beépített
alapértelmezett debug-aláírását - nem kell külön hozzárendelni a
`debug` build-típushoz.

**Ez a keystore fájl bináris, és szándékosan commitolva van a repóba** -
ez nem biztonsági kockázat, mert (1) kizárólag debug buildekhez
használjuk, sosem élesben, (2) a jelszavak amúgy is nyilvánosan ismertek
minden Android debug-kulcsnál, (3) pontosan az a cél, hogy minden
gépen és minden CI-futáson ugyanaz a kulcs legyen elérhető - egy
`.gitignore`-olt, csak helyi kulcs pont az ellenkező hatást érné el.

**Egyszeri teendő a felhasználó oldalán**: mivel a korábbi (v1.0.0-v1.8.0)
kiadások mind eltérő, ideiglenes kulccsal voltak aláírva, a jelenleg
telepített verziót egyszer teljesen el kell távolítani, mielőtt az ezt
követő, már stabil kulccsal aláírt verzió telepíthető. Ezután minden
további frissítés zökkenőmentesen, eltávolítás nélkül fog menni, mert
mind ugyanazzal a kulccsal lesz aláírva.

Forrás: [App signing overview - Android fejlesztői dokumentáció](https://developer.android.com/studio/publish/app-signing)

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

## Átfedések a meglévő módokkal - átlátható jelzés

Amikor a felhasználó a "Véletlenszerű időzítés", "Véletlenszerű
intenzitás", "Véletlenszerű időzítés + intenzitás", "Szinuszos",
"Négyszögjel-szerű" és "Fűrészfog" módokat kérte, három közülük
technikailag jelentősen átfed egy már meglévő móddal. Ezt itt, nem pedig
elhallgatva dokumentáljuk:

- **"Szinuszos" ≈ "Hullámzó intenzitás" (10. mód)**: mindkettő szinusz-
  görbe szerint modulálja az erősséget. A különbség: a "Hullámzó
  intenzitás" a "Rezgés hossza" beállításból számolt ciklushosszal
  dolgozik, míg a "Szinuszos" a specifikáció szerint kifejezetten Hz-ben
  megadható frekvenciát és külön állítható lengés-mértéket kapott - ezért
  külön mód, saját beállításokkal, nem csupán álnév.
- **"Négyszögjel-szerű" ≈ "Pulzáló mód" (2. mód)**: mindkettő éles
  BE/KI váltásokat ad. A különbség: a "Pulzáló mód" a "Rezgés hossza" és
  "Szünet hossza" két KÜLÖN beállításából épül (tetszőlegesen aszimmetrikus
  be/ki arány), míg a "Négyszögjel-szerű" mód a Hz-alapú frekvenciából
  szimmetrikus (50%-os kitöltési tényezőjű) négyszögjelet számol - ez egy
  precízebb, jelfeldolgozási értelemben vett "négyszögjel" fogalomnak felel
  meg.
- **"Fűrészfog" ≈ "Fokozatos erősödés" (8. mód)**: ez a kettő
  **funkcionálisan azonos** - egy fűrészfog-hullám definíció szerint
  pontosan az, amit a "Fokozatos erősödés" már csinál: lineáris rámpa
  minimumtól maximumig, amit a natív hullámforma-ismétlés (`repeat=0`)
  azonnali, interpoláció nélküli visszaugrással zár le minden ciklus
  végén. Nem volt mit technikailag hozzátenni - külön gombként azért
  szerepel, mert külön kérték, két különböző névvel.

## Wake lock időkorlát és gyorsulásmérő

- A wake lock korábban 30 perces `acquire(timeout)`-tal futott; lejárta után
  az időzítés-alapú módok lezárt képernyőnél elakadhattak. Most
  időkorlát nélküli, minden leállítási úton felszabadul.
- A "Mozgásra reagáló" mód a `SensorManager`/`TYPE_ACCELEROMETER`
  eltérését használja a gravitációtól (`GRAVITY_EARTH`); külön jogosultság
  nem kell hozzá. Forrás: [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)

## Az erősség-csúszka "nem csinál semmit" - második kör (v2.2.0)

**Tünet**: a Beállításokban az erősséget 0-ra vagy 100-ra állítva sem változott semmi
(Samsung S25).

**Az app hibája, amit ez a kör javít**: az "egyéni erősség" kapcsoló + csúszka
csak az Állandó, Pulzáló, Következetlen és Véletlenszerű időzítés módra hatott;
a többi mód (Kalapács, Burst, Négyszögjel, Szinuszos stb.) fix 255-ös vagy
saját relatív erősséget használt. Aki ezek valamelyikét próbálta, joggal látta
úgy, hogy a csúszka hatástalan. Mostantól egyetlen **fő erősség (1-100%)**
érvényesül a lejátszás központi pontján, minden módra: az amplitúdókra
(`scaleAmp`), a kitöltési tényezős emulációra, a kompozíció-primitívek
`scale` paraméterére és az envelope-intenzitásra. Kivétel: az előre definiált
effektusokat (`createPredefined`) az Android nem engedi skálázni.

**Ami eszköz nélkül nem dönthető el**: hogy az S25 a kért amplitúdót valóban
figyelembe veszi-e. A Samsung-fórumon és a libretro-fórumon vannak jelentések
arról, hogy egyes Galaxy-eszközök az alkalmazás által kért rezgéserősséget
figyelmen kívül hagyják (az API hibátlanul elfogadja, a hatás elmarad), de az
S25-re vonatkozó hivatalos dokumentációt nem találtam. Ezért az app nem
találgat, hanem **mér**: a Beállítások "Erősség-kalibráció" gombja a telefon
saját gyorsulásmérőjével 15% / 50% / 100% szinten lejátssza az amplitúdós és a
kitöltési tényezős rezgést, és méri a tényleges rezgési energiát (a három
tengely szórásának összegéből vett gyök). Amelyik módszernél az energia a
szinttel mérhetően nő, azt választja az "Intenzitás-módszer" értékének; ha
egyiknél sem, kiírja, hogy a rendszer Rezgés erőssége beállítása vagy a
felhasználási osztály felülírhatja.

Korlátok: a mérés csak mozdulatlan, kemény felületen megbízható; Android 12-től
a gyorsulásmérő mintavétele külön engedély nélkül 200 Hz-re korlátozott, ami a
motor frekvenciájához képest alulmintavételezés, de az energia-összehasonlításhoz
(szintek egymáshoz képest) elegendő. A háttérből indított rezgésnél a haptikai
dokumentáció szerint figyelemfelkeltő felhasználási osztály kell (pl.
`USAGE_ALARM`), a hang-attribútumok pedig a rezgésre önmagukban nincsenek hatással.

Forrás: [Android haptics API reference](https://developer.android.com/develop/ui/views/haptics/haptics-apis),
[Samsung Developer Forum](https://forum.developer.samsung.com/t/hasamplitudecontrol-createwaveform-api/30327),
[libretro fórum: vibrációs erősség figyelmen kívül hagyva](https://forums.libretro.com/t/haptic-feedback-vibration-strength-on-android-is-ignored/38728)

## Fő hivatkozások

- https://developer.android.com/reference/kotlin/android/os/VibrationEffect
- https://developer.android.com/reference/android/os/Vibrator
- https://developer.android.com/develop/ui/views/haptics/haptics-apis
- https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects
- https://developer.android.com/develop/ui/views/haptics/haptic-feedback
