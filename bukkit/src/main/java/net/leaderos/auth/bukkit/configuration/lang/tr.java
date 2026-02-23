package net.leaderos.auth.bukkit.configuration.lang;

import com.google.common.collect.Lists;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;
import net.leaderos.auth.bukkit.configuration.Language;

import java.util.List;

/**
 * Turkish language configuration
 */
@Getter
@Setter
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class tr extends Language {

    /**
     * Settings menu of config
     */
    @Comment("Main settings")
    private Messages messages = new Messages();

    /**
     * Messages of plugin
     */
    @Getter
    @Setter
    public static class Messages extends Language.Messages {

        private String prefix = "&3LeaderOS-Auth &8»";

        private String update = "{prefix} &eLeaderOS Auth plugini için yeni bir güncelleme mevcut! Lütfen &a%version% &eversiyonuna güncelleyin!";

        private String wait = "{prefix} &cÇok hızlı komut gönderiyorsunuz. Lütfen bir saniye bekleyin.";

        private String anErrorOccurred = "{prefix} &cİsteğiniz işlenirken bir hata oluştu. Lütfen daha sonra tekrar deneyin.";

        private List<String> kickTimeout = Lists.newArrayList(
                "&cSüre dolduğu için sunucudan atıldınız.",
                "&cDevam etmek için lütfen sunucuya tekrar bağlanın.");

        private List<String> kickAnError = Lists.newArrayList(
                "&cGiriş sistemlerine şu anda erişilemiyor.",
                "&cLütfen daha sonra tekrar deneyin.");

        private List<String> kickNotRegistered = Lists.newArrayList(
                "&cSunucuda kayıtlı değilsiniz!",
                "&cLütfen devam etmek için sitemizden kayıt olun.");

        private List<String> kickWrongPassword = Lists.newArrayList(
                "&cHatalı şifre!");

        private List<String> kickInvalidUsername = Lists.newArrayList(
                "&cGeçersiz kullanıcı adı!");

        private List<String> kickUsernameCaseMismatch = Lists.newArrayList(
                "&cLütfen &e{invalid} &cyerine doğru kullanıcı adınız olan &a{valid} &cile giriş yapın.");

        private List<String> kickEmailNotVerified = Lists.newArrayList(
                "&cSunucumuzda oynayabilmek için lütfen email adresinizi websitemizden doğrulayınız.");

        private List<String> kickMaxConnectionsPerIP = Lists.newArrayList(
                "&cBu IP adresinden sunucuya izin verilen maksimum bağlantı sayısına ulaşıldı.");

        private String unknownAuthCommand = "{prefix} &cBilinmeyen komut! Lütfen &a/register <şifre> <şifre> &aveya &a/login <şifre> &ckomutlarını kullanın.";

        private String reload = "{prefix} &aEklenti başarıyla yeniden yüklendi.";

        private String setSpawn = "{prefix} &aSpawn noktası başarıyla ayarlandı.";

        private String alreadyLoggedIn = "{prefix} &cZaten giriş yaptınız!";

        private Register register = new Register();
        private Login login = new Login();
        private Tfa tfa = new Tfa();
        private BedrockForms bedrockForms = new BedrockForms();

        /**
         * Command object
         */
        private Command command = new Command();

        @Getter
        @Setter
        public static class BedrockForms extends Language.Messages.BedrockForms {

            private LoginForm loginForm = new LoginForm();
            private RegisterForm registerForm = new RegisterForm();
            private TfaForm tfaForm = new TfaForm();

            @Getter
            @Setter
            public static class LoginForm extends Language.Messages.BedrockForms.LoginForm {
                private String title = "Giriş";
                private String description = "Lütfen giriş yapmak için şifrenizi girin.";
                private String passwordLabel = "Şifre";
                private String submitButton = "Giriş Yap";
            }

            @Getter
            @Setter
            public static class RegisterForm extends Language.Messages.BedrockForms.RegisterForm {
                private String title = "Kayıt";
                private String description = "Lütfen kayıt olmak için bir şifre oluşturun.";
                private String passwordLabel = "Şifre";
                private String confirmPasswordLabel = "Şifre Tekrar";
                private String emailLabel = "E-posta";
                private String submitButton = "Kayıt Ol";
            }

            @Getter
            @Setter
            public static class TfaForm extends Language.Messages.BedrockForms.TfaForm {
                private String title = "İki Faktörlü Doğrulama";
                private String description = "Lütfen TFA kodunuzu girin.";
                private String codeLabel = "TFA Kodu";
                private String submitButton = "Doğrula";
            }
        }

        @Getter
        @Setter
        public static class Register extends Language.Messages.Register {

            private String title = "&6KAYIT";

            private String subtitle = "&e/register <şifre> <şifre>";

            private String bossBar = "&fKayıt olmak için {seconds} saniye kaldı!";

            private String message = "{prefix} &eLütfen &a/register <şifre> <şifre> &ekomutu ile kayıt olun.";

            private String passwordMismatch = "{prefix} &cŞifreler uyuşmuyor!";

            private String passwordTooShort = "{prefix} &cŞifre en az {min} karakter uzunluğunda olmalıdır!";

            private String passwordTooLong = "{prefix} &cŞifre {max} karakterden kısa olmalıdır!";

            private String alreadyRegistered = "{prefix} &cZaten kayıtlısınız! Lütfen giriş yapınız.";

            private String invalidPassword = "{prefix} &cŞifre geçersiz! Lütfen geçerli bir şifre giriniz.";

            private String invalidName = "{prefix} &cKullanıcı adınız geçersiz! Lütfen geçerli bir kullanıcı ad kullanınız.";

            private String invalidEmail = "{prefix} &cGeçerli bir email adresi giriniz.";

            private String emailInUse = "{prefix} &cBu email adresi başkası tarafından kullanılıyor!";

            private String registerLimit = "{prefix} &cİzin verilen maksimum kayıt sayısına ulaştınız!";

            private String success = "{prefix} &aBaşarıyla kayıt oldunuz!";

            private String unsafePassword = "{prefix} &cŞifreniz çok zayıf! Lütfen daha güçlü bir şifre seçiniz.";

        }

        @Getter
        @Setter
        public static class Login extends Language.Messages.Login {

            private String title = "&6GIRIŞ";

            private String subtitle = "&e/login <şifre>";

            private String bossBar = "&fGiriş yapmak için {seconds} saniye kaldı!";

            private String message = "{prefix} &eLütfen &a/login <şifre> &ekomutu ile giriş yapın.";

            private String incorrectPassword = "{prefix} &cYanlış şifre!";

            private String accountNotFound = "{prefix} &cSunucumuza kayıtlı değilsiniz! Lütfen kayıt olunuz.";

            private String success = "{prefix} &aBaşarıyla giriş yaptınız!";

        }

        @Getter
        @Setter
        public static class Tfa extends Language.Messages.Tfa {

            private String title = "&6TFA";

            private String subtitle = "&e/tfa <kod>";

            private String bossBar = "&fLütfen {seconds} saniye içinde kodu giriniz.";

            private String required = "{prefix} &eİki faktörlü kimlik doğrulama gerekli! Lütfen TFA kodunuzu &a/tfa <kod> &ekomutu ile girin.";

            private String usage = "{prefix} &eLütfen TFA kodunuzu &a/tfa <kod> &ekomutu ile girin.";

            private String notRequired = "{prefix} &cŞu anda iki faktörlü kimlik doğrulama gerekli değil.";

            private String invalidCode = "{prefix} &cGeçersiz TFA kodu! Lütfen tekrar deneyin.";

            private String sessionNotFound = "{prefix} &cOturum bulunamadı! Lütfen tekrar giriş yapın.";

            private String verificationFailed = "{prefix} &cTFA doğrulaması başarısız! Lütfen tekrar deneyin.";

            private String success = "{prefix} &aİki faktörlü kimlik doğrulama başarılı!";

        }

        @Getter
        @Setter
        public static class Discord extends Language.Messages.Discord {

            private String username = "{player} - Yan Hesap Bulundu";

            private String mcServerName = "TwiLight Network";

            private String embedTitle = "Yan Hesaplar Bulundu - {player}";

            private String embedDescription = "`{content}`";

        }

        /**
         * Command arguments class
         */
        @Getter
        @Setter
        public static class Command extends Language.Messages.Command {

            /**
             * Invalid argument message
             */
            private String invalidArgument = "{prefix} &cGeçersiz argüman girdiniz!";

            /**
             * Unknown command message
             */
            private String unknownCommand = "{prefix} &cBilinmeyen komut!";

            /**
             * Not enough arguments message
             */
            private String notEnoughArguments = "{prefix} &cGerekli argümanları girmediniz!";

            /**
             * too many arguments message
             */
            private String tooManyArguments = "{prefix} &cÇok fazla argüman girdiniz!";

            /**
             * no perm message
             */
            private String noPerm = "{prefix} &cBu işlemi yapabilmek için yeterli yetkiye sahip değilsin!";

        }
    }
}