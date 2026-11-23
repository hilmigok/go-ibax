# YZ Gerçekçi Sahne (Android)

Yazdığın metinden **fotoğraf gerçekliğinde** sahneler üreten ve bunları sinematik bir
**MP4 videoya** dönüştüren uygulama. Önceki uygulamalar şekilleri kendisi çiziyordu;
bu uygulama görüntüleri **Google'ın görüntü üretme modelleriyle** (Gemini Image / Imagen)
ürettirir. Bu yüzden sonuç gerçek bir fotoğraf ya da film karesi gibi görünür.

## Nasıl çalışır?

```
Metin ──► Gemini (yönetmen) ──► Senaryo: N çekim, her biri için ayrıntılı İngilizce tarif,
                                kamera hareketi, atmosfer efekti, süre, altyazı
                │
                ▼
        Görüntü modeli (her çekim için 1 gerçekçi kare, 9:16 ya da 16:9)
        └─ Karakter tutarlılığı: önceki kare referans olarak gönderilir
                │
                ▼
        FrameRenderer: Ken Burns kamera hareketi + çapraz geçiş + toz/duman/
        yağmur/kar/kıvılcım/sis + vinyet + film greni + renk düzeltme + altyazı
                │
        ┌───────┴────────┐
        ▼                ▼
  Canlı önizleme    VideoExporter (1080×1920 ya da 1920×1080, 30 fps, H.264)
```

1. **Senaryo:** Gemini'ye bir "yönetmen" talimatı (`StoryboardPrompt.kt`) gönderilir. Model,
   anlatımını gerçek bir film sahnesi gibi çekimlere böler (geniş açı, yakın plan, detay…).
   Her çekim için ışık, lens, açı, kıyafet ve duyguyu anlatan ayrıntılı bir tarif yazar.
2. **Görüntüler:** Her tarif, seçtiğin **üslubun** (sinematik, karanlık fantastik, belgesel,
   epik tarih, bilim kurgu) eklenmesiyle görüntü modeline gönderilir.
3. **Tutarlılık:** Gemini görüntü modellerinde önceki kare referans olarak eklenir. Böylece
   karakterler ve mekân kareden kareye aynı kalır (Ayarlar'dan kapatılabilir).
4. **Video:** Kareler kamera hareketi ve atmosfer efektleriyle canlandırılır. Önizleme ile
   video aynı çizim kodunu kullanır; video, önizlemenin birebir aynısıdır.

## Sesler (v2)

- **Gerçek ses kayıtları:** Gemini her kare için "ne duyulur" sorusunu yanıtlar (ör. *wind ruins*,
  *woman crying*, *distant explosions*). Uygulama bu sözcüklerle **Freesound.org**'da yalnızca
  **CC0 (telifsiz, atıf gerektirmeyen)** kayıtları arar, indirir ve kareye yerleştirir.
  Ücretsiz Freesound anahtarı: https://freesound.org/apiv2/apply → Ayarlar'a yaz.
- **Anahtarsız gerçek kayıtlar:** Freesound anahtarı yoksa sesler **Openverse**'ten (Freesound,
  Wikimedia, Jamendo'yu tarayan açık arama motoru) yalnızca CC0 / kamu malı kayıtlar arasından aranır.
- **CapCut:** CapCut'ın ses kütüphanesine dışarıdan erişilemez ve oradaki sesler yalnızca CapCut içinde
  kullanılmak üzere lisanslıdır. "Fon müziği" anahtarını kapatıp videoyu kaydet, sonra CapCut'ta kendi
  müziğini ekle.
- **Hiçbiri bulunamazsa da ses var:** rüzgâr, yağmur, ateş çıtırtısı, uzak patlamalar, şehir uğultusu ve
  kuş sesleri cihazda sıfırdan sentezlenir (`Synth.kt`).
- **Fon müziği:** filmin havasına göre (hüzünlü, karanlık, epik, gergin, umutlu) cihazda bestelenir.
- **Anlatıcı:** Gemini'nin gerçekçi sesiyle Türkçe seslendirme (`gemini-2.5-flash-preview-tts`). Kare
  süreleri anlatıma göre uzar; anlatıcı konuşurken ortam sesi ve müzik otomatik kısılır.
- Hepsi karıştırılıp videoya **AAC ses kanalı** olarak eklenir; önizlemede de duyulur.

## Mimikler (v2)

Karakterler yapay zekânın ürettiği fotoğraflar olduğu için mimikler fotoğrafın üstünde yapılır
(Blender gibi 3D araçlar burada işe yaramaz):

1. **Google ML Kit** yüzleri ve göz/ağız noktalarını bulur (cihaz içinde, ücretsiz, internetsiz).
2. Yüz bölgesi ince bir ağ (mesh) ile esnetilir: **göz kırpma**, **nefes**, hafif **baş hareketi**
   ve Gemini'nin seçtiği duyguya göre ifade: *hüzün* (kaşların içi kalkar, ağız köşeleri düşer),
   *ağlama* (kısık gözler, titreyen dudak, hıçkırık nefesi), *korku*, *öfke*, *şaşkınlık*, *gülümseme*.
3. Esneme bölge kenarında sıfıra iner; yüz, fotoğrafın geri kalanıyla dikişsiz birleşir.

Uzak/küçük yüzlere mimik uygulanmaz. Ana ekrandaki anahtarlarla ses, anlatıcı ve mimikler ayrı
ayrı kapatılabilir.

Bir kareyi beğenmediysen küçük resmine dokun. Tarifi düzenleyip yalnızca o kareyi
yeniden üretebilir ya da fotoğrafı tek başına kaydedebilirsin.

## Kurulum ve kullanım

1. APK'yı kur: https://github.com/hilmigok/go-ibax/releases/download/apk-gercekci-latest/yz-gercekci.apk
2. **Ayarlar**'a gir ve Gemini API anahtarını yaz (https://aistudio.google.com/apikey).
3. Sahneyi anlat, üslubu ve kare sayısını seç, **Üret**'e bas.
4. Önizleme hoşuna gittiyse **Videoyu kaydet ve paylaş**. Video *Filmler/YZGercekci*,
   fotoğraflar *Resimler/YZGercekci* klasörüne kaydedilir.

### Örnek anlatım

> Savaşla yıkılmış bir şehrin harabeleri. Gökyüzünde fırtına bulutları arasında devasa,
> karanlık kanatlı bir melek süzülüyor. Aşağıda toz ve duman içinde başörtülü iki anne
> çocuklarına sarılmış, ağlıyorlar. Hüzünlü, sinematik.

Üslup: **Karanlık fantastik**, kare sayısı: 4, biçim: Dikey 9:16.

## Modeller ve ücret

| Ayar | Varsayılan | Not |
|---|---|---|
| Senaryo modeli | `gemini-2.5-flash` | Ücretsiz kotada çalışır |
| Görüntü modeli | `gemini-2.5-flash-image` | Tutarlılık referansını destekler |
| | `gemini-3-pro-image-preview` | Daha kaliteli ve daha pahalı |
| | `imagen-4.0-generate-001` / `imagen-4.0-ultra-generate-001` | Çok gerçekçi; referans desteklemez, çocuk figürleri üretmez |

> **Önemli:** Görüntü üreten modeller çoğunlukla Google AI Studio'da **faturalandırmanın açık**
> olmasını ister. Kare başına yaklaşık birkaç sent ödenir. Ücretsiz anahtarla "kota aşıldı"
> ya da "faturalandırma gerekiyor" hatası alırsan sebebi budur.

## Proje yapısı

| Dosya | Görev |
|---|---|
| `StoryboardPrompt.kt` | Yönetmen talimatı, üslup ve oran ekleri |
| `StoryboardParser.kt` | Senaryo JSON'unu doğrular, sınırlar (en fazla 8 kare, 2,5–8 sn) |
| `GeminiClient.kt` | Senaryo ve görüntü istekleri (Gemini Image + Imagen) |
| `Timeline.kt` | Zaman çizelgesi, çapraz geçiş, Ken Burns hesapları |
| `Particles.kt` | Deterministik atmosfer parçacıkları |
| `FrameRenderer.kt` | Bir video karesini çizer (önizleme + video ortak) |
| `VideoExporter.kt` | Canvas → OpenGL dokusu → H.264 + AAC ses → MP4 → galeri |
| `FaceFinder.kt` / `Mimic.kt` | Yüz noktaları (ML Kit) / mimik esnetme hesabı |
| `FreesoundClient.kt` / `AudioDecoder.kt` | CC0 ses arama-indirme / MP3 → PCM |
| `Synth.kt` / `Soundtrack.kt` | Ortam sesi ve müzik sentezi / karıştırma, ducking |

## Geliştirme

```bash
cd android-yz-gercekci
gradle testDebugUnitTest   # birim testleri
gradle assembleDebug       # APK
```

GitHub Actions (`.github/workflows/android-gercekci-build.yml`) her değişiklikte testleri
çalıştırır, APK'yı derler ve Releases'e yükler.
