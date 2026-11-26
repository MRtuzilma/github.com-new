# Avto Launcher

Android planshet uchun mashina bosh ekrani (launcher). Qorong'i dizayn, landshaft rejim.

## Nimalar bor
- **Spidometr**: GPS bo'yicha tezlik (km/soat), 90 dan oshsa sariq, 120 dan oshsa qizil rangga o'tadi.
- **Soat, sana va batareya**.
- **Musiqa paneli**: qo'shiq nomi, ijrochi, muqova, ⏮ ⏯ ⏭ tugmalari. Yandex Music, Spotify, YouTube Music va boshqalar bilan ishlaydi.
- **6 ta katta tugma**: navigator, musiqa, BT Ekran, Telegram, barcha ilovalar, sozlamalar.
  Tugmani **bosib tursangiz**, unga boshqa ilova biriktirasiz.
- Ekran o'chmaydi, orqaga tugmasi launcherni yopmaydi.

## APK yig'ish

**1-usul: Android Studio (kompyuterda)**
1. Android Studio'ni o'rnating: https://developer.android.com/studio
2. `File → Open` orqali `AvtoLauncher` papkasini oching, Gradle sinxronizatsiyasi tugashini kuting.
3. `Build → Build App Bundle(s) / APK(s) → Build APK(s)`.
4. Tayyor fayl: `app/build/outputs/apk/debug/app-debug.apk`.

**2-usul: GitHub (Android Studio'siz)**
1. GitHub'da yangi repozitoriy oching va shu papkadagi hamma fayllarni yuklang.
2. `Actions` bo'limida "APK yig'ish" avtomatik ishga tushadi (~5 daqiqa).
3. Tugagach, o'sha ishga tushirish sahifasidagi `AvtoLauncher-apk` faylini yuklab oling.

## Planshetda sozlash
1. APK'ni o'rnating va oching.
2. Joylashuvga ruxsat bering (spidometr uchun).
3. Musiqa panelidagi yozuvga bosing → ro'yxatdan **Avto Launcher**'ni yoqing (qo'shiq nomi chiqishi uchun).
4. Uy tugmasini bosing → **Avto Launcher** → **Har doim** tanlang.
   Yoki: Sozlamalar → Ilovalar → Standart ilovalar → Bosh ekran → Avto Launcher.
