# Avto Launcher

Android planshet uchun mashina bosh ekrani (launcher). Qorong'i dizayn, landshaft rejim.

## Nimalar bor
- **Spidometr**: GPS bo'yicha tezlik (km/soat), 90 dan oshsa sariq, 120 dan oshsa qizil rangga o'tadi.
- **Soat, sana, batareya, ob-havo** (Open-Meteo, internet kerak) va **kompas** (harakat yo'nalishi).
- **Ko'rinish** (soat panelidagi 🎨 tugma): 6 xil rang, tungi / kunduzgi / avto rejim, o'z fon rasmingiz.
- **Musiqa paneli**: ⏮ ⏯ ⏭ tugmalari oxirgi ishlatilgan musiqa ilovasini boshqaradi (maxsus ruxsat kerak emas).
- **6 ta katta tugma**: navigator, musiqa, BT Ekran, Telegram, barcha ilovalar, sozlamalar.
  Tugmani **bosib tursangiz**, unga boshqa ilova biriktirasiz.
- **Navigator + panel** (soat panelidagi chap yuqori tugma): ekran ikkiga bo'linadi — bir tomonda Yandex Navigator, ikkinchisida ixcham panel (spidometr, soat, ob-havo, musiqa). Android 12L+ da avtomatik, eskilarida qo'lda (ko'rsatma chiqadi).
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
4. Uy tugmasini bosing → **Avto Launcher** → **Har doim** tanlang.
   Yoki: Sozlamalar → Ilovalar → Standart ilovalar → Bosh ekran → Avto Launcher.
