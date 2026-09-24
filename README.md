# Rezgés Tesztelő

Android alkalmazás, amely különböző rezgésmintákat játszik le, elsődlegesen vak
felhasználók számára végzett haptikai visszajelzés-tesztekhez. TalkBack-kel
kompatibilis.

## Mit csinál a program

Tíz rezgésmintát tud lejátszani, és bármelyik leállítható a Leállítás
gombbal:

1. **Állandó mód** – folyamatos, gyakorlatilag szünet nélküli rezgés,
   beállítható hosszal, mindig kis átfedéssel, hogy ne legyen érzékelhető
   megszakítás.
2. **Pulzáló mód** – szabályos ritmusú impulzusok, a Beállításokban
   megadott hosszal és szünettel.
3. **Következetlen mód** *(korábban "Kiszámíthatatlan mód" - lásd lent,
   miért lett átnevezve)* – a Beállításokban megadott hossz és szünet
   körül, a "Kiszámíthatatlanság mértéke" csúszkával vezérelt mértékben
   szóró rezgések.
4. **Vákuum / szívó mód** – fokozatosan erősödő rezgés, amit hirtelen
   leállás követ. Ez a mód a forráskódban megvolt, de a felhasználói
   felületre nem volt bekötve; most bekötöttem, mivel a kód készen állt rá.
5. **Kiszámíthatatlan mód** *(új)* – lásd a következő szakaszt.
6. **Kalapács mód** *(új)* – lásd a "Kalapács mód" szakaszt lent.
7. **Rövid-hosszú váltakozás** *(új)* – rövid, majd hosszú rezgés fix,
   minden ismétlésnél azonos időzítésű mintázatban.
8. **Fokozatos erősödés** *(új)* – alacsonyról indulva folyamatosan
   erősödik a maximumig, majd újrakezdi.
9. **Fokozatos gyengülés** *(új)* – a beállított erősségről indulva
   fokozatosan gyengül majdnem nulláig, majd újrakezdi.
10. **Hullámzó intenzitás** *(új)* – az erősség folyamatosan hullámzik
    fel-le, szinusz-görbe szerint.

Minden gombhoz `contentDescription` tartozik, ezért TalkBack alatt a gomb
látható felirata helyett a hosszabb, cselekvést leíró szöveg hangzik el (ez a
viselkedés az eredeti kódban is megvolt, nem az én módosításom vezette be).

## "Következetlen mód" átnevezés és az új "Kiszámíthatatlan mód"

A korábbi 3. gomb ("Kiszámíthatatlan mód") át lett nevezve **"Következetlen
mód"**-ra (a működése nem változott), mert a kérés szerint kellett egy
ténylegesen, jóval erősebben véletlenszerű mód is "Kiszámíthatatlan mód"
néven, és a kettő világos megkülönböztetéshez különböző nevet kapott.

Az új, 5. gombként elérhető **Kiszámíthatatlan mód** minden lehetséges
paramétert egymástól függetlenül, széles tartományban véletlenszerűsít
lüktetésenként - nem csak a hosszt és a szünetet, hanem az erősséget és
magát a rezgés "típusát" is (egyéni hullámforma / előre definiált effektus
/ összetett primitívek / Android 16-os envelope-effektus közül
véletlenszerűen választva, amelyik éppen elérhető a készüléken), és
ciklusonként 1-3 lüktetésből álló, véletlenszerű belső résekkel tagolt
"eseményeket" játszik le. Ezért nem korlátozza a Beállítások
duration/pause/rezgéstípus értéke - azok csak a Következetlen módot
vezérlik. A cél kifejezetten az volt, hogy ne alakulhasson ki idővel
felismerhető ritmus vagy minta. A `VIBRATION_API_RESEARCH.md`-ben
dokumentáltam a hozzá használt `Composition.addPrimitive(id, scale, delay)`
és `VibrationEffect.BasicEnvelopeBuilder` API-k pontos működését és a
hivatalos tervezési ajánlásokat is.

**Fontos korlát, amit érdemes tudni**: sok, főleg olcsóbb, ERM-motoros (nem
LRA) készülék hardver szinten egyáltalán nem támogatja az
erősségszabályzást (`hasAmplitudeControl()` hamisat ad) - ezt a
Beállítások "Eszköz képessége" szakasza is kiírja. Ilyen készüléken az
erősség ténylegesen nem tud változni - ez nem szoftverhiba, hanem a
motor fizikai korlátja. Hogy a mód ettől függetlenül is érezhetően
változatos maradjon, az "egyéni hullámforma" íz lüktetésenként
véletlenszerűen választ egy sima, folytonos impulzus és egy apró,
mikro-lüktetésekből álló, "recés" sorozat között - ez a hullámforma
*szerkezetét* teszi véletlenszerűvé, ami erősségszabályzás nélkül is
másképp érződik.

**Több "random mód" és ismétlődés-elkerülés**: a Kiszámíthatatlan mód négy
különböző esemény-stratégia (BURST, SPARSE, ROLLING, PAIRED - lásd
`VIBRATION_API_RESEARCH.md`) között is véletlenszerűen vált, hogy ne csak
az egyes értékek, hanem a véletlenszerűség *jellege* (ritmusa, sűrűsége) is
változzon - egy mindig azonos ritmusú lüktetés-sorozat, még ha az értékei
véletlenszerűek is, idővel felismerhetővé válna. Emellett a stratégia-, íz-
és effektus-választás nem ismétli meg kétszer egymás után ugyanazt
(`RepeatAvoidingPicker`) - ez a játék-hangtervezésben bevett "Repeat
Prevention" technika (Unity Audio Random Container, RNGNeeds könyvtár)
alkalmazása, mert kutatás szerint a matematikailag helyes, egyenletes
véletlen is "csomósnak" érződik az embereknek.

## Kalapács mód

A hatodik, új gomb a lehető legerősebb rezgést indítja, folyamatosan, de
nagyon apró (25-60ms) szünetekkel megszakítva - mintha valaki ismételten
lesújtana egy kalapáccsal. Fontos különbség a Kiszámíthatatlan módhoz
képest: itt szándékosan **nem** véletlenszerű az erősség - az mindig a
lehető legnagyobb -, csak az ütések apró időzítése kap enyhe, emberi
jellegű ingadozást, hogy ne érződjön robotikusan egyenletesnek.

Amikor a készülék jelzi a támogatását, a mód az `EFFECT_HEAVY_CLICK` előre
definiált effektust használja - ezt kifejezetten erős, hirtelen "ütés"
érzetre tervezték, és gyakran a gyártó hangolja az adott hardverre, ezért
hitelesebb "csattanást" ad, mint egy generikus impulzus. Ha nem elérhető,
egy 255-ös (maximális) erősségű, rövid impulzusra esik vissza - ez
biztonságosan kérhető erősségszabályzás nélküli hardveren is, mert a
hivatalos dokumentáció szerint minden nem nulla erősségérték automatikusan
100%-ra kerekítődik ilyen eszközön (lásd `VIBRATION_API_RESEARCH.md`).

## Négy determinisztikus mód: Rövid-hosszú váltakozás, Fokozatos erősödés/gyengülés, Hullámzó intenzitás

Ez a négy mód - a Kiszámíthatatlan és a Kalapács móddal szemben -
szándékosan **nem** véletlenszerű: a mintázatnak minden ismétlésnél
pontosan ugyanazzal az időzítéssel kell futnia. Ehhez nem saját
`delay()`-alapú Kotlin-ciklust használnak, hanem a natív
`VibrationEffect.createWaveform(timings, amplitudes, repeat=0)`
mechanizmust - egyetlen hívással a rendszer maga ismétli a hullámformát a
végtelenségig, ami garantáltan azonos időzítést ad, mert nem a mi
coroutine-unk (apró ütemezési ingadozásoknak kitéve), hanem a platform
saját, natív rezgésütemezője hajtja végre.

Mindegyik a meglévő "Rezgés hossza" és/vagy "Szünet hossza" beállítást
használja fel új beállítás bevezetése nélkül: a Rövid-hosszú váltakozás a
hosszhoz és a szünethez, a rámpák és a hullám a "Rezgés hossza" értéket a
rámpa/ciklus teljes időtartamaként.

A Fokozatos erősödés/gyengülés és a Hullámzó intenzitás nem az Android
16-os envelope API-t használja (ellentétben a Kiszámíthatatlan móddal),
hanem sok apró (30ms-es) lépésből épít egy lépcsőzetes közelítést a
folytonos görbéhez - ez API 26-tól (Android 8.0) mindenhol működik, nem
csak a legújabb készülékeken. Erősségszabályzás nélküli hardveren mindhárom
az on/off arányt (duty cycle-t) modulálja amplitúdó helyett, ugyanazzal a
technikával, amit a Kiszámíthatatlan mód mikro-lüktetés-sorozata is használ.

## Amit hozzáadtam: "Folytatás képernyőzár után is" jelölőnégyzet

### A probléma

Az eredeti kód a rezgés-ciklust az Activity saját `CoroutineScope`-jában
futtatta. Ez azt jelenti, hogy amint a rendszer az Activity-t (és vele a
folyamatot) leállítja vagy kilövi – ami képernyőzár után vagy háttérbe
kerülés után idővel gyakorlatilag mindig bekövetkezik –, a rezgés
megszakad, kiszámíthatatlan időpontban.

### A megoldás

Új `VibrationService` osztály végzi a rezgés-ciklust, az Activity-től
függetlenül:

- Ha a jelölőnégyzet **be van jelölve**, a szolgáltatás
  `ServiceCompat.startForeground(...)` hívással előtér-szolgáltatássá
  (foreground service) lép elő, `specialUse` típussal (ez az Android 14-ben
  bevezetett típus, amit pontosan erre az esetre – egyéb kategóriába nem
  sorolható, folyamatos háttérműködésre – szántak, futásidejű előfeltétel
  nélkül). Emellett egy `PARTIAL_WAKE_LOCK`-ot is tart, mert a CPU
  képernyőzár alatti alvása önmagában a foreground service melletti is
  pontatlanná tehetné a `delay()` hívások időzítését. Egy állandó
  értesítés jelzi a futást, benne egy Leállítás gombbal.
- Ha a jelölőnégyzet **nincs bejelölve**, a szolgáltatás rendes (nem
  előtér-) szolgáltatásként fut, amit a rendszer – szándékosan, az eredeti
  viselkedést megközelítve – pár másodperc–néhány perc múlva leállíthat,
  ha az app háttérbe kerül. Így a jelölőnégyzet ki/be állása ténylegesen
  érzékelhető különbséget okoz, nem csak látszólagosat.

A jelölőnégyzet állapota `SharedPreferences`-ben megmarad az újraindítások
között.

### Miért ezt a dokumentációt követtem

Az Android 14 (API 34) célzása esetén minden előtér-szolgáltatáshoz kötelező
típust megadni; típus nélkül `MissingForegroundServiceTypeException`. A
`specialUse` típus az egyetlen, amelyik nem igényel se futásidejű engedélyt,
se speciális szerepkört (ellentétben pl. a `systemExempted` típussal, amely
csak rendszeralkalmazásoknak, eszközkezelőknek, VPN-eknek stb. engedélyezett)
– csak egy szöveges indoklást (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) a
manifestben, amit Google Play-re való feltöltéskor néznek át. Mivel az app
nem esik egyik konkrétabb kategóriába (kamera, mikrofon, helymeghatározás,
médialejátszás stb.) sem, ez a hivatalosan javasolt típus erre a célra.
Forrás: [Android fejlesztői dokumentáció – Foreground service types are
required](https://developer.android.com/about/versions/14/changes/fgs-types-required).

## Beállítások képernyő (5. gomb)

A főképernyő ötödik, utolsó gombja megnyitja a Beállítások képernyőt, ami a
`VIBRATION_API_RESEARCH.md`-ben dokumentált teljes rezgés-API felületet
szabályozhatóvá teszi: erősség, hossz, szünet, a Kiszámíthatatlan mód
szórásának mértéke, valamint a rezgés típusa (egyéni hullámforma / előre
definiált effektus / összetett primitívek). A képernyő tetején egy "Eszköz
képességei" szakasz kiírja, hogy az adott telefonon melyik funkció érhető el
és melyik nem - futásidejű ellenőrzéssel, nem csak Android-verzió alapján
következtetve. A Vákuum mód szándékosan nem használja ezeket a
beállításokat (a duration/pause és a rezgéstípus kivételével a szünetnél),
mert a fokozatosan erősödő ütem a lényege.

Ugyanitt van a "Gomb ismételt megnyomása újraindítja a rezgést" kapcsoló is,
részletes leírással: alapértelmezésben bekapcsolva (minden gombnyomás
újraindítja a rezgést, ahogy korábban is), kikapcsolva pedig egy már aktív
mód gombjának ismételt megnyomása nem csinál semmit.

## Javított hiba

Az eredeti kód `findViewById<Button>(R.btnConsistent)` (és a másik három
gombnál is) hibás hivatkozást tartalmazott – helyesen `R.id.btnConsistent`
kellett volna. Enélkül a projekt nem fordult volna le. Ezt minden gombnál
javítottam.

## TalkBack-ellenőrzés

- **Minden interaktív elem feliratozott**: mind az öt gomb rendelkezik
  `contentDescription`-nel, a jelölőnégyzet `android:text`-je pedig
  automatikusan felolvasásra kerül a `CheckBox` widget natív
  kisegítő-lehetőség támogatása miatt, kiegészülve a bejelölt/nem bejelölt
  állapot bemondásával – ezt nem a `contentDescription` biztosítja, hanem a
  widget saját `Checkable` állapota, amit a `contentDescription` megadása
  nem nyom el.
- **Működő vezérlők**: minden gomb `OnClickListener`-rel van ellátva, ezek
  mind a szolgáltatás megfelelő `Intent`-jét indítják el; nincs üres vagy
  csak-vizuális gomb.
- **Explicit bemondás**: minden állapotváltásnál (mód indítása, leállítás, a
  jelölőnégyzet ki/be kapcsolása) a kód `announceForAccessibility()`-t hív,
  ami TalkBack esetén azonnali szóbeli visszajelzést ad – ez fontos, mert a
  gombnyomás vizuális visszajelzése (pl. gomb "lenyomott" állapota)
  önmagában nem közli, hogy *melyik* rezgésmód indult el.
- **Amit nem tudok ellenőrizni innen**: a tényleges TalkBack-kel való
  felolvasást csak valós eszközön vagy emulátoron lehet leellenőrizni,
  screen reader nélküli statikus kódelemzéssel nem. A fentiek a kód alapján
  garantáltan helyesen vannak bekötve, de a végső ellenőrzést érdemes
  elvégezned egy Android-eszközön bekapcsolt TalkBack mellett.

## Fordítási korlátozás – ezt nem tudtam elvégezni

Nem tudtam ténylegesen lefordítani a projektet `.apk` fájllá. Ennek a
környezetnek (ahol ez a kód készült) nincs telepített Android SDK-ja,
Gradle-je és nincs hálózati hozzáférése a Google Maven-tárolóhoz vagy a
Gradle disztribúciós szerverekhez – ezek nélkül egy Android-projekt
egyszerűen nem fordítható. Ez nem szimulált vagy kihagyott lépés: ténylegesen
nem állt rendelkezésre az eszköz hozzá.

A fenti forráskód egy teljes, azonnal megnyitható Android Studio projekt.
A tényleges `.apk` előállításához az alábbi lehetőségek egyike szükséges:

1. **Android Studio** (a legegyszerűbb): nyisd meg ezt a mappát projektként,
   hagyd, hogy szinkronizálja a Gradle-t, majd *Build > Build Bundle(s) /
   APK(s) > Build APK(s)*.
2. **Parancssor**, ha van telepített Android SDK-d és Gradle-ed:
   `./gradlew assembleDebug` (a `gradlew` wrapper szkriptet és a hozzá tartozó
   `.jar`-t Android Studio első megnyitáskor automatikusan legenerálja, ezért
   nincs mellékelve).
3. **GitHub Actions**: be lehet állítani egy workflow-t, amely minden push-nál
   lefordítja az apk-t, és letölthető artifactként teszi elérhetővé – ha
   szeretnéd, ezt is összeállítom.

## Fájlstruktúra

```
app/
  build.gradle
  src/main/
    AndroidManifest.xml
    java/com/example/vibrationtester/
      MainActivity.kt
      VibrationService.kt
    res/
      layout/activity_main.xml
      values/strings.xml
      drawable/ic_launcher.xml
      drawable/ic_launcher_round.xml
      drawable/ic_notification.xml
build.gradle
settings.gradle
gradle.properties
```
