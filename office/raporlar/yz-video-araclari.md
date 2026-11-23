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
