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
- Forrás: [VibrationEffect referencia](https://developer.android.com/reference/kotlin/android/os/VibrationEffect)

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

## Amit szándékosan nem tettünk beállítássá

- **Több rezgőmotor egyidejű, külön-külön vezérlése (`CombinedVibration`,
  Android 12+)**: a legtöbb telefonon egyetlen motor van, és a teszt célja
  (visszajelzés-minták demonstrálása) nem igényli motoronkénti
  megkülönböztetést.
- **Envelope/frekvencia-alapú egyedi hullámformák (Android 13+,
  `VibrationEffect.Composition.addPrimitive` haladó paraméterezése)**: ez
  már nem egy-két csúszkával leírható beállítás, hanem egy teljes
  hullámforma-szerkesztő lenne - ha ez is kell, jelezd, külön kiegészítő
  fejlesztésként megoldható.

## Fő hivatkozások

- https://developer.android.com/reference/kotlin/android/os/VibrationEffect
- https://developer.android.com/reference/android/os/Vibrator
- https://developer.android.com/develop/ui/views/haptics/haptics-apis
- https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects
- https://developer.android.com/develop/ui/views/haptics/haptic-feedback
