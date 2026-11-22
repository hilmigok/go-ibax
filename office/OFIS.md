# Kişisel Ofis — Çalışma Kuralları

Bu depo, birden fazla Claude ajanının birlikte çalıştığı bir "ofis" olarak kullanılır.
Her ajan ayrı bir Claude Code bulut oturumunda çalışır; sahibi (ofis sahibi) hepsine
claude.ai/code veya Claude uygulamasından (tablet/bilgisayar) ulaşır.

## Roller

| Rol | Dosya | Görev |
|-----|-------|-------|
| Müdür | `.claude/agents/mudur.md` | Görevleri alır, böler, ajanlara dağıtır, panoyu günceller, sahibine özet verir |
| Kodcu | `.claude/agents/kodcu.md` | Kod yazar, hata düzeltir, test ekler |
| Araştırmacı | `.claude/agents/arastirmaci.md` | Kodu/dokümanı/webi inceler, rapor çıkarır, seçenek önerir |
| İnceleyici | `.claude/agents/inceleyici.md` | Kodcunun işini inceler, test çalıştırır, onay/ret verir |
| Viral | `.claude/agents/viral.md` | YouTube, TikTok ve Instagram'daki viral animasyonları tarar, her gün rapor hazırlar |

## İş akışı

1. Sahip, Müdür oturumuna bir iş yazar.
2. Müdür işi görevlere böler, panoya ekler (durum: `bekliyor`) ve ilgili ajana mesaj gönderir.
3. Ajan işe başlayınca durumu `çalışıyor` yapar (Müdür üzerinden), bitince Müdür'e kısa rapor gönderir.
4. Kod değişiklikleri İnceleyici'ye gider; onaylanınca durum `bitti` olur.
5. Müdür, sahibe Türkçe kısa özet verir.

## Kurallar

- Dil: Sahiple ve ajanlar arası iletişim **Türkçe**; kod, commit mesajları ve kod yorumları İngilizce olabilir.
- Her ajan **kendi dalında** çalışır: `ofis/<rol>/<kısa-görev-adı>`. `main`'a doğrudan push yok.
- Pull request yalnızca sahip isterse açılır.
- Bulut makineleri geçicidir: kalıcı olması gereken her şey commit + push edilir.
- Gizli bilgi (anahtar, parola) depoya veya mesajlara yazılmaz.
- Emin olunmayan, geri alınması zor işlerde önce sahibe sorulur.

## Rapor formatı (ajan → Müdür)

```
GÖREV: <ad>
DURUM: bitti | takıldı | sürüyor
YAPILAN: <1-3 madde>
DAL/COMMIT: <varsa>
SONRAKİ: <varsa öneri veya engel>
```
