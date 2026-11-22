# Metin Animatör (Android)

Yazdığın metni **çevrimdışı** olarak 2D sahne animasyonuna çeviren bir Android uygulaması.
İnternet, API anahtarı veya ücret gerektirmez — her şey cihazda, `Canvas` ile çizilir.

## Nasıl çalışır?

1. Bir cümle yazarsın (Türkçe).
2. `TextParser` metni anahtar kelime kökleriyle tarar (ör. `güneş`, `yağmur`, `araba`, `kuş`, `deniz`, `gece`, `ay`...).
3. Eşleşen kelimeler bir `Scene` tarifine dönüşür (arka plan, zemin, öğeler).
4. `AnimationView` bu tarifi kare kare, zamana bağlı olarak canlandırır.

### Örnek metinler
- `Güneşli bir günde kuşlar uçuyor, araba yolda gidiyor.`
- `Gece gökyüzünde ay ve yıldızlar parlıyor.`
- `Yağmur yağıyor, bulutlar gökyüzünü kaplıyor.`
- `Denizde balıklar yüzüyor, martılar uçuyor.`
- `Kış geldi, kar yağıyor ve dağlar bembeyaz.`
- `Bahçede çiçekler açtı, bir ev ve sallanan ağaçlar var.`

## Desteklenen öğeler

| Kelime (kök) | Öğe |
|---|---|
| güneş / gündüz / yaz | Güneş (ışınları döner) |
| ay / mehtap / hilal | Ay + gece |
| yıldız | Parlayan yıldızlar |
| bulut / sisli | Sürüklenen bulutlar |
| yağmur | Yağmur |
| kar / kış | Kar |
| araba / kamyon | Geçen araba (tekerlekler döner) |
| ağaç / orman | Büyüyüp sallanan ağaç |
| çiçek / gül / lale | Açan çiçekler |
| kalp / aşk / sevgi | Yükselen kalpler |
| kuş / martı | Kanat çırpan kuşlar |
| balık / yunus | Yüzen balıklar (+ su) |
| ev / köşk | Ev |
| insan / çocuk / kadın | El sallayan figür |
| balon | Yükselen balonlar |
| dağ / tepe | Dağlar |
| deniz / göl / su | Su zemini |
| kum / plaj / çöl | Kum zemini |
| gece | Gece arka planı |
| akşam / gün batımı | Gün batımı arka planı |

> Hiçbir kelime eşleşmezse varsayılan bir manzara (güneş + bulut + ağaç) gösterilir.

## Derleme

Gereken: **Android Studio** (Giraffe veya üstü), JDK 17.

```bash
# Android Studio ile:
#  File > Open > android-metin-animator klasörünü seç > Sync > Run

# Komut satırıyla (gradle wrapper jar'ı yoksa önce bir kez oluştur):
gradle wrapper        # gradle-wrapper.jar'ı indirir
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> Not: Depoya ikili dosya (`gradle-wrapper.jar`, PNG simgeler) eklemedim.
> Android Studio projeyi açtığında wrapper'ı otomatik tamamlar; simgeler
> tamamen vektör/adaptif XML olarak tanımlıdır (`minSdk = 26`).

## Yapı

```
app/src/main/java/com/ornek/metinanimator/
├── MainActivity.kt     # Giriş ekranı: metin kutusu + "Canlandır" / "Temizle"
├── TextParser.kt       # Metin -> Scene (kural tabanlı eşleştirme)
├── Scene.kt            # Veri modelleri (Scene, Background, Ground, ElementType)
└── AnimationView.kt    # Canvas tabanlı, zamana bağlı animasyon motoru
```

## Genişletmek

Yeni bir öğe eklemek için:
1. `Scene.kt` içindeki `ElementType`'a değer ekle.
2. `TextParser.kt` içine anahtar kelime kuralını yaz.
3. `AnimationView.kt` içine bir `drawXxx(...)` fonksiyonu ekle ve `onDraw` içinde çağır.
