# Rezgés Tesztelő

Android alkalmazás, amely különböző rezgésmintákat játszik le, elsődlegesen vak
felhasználók számára végzett haptikai visszajelzés-tesztekhez. TalkBack-kel
kompatibilis.

## Mit csinál a program

Négy rezgésmintát tud lejátszani, és bármelyik leállítható a Leállítás
gombbal:

1. **Állandó mód** – folyamatos, gyakorlatilag szünet nélküli rezgés (2
   másodperces impulzusok 100 ms átfedéssel, hogy ne legyen érzékelhető
   megszakítás).
2. **Pulzáló mód** – szabályos ritmusú impulzusok: 400 ms rezgés, 800 ms
   szünet.
3. **Kiszámíthatatlan mód** – véletlenszerű hosszúságú (100–1000 ms) rezgések
   véletlenszerű (200–1500 ms) szünetekkel.
4. **Vákuum / szívó mód** – fokozatosan erősödő rezgés (5 lépésben 40 ms
   alatt), amit hirtelen leállás követ, 600 ms szünettel a következő ciklus
   előtt. Ez a mód a forráskódban megvolt, de a felhasználói felületre nem
   volt bekötve; most bekötöttem, mivel a kód készen állt rá.

Minden gombhoz `contentDescription` tartozik, ezért TalkBack alatt a gomb
látható felirata helyett a hosszabb, cselekvést leíró szöveg hangzik el (ez a
viselkedés az eredeti kódban is megvolt, nem az én módosításom vezette be).

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
