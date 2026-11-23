# YZ ile Video Oluşturma Araçları: Nasıl Çalışırlar?

*Araştırmacı raporu, 23 Kasım 2026. Sade dille yazıldı.*

> **Önemli not:** Fiyatlar ve teknik özellikler çok sık değişiyor. Kaynakların çoğu resmi sayfa değil, karşılaştırma blogları ve bunlar arasında çelişkiler var. Aşağıdaki rakamları yaklaşık kabul edin, satın almadan önce aracın kendi fiyat sayfasına bakın. Bu raporda web sayfaları tek tek açılmadı. Bilgiler arama sonuçlarındaki özetlerden derlendi.

---

## 1. Öne çıkan araçlar

| Araç | Ne yapar? | Kısa not |
|---|---|---|
| **OpenAI Sora** | Metinden video üretiyordu | **Kapatıldı.** OpenAI kapatmayı 24 Mart 2026'da duyurdu. Bloglara göre uygulama 26 Nisan'da, API 24 Eylül 2026'da kapandı. Artık seçenek değil. |
| **Google Veo 3.1** | Metinden veya görselden video üretir, sesi de kendisi üretir (diyalog, efekt, müzik) | Görüntü kalitesinde en çok övülen araç. Klipler kısa (4–8 sn) ama uzatılabiliyor. Google AI Pro aboneliğiyle (~20 $/ay) ya da API ile kullanılıyor. |
| **Kling 3.0** (Kuaishou) | Metinden veya görselden video üretir, sesli ve çok dilli dudak senkronu da yapar | En uygun fiyatlı güçlü seçenek. 3–15 sn klip üretir, ücretsiz kotası var, ücretli plan ~7–9 $/ay. |
| **Runway** (Gen-4.5) | Video üretir ve düzenler: mevcut videoya efekt, stil değişikliği, nesne çıkarma | Her işi iyi yapan "çok yönlü" seçenek. Standart plan ~12–15 $/ay, kredi sistemiyle çalışır. |
| **Luma** (Ray3 / Dream Machine) | Video üretir, gerçek çekime YZ ile görsel efekt ekler (Ray3 Modify) | 1080p, HDR destekli, profesyonel üretim akışına yönelik. |
| **Pika** (2.5) | Hızlı, eğlenceli, stilize kısa klipler üretir | Sosyal medya efektleri ve çok sayıda deneme için uygun. |
| **HeyGen** | Konuşan dijital insan (avatar): metni yazarsınız, avatar konuşur. Dublaj da yapar | Dudak senkronu ve mimikler en gerçekçi bulunan araç. ~29 $/ay'dan başlar. |
| **Synthesia** | Kurumsal eğitim videoları için avatar | Dakika başına daha ucuz (~19 $/ay'dan başlar) ama biraz daha "yapay" görünüyor. |
| **CapCut** (+ Dreamina) | YZ destekli kolay video düzenleme: otomatik altyazı, kesme, efekt. Dreamina ile video da üretir | TikTok tarzı içerik için pratik. |
| **Descript** | Videoyu metin gibi düzenletir: yazıdan bir kelimeyi silince videodan da silinir. YZ asistanı ile otomatik düzenleme yapar | Konuşmalı video ve podcast için ideal. ~24 $/kullanıcı/ay. |

---

## 2. Arkadaki teknoloji, benzetmelerle

### a) Difüzyon: "Karıncalı ekrandan resmi ortaya çıkarmak"
Eski televizyonlardaki karıncalı ekranı düşünün. Model eğitim sırasında milyonlarca videoya adım adım "karınca" (gürültü) ekler ve bu gürültüyü geri temizlemeyi öğrenir. Üretim sırasında tamamen karıncalı bir ekrandan başlar, verdiğiniz metne bakarak bunu adım adım temizler ve sonunda anlamlı bir video ortaya çıkar. Bu, bir heykeltıraşın taştan fazlalıkları yontmasına benzer.

### b) Sıkıştırma: "Önce taslak, sonra detay"
Her pikseli tek tek işlemek çok pahalı olurdu. Bu yüzden video önce küçük bir "özet" hâline sıkıştırılır (gizli/latent alan). Model bu özet üzerinde çalışır, bitince özet tekrar tam videoya açılır. Kabaca, bir filmi önce storyboard'da çizip sonra renklendirmeye benzer.

### c) Transformer ve "uzay-zaman yamaları": "Yapboz parçaları"
Sıkıştırılmış video küçük 3 boyutlu küplere bölünür: en, boy ve zaman. Her küp bir yapboz parçası gibidir. Transformer (ChatGPT'deki kelime işleme mantığının aynısı) her parçanın diğer bütün parçalara bakmasını sağlar. Böylece 1. saniyedeki kedi 5. saniyede de aynı kedi olarak kalır. Bu da **kare tutarlılığının** sırrıdır. Metin ise her adımda "pusula" görevi görür: model sürekli "istenen bu muydu?" diye ona bakar.

### d) Eğitim verisi
Modeller, açıklama metinleri eklenmiş muazzam miktarda video ve görselle eğitilir. Verinin nereden geldiği, yani izin alınıp alınmadığı, bugün davaların ana konusu (bkz. 4. bölüm).

### e) Ses ve dudak senkronu
Yeni nesil modeller (Veo 3.1, Kling 3.0) sesi görüntüyle **aynı anda** üretir. Kapı çarpınca ses de aynı anda gelir. Avatar araçları (HeyGen, Synthesia) ise önce metni yapay sese çevirir, sonra o sesin hecelerine göre ağız ve yüz hareketlerini üretir. Bunu, bir seslendirmeye birebir uyan bir kukla oynatıcısı gibi düşünebilirsiniz.

---

## 3. Tipik iş akışı

**Kullanıcı ne verir?**
- Bir **metin komutu** ("gün batımında sahilde koşan turuncu kedi, sinematik, yavaş çekim")
- İsteğe bağlı olarak bir **başlangıç görseli**, bir karakter referansı, kamera hareketi talimatı ya da mevcut bir video (düzenleme için)
- Avatar araçlarında: **senaryo metni**, avatar seçimi ve dil

**Ne alır?**
- Genelde **4–15 saniyelik** klipler. Daha uzun video için klipler uç uca eklenir veya "uzat" komutu kullanılır.
- Çözünürlük: çoğu araçta **720p–1080p**. Üst modellerde **4K'ya** kadar çıkılıyor.
- Avatar araçlarında dakikalarca süren konuşmalı videolar.

**Maliyet (yaklaşık):**
- Abonelik: ücretsiz kotadan başlayıp **~7–30 $/ay** (Kling en ucuzu, Runway ve Veo orta, HeyGen ve Synthesia ~19–29 $/ay).
- API veya saniye bazlı: **~0,10–0,40 $/saniye**. 8 saniyelik bir klip ~1–3 $ eder.
- Avatar: dakika başına ~0,8–1,5 $ (plan tam kullanılırsa).
- Pratikte iyi bir sonuç almak için genelde aynı klibi **birkaç kez** üretmek gerekir. Bu yüzden gerçek maliyet bu rakamların 3–5 katı olabilir.

---

## 4. Kısıtlar ve riskler

- **Tutarlılık hataları:** Eller veya parmaklar bozulabilir, karakterin yüzü sahneden sahneye değişebilir, yazılar anlamsız çıkabilir, fizik tuhaflaşabilir (su yukarı akar gibi). Uzun ve çok sahneli hikâyede karakteri aynı tutmak hâlâ en zor iş.
- **Telif:** Disney ve Universal, Midjourney'e karşı dava açtı (Haziran 2025). Gerekçe: modelin telifli karakterleri üretmesi ve video modelinin telifli eserlerle eğitilmesi. Benzer bir dava Minimax'e de açıldı. **Tanınmış karakter, marka veya sanatçı tarzı kullanmak risklidir.** Ayrıca birçok ülkede tamamen YZ'nin ürettiği içeriğin telif koruması belirsiz.
- **Deepfake:** Gerçek kişilerin izinsiz taklidi hem yasal hem etik sorun. Sora'nın kapanışı da haberlerde deepfake tartışmalarıyla birlikte anıldı. Ciddi araçlar gerçek kişi yüzü yüklemeyi kısıtlıyor veya kişinin onayını istiyor.
- **Filigran ve etiketleme:**
  - **SynthID** (Google): İnsan gözünün göremediği bir "dijital imza" görüntüye gömülür. Google'a göre Apple, OpenAI, NVIDIA ve ElevenLabs da bunu benimsiyor.
  - **C2PA / Content Credentials:** Dosyaya eklenen bir "içerik kimliği" (nerede, nasıl üretildi bilgisi). Zayıf yanı şu: sosyal medyaya yüklerken veya ekran görüntüsü alınca çoğu zaman silinir. Bu yüzden iki yöntem birlikte kullanılıyor.
  - **AB Yapay Zekâ Yasası, Madde 50:** Ağustos 2026'dan itibaren YZ içeriğinin makineyle tespit edilebilir şekilde işaretlenmesi ve deepfake'lerin açıkça etiketlenmesi zorunlu. Cezası 15 milyon € veya cironun %3'üne kadar çıkabiliyor. TikTok, YouTube ve Instagram da YZ içeriğinin etiketlenmesini istiyor.

---

## 5. Sahibe öneri

| İhtiyaç | Önerilen araç | Neden |
|---|---|---|
| **Viral kısa video, deneme yanılma** | **Kling 3.0** | Ucuz, ücretsiz kotası var, 15 sn'ye kadar sesli klip üretiyor. Bol deneme için ideal. |
| **En yüksek kalite "vay be" sahneleri** | **Veo 3.1** (Google AI Pro) | Görüntü ve ses kalitesi en iyisi. Kısa ama etkileyici sahneler çıkarıyor. |
| **Kısa animasyon ve hikâye, sahne düzenleme** | **Runway** | Üretim ile düzenlemeyi tek yerde topluyor. Karakter referansı ve stil kontrolü iyi. |
| **Konuşan sunucu, anlatım, dublaj** | **HeyGen** | En gerçekçi dudak senkronu. Tek videoyu birçok dile çevirebiliyor. |
| **Kurgu, altyazı, son dokunuş** | **CapCut** (sosyal medya) veya **Descript** (konuşmalı içerik) | Üretilen klipleri birleştirip yayına hazır hâle getirir. |

**Pratik başlangıç önerisi:** Önce **Kling'in ücretsiz kotası + CapCut** ile deneyin (maliyet ~0 $). İşe yararsa kalite için **Veo** veya **Runway** aboneliğine geçin. Konuşan karakter gerekiyorsa HeyGen'i ekleyin. Her zaman kendi karakterlerinizi ve tarzınızı kullanın, ünlü karakter veya gerçek kişi taklit etmeyin, platformların "YZ ile üretildi" etiketini açın.

---

## 6. Bunu kendimize özel yapabilir miyiz?

Evet, ama "özel" üç farklı derinlikte yapılabilir. Benzetmeyle: (a) kendi otomobil fabrikanı kurmak, (b) hazır bir motoru alıp kendi arabana takmak ve ayarlamak, (c) taksi şirketlerinin üstüne kendi çağrı uygulamanı kurmak.

### (a) Sıfırdan kendi temel modelimizi eğitmek

| | |
|---|---|
| **Donanım** | Yüzlerce üst seviye GPU (H100/H200) haftalarca çalışmalı. Open-Sora 2.0 ekibi 224 GPU kullandı. Meta'nın Movie Gen'i 6.144 GPU ve ~1,25 milyon GPU-saat harcadı. |
| **Maliyet** | Bilinen en ucuz örnek Open-Sora 2.0: **~200 bin $** (tek eğitim turu). Deneme turları ve veri hazırlığı hariç. Karşılaştırma olarak Movie Gen ~2,5 milyon $, Step-Video ~1 milyon $ olarak bildiriliyor. Bazı kaynaklar aynı iş için ~107 bin $ diyor; bu çelişki doğrulanamadı. |
| **Süre** | Veri toplama ve temizleme dahil aylar (6–12 ay gerçekçi). |
| **Gereken bilgi** | Araştırmacı seviyesinde derin öğrenme, dağıtık eğitim ve büyük ölçekli veri hattı. Ayrıca milyonlarca **lisanslı** video. |
| **Lisans** | Model tamamen bizim olur. Ama eğitim verisinin lisansı en büyük hukuki risk (bkz. 4. bölümdeki davalar). |
| **Artı** | Tam kontrol, kimseye bağımlılık yok. |
| **Eksi** | Çok pahalı, uzun ve riskli. Sonuç büyük ihtimalle Veo/Kling kalitesinin gerisinde kalır. **Bizim ölçeğimizde mantıklı değil.** |

### (b) Açık kaynak modeli kendi sunucumuzda çalıştırıp ince ayar (LoRA) yapmak

LoRA, modelin tamamını değil küçük bir "eklenti katmanını" eğitmektir. Modele 20–50 örnek görsel veya kısa klip gösterip "bizim karakterimiz ve stilimiz bu" diye öğretiriz. Ana model aynı kalır, sadece küçük bir ek dosya (yüzlerce MB) üretilir.

**Aday modeller (lisans ve donanım):**

| Model | Lisans (ticari kullanım) | Çalıştırmak için VRAM (yaklaşık) |
|---|---|---|
| **Wan 2.1 / 2.2** (Alibaba) | **Apache 2.0**, ticari serbest. En "temiz" lisans. | 1,3B/5B küçük sürüm: ~8–24 GB. 14B büyük sürüm: ~65–80 GB (sıkıştırmayla 24 GB'a iner, yavaşlar). |
| **Mochi 1** (Genmo) | **Apache 2.0**, ticari serbest | ~60 GB (optimizasyonla 24 GB) |
| **LTX-Video / LTX-2** (Lightricks) | Yeni sürümler çift lisanslı: yıllık geliri **~10 milyon $ altı ücretsiz**, üstü ücretli. Eski sürümler farklı olabilir. | En hafif seçenek: ~16–32 GB |
| **HunyuanVideo** (Tencent) | Tencent Community License: aylık aktif kullanıcı **100 milyon altı** ticari serbest. Bir kaynağa göre **AB, İngiltere ve Güney Kore hariç**. Bu kritik, doğrulanmalı. | ~60 GB+ (INT8 ile ~24 GB) |
| **CogVideoX** (Zhipu) | 2B: Apache 2.0. **5B: kendi özel lisansı**, ticari şartlar incelenmeli. | 2B hafif, 5B ~24 GB |

> ⚠️ Lisans ve VRAM bilgileri karşılaştırma bloglarından; kaynaklar arasında çelişki var (ör. bir kaynak HunyuanVideo'yu yanlışlıkla Apache 2.0 diye gösteriyor). Ticari kullanmadan önce her modelin GitHub veya Hugging Face'teki **LICENSE dosyası mutlaka okunmalı**. Ben bu dosyaları doğrudan açıp okumadım.

| | |
|---|---|
| **Donanım** | Deneme ve üretim için 24 GB'lık bir GPU (RTX 4090/5090) yeterli. Büyük modelle LoRA eğitimi için 48–80 GB (A100/H100) rahat ettirir. Kendi makinemiz yoksa kiralanabilir. |
| **Kiralık GPU fiyatı** (RunPod, Kasım 2026 civarı) | RTX 4090: **~0,34–0,74 $/saat**. A100: **~1,2–2,2 $/saat**. H100: **~2–4,4 $/saat**. Vast.ai daha ucuz ama fiyatı oynak. Kaynaklara göre 2026'da fiyatlar arttı. |
| **Maliyet** | Bir karakter LoRA'sı: topluluk raporlarına göre 16–24 GB kartta **8–12 saat**, yani kiralık 4090 ile **~5–10 $**, A100/H100 ile birkaç saatte **~10–30 $**. Bu benim hesabım; doğrudan bir kaynak bulamadım. Üretim tarafında 5 sn'lik bir klip 4090'da birkaç dakika sürer. Birkaç yüz klip için ayda ~50–200 $ GPU kirası öngörülebilir. |
| **Süre** | Kurulum ve ilk LoRA: **1–2 hafta** (öğrenme dahil). |
| **Gereken bilgi** | Orta seviye: Linux, Python, ComfyUI (sürükle-bırak arayüz) ve LoRA eğitim aracı (ör. musubi-tuner, diffusion-pipe). Akademik bilgi gerekmez, topluluk rehberleri bol. |
| **Artı** | Karakter ve stil **tutarlılığı** en iyi bu yolla sağlanır. Verimiz dışarı çıkmaz. Üretim başına ücret yok. Sansür ve kota yok. Wan ile lisans temiz. |
| **Eksi** | Kalite hâlâ Veo/Kling üst modellerinin gerisinde (özellikle ses: açık modellerin çoğunda yerleşik ses yok). Teknik bakım gerekiyor, GPU yönetmek zahmetli. |

### (c) Hazır API'lerin üstüne kendi uygulamamızı kurmak

| | |
|---|---|
| **Donanım** | Yok. Sıradan bir sunucu veya bilgisayar yeterli. Ağır iş sağlayıcıda yapılır. |
| **Maliyet** | Saniye başına ödeme. Veo 3.1 Fast ~0,10–0,12 $/sn, Veo 3.1 Standart ~0,40 $/sn (8 sn klip ≈ 3,2 $). Kling 3.0 Pro ~0,11–0,17 $/sn. Luma Ray3.2 10 sn klip ~0,9–3,6 $. Bir kaynağa göre 30 saniyelik bir reklam, tekrar denemelerle **19–86 $ API ücreti** tutuyor. |
| **Süre** | Basit prototip **birkaç gün**. |
| **Gereken bilgi** | Normal yazılım geliştirme (HTTP API çağırmak, dosya saklamak). YZ uzmanlığı gerekmez. |
| **Lisans / sahiplik** | **Runway:** çıktılar üzerinde mülkiyet iddia etmiyor, ticari kullanımı kısıtlamıyor. **Luma:** ticari kullanım yalnızca aktif ücretli abonelikle; tazminat güvencesi yok; girdilerinizi eğitimde kullanabilir. **Google (Vertex/Gemini Enterprise API):** Veo'yu telif tazminatı kapsamına alıyor; yazılı teyit önerilir. **Kling:** API sahiplik şartlarını bulamadım; ticari kullanım için Pro plan gerektiği bildiriliyor. Bilgiler tek bir karşılaştırma kaynağından, doğrulanmalı. |
| **Artı** | En yüksek kalite ve sesli video hemen kullanılır. Bakım yok. Hızlı başlangıç. Sağlayıcı değiştirmek kolay (Runway API tek yerden Veo ve Kling'e de erişim veriyor). |
| **Eksi** | Klip başına sürekli maliyet. Sağlayıcıya bağımlılık: **Sora örneğinde olduğu gibi servis aniden kapanabilir.** Bir kaynağa göre Google, Gemini API'deki Veo 3.1 önizleme modellerini 22 Ekim 2026'da kapattı ve yeni bir modele yönlendirdi; doğrulanmalı. Karakter tutarlılığı kısıtlı. İçerik politikaları bazı istekleri reddedebilir. |

### Karşılaştırma özeti

| | (a) Sıfırdan | (b) Açık model + LoRA | (c) API üstüne uygulama |
|---|---|---|---|
| İlk maliyet | 200 bin $ – milyonlar | ~0–2.000 $ (GPU kirası; kendi 4090'ımız ~2.000 $) | ~0 $ |
| Klip başı maliyet | Düşük | Çok düşük | Orta–yüksek (~1–3 $ / 8 sn) |
| Kalite | Belirsiz | İyi | En iyi |
| Kendi karakterimiz | Tam | **En iyi** | Sınırlı (referans görselle) |
| Süre | Aylar–yıl | 1–2 hafta | Günler |
| Risk | Çok yüksek | Düşük–orta | Sağlayıcıya bağımlılık |

### Net öneri

**(a)'yı unutun. (c) ile hemen başlayın, (b)'yi paralel olarak deneyin.** Hedef "hibrit" bir yapı: uygulama tek bir arayüz sunsun, arkasında hem API (yüksek kalite, sesli sahneler) hem de kendi Wan+LoRA modelimiz (tutarlı karakter, ucuz toplu üretim) çalışsın. Böylece bir sağlayıcı kapanırsa diğerine geçilir.

### İlk adım: küçük prototip planı (Kodcu ölçeğinde, ~1–2 hafta)

1. **Gün 1–2, API sarmalayıcı:** Basit bir komut satırı aracı veya küçük bir web sayfası. Girdi: metin komutu + isteğe bağlı başlangıç görseli. Çıktı: indirilen MP4. Tek bir sağlayıcıyla başlanır (öneri: Runway API veya Kling API; ikisi de saniye başı ücretli). API anahtarı ortam değişkeninde tutulur, koda yazılmaz.
2. **Gün 3, sağlayıcı soyutlaması:** "VideoSağlayıcı" arayüzü yazılır, ikinci bir sağlayıcı eklenir. Her üretimin maliyeti ve süresi bir log dosyasına kaydedilir.
3. **Gün 4, şablonlar:** Kendi karakterimiz için sabit bir "karakter tarifi" (görünüş, stil, renk paleti) ve referans görseller tanımlanır. Her komuta otomatik eklenir. Böylece tutarlılık artar.
4. **Gün 5, birleştirme:** Üretilen kısa klipler ffmpeg ile uç uca eklenir, altyazı ve müzik konur. Sonuç dikey (9:16) sosyal medya formatında çıkar.
5. **Gün 6–10, deney (b):** Kiralık bir RTX 4090'da (RunPod, ~0,5 $/saat) ComfyUI ile **Wan 2.1/2.2'nin küçük sürümü** kurulur. Karakterimizin 20–30 görseliyle bir LoRA eğitilir (bütçe ~20 $). Aynı komutlarla API çıktısıyla yan yana karşılaştırılır.
6. **Karar noktası:** Kalite, maliyet ve tutarlılık tablosu çıkarılır. Hangi işin hangi yoldan gideceğine karar verilir.

**Toplam prototip bütçesi:** ~50–100 $ (API denemeleri + GPU kirası).
**Riskler:** API anahtarlarının güvenli saklanması; Wan lisansının (Apache 2.0) LICENSE dosyasından teyidi; üretilen videolarda YZ etiketi (AB Madde 50).

---

## Kaynaklar

**Araç karşılaştırmaları ve fiyatlar**
- https://rangy.ai/blog/ai-video-generators-compared-2026/
- https://www.digitalapplied.com/blog/after-sora-best-ai-video-generators-2026-runway-kling-veo
- https://www.curionic.net/2026/06/runway-vs-kling-vs-veo-vs-sora-ai-video-generator-comparison-2026.html
- https://comparebestai.com/articles/ai-video-generator-comparison
- https://www.getaiperks.com/en/blogs/44-best-ai-video-generators-2026
- https://invideo.io/blog/kling-3-vs-veo-3-1/
- https://picsart.com/compare-models/veo-3-1-vs-kling-3-0/
- https://magichour.ai/blog/kling-30-vs-veo-31
- https://dev.to/genra_ai/luma-ray3-complete-guide-ray314-ray3-modify-luma-agents-explained-2026-1fd6
- https://lumalabs.ai/blog/luma-ai-updates
- https://dreamina.capcut.com/ai-video/ai-video-gen-stack-for-content-creators-2026
- https://fliki.ai/blog/best-ai-video-generators

**Sora'nın kapanışı**
- https://www.axios.com/2026/03/24/openai-discontinue-sora-video-app
- https://www.cbsnews.com/news/sora-ai-openai-discontinues
- https://www.opb.org/article/2026/03/24/openai-pulls-the-plug-on-sora-the-viral-ai-video-app-that-sparked-deepfake-concerns
- https://www.pcgamer.com/software/ai/openai-discontinues-sora-video-generation-app-disney-pulls-out-of-usd1-billion-investment-deal

**Avatar araçları**
- https://www.aistudios.com/ai-studios-vs-heygen-vs-synthesia-pricing
- https://www.arcade.software/post/heygen-pricing
- https://magichour.ai/blog/synthesia-pricing-2026
- https://dupple.com/learn/heygen-vs-synthesia

**Teknoloji (difüzyon transformer)**
- https://arxiv.org/abs/2401.03048 (Latte, video difüzyon transformer)
- https://arxiv.org/pdf/2503.20314 (Wan, açık video modeli)
- https://arxiv.org/pdf/2503.12964 (NVIDIA NeMo, video temel modeli eğitimi)

**Telif, deepfake, etiketleme**
- https://news.justia.com/wp-content/uploads/2025/06/Disney-Universal-v.-Midjourney-Complaint.pdf
- https://news.bloomberglaw.com/ip-law/disneys-output-focus-puts-fresh-spin-on-ai-copyright-litigation
- https://blog.google/innovation-and-ai/products/making-it-easier-to-understand-how-content-was-created-and-edited/
- https://blog.google/company-news/outreach-and-initiatives/public-policy/eu-ai-act-transparency-code-of-practice/
- https://heise.de/-11302400
- https://iptc.org/news/eu-ai-transparency-code-of-practice-june-2026/
- https://www.resemble.ai/resources/generative-ai-watermarking-opportunities-challenges

**6. bölüm: Kendi modelimiz / açık modeller / API'ler**
- https://arxiv.org/abs/2503.09642 (Open-Sora 2.0: ~200 bin $ eğitim)
- https://the-decoder.com/open-sora-2-0-achieves-competitive-ai-video-quality-at-one-tenth-the-training-cost-of-commercial-models/
- https://introl.com/blog/video-generation-ai-infrastructure-sora-scale-models-guide-2025 (Movie Gen GPU sayısı)
- https://chatforest.com/reviews/open-sora-2-hpc-ai-tech-200k-training-open-source-video/
- https://www.thundercompute.com/blog/best-open-source-ai-video-generation-models
- https://www.spheron.network/blog/ai-video-generation-gpu-guide/
- https://www.forasoft.com/learn/ai-for-video-engineering/articles-ai/self-hosting-hunyuanvideo-cogvideox-mochi-ltx
- https://techiehub.blog/best-local-ai-video-generator/
- https://ltx.io/blog/open-source-video-generation-models-guide
- https://github.com/kohya-ss/musubi-tuner/discussions/455 (Wan LoRA eğitim süreleri)
- https://civitai.com/articles/12980 (Wan LoRA rehberi, VRAM)
- https://rocm.blogs.amd.com/artificial-intelligence/finetuning-wan-part1/README.html
- https://www.runpod.io/pricing
- https://vast.ai/blog/how-much-does-it-cost-to-rent-a-gpu-in-the-cloud
- https://neurohive.io/en/guides/cloud-gpu-pricing/
- https://docs.dev.runwayml.com/guides/pricing/
- https://www.beri.net/article/video-generation-api-pricing-veo-runway-luma-sora-per-second-retakes
- https://apiframe.ai/blog/ai-video-api-pricing-2026
- https://awesomeagents.ai/pricing/video-generation-pricing/
