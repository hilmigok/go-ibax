# YZ 3D Video (Android)

Yazdığın metinden **Google Gemini** ile bir 3D sahne tasarlatan, sahneyi cihazda
**OpenGL ES** ile canlandıran ve **MP4 video** olarak galeriye kaydeden uygulama.

## Nasıl çalışır?

```
Metin ──► Gemini (sahne JSON'u) ──► SceneParser (doğrulama + sınırlar)
                                          │
                     ┌────────────────────┴────────────────────┐
                     ▼                                         ▼
          PreviewRenderer (ekranda döngü)        VideoExporter (1280×720, 30 fps, H.264)
                     └──────────── SceneRenderer (OpenGL ES 2.0) ┘
```

1. Gemini'ye bir sistem talimatı (`ScenePrompt.kt`) ile birlikte metnin gönderilir.
   Model; nesneleri, renkleri, ışığı, kamera hareketini, animasyonları ve altyazıları
   JSON olarak döndürür.
2. Her nesne **ilkel şekillerden** (küp, küre, silindir, koni, düzlem) birleştirilir.
   Böylece hazır 3D modele gerek kalmadan araba, insan, ev, roket gibi her şey kurulabilir.
3. Animasyonlar: `move` (zincirlenebilir), `rotate`, `bounce`, `scale`, `orbit`.
   Kamera başlangıç → bitiş arasında yumuşakça hareket eder.
4. Önizleme ve video **aynı** çizim kodunu kullanır; video, önizlemenin birebir aynısıdır.

## Video biçimleri

| Biçim | Boyut | Platform |
|---|---|---|
| Dikey 9:16 (varsayılan) | 1080×1920, 30 fps, H.264 | TikTok, Instagram Reels, YouTube Shorts |
| Yatay 16:9 | 1920×1080, 30 fps, H.264 | YouTube |

Seçilen biçim Gemini'ye de bildirilir; dikeyde model özneyi ortada tutar, kamerayı
yaklaştırır ve dikey hareketleri tercih eder. Dikey videoda altyazı, platformların
alt kısımdaki arayüzüne denk gelmesin diye yukarı alınır. Süre en fazla 20 sn'dir.

## Kurulum ve kullanım

1. APK'yı kur: https://github.com/hilmigok/go-ibax/releases/download/apk-3d-latest/yz-3d-video.apk
2. **Ayarlar**'a gir, Gemini API anahtarını yaz (https://aistudio.google.com/apikey).
   Model adı varsayılan olarak `gemini-2.5-flash`'tır; Google yeni model çıkarırsa buradan değiştirebilirsin.
3. Biçimi seç (Dikey 9:16 / Yatay 16:9) → metni yaz → **YZ ile üret** → önizlemeyi izle → **Videoyu kaydet ve paylaş**.
4. Videolar `Filmler/YZ3DVideo` klasörüne kaydedilir.

**Örnek sahne** düğmesi internet/anahtar olmadan da çalışır.

## Güvenlik notları

- API anahtarı koda veya depoya **girmez**. Kullanıcı tarafından Ayarlar'a girilir,
  `EncryptedSharedPreferences` (Android Keystore) ile şifreli saklanır, ekranda gösterilmez.
- Anahtar yalnızca `generativelanguage.googleapis.com`'a, URL'de değil `x-goog-api-key`
  başlığında gönderilir.
- YZ cevabına güvenilmez: `SceneParser` tüm sayıları sınırlar, nesne/parça/animasyon
  sayısını kısıtlar ve geçersiz alanları varsayılanla doldurur.

## Yapı

```
app/src/main/java/com/ornek/yz3dvideo/
├── MainActivity.kt      # Metin girişi, önizleme, üretme ve dışa aktarma
├── SettingsActivity.kt  # API anahtarı ve model adı
├── SecretStore.kt       # Şifreli ayar deposu
├── GeminiClient.kt      # Gemini REST istemcisi + hata mesajları
├── ScenePrompt.kt       # Gemini'ye verilen sahne şeması/talimatı
├── SceneModel.kt        # Veri modelleri
├── SceneParser.kt       # JSON → Scene3D (doğrulama, sınırlar)
├── Animator.kt          # t anındaki konum/dönüş/ölçek/kamera (saf Kotlin, testli)
├── Meshes.kt            # İlkel şekil ağları
├── SceneRenderer.kt     # OpenGL ES 2.0 çizici (ışık, gökyüzü, altyazı)
├── PreviewRenderer.kt   # GLSurfaceView önizlemesi
├── VideoExporter.kt     # EGL + MediaCodec + MediaMuxer ile MP4
├── VideoFormat.kt       # 9:16 ve 16:9 çıktı boyutları
└── DemoScene.kt         # Çevrimdışı örnek sahne
```

## Derleme

- CI: `.github/workflows/android-3d-build.yml` her değişiklikte testleri çalıştırır,
  APK'yı derler ve `apk-3d-latest` Release'ine yükler.
- Yerelde: Android Studio ile `android-yz-3d-video` klasörünü aç → Run.
- Testler: `gradle testDebugUnitTest`

## Sınırlar

- Görüntü stilize 3D'dir (ilkel şekiller + düz ışık), gerçekçi film değildir.
- Ses yoktur (platformlarda müzik paylaşırken eklenebilir).
- `minSdk = 29` (Android 10+).
