<!-- DOWNLOAD_BADGES_START -->
<p align="center">
  <a href="https://gitlab.com/siberanka/leaderos-auth-plus/-/releases/permalink/latest/downloads/bukkit.jar"><img alt="Download Bukkit" src="https://img.shields.io/badge/Download-Bukkit-f39c12?logo=gitlab&logoColor=white"></a>
  <a href="https://gitlab.com/siberanka/leaderos-auth-plus/-/releases/permalink/latest/downloads/bungeecord.jar"><img alt="Download BungeeCord" src="https://img.shields.io/badge/Download-BungeeCord-6f42c1?logo=gitlab&logoColor=white"></a>
  <a href="https://gitlab.com/siberanka/leaderos-auth-plus/-/releases/permalink/latest/downloads/velocity.jar"><img alt="Download Velocity" src="https://img.shields.io/badge/Download-Velocity-1f6feb?logo=gitlab&logoColor=white"></a>
</p>
<!-- DOWNLOAD_BADGES_END -->

# LeaderOS Auth Plus

**Minecraft sunucuları için LeaderOS panel kimlik doğrulama eklentisi.** **Bukkit/Spigot/Paper/Folia**, **BungeeCord** ve **Velocity** proxy sunucularını destekler.

> **Sürüm:** 1.1.1-siberanka
> **Yazarlar:** leaderos, efekurbann, siberanka

> 📖 Ayrıntılı Türkçe belge: **[WIKI.md](WIKI.md)** — kurulum, tüm yapılandırma anahtarları, güvenlik modeli, sorun giderme.

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

#### 🔁 Yeniden Bağlanma ve Proxy Akışı (BungeeCord / Velocity)
- **Proxy Tarafında Oturum Kontrolü (BungeeCord)** — Oyuncu proxy'ye girerken panelden oturumu (ad + IP + sabit user-agent) sorulur; geçerli oturumu olan oyuncu auth sunucusuna hiç uğramadan istediği sunucuya gider. Karar yine panelindir; hata veya zaman aşımında eski akış (auth sunucusu) uygulanır
- **İstenen Sunucuya Dönüş** — Auth sunucusunda tutulan oyuncunun asıl istediği sunucu (forced host, twilight-proxy yönlendirmesi vb.) 10 dakikalığına hatırlanır; girişten sonra oyuncu sabit `send-after-auth` sunucusu yerine oraya **yeni bir bağlantı isteğiyle** gönderilir (diğer eklentilerin izin kontrolleri yeniden çalışır). `send-after-auth` yedek olarak kalır
- **Velocity Limbo Düzeltmesi** — Limbo'da giriş/kayıt veya geçerli oturum sonrası ilk sunucu bağlantısı artık reddedilmez (1.0.x'te oyuncular limbodan sonra hiçbir sunucuya bağlanamıyordu)
- **Hızlı Yeniden Bağlanma Yarışları** — Eski bağlantı henüz düşmeden gelen yeniden bağlanma yalnızca aynı UUID **ve** aynı IP'den geliyorsa kabul edilir (sunucu eskisini düşürür); farklı IP/UUID'den aynı isimle giriş yine reddedilir. Yeni bağlantı kendi oturumunu kullanır ve ayrıca doğrulanır
- **twilight-proxy Uyumu** — Bedrock paket yeniden bağlanmalarında oyuncu ya oturumuyla doğrudan hedefe gider ya da girişten sonra yönlendirildiği sunucuya döner

#### 📱 Bedrock / Floodgate Desteği (Bukkit + Velocity)
- **Otomatik Form Menüleri** — Bedrock oyuncularına Floodgate `CustomForm` arayüzü ile giriş, kayıt ve 2FA formları gönderilir (Bukkit'te ve Velocity auth limbosunda)
- **Xbox (XUID) Güveni — isteğe bağlı, varsayılan kapalı** — `bedrock.trust-xbox: true` ile hesap, Floodgate oyuncusu şifresiyle (ve gerekiyorsa 2FA ile) giriş yaptığında veya kayıt olduğunda o oyuncunun XUID'sine bağlanır; sonraki girişlerde yalnızca Floodgate API'sinin doğruladığı aynı XUID şifresiz girer. Karar **asla** isim önekiyle (`.`) verilmez; başka bir Xbox hesabı bağı devralamaz; bağ son şifreli girişten sonra `trust-max-age-days` gün geçerlidir; `/leaderosauth unlinkbedrock <oyuncu>` ile kaldırılır
- **Yapılandırılabilir Gecikme** — Formlar, oyuncu girdikten sonra yapılandırılabilir bir gecikmeyle gösterilir (varsayılan: 2 saniye)
- **Exploit Korumaları** — Form kilidi (çift gönderimi engeller), gönderimler arası bekleme süresi, oturum durumu doğrulama
- **Otomatik Yeniden Gönderim** — Hata veya geçersiz giriş sonrasında formlar otomatik yeniden gösterilir
- **Tam Yerelleştirme** — Tüm form metinleri `lang/en.yml` ve `lang/tr.yml` ile yapılandırılabilir

#### 🛡️ Güvenlik
- **İmzalı Proxy Mesajları** — Backend → proxy giriş durumu ve yönlendirme mesajları (`leaderos:auth` kanalı) HMAC-SHA256, zaman damgası ve tek kullanımlık nonce ile imzalanır; imzasız, sahte, süresi geçmiş veya tekrar oynatılan mesajlar reddedilir. Kanal proxy'de iki yönde de tüketilir: istemciler okuyamaz ve backend'e gönderemez
- **AuthMe Köprüsü Sertleştirmesi** — `AuthMe.v2 perform.login` mesajı yalnızca `authme-bridge.accept-proxy-login: true` iken **ve** sunucu bir proxy arkasındayken kabul edilir (1.0.x'te proxy'siz sunucularda değiştirilmiş bir istemci bu mesajla şifresiz giriş yapabiliyordu)
- **Korumasız Yönlendirme Uyarısı** — BungeeCord IP forwarding BungeeGuard olmadan veya Velocity `legacy`/`none` forwarding ile kullanılıyorsa proxy ve backend açılışta açık bir hata yazar: bu durumda backend portuna doğrudan ulaşan herkes istediği isim/UUID/IP ile girip auth'u atlayabilir. Çözüm eklentinin içinde değil, ağdadır: BungeeGuard veya Velocity `modern` forwarding + backend portlarına yalnızca proxy'nin erişmesi
- **Floodgate'siz Çalışma Düzeltmesi** — Floodgate kurulu olmayan sunucularda giriş olayının her seferinde hata vermesine yol açan sınıf yükleme sorunu giderildi
- **IP Bağlantı Limiti** — IP başına maksimum eşzamanlı bağlantı; çevrimiçi oyuncular canlı sayılır, yalnızca süren girişler izlenir: başarısız girişler slot sızdırmaz, sunucu geçişleri çifte sayılmaz, Velocity'de limbodaki oyuncular da sayılır (Bukkit, BungeeCord, Velocity)
- **Komut Engelleme** — Giriş yapmamış oyuncular yalnızca kimlik doğrulama komutlarını kullanabilir
- **Tab-Complete Gizleme** — Giriş yapmamış oyunculara sadece auth komutları gösterilir, namespace'li komutlar da filtrelenir (Bukkit 1.13+, BungeeCord)
- **Komut Cooldown** — Giriş yapmamış oyuncular için komut spam koruması (Bukkit, Velocity)
- **Gelişmiş Yan Hesap Takibi** — Oyuncunun geçmişte kullandığı hesap–IP ilişkilerini tam ve geçişli bir grafik olarak izler; ilişkili ağlardaki çoklu hesap kullanımları loglanır ve Discord'a (Webhook) bildirilebilir
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
| **BungeeCord** | Auth sunucuya yönlendirme, proxy tarafında oturum kontrolü, istenen sunucuya dönüş, imzalı mesajlar, Xbox güveni (paylaşılan MySQL), komut/sohbet engelleme, tab-complete gizleme, IP limiti |
| **Velocity** | LimboAPI entegrasyonu, özel dünya desteği, tam auth akışı, Bedrock formları, Xbox güveni, imzalı mesajlar, komut cooldown, IP limiti (3.4 ve 4.1 ile test edildi) |

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
   - `leaderos-auth-bukkit-1.1.1-siberanka.jar` — Bukkit/Spigot/Paper/Folia
   - `leaderos-auth-bungee-1.1.1-siberanka.jar` — BungeeCord
   - `leaderos-auth-velocity-1.1.1-siberanka.jar` — Velocity (LimboAPI gerektirir)
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
| `/leaderosauth reload` | Yapılandırmayı ve dil dosyalarını yeniler (Bukkit: `leaderos.reload`, Velocity: `leaderosauth.reload`) |
| `/leaderosauth setspawn` | Auth spawn noktasını ayarla |
| `/leaderosauth unlinkbedrock <oyuncu>` | Hesabın Bedrock (Xbox) giriş güvenini kaldırır (`leaderos.bedrock.unlink`; Bukkit ve Velocity) |

**Komut Takma Adları:** `log`, `l`, `gir`, `giriş`, `reg`, `kaydol`, `kayıt`, `2fa`

### 1.0.x'ten yükseltme

- Backend (Bukkit) ve proxy (BungeeCord/Velocity) eklentisini **birlikte** güncelleyin; mesaj protokolü değişti.
- Proxy imzasız mesajları varsayılan olarak reddeder. Gizli anahtar otomatik bulunur: Velocity modern/BungeeGuard forwarding gizlisi veya BungeeGuard token'ı. Bunlar yoksa backend'de `proxy-messaging.secret` ve proxy'de `messaging.secret` alanına **aynı** değeri (16+ karakter) yazın; aksi hâlde oyuncular girişten sonra auth sunucusunda kalır (konsola açık bir hata yazılır).
- Geçiş sırasında `messaging.require-signature: false` ile eski backend mesajları geçici olarak kabul edilebilir (güvensiz, yalnızca yükseltme için).
- BungeeCord proxy'de oturum kontrolü için `url` ve `api-key` girin (auth sunucusuyla aynı).

### twilight-proxy / Geyser için önerilen ayarlar

- Auth sunucusunda `session: true` bırakın; oturumu olan oyuncu paket yeniden bağlanmasında auth'a düşmeden hedefine gider.
- Proxy'de `return-to-requested-server: true` (varsayılan) bırakın ve auth sunucusunu twilight-proxy'nin `login-servers` listesine ekleyin.
- Floodgate verisi backend'lere iletiliyorsa (`send-floodgate-data: true`) Floodgate'i **tüm** backend'lere aynı `key.pem` ile kurun.
- `bedrock.trust-xbox` yalnızca Geyser'de `validate-bedrock-login: true` iken güvenlidir (XUID'yi güvenilir yapan budur).

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

#### 🔁 Reconnects and Proxy Flow (BungeeCord / Velocity)
- **Proxy-side Session Check (BungeeCord)** — While a player logs in to the proxy, the panel is asked for its session (name + IP + fixed user agent); a player with a valid session goes straight to the server it asked for without visiting the auth server. The panel still decides; errors or timeouts fall back to the auth server
- **Return to the Requested Server** — The server a player held on the auth server originally asked for (forced host, a twilight-proxy route, …) is remembered for 10 minutes; after the login the player is sent there **through a new connection request** (other plugins' permission checks run again) instead of the fixed `send-after-auth` server, which stays the fallback
- **Velocity Limbo Fix** — The first server connection after a limbo login/registration or a valid session is no longer refused (in 1.0.x players could not reach any server after the limbo)
- **Quick Reconnect Races** — A reconnect that arrives while the old connection is still listed is accepted only from the same UUID **and** the same IP (the server drops the old one); the same name from another IP/UUID is still refused. The new connection uses its own session and authenticates on its own
- **twilight-proxy Compatibility** — On Bedrock pack reconnects the player either goes straight to its target with its session or returns to the server it was routed to after logging in

#### 📱 Bedrock / Floodgate Support (Bukkit + Velocity)
- **Automatic Form Menus** — Bedrock players receive Floodgate `CustomForm` UI for login, register, and TFA (on Bukkit and in the Velocity auth limbo)
- **Xbox (XUID) Trust — optional, off by default** — With `bedrock.trust-xbox: true` an account is bound to the XUID of the Floodgate player that logs in with its password (and TFA when required) or registers; later logins skip the password only for that same XUID, as verified by the Floodgate API. The decision **never** relies on the name prefix (`.`); another Xbox account can never take over a binding; a binding stays valid for `trust-max-age-days` days after the last password login; remove it with `/leaderosauth unlinkbedrock <player>`
- **Configurable Delay** — Forms appear after a configurable delay (default: 2 seconds after join)
- **Exploit Protections** — Form lock (prevents double-submit), cooldown between submissions, session state validation
- **Auto Re-send** — Forms re-appear automatically after errors or invalid input
- **Fully Localized** — All form text configurable via `lang/en.yml` and `lang/tr.yml`

#### 🛡️ Security
- **Signed Proxy Messages** — Backend → proxy login status and redirect messages (`leaderos:auth` channel) carry an HMAC-SHA256, a timestamp and a single-use nonce; unsigned, forged, stale or replayed messages are refused. The proxy consumes the channel in both directions: clients can neither read it nor send it to a backend
- **AuthMe Bridge Hardening** — `AuthMe.v2 perform.login` is only accepted with `authme-bridge.accept-proxy-login: true` **and** while the server runs behind a proxy (in 1.0.x a modified client could log in without a password with this message on servers without a proxy)
- **Unprotected Forwarding Warning** — With BungeeCord IP forwarding without BungeeGuard, or Velocity `legacy`/`none` forwarding, the proxy and the backend log a clear error at start-up: anyone who reaches a backend port directly can then join as any name/UUID/IP and skip the auth server. The fix lives in the network, not in this plugin: BungeeGuard or Velocity `modern` forwarding, and backend ports reachable only from the proxy
- **Runs Without Floodgate** — Fixed a class-loading problem that made the join handler fail on every join on servers without Floodgate
- **IP Connection Limit** — Max concurrent connections per IP; online players are counted live and only logins in progress are tracked: failed logins never leak a slot, server switches never count twice, and on Velocity players in the limbo count too (Bukkit, BungeeCord, Velocity)
- **Command Blocking** — Only authentication commands are allowed for unauthenticated players
- **Tab-Complete Hiding** — Hides all commands from tab-completion except auth commands, including namespaced commands (Bukkit 1.13+, BungeeCord)
- **Command Cooldown** — Rate-limiting for unauthenticated player commands to prevent API flooding (Bukkit, Velocity)
- **Advanced Alt Account Tracking** — Tracks historical account–IP relationships as a complete transitive graph; linked-network activity is logged and can be forwarded to Discord via Webhooks
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
| **BungeeCord** | Auth server redirection, proxy-side session check, return to the requested server, signed messages, Xbox trust (shared MySQL), command/chat blocking, tab-complete hiding, IP limit |
| **Velocity** | LimboAPI integration, custom world support, full auth flow, Bedrock forms, Xbox trust, signed messages, command cooldown, IP limit (tested with 3.4 and 4.1) |

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
   - `leaderos-auth-bukkit-1.1.1-siberanka.jar` for Bukkit/Spigot/Paper/Folia
   - `leaderos-auth-bungee-1.1.1-siberanka.jar` for BungeeCord
   - `leaderos-auth-velocity-1.1.1-siberanka.jar` for Velocity (requires LimboAPI)
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
| `/leaderosauth reload` | Reloads configuration and language files (Bukkit: `leaderos.reload`, Velocity: `leaderosauth.reload`) |
| `/leaderosauth setspawn` | Set the auth spawn location |
| `/leaderosauth unlinkbedrock <player>` | Removes the Bedrock (Xbox) login trust of an account (`leaderos.bedrock.unlink`; Bukkit and Velocity) |

**Command Aliases:** `log`, `l`, `gir`, `giriş`, `reg`, `kaydol`, `kayıt`, `2fa`

### Upgrading from 1.0.x

- Update the backend (Bukkit) and the proxy (BungeeCord/Velocity) plugin **together**; the message protocol changed.
- The proxy refuses unsigned messages by default. The secret is found automatically: the Velocity modern/BungeeGuard forwarding secret or the BungeeGuard token. Without those, set the **same** value (16+ characters) in `proxy-messaging.secret` on the backend and `messaging.secret` on the proxy; otherwise players stay on the auth server after logging in (a clear error is logged).
- During the switch, `messaging.require-signature: false` temporarily accepts messages of old backends (insecure, upgrades only).
- Set `url` and `api-key` on the BungeeCord proxy (same as the auth server) for the proxy-side session check.

### Recommended settings with twilight-proxy / Geyser

- Keep `session: true` on the auth server: a player with a session reaches its target after a pack reconnect without passing the auth server.
- Keep `return-to-requested-server: true` (default) on the proxy and add the auth server to twilight-proxy's `login-servers`.
- When Floodgate data is forwarded (`send-floodgate-data: true`), install Floodgate on **every** backend with the same `key.pem`.
- `bedrock.trust-xbox` is only safe while Geyser keeps `validate-bedrock-login: true` (that is what makes the XUID trustworthy).

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
  # Proxy'de return-to-requested-server açıksa istenen sunucu önceliklidir; bu sunucu yedektir.
  # With return-to-requested-server on the proxy, the requested server wins; this is the fallback.
  send-after-auth:
    enabled: false
    server: "lobby"

  # Proxy mesaj imzası / Proxy message signing (HMAC-SHA256 + timestamp + nonce)
  proxy-messaging:
    # Boş: Paper Velocity forwarding gizlisi veya ilk BungeeGuard token'ı kullanılır.
    # Empty: Paper's Velocity forwarding secret or the first BungeeGuard token is used.
    secret: ""

  # AuthMe köprüsü / AuthMe bridge
  authme-bridge:
    # AuthMeBungee/AuthMeVelocity "perform.login" kabul edilsin mi? Yalnızca proxy arkasında geçerlidir.
    # Accept AuthMeBungee/AuthMeVelocity "perform.login"? Only ever honoured behind a proxy.
    accept-proxy-login: false

  # Bedrock/Floodgate form ayarları / Bedrock form settings
  bedrock:
    enabled: true
    form-delay: 40  # tick (20 = 1 saniye / 1 second)
    # Xbox (XUID) güveni / Xbox (XUID) trust - varsayılan kapalı / off by default
    trust-xbox: false
    trust-max-age-days: 30

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
    ipv6-prefix-length: 64
    reservation-timeout-seconds: 600

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

### Kayıt güvenliği modeli / Registration security model

- Kayıt hakkı uzak API çağrısından **önce**, SQLite veya MySQL üzerinde tek transaction içinde ayrılır. Aynı anda gönderilen komutlar/formlar bekleyen kayıtları da saydığı için klasik “kontrol et, sonra artır” yarışını kullanamaz.
- Limit yalnızca o anki IP'yi değil, geçmişte ortak IP kullanmış hesapların tüm geçişli hesap–IP ağını sayar. IPv6 gizlilik adresleri varsayılan olarak `/64` ağında gruplanır.
- Başarılı her giriş güvenlik grafiğine yazılır. Bu geçmiş; Discord bildiriminin kapatılmasından, muafiyet izninden ve bildirim verisi için kullanılan `expiration-time` ayarından bağımsızdır.
- Hatalı limit, IPv6 öneki, rezervasyon süresi, veritabanı türü/portu ve tablo öneki güvenli aralığa zorlanır. Limit etkinse veritabanı hatasında kayıt **fail-closed** olarak reddedilir.
- Eski `playertable`, `iptable` ve `registrationtable` verileri ilk açılışta idempotent biçimde yeni güvenlik şemasına aktarılır.
- Bu mekanizma donanım parmak izi toplamaz. Hiçbir hesabı veya IP ağıyla ilişki kurmamış tamamen yeni bir bağlantıyı Minecraft protokolü üzerinden güvenilir biçimde aynı kişiye bağlamak mümkün değildir. Proxy arkasında gerçek istemci IP'sinin güvenli forwarding ile sunucuya ulaştığından emin olun.

The limiter atomically reserves a slot before the remote API call, counts pending attempts, follows the complete historical account–IP graph, groups rotating IPv6 addresses, migrates legacy data, and denies registration on storage/security errors. It intentionally does not claim hardware fingerprinting; reliable proxy IP forwarding remains an operator requirement.

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

  # Panel (proxy tarafında oturum kontrolü için) / Panel (for the proxy-side session check)
  url: "https://yourwebsite.com"
  api-key: "YOUR_API_KEY"
  session: true
  session-check-timeout-millis: 3000

  # Girişten sonra istenen sunucuya dön / Return to the requested server after login
  return-to-requested-server: true
  requested-server-ttl-seconds: 600

  # İmzalı backend mesajları / Signed backend messages
  messaging:
    secret: ""               # boş = BungeeGuard token / empty = BungeeGuard token
    require-signature: true  # false yalnızca yükseltme sırasında / false only while upgrading

  # Xbox güveni (auth sunucusuyla paylaşılan MySQL) / Xbox trust (MySQL shared with the auth server)
  bedrock:
    trust-xbox: false
    trust-max-age-days: 30
    database:
      mysql-hostname: "localhost"
      mysql-port: "3306"
      mysql-database: "minecraft"
      mysql-username: "root"
      mysql-password: ""
      jdbcurl-properties: "?useSSL=false&autoReconnect=true"
      prefix: "leaderos_auth_"
```

### Velocity `config.yml` (yeni anahtarlar / new keys)

```yaml
settings:
  messaging:
    secret: ""          # boş = Velocity forwarding gizlisi / empty = Velocity forwarding secret
  bedrock:
    forms: true         # auth limbosunda Bedrock formları / Bedrock forms in the auth limbo
    form-delay-millis: 2000
    trust-xbox: false
    trust-max-age-days: 30
```

---

## Derleme / Building from Source

```bash
# Tüm modüller için derleme gereksinimleri / Full-reactor build requirements: JDK 17+, Maven 3.6+
mvn clean package -DskipTests
```

Çıktı / Output JARs:
- `bukkit/target/leaderos-auth-bukkit-1.1.1-siberanka.jar`
- `bungee/target/leaderos-auth-bungee-1.1.1-siberanka.jar`
- `velocity/target/leaderos-auth-velocity-1.1.1-siberanka.jar`

---

## Lisans / License

This project is licensed under the [MIT License](LICENSE).
