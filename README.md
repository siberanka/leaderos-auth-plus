# LeaderOS Auth Plus

**Minecraft sunucuları için LeaderOS panel kimlik doğrulama eklentisi.** **Bukkit/Spigot/Paper/Folia**, **BungeeCord** ve **Velocity** proxy sunucularını destekler.

> **Sürüm:** 1.0.5-fork  
> **Yazarlar:** leaderos, efekurbann, siberanka

---

## 🇹🇷 Türkçe

### Özellikler

#### 🔐 Kimlik Doğrulama Sistemi
- **Giriş / Kayıt / 2FA** — LeaderOS panel API ile entegre tam kimlik doğrulama akışı
- **Oturum Desteği** — Oyuncular yeniden bağlandığında otomatik olarak giriş yapmasını sağlayan güvenli geçişler (varsayılan: aktif)
- **Şifre Doğrulama** — Minimum/maksimum şifre uzunluğu, güvensiz şifre kara listesi
- **E-posta Doğrulama** — İsteğe bağlı e-posta doğrulama, kayıt sonrası atma desteği
- **Yanlış Şifrede Atma** — Yapılandırılabilir yanlış şifre koruması
- **Kimlik Doğrulama Süresi** — Belirli süre içinde giriş yapmayan oyuncular atılır

#### 📱 Bedrock / Floodgate Desteği (Bukkit)
- **Otomatik Form Menüleri** — Bedrock oyuncularına Floodgate `CustomForm` arayüzü ile giriş, kayıt ve 2FA formları gönderilir
- **Yapılandırılabilir Gecikme** — Formlar, oyuncu girdikten sonra yapılandırılabilir bir gecikmeyle gösterilir (varsayılan: 2 saniye)
- **Exploit Korumaları** — Form kilidi (çift gönderimi engeller), gönderimler arası bekleme süresi, oturum durumu doğrulama
- **Otomatik Yeniden Gönderim** — Hata veya geçersiz giriş sonrasında formlar otomatik yeniden gösterilir
- **Tam Yerelleştirme** — Tüm form metinleri `lang/en.yml` ve `lang/tr.yml` ile yapılandırılabilir

#### 🛡️ Güvenlik
- **IP Bağlantı Limiti** — IP başına yapılandırılabilir maksimum eşzamanlı bağlantı sayısı, atomik sayaç ile race condition korumalı (Bukkit, BungeeCord, Velocity)
- **Komut Engelleme** — Giriş yapmamış oyuncular yalnızca kimlik doğrulama komutlarını kullanabilir
- **Tab-Complete Gizleme** — Giriş yapmamış oyunculara sadece auth komutları gösterilir, namespace'li komutlar da filtrelenir (Bukkit 1.13+, BungeeCord)
- **Komut Cooldown** — Giriş yapmamış oyuncular için komut spam koruması (Bukkit, Velocity)
- **Gelişmiş Yan Hesap Takibi** — Oyuncunun IP adresi değişse dahi donanım/hesap eşleşmeleriyle yan hesap (alt-account) tespit edilir; çoklu hesap kullanımları loglanır ve anlık olarak Discord'a (Webhook) bildirim gönderilir
- **Eylem Engelleme** — Giriş yapmamış oyuncular hareket edemez, sohbet edemez, etkileşimde bulunamaz, blok kırıp/koyamaz
- **Anti-Bot** — IP tabanlı bağlantı sınırlaması bot saldırılarını önlemeye yardımcı olur
- **Kullanıcı Adı Doğrulama** — Büyük/küçük harf uyumsuzluğu tespiti ve geçersiz kullanıcı adı engelleme
- **Konsol Log Filtreleme** — Kimlik doğrulama komutları konsolda gizlenir (şifre sızıntısını önler)
- **Thread-Safe Oturum Yönetimi** — ConcurrentHashMap ile güvenli eşzamanlı erişim
- **Veri Kaybı (Crash) Koruması (SQLite)** — `journal_mode=WAL` ve `synchronous=NORMAL` entegrasyonu sayesinde sunucu çöküşlerinde veritabanının sıfırlanması veya kilitlenmesi engellenmiştir.
- **Kesin Bellek (Memory Leak) Koruması** — Önbellek haritaları, komut süreleri vb. bilgiler, oyuncular çıkış komutu veya `PlayerQuitEvent` tetiklediği andan itibaren doğrudan GC vasıtasıyla bellekten atılır.
- **Vanilla İstismar (Exploit/Dupe) Önleyici** — Giriş onaylanmadan veya kayıt bitmeden önce gerçekleşen eşya sürükleme (`InventoryDrag`), el değiştirme (`SwapHandItem`) ve anlık can kaybında (`PlayerDeath`) eşyaların dupe edilmesi tamamen engellendi.

#### 🌍 Çoklu Dil Desteği
- **İngilizce (`en`)** ve **Türkçe (`tr`)** dil dosyaları dahil
- Tüm mesajlar `lang/` dizinindeki YAML dosyaları ile tamamen yapılandırılabilir
- **Okaeri Orphan Config:** Konfigürasyon ve dil dosyaları güncellendiğinde artık kullanılmayan, eski veya yanlış yazılmış mesajları/anahtarları otomatik olarak temizler.
- **Discord Mesaj Desteği:** Yan hesap bulunduğunda atılan webhook bildirimleri, her dil dosyası için özel mesaj, başlık, kullanıcı adı ve bot adı ile yapılandırılabilir.

#### 🖥️ Çoklu Platform

| Platform | Özellikler |
|----------|-----------|
| **Bukkit / Spigot / Paper** | Tam auth, Bedrock Floodgate formları, başlıklar, boss bar, spawn ışınlama, AuthMe API köprüsü, tab-complete koruması (1.13+), komut cooldown |
| **Folia** | Tam Folia uyumluluğu (`folia-supported: true`) |
| **BungeeCord** | Auth sunucuya yönlendirme, komut/sohbet engelleme, tab-complete gizleme, IP limiti |
| **Velocity** | LimboAPI entegrasyonu, özel dünya desteği, tam auth akışı, komut cooldown, IP limiti |

#### 📊 Ek Özellikler
- **Başlık & Boss Bar** — Özelleştirilebilir başlık ve boss bar kimlik doğrulama uyarıları
- **Spawn Işınlama** — Kimlik doğrulama sırasında oyuncuları spawn'a ışınlama
- **Oyun Modu Zorlama** — Giriş yapmamış oyuncular için survival modu zorlama
- **Auth Sonrası Gönderme** — Kimlik doğrulama sonrası başka sunucuya yönlendirme
- **AuthMe API Köprüsü** — Tam AuthMe API entegrasyonu (AuthMeApi, FailedLoginEvent, LoginEvent, RegisterEvent, LogoutEvent, BungeeCord plugin message desteği)
- **bStats Metrikleri** — Sunucu metrikleri toplama
- **PlaceholderAPI** — Placeholder desteği (Bukkit)

### Kurulum

1. Platformunuza uygun JAR dosyasını indirin:
   - `leaderos-auth-bukkit-1.0.5-fork.jar` — Bukkit/Spigot/Paper/Folia
   - `leaderos-auth-bungee-1.0.5-fork.jar` — BungeeCord
   - `leaderos-auth-velocity-1.0.5-fork.jar` — Velocity (LimboAPI gerektirir)
2. JAR dosyasını sunucunuzun `plugins/` dizinine yerleştirin
3. Sunucuyu başlatarak yapılandırma dosyalarını oluşturun
4. `config.yml` dosyasını LeaderOS panel URL'niz ve API anahtarınızla düzenleyin
5. Sunucuyu yeniden başlatın

### Komutlar

| Komut | Açıklama |
|-------|----------|
| `/login <şifre>` | Şifre ile giriş yap |
| `/register <şifre> <şifre/email>` | Yeni hesap oluştur |
| `/tfa <kod>` | İki faktörlü doğrulama kodu gir |
| `/losauthreload` | Yapılandırmayı ve veritabanı bağlantılarını yeniler, giriş yapmayanları atar (Sadece Bukkit) |
| `/leaderosauth setspawn` | Auth spawn noktasını ayarla |

**Komut Takma Adları:** `log`, `l`, `gir`, `giriş`, `reg`, `kaydol`, `kayıt`, `2fa`

---

## 🇬🇧 English

### Features

#### 🔐 Authentication System
- **Login / Register / 2FA** — Full authentication flow integrated with the LeaderOS panel API
- **Session Support** — Securely keeps dynamic auth-sessions valid across server reconnects automatically (Enabled by default)
- **Password Validation** — Minimum/maximum password length, unsafe password blacklist
- **Email Verification** — Optional email verification with kick-after-register support
- **Kick on Wrong Password** — Configurable wrong password kick protection
- **Auth Timeout** — Players are kicked if they fail to authenticate within a configurable time limit

#### 📱 Bedrock / Floodgate Support (Bukkit)
- **Automatic Form Menus** — Bedrock players receive Floodgate `CustomForm` UI for login, register, and TFA
- **Configurable Delay** — Forms appear after a configurable delay (default: 2 seconds after join)
- **Exploit Protections** — Form lock (prevents double-submit), cooldown between submissions, session state validation
- **Auto Re-send** — Forms re-appear automatically after errors or invalid input
- **Fully Localized** — All form text configurable via `lang/en.yml` and `lang/tr.yml`

#### 🛡️ Security
- **IP Connection Limit** — Configurable max concurrent connections per IP, atomic counter to prevent race conditions (Bukkit, BungeeCord, Velocity)
- **Command Blocking** — Only authentication commands are allowed for unauthenticated players
- **Tab-Complete Hiding** — Hides all commands from tab-completion except auth commands, including namespaced commands (Bukkit 1.13+, BungeeCord)
- **Command Cooldown** — Rate-limiting for unauthenticated player commands to prevent API flooding (Bukkit, Velocity)
- **Advanced Alt Account Tracking** — Detects multi-account/alt-account usage even if the player changes their IP address; all suspicious activities are logged and instantly forwarded to Discord via Webhooks
- **Action Blocking** — Unauthenticated players cannot move, chat, interact, break/place blocks, open inventories, or perform any action
- **Anti-Bot** — IP-based connection limiting helps prevent bot attacks
- **Username Validation** — Username case mismatch detection and invalid username blocking
- **Console Log Filtering** — Authentication commands are hidden from console logs (prevents password leaks)
- **Thread-Safe Session Management** — ConcurrentHashMap for safe concurrent access
- **Database Crash Resilience (SQLite)** — Forcefully integrates `journal_mode=WAL` and `synchronous=NORMAL` queries, assuring databases never wipe out or fail upon unexpected/hard server crashes (`kill -9`).
- **Memory Leak Preventions** — Ensures completely strict lifecycle checks across Maps/Arrays efficiently, explicitly removing generic UUID data logs safely executing on generic `PlayerQuitEvent`.
- **Vanilla Exploit Defenses** — Unauthenticated profiles cannot interact with inner-game exploits securely, blocking capabilities heavily related to generic `InventoryDragEvent` (dragging items), SwapHands, and Drop items prior explicitly utilizing `PlayerDeathEvent` preventing item duplications dynamically.

#### 🌍 Multi-Language Support
- **English (`en`)** and **Turkish (`tr`)** language files included
- All messages are fully configurable via YAML files in `lang/` directory
- **Okaeri Orphan Config:** Configuration and language files will automatically remove abandoned/older or misspelled text components and settings when the server initializes.
- **Discord Webhook Texts:** Webhook notification text elements for Alt Detected functionality are mapped directly into your current language file.

#### 🖥️ Multi-Platform

| Platform | Features |
|----------|----------|
| **Bukkit / Spigot / Paper** | Full auth, Bedrock Floodgate forms, titles, boss bar, spawn teleport, AuthMe API bridge, tab-complete protection (1.13+), command cooldown |
| **Folia** | Full Folia compatibility (`folia-supported: true`) |
| **BungeeCord** | Auth server redirection, command/chat blocking, tab-complete hiding, IP limit |
| **Velocity** | LimboAPI integration, custom world support, full auth flow, command cooldown, IP limit |

#### 📊 Additional Features
- **Title & Boss Bar** — Customizable title and boss bar prompts for authentication
- **Spawn Teleport** — Force teleport players to spawn during authentication
- **Gamemode Forcing** — Force survival gamemode for unauthenticated players
- **Send After Auth** — Redirect players to another server after authentication
- **AuthMe API Bridge** — Full AuthMe API integration (AuthMeApi, FailedLoginEvent, LoginEvent, RegisterEvent, LogoutEvent, BungeeCord plugin message support)
- **bStats Metrics** — Server metrics collection
- **PlaceholderAPI** — Placeholder support (Bukkit)

### Installation

1. Download the appropriate JAR for your platform:
   - `leaderos-auth-bukkit-1.0.5-fork.jar` for Bukkit/Spigot/Paper/Folia
   - `leaderos-auth-bungee-1.0.5-fork.jar` for BungeeCord
   - `leaderos-auth-velocity-1.0.5-fork.jar` for Velocity (requires LimboAPI)
2. Place the JAR in your server's `plugins/` directory
3. Start the server to generate config files
4. Edit `config.yml` with your LeaderOS panel URL and API key
5. Restart the server

### Commands

| Command | Description |
|---------|-------------|
| `/login <password>` | Login with password |
| `/register <password> <password/email>` | Register a new account |
| `/tfa <code>` | Enter two-factor authentication code |
| `/losauthreload` | Securely reloads config/DBs and kicks unauthenticated players (Bukkit Only) |
| `/leaderosauth setspawn` | Set the auth spawn location |

**Command Aliases:** `log`, `l`, `gir`, `giriş`, `reg`, `kaydol`, `kayıt`, `2fa`

---

## Yapılandırma / Configuration

### Bukkit `config.yml`

```yaml
settings:
  # Dil / Language: en or tr
  lang: en
  
  # LeaderOS panel URL
  url: "https://yourwebsite.com"
  
  # API anahtarı / API key
  api-key: ""
  
  # Oturum desteği / Session support
  session: true
  
  # Yanlış şifrede at / Kick on wrong password
  kick-on-wrong-password: true
  
  # Kimlik doğrulama süresi (saniye) / Auth timeout (seconds)
  auth-timeout: 60
  
  # Komut bekleme süresi (saniye) / Command cooldown (seconds)
  command-cooldown: 3
  
  # Minimum şifre uzunluğu / Minimum password length
  min-password-length: 5
  
  # IP başına maks bağlantı (0 = devre dışı) / Max connections per IP (0 = disabled)
  max-join-per-ip: 0
  
  # Kayıt ikinci argüman / Register second argument: PASSWORD_CONFIRM or EMAIL
  register-second-arg: PASSWORD_CONFIRM
  
  # Auth sonrası gönderme / Send after auth
  send-after-auth:
    enabled: false
    server: "lobby"
  
  # Bedrock/Floodgate form ayarları / Bedrock form settings
  bedrock:
    enabled: true
    form-delay: 40  # tick (20 = 1 saniye / 1 second)
  
  # Yan hesap bildirimi için Discord ayarları / Discord Webhook settings for Alt Account tracking
  discord:
    enabled: true
    webhook-url: "https://discord.com/api/webhooks/your_webhook"
    avatar-url: "https://minotar.net/helm/{player}/100.png"
    embed-thumbnail-url: ""
    embed-color: 16711680 # Renk (Decimal format) / Color

  # IP başına kayıt olma sınırı / Registration Limit settings per IP
  register-limit:
    enabled: true
    max-accounts-per-ip: 3

  # Veritabanı bağlantı ayarları / Database Connection settings (SQLITE or MYSQL)
  database:
    type: "SQLITE"
    mysql-hostname: "localhost"
    mysql-port: "3306"
    mysql-database: "minecraft"
    mysql-username: "root"
    mysql-password: ""
    jdbcurl-properties: "?useSSL=false&autoReconnect=true"
    prefix: "leaderos_auth_"
    debug: false

  # Güvensiz şifre kara listesi / Unsafe passwords blacklist
  unsafe-passwords:
    - "123456"
    - "password"
    - "qwerty"
```

### BungeeCord `config.yml`

```yaml
settings:
  # Auth sunucu adı / Auth server name
  auth-server: "auth_lobby"
  
  # İzin verilen komutlar / Allowed commands
  allowed-commands:
    - "login"
    - "register"
    - "tfa"
    - "2fa"
  
  # Tab-complete gizleme / Hide tab-complete
  hide-tab-complete: true
  
  # Tab-complete izinli komutlar / Tab-complete allowed commands
  tab-complete-allowed-commands:
    - "2fa"
    - "gir"
    - "giriş"
    - "login"
    - "register"
    - "tfa"
  
  # IP başına maks bağlantı (0 = devre dışı) / Max connections per IP (0 = disabled)
  max-join-per-ip: 0
  
  # IP limiti atma mesajı / IP limit kick message
  kick-max-connections-per-ip: "&cToo many connections from your IP address!"
```

---

## Derleme / Building from Source

```bash
# Gereksinimler / Requirements: Java 8+, Maven 3.6+
mvn clean package -DskipTests
```

Çıktı / Output JARs:
- `bukkit/target/leaderos-auth-bukkit-1.0.5-fork.jar`
- `bungee/target/leaderos-auth-bungee-1.0.5-fork.jar`
- `velocity/target/leaderos-auth-velocity-1.0.5-fork.jar`

---

## Lisans / License

This project is licensed under the [MIT License](LICENSE).
