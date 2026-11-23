# YZ Gerçekçi Sahne (Android) — anahtarsız

Senin yazdırdığın senaryodan **fotoğraf gerçekliğinde** sahneler üreten, sesli ve mimikli
bir **MP4 video** hazırlayan uygulama. **Hiçbir API anahtarı istemez.**

## Nasıl çalışır?

```
Senaryo (sen yapıştırırsın: JSON ya da düz metin)
   │  ScriptParser: sahneler, anlatım, altyazı; duygu/ses/efekt tahmini
   ▼
Görseller: Pollinations.ai (ücretsiz, anahtarsız, Flux)  —  ya da galeriden kendi görselin
   ▼
Mimikler (ML Kit, cihaz içi)  +  Sesler (Openverse / Freesound CC0, yoksa sentez)
+ Fon müziği (sentez)  +  Anlatıcı (telefonun Türkçe metin okuma sesi)
   ▼
Önizleme  →  MP4 (H.264 + AAC), galeriye kaydet ve paylaş
```

## Grok ile gerçek video (isteğe bağlı, ücretli)

Ana ekrandaki **"Grok ile gerçek video"** düğmesi istediğin an açılıp kapatılır. Açıkken her sahne
hareketsiz fotoğraf yerine **xAI Grok Imagine Video** ile gerçek videoya dönüşür: karakterler hareket
eder, diyaloglarını kendi sesleriyle (dudak senkronlu) söyler, sahnenin doğal sesi de üretilir.

- Ayarlar → xAI API anahtarı (https://console.x.ai). xAI saniye başına ücretlendirir; uygulama
  üretmeden önce toplam süreyi ve tahmini tutarı gösterip onay ister.
- Sahne başına klip süresi varsayılan **15 sn** (Ayarlar'dan 1–15).
- Sahnenin görseli varsa (Pollinations ya da galeri) videonun ilk karesi olur; yoksa doğrudan tariften üretilir.
- Tek bir sahneyi de çevirebilirsin: kareye dokun → **Grok ile videoya çevir**.

## Karakter referansları

"Karakterler" şeridinden **+ Karakter ekle** → galeriden resim → senaryodaki adını yaz (ör. KOVAN).
Adı geçen (ya da diyalogda konuşan) her sahnede o resim Grok'a referans olarak gönderilir; yüz, saç ve
kıyafet tutarlı kalır. Karakterler cihazda saklanır. Ücretsiz fotoğraf modunda referans kullanılamaz.

## Önizleme

Önizlemeye dokun ya da ⏯ ile **durdur / başlat**; çubukla istediğin saniyeye **sar**.

## Senaryo nasıl verilir?

1. **"Senaryo şablonunu kopyala"** düğmesine bas.
2. ChatGPT, Gemini ya da Claude'a yapıştır ve en alttaki `[BURAYA HİKÂYENİ YAZ]` yerine hikâyeni yaz.
3. Çıkan metni (JSON) uygulamadaki kutuya **yapıştır** ve **"Senaryodan videoyu hazırla"**ya bas.

JSON şart değil, **düz metin** de olur:
- Boş satırla ayrılan her paragraf ya da `Sahne 1`, `Kare 2` gibi başlıklar → bir sahne
- Tanınan etiketler: `Başlık:`, `Görsel:`, `Anlatım:`, `Altyazı:` / `Ekran yazısı:`, `Ses:` / `Efekt:`
- `KOVAN: (İç çekerek) Başımıza iş alacağız…` gibi diyaloglar anlatıcıya okutulur
- Duygu (ağlama, korku, gülümseme…), ortam sesi ve efektler metindeki sözcüklerden tahmin edilir

> En gerçekçi görseller için görsel tarifleri **İngilizce** olmalı; şablon bunu zaten istiyor.

## Görseller

- **Otomatik:** Pollinations.ai'nin ücretsiz Flux modeli. Anahtar istemez ama ücretsiz sunucu
  yoğun olabilir. Uygulama kareleri sırayla üretir, yoğunlukta bekleyip kendiliğinden tekrar dener.
- **Kendi görselin:** Bir kareye dokun → **Galeriden seç**. "Görselleri otomatik üret"i kapatırsan
  uygulama yalnızca sahneleri hazırlar, görselleri sen seçersin.
- Kareye dokunup tarifi düzenleyerek yeniden üretebilir ya da fotoğraf olarak kaydedebilirsin.

## Sesler

- Gerçek kayıtlar **Openverse**'ten (anahtarsız) ya da Freesound'dan (isteğe bağlı ücretsiz anahtar);
  yalnızca **CC0 / kamu malı**, yani telif sorunu yok. Bulunamazsa sesler cihazda sentezlenir.
- **Fon müziği** filmin havasına göre cihazda bestelenir. CapCut'ta kendi müziğini ekleyeceksen kapat.
- **Anlatıcı** telefonun metin okuma motorudur. Doğal gelmiyorsa Ayarlar'dan "Google Konuşma
  Hizmetleri"ni seçip Türkçe ses paketini indir.

## Mimikler

Google ML Kit yüzleri ve göz/ağız noktalarını bulur (cihaz içinde). Yüz hafifçe esnetilerek göz
kırpma, nefes, baş hareketi ve duyguya göre ifade (hüzün, ağlama, korku, öfke, şaşkınlık,
gülümseme) eklenir.

## Kurulum

APK: https://github.com/hilmigok/go-ibax/releases/download/apk-gercekci-latest/yz-gercekci.apk

## Geliştirme

```bash
cd android-yz-gercekci
gradle testDebugUnitTest   # birim testleri
gradle assembleDebug       # APK
```
