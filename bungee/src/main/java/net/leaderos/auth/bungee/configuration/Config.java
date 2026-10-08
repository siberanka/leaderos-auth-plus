package net.leaderos.auth.bungee.configuration;

import com.google.common.collect.Lists;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import lombok.Getter;
import lombok.Setter;
import net.leaderos.auth.shared.enums.DebugMode;

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

        @Comment({
                "Debug mode for API requests.",
                "Available modes:",
                "DISABLED: No debug messages",
                "ENABLED: All debug messages",
                "ONLY_ERRORS: Only error messages"
        })
        private DebugMode debugMode = DebugMode.ONLY_ERRORS;

        @Comment("Players will be redirected to this server to login/register.")
        private String authServer = "auth_lobby";

        @Comment({
                "Url of your website (same as on the auth server).",
                "Only needed for the proxy-side session check below."
        })
        private String url = "https://yourwebsite.com";

        @Comment({
                "API Key for request (same as on the auth server). Never publish it.",
                "You can get your API key from Dashboard > API"
        })
        private String apiKey = "YOUR_API_KEY";

        @Comment({
                "Ask the LeaderOS panel for the player's session before choosing the first server.",
                "A player with a valid panel session (same name, IP and session as on the auth server) goes",
                "straight to the server it asked for instead of the auth server - e.g. after a quick",
                "Bedrock reconnect. The panel decides; on errors or timeouts the auth server is used.",
                "Requires url and api-key, and session: true on the auth server."
        })
        private boolean session = true;

        @Comment("How long the proxy waits for the panel during login (milliseconds, 500-5000).")
        private int sessionCheckTimeoutMillis = 3000;

        @Comment({
                "After a successful login, send players back to the server they originally asked for",
                "(default server, forced host, or the server another plugin such as twilight-proxy routed",
                "them to) instead of the auth server's send-after-auth server, which stays the fallback.",
                "The move is a new connection request, so other plugins' permission checks run again."
        })
        private boolean returnToRequestedServer = true;

        @Comment("How long the requested server is remembered while the player logs in (seconds, 30-3600).")
        private int requestedServerTtlSeconds = 600;

        @Comment({
                "Messages from LeaderOS Auth on the backend servers (login status, send-after-auth) are",
                "signed with a shared secret (HMAC-SHA256, timestamp, single-use nonce)."
        })
        private Messaging messaging = new Messaging();

        @Getter
        @Setter
        public static class Messaging extends OkaeriConfig {
            @Comment({
                    "Shared secret (16+ characters); the same value as proxy-messaging.secret on the backends.",
                    "Empty: use the BungeeGuard token (plugins/BungeeGuard/token.yml). Never publish it."
            })
            private String secret = "";

            @Comment({
                    "Refuse unsigned, forged, stale or replayed messages. Keep true.",
                    "false accepts unsigned messages from older backends (insecure, for upgrades only)."
            })
            private boolean requireSignature = true;
        }

        @Comment("Bedrock (Floodgate) settings on the proxy")
        private Bedrock bedrock = new Bedrock();

        @Getter
        @Setter
        public static class Bedrock extends OkaeriConfig {
            @Comment({
                    "Skip the auth server for Bedrock players whose account is bound to their Xbox account",
                    "(XUID), checked through the Floodgate API on this proxy - never by name prefix.",
                    "Bindings are created by the auth server (bedrock.trust-xbox there), so both must use",
                    "the same MySQL database below. Requires Floodgate on this proxy, url and api-key."
            })
            private boolean trustXbox = false;

            @Comment("Days a binding stays trusted after the last password login (1-365); match the auth server.")
            private int trustMaxAgeDays = 30;

            @Comment("MySQL database shared with the auth server (only used when trust-xbox is true)")
            private Database database = new Database();

            @Getter
            @Setter
            public static class Database extends OkaeriConfig {
                private String mysqlHostname = "localhost";
                private String mysqlPort = "3306";
                private String mysqlDatabase = "minecraft";
                private String mysqlUsername = "root";
                private String mysqlPassword = "";
                private String jdbcurlProperties = "?useSSL=false&autoReconnect=true";

                @Comment("Table prefix; must match the auth server")
                private String prefix = "leaderos_auth_";
            }
        }

        @Comment("List of commands that will be allowed")
        private List<String> allowedCommands = Lists.newArrayList("login", "log", "l", "giris", "giriş", "gir", "register", "reg", "kayit", "kayıt", "kaydol", "tfa", "2fa");

        @Comment("Should tab-complete be hidden for unauthenticated players?")
        private boolean hideTabComplete = true;

        @Comment({
                "List of commands that will be shown in tab-complete for unauthenticated players",
                "Only used when hide-tab-complete is enabled"
        })
        private List<String> tabCompleteAllowedCommands = Lists.newArrayList(
                "2fa", "gir", "giriş", "kaydol", "kayıt", "l", "log", "login", "reg", "register", "tfa"
        );

        @Comment({
                "Maximum number of players that can join from the same IP address.",
                "Set to 0 to disable this feature."
        })
        private int maxJoinPerIP = 0;

        @Comment("Kick message when player reached max connections per IP.")
        private String kickMaxConnectionsPerIP = "&cToo many connections from your IP address!";

    }

}
