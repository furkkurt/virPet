# VirPet

Reigns tarzı bir sanal evcil hayvan oyunu. Pet'inizi oluşturun, olay kartlarına Yes/No ile cevap verin, statlarını dengede tutmaya çalışın.

## Ekran görüntüsü

Oluşturma ekranında şapka, vücut, yüz ve bacak parçalarını seçip pet'inize isim verirsiniz. Oyun ekranında ise rastgele gelen olay kartlarına cevap vererek açlık, sevgi, kilo ve sağlık çubuklarını dengede tutmaya çalışırsınız.

## Nasıl çalışır?

1. **Oluşturma** — Şapka, vücut, yüz, bacak parçalarını oklarla seçin ve pet'inize isim verin.
2. **Start** — Oyun ekranına geçer.
3. **Olay kartları** — Rastgele kartlar gelir; **Yes** veya **No** seçimi dört stat'ı etkiler:
   - **Hunger** (açlık) — Yükseldikçe pet aç kalır.
   - **Affection** (sevgi) — 0'a düşerse pet evi terk eder.
   - **Weight** (kilo) — Çok düşük veya çok yüksek olursa sağlığa zarar verir.
   - **Health** (sağlık) — 0'a düşerse oyun biter.
4. **Zaman geçişi** — Her turda açlık artar, kilo ve sevgi azalır; aşırı kiloda sağlık düşer.
5. **Game over** — Sağlık veya sevgi 0'a indiğinde oyun biter. **Play again** ile oluşturma ekranına dönersiniz.

## Proje yapısı

```
virPet/
├── PetCreationApp.java     Ana pencere, tüm arayüz (Swing), oyun döngüsü
├── PetGameModel.java       Stat kuralları, zaman geçişi, kaybetme koşulları
├── PetSpriteComposer.java  Parça PNG'lerini 16×48 tuval üzerine birleştirir
├── EventCard.java          Tek olay kartı verisi (id, metin, yes/no deltaları)
├── StatDelta.java          Bir seçimin dört stat'a etkisi
├── EventCardLoader.java    event_cards.cards dosyasını okur
├── EventCardDeck.java      Kartları rastgele çeker (tekrar önleme)
├── GameAudio.java          WAV ses (javax.sound.sampled — Win/Linux)
├── assets/
│   ├── event_cards.cards   44 olay kartı (satır tabanlı format)
│   ├── bg.wav              Arka plan müziği
│   ├── tap.wav             Tıklama sesi
│   ├── death.wav           Ölüm sesi
│   ├── runaway.wav         Kaçış sesi
│   ├── bg.png              Pet penceresi arka planı
│   ├── DS-TERM.TTF         Oyun fontu
│   └── parts/              Pet parçaları
│       ├── hats/           hat0.png, hat1.png, ...
│       ├── legs/           legs0.png, legs1.png, ...
│       ├── bodies/         1/normal/, 1/skinny/, 1/fat/, ...
│       └── faces/          1/normal/, 1/sad/, 1/happy/, ...
├── androidPort/            Android sürümü (Gradle projesi)
│   └── app/src/main/
│       ├── java/.../VirPetActivity.java   Tek dosyalık Android port
│       └── assets/         Masaüstüyle aynı asset'ler (MP3 formatında ses)
└── manual.html             Kod el kitabı (Türkçe)
```

## Çalıştırma

### Masaüstü (Java 8+)

```bash
cd virPet
javac -source 8 -target 8 *.java
java PetCreationApp
```

Ses için ek program gerekmez (WAV, JDK `javax.sound.sampled` ile çalınır).

### Android

```bash
cd androidPort
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Kart formatı

Kartlar `assets/event_cards.cards` içinde satır tabanlı formattadır:

```
card empty_bowl
text
The food bowl is empty. Your pet looks at you with big eyes. Fill it up?
yes hunger=-14 affection=3 weight=6
no hunger=6 affection=-6
end
```

- `card <id>` — Kartı başlatır.
- `text` ile `yes`/`no`/`end` arası — Olay metni.
- `yes`/`no` — `stat=değer` çiftleri; yazılmayan stat 0 kalır.
- `#` ile başlayan satırlar ve boş satırlar yok sayılır.

## Sprite sistemi

Pet görüntüsü 16×48 piksel tuval üzerine alttan üste çizilir:

| Katman | Y | Açıklama |
|--------|---|----------|
| Bacak  | 32 | En altta |
| Gövde  | 16 | Kiloya göre: skinny / normal / fat |
| Yüz    | 16 | Sevgiye göre: sad / normal / happy |
| Şapka  | 0  | En üstte |

## Dokümantasyon

`manual.html` dosyası, projenin tüm Java dosyalarını, kullandığı kavramları ve algoritmalarını başlangıç seviyesinde açıklar.

## Teknolojiler

| Platform | Arayüz | Ses | Dosya okuma |
|----------|--------|-----|-------------|
| Masaüstü | Java Swing | `javax.sound.sampled` (WAV) | `java.nio.file` |
| Android  | Activity + View | `MediaPlayer` (MP3) | `AssetManager` |
