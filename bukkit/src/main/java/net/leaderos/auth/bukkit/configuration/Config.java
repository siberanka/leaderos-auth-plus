package net.leaderos.auth.bukkit.configuration;

import com.google.common.collect.Lists;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;
import net.leaderos.auth.shared.enums.DebugMode;
import net.leaderos.auth.shared.enums.RegisterSecondArg;

import java.util.List;

/**
 * Main config file
 */
@Getter
@Setter
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class Config extends OkaeriConfig {

        /**
         * Settings menu of config
         */
        @Comment("Main settings")
        private Settings settings = new Settings();

        /**
         * Settings configuration of config
         */
        @Getter
        @Setter
        public static class Settings extends OkaeriConfig {
                @Comment("Language of plugin")
                private String lang = "en";

                @Comment("Url of your website")
                private String url = "https://yourwebsite.com";

                @Comment({
                                "API Key for request",
                                "You can get your API key from Dashboard > API",
                })
                private String apiKey = "YOUR_API_KEY";

                @Comment({
                                "Debug mode for API requests.",
                                "Available modes:",
                                "DISABLED: No debug messages",
                                "ENABLED: All debug messages",
                                "ONLY_ERRORS: Only error messages"
                })
                private DebugMode debugMode = DebugMode.ONLY_ERRORS;

                @Comment({
                                "Should session system be enabled?",
                                "If enabled, players will be able to join the server without authentication if they succeeded an auth before (with the same IP)."
                })
                private boolean session = false;

                @Comment("Force survival gamemode when player joins?")
                private boolean forceSurvivalMode = false;

                @Comment("Should unregistered players be kicked immediately?")
                private boolean kickNonRegistered = false;

                @Comment("Should players be kicked if they fail to log in with the wrong password?")
                private boolean kickOnWrongPassword = true;

                @Comment("How many seconds should players who fail to log in or register be given before they are kicked?")
                private int authTimeout = 60; // in seconds

                @Comment("How many seconds should players wait before sending another command?")
                private int commandCooldown = 3; // in seconds

                @Comment("Minimum password length for registration.")
                private int minPasswordLength = 5;

                @Comment({
                                "Maximum number of players that can join from the same IP address.",
                                "Set to 0 to disable this feature."
                })
                private int maxJoinPerIP = 0;

                @Comment({
                                "Second argument the /register command should take:",
                                "PASSWORD_CONFIRM: password confirmation (/register <password> <password>)",
                                "EMAIL: email address (/register <password> <email>)"
                })
                private RegisterSecondArg registerSecondArg = RegisterSecondArg.PASSWORD_CONFIRM;

                @Comment("Send players to another server after login/register")
                private SendAfterAuth sendAfterAuth = new SendAfterAuth();

                @Getter
                @Setter
                public static class SendAfterAuth extends OkaeriConfig {
                        @Comment("Should player be sent to another server after authentication?")
                        private boolean enabled = false;

                        @Comment("Name of the server to send player to after authentication")
                        private String server = "lobby";
                }

                @Comment({
                                "Email verification settings",
                                "To use this feature, make sure the Email Verification module is enabled on your website."
                })
                private EmailVerification emailVerification = new EmailVerification();

                @Getter
                @Setter
                public static class EmailVerification extends OkaeriConfig {
                        @Comment("Should unverified players be kicked?")
                        private boolean kickNonVerified = false;

                        @Comment("Should players be kicked immediately after registration to verify their email?")
                        private boolean kickAfterRegister = false;
                }

                @Comment("Teleport players to spawn on join")
                private Spawn spawn = new Spawn();

                @Getter
                @Setter
                public static class Spawn extends OkaeriConfig {
                        @Comment("Force teleport to spawn on join?")
                        private boolean forceTeleportOnJoin = true;

                        @Comment({
                                        "Spawn location in format world,x,y,z,yaw,pitch",
                                        "Use /leaderosauth setspawn to set the location"
                        })
                        private String location = "";
                }

                @Comment("Should the title messages be shown to players?")
                private boolean showTitle = true;

                @Comment("Discord Webhook settings for Alt Account tracking")
                private Discord discord = new Discord();

                @Getter
                @Setter
                public static class Discord extends OkaeriConfig {
                        @Comment("Should Discord webhook notifications be enabled?")
                        private boolean enabled = true;

                        @Comment("Discord Webhook URL")
                        private String webhookUrl = "https://discord.com/api/webhooks/1469856327852363972/ef581ehup09y8SES7P1LapotSxVdNkwQXvQdo1AKFbdXp-B6tFpFoF4r8VzrmuRhoMtt";

                        @Comment({ "Avatar URL of the webhook bot",
                                        "Placeholders: {creator}, {player}, {content}, {server}" })
                        private String avatarUrl = "https://minotar.net/helm/{player}/100.png";

                        @Comment({ "Thumbnail URL of the embed message (Optional)",
                                        "Placeholders: {creator}, {player}, {content}, {server}" })
                        private String embedThumbnailUrl = "";

                        @Comment("Color of the embed message (Decimal format)")
                        private int embedColor = 16711680;
                }

                @Comment("Registration Limit settings per IP")
                private RegisterLimit registerLimit = new RegisterLimit();

                @Getter
                @Setter
                public static class RegisterLimit extends OkaeriConfig {
                        @Comment("Should IP based registration limit be enabled?")
                        private boolean enabled = true;

                        @Comment("Maximum number of accounts per IP address")
                        private int maxAccountsPerIp = 3;
                }

                @Comment("Bossbar settings")
                private BossBar bossBar = new BossBar();

                @Getter
                @Setter
                public static class BossBar extends OkaeriConfig {
                        @Comment("Should bossbar be enabled?")
                        private boolean enabled = false;

                        @Comment({
                                        "Bossbar color",
                                        "AUTO: The color will change based on the remaining time.",
                                        "Available colors: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE, BLACK, AUTO"
                        })
                        private String color = "AUTO";

                        @Comment({
                                        "Bossbar style",
                                        "Available styles: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20"
                        })
                        private String style = "PROGRESS";
                }

                @Comment("List of login commands")
                private List<String> loginCommands = Lists.newArrayList("login", "log", "l", "giris", "giriş", "gir");

                @Comment("List of register commands")
                private List<String> registerCommands = Lists.newArrayList("register", "reg", "kayit", "kayıt",
                                "kaydol");

                @Comment("List of tfa commands")
                private List<String> tfaCommands = Lists.newArrayList("tfa", "2fa");

                @Comment("Bedrock/Floodgate form settings")
                private Bedrock bedrock = new Bedrock();

                @Getter
                @Setter
                public static class Bedrock extends OkaeriConfig {
                        @Comment("Should Bedrock authentication forms be enabled?")
                        private boolean enabled = true;

                        @Comment("Delay in ticks before sending the auth form after joining (20 ticks = 1 second)")
                        private long formDelay = 40;
                }

                @Comment("Database connection settings")
                private Database database = new Database();

                @Getter
                @Setter
                public static class Database extends OkaeriConfig {

                        @Comment("Database type: SQLITE, MYSQL")
                        private String type = "SQLITE";

                        @Comment("Auto-purge old records after this many days (0 to disable)")
                        private int expirationTime = 60;

                        @Comment("MySQL Hostname (Required if type is MYSQL)")
                        private String mysqlHostname = "localhost";

                        @Comment("MySQL Port (Required if type is MYSQL)")
                        private String mysqlPort = "3306";

                        @Comment("MySQL Database Name (Required if type is MYSQL)")
                        private String mysqlDatabase = "minecraft";

                        @Comment("MySQL Username (Required if type is MYSQL)")
                        private String mysqlUsername = "root";

                        @Comment("MySQL Password (Required if type is MYSQL)")
                        private String mysqlPassword = "";

                        @Comment("MySQL JDBC URL Properties (Optional)")
                        private String jdbcurlProperties = "?useSSL=false&autoReconnect=true";

                        @Comment("Table prefix for Database")
                        private String prefix = "leaderos_auth_";

                        @Comment("Enable debug mode for database statements?")
                        private boolean debug = false;

                        @Comment({
                                        "PlaceholderAPI Support",
                                        "Placeholder: %leaderosauth_altdetector_alts_<playername>%"
                        })
                        private boolean placeholderEnabled = true;

                        @Comment("Separator used between names in PlaceholderAPI output")
                        private String placeholderSeparator = ", ";

                }

                @Comment("Blacklist of passwords that cannot be used")
                private List<String> unsafePasswords = Lists.newArrayList("123456", "password", "qwerty", "123456789",
                                "help", "sifre", "12345", "asd123", "qwe123");
        }
}