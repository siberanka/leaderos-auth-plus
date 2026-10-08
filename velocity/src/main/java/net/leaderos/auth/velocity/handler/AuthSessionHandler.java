package net.leaderos.auth.velocity.handler;

import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.title.Title;
import net.leaderos.auth.shared.helpers.ValidationUtil;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.helpers.BedrockTrust;
import net.leaderos.auth.velocity.helpers.ChatUtil;
import net.leaderos.auth.velocity.helpers.BedrockAuthForms;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.ErrorCode;
import net.leaderos.auth.shared.enums.RegisterSecondArg;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.shared.security.RegistrationDecision;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class AuthSessionHandler implements LimboSessionHandler {
    /** Pause before a Bedrock form is shown again after an error or after it was closed. */
    private static final long FORM_RETRY_MILLIS = 2000L;

    private final Velocity plugin;
    private final Player proxyPlayer;
    private final String ip;
    private final GameSessionResponse session;

    private long joinTime = System.currentTimeMillis();
    private long lastCommand = -1L;

    private LimboPlayer limboPlayer;

    private ScheduledFuture<?> authMainTask;

    private BossBar bossBar;

    /**
     * Forms for Bedrock players; null for Java players or when forms are disabled. Never touched
     * through form library types here, so this handler loads without Floodgate.
     */
    private BedrockAuthForms forms;
    /** One authentication request at a time, from chat or a form. */
    private final AtomicBoolean requestInFlight = new AtomicBoolean();

    @Override
    public void onSpawn(Limbo server, LimboPlayer player) {
        this.limboPlayer = player;

        player.disableFalling();

        if (session == null) {
            Shared.getDebugAPI().send("Session response is null for player " + proxyPlayer.getUsername(), true);
            proxyPlayer.disconnect(Component.join(JoinConfiguration.newlines(),
                    ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickAnError(),
                            new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
            return;
        }

        if (session.getState() == SessionState.LOGIN_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getLogin().getMessage());

            if (plugin.getConfigFile().getSettings().isShowTitle()) {
                proxyPlayer
                        .showTitle(Title.title(ChatUtil.color(plugin.getLangFile().getMessages().getLogin().getTitle()),
                                ChatUtil.color(plugin.getLangFile().getMessages().getLogin().getSubtitle()),
                                Title.Times.times(
                                        Duration.ZERO,
                                        Duration.ofSeconds(plugin.getConfigFile().getSettings().getAuthTimeout()),
                                        Duration.ZERO)));
            }
        }
        if (session.getState() == SessionState.REGISTER_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getMessage());

            if (plugin.getConfigFile().getSettings().isShowTitle()) {
                proxyPlayer.showTitle(
                        Title.title(ChatUtil.color(plugin.getLangFile().getMessages().getRegister().getTitle()),
                                ChatUtil.color(plugin.getLangFile().getMessages().getRegister().getSubtitle()),
                                Title.Times.times(
                                        Duration.ZERO,
                                        Duration.ofSeconds(plugin.getConfigFile().getSettings().getAuthTimeout()),
                                        Duration.ZERO)));
            }
        }
        if (session.getState() == SessionState.TFA_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getRequired());

            if (plugin.getConfigFile().getSettings().isShowTitle()) {
                proxyPlayer
                        .showTitle(Title.title(ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getTitle()),
                                ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getSubtitle()),
                                Title.Times.times(
                                        Duration.ZERO,
                                        Duration.ofSeconds(plugin.getConfigFile().getSettings().getAuthTimeout()),
                                        Duration.ZERO)));
            }
        }

        // Bedrock players (verified by Floodgate, never by name) get forms instead of typing commands.
        if (plugin.getConfigFile().getSettings().getBedrock().isForms() && BedrockTrust.isBedrockPlayer(proxyPlayer)) {
            forms = new BedrockAuthForms(proxyPlayer, new BedrockAuthForms.Actions() {
                @Override
                public SessionState state() {
                    return session.getState();
                }

                @Override
                public boolean active() {
                    return proxyPlayer.isActive();
                }

                @Override
                public boolean cooldown() {
                    return checkCooldown();
                }

                @Override
                public void login(String password) {
                    attemptLogin(password);
                }

                @Override
                public void register(String password, String secondArgument) {
                    attemptRegister(password, secondArgument);
                }

                @Override
                public void tfa(String code) {
                    attemptTfa(code);
                }

                @Override
                public void passwordMismatch() {
                    ChatUtil.sendMessage(proxyPlayer,
                            plugin.getLangFile().getMessages().getRegister().getPasswordMismatch());
                }

                @Override
                public void retryLater() {
                    showFormLater(FORM_RETRY_MILLIS);
                }
            });
            showFormLater(plugin.getConfigFile().getSettings().getBedrock().getFormDelayMillis());
        }

        AtomicInteger i = new AtomicInteger();
        this.authMainTask = this.limboPlayer.getScheduledExecutor().scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() - this.joinTime > plugin.getConfigFile().getSettings().getAuthTimeout()
                    * 1000L) {
                proxyPlayer.disconnect(Component.join(JoinConfiguration.newlines(),
                        ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickTimeout(),
                                new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                return;
            }

            // We'll send a message every 5 seconds to remind the player to login or
            // register
            if (i.incrementAndGet() % 5 == 0) {
                if (session.getState() == SessionState.LOGIN_REQUIRED) {
                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getLogin().getMessage());
                }
                if (session.getState() == SessionState.REGISTER_REQUIRED) {
                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getMessage());
                }
                if (session.getState() == SessionState.TFA_REQUIRED) {
                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getRequired());
                }
            }

            // Update boss bar progress
            if (plugin.getConfigFile().getSettings().getBossBar().isEnabled()) {
                int remainingSeconds = (int) ((plugin.getConfigFile().getSettings().getAuthTimeout() * 1000L
                        - (System.currentTimeMillis() - this.joinTime)) / 1000L);
                float progress = 1.0f - ((System.currentTimeMillis() - this.joinTime)
                        / (float) (plugin.getConfigFile().getSettings().getAuthTimeout() * 1000L));
                float barProgress = (Math.max(0f, Math.min(1f, progress)));

                String barTitle = "";
                if (session.getState() == SessionState.LOGIN_REQUIRED) {
                    barTitle = plugin.getLangFile().getMessages().getLogin().getBossBar().replace("{seconds}",
                            String.valueOf(remainingSeconds));
                }
                if (session.getState() == SessionState.REGISTER_REQUIRED) {
                    barTitle = plugin.getLangFile().getMessages().getRegister().getBossBar().replace("{seconds}",
                            String.valueOf(remainingSeconds));
                }
                if (session.getState() == SessionState.TFA_REQUIRED) {
                    barTitle = plugin.getLangFile().getMessages().getTfa().getBossBar().replace("{seconds}",
                            String.valueOf(remainingSeconds));
                }

                // Set bar color to auto if enabled
                BossBar.Color barColor;
                if (plugin.getConfigFile().getSettings().getBossBar().getColor().equals("AUTO")) {
                    if (progress > 0.5f) {
                        barColor = BossBar.Color.GREEN;
                    } else if (progress > 0.25f) {
                        barColor = BossBar.Color.YELLOW;
                    } else {
                        barColor = BossBar.Color.RED;
                    }
                } else {
                    barColor = BossBar.Color.valueOf(plugin.getConfigFile().getSettings().getBossBar().getColor());
                }

                if (bossBar == null) {
                    bossBar = BossBar.bossBar(
                            ChatUtil.color(barTitle),
                            barProgress,
                            barColor,
                            plugin.getConfigFile().getSettings().getBossBar().getStyle());
                } else {
                    bossBar.name(ChatUtil.color(barTitle));
                    bossBar.progress(barProgress);
                    bossBar.color(barColor);
                }

                // Show boss bar to player
                proxyPlayer.showBossBar(bossBar);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onChat(String message) {
        if (!message.startsWith("/"))
            return;

        if (!checkCooldown())
            return;

        String[] args = message.split(" ");
        String command = args[0].startsWith("/") ? args[0].toLowerCase().substring(1) : args[0].toLowerCase();
        if (plugin.getConfigFile().getSettings().getLoginCommands().contains(command) && args.length > 1) {
            attemptLogin(args[1]);
        } else if (plugin.getConfigFile().getSettings().getRegisterCommands().contains(command) && args.length > 2) {
            attemptRegister(args[1], args[2]);
        } else if (plugin.getConfigFile().getSettings().getTfaCommands().contains(command) && args.length > 1) {
            attemptTfa(args[1]);
        } else {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getUnknownAuthCommand());
        }
    }

    /**
     * LimboAPI hands every other client packet here; Bedrock form responses arrive as plugin messages.
     */
    @Override
    public void onGeneric(Object packet) {
        BedrockAuthForms current = forms;
        if (current != null) {
            current.handlePacket(packet);
        }
    }

    private boolean checkCooldown() {
        if (lastCommand > 0 && System.currentTimeMillis()
                - lastCommand < (plugin.getConfigFile().getSettings().getCommandCooldown() * 1000L)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getWait());
            return false;
        }
        lastCommand = System.currentTimeMillis();
        return true;
    }

    private void attemptLogin(String password) {
        // Prevent trying to login if already authenticated
        if (session.getState() == SessionState.AUTHENTICATED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAlreadyAuthenticated());
            return;
        }

        // Prevent trying to login if TFA is required
        if (session.getState() == SessionState.TFA_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getRequired());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        // Prevent trying to login if need to register
        if (session.getState() == SessionState.REGISTER_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getMessage());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        if (!requestInFlight.compareAndSet(false, true)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getWait());
            return;
        }

        String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());
        AuthUtil.login(proxyPlayer.getUsername(), password, ip, userAgent).whenComplete((result, ex) -> {
            requestInFlight.set(false);
            if (ex != null) {
                ex.printStackTrace();
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
                showFormLater(FORM_RETRY_MILLIS);
                return;
            }

            if (result.isStatus()) {
                // Set session token
                session.setToken(result.getToken());

                if (result.isTfaRequired()) {
                    // Change session state to TFA required
                    session.setState(SessionState.TFA_REQUIRED);

                    // Reset timeout timer for TFA verification
                    this.joinTime = System.currentTimeMillis();

                    // Update title to TFA
                    if (plugin.getConfigFile().getSettings().isShowTitle()) {
                        proxyPlayer.showTitle(Title.title(
                                ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getTitle()),
                                ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getSubtitle()),
                                Title.Times.times(
                                        Duration.ZERO,
                                        Duration.ofSeconds(plugin.getConfigFile().getSettings().getAuthTimeout()),
                                        Duration.ZERO)));
                    }

                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getRequired());
                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getUsage());
                    showFormLater(500L);
                } else {
                    ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getLogin().getSuccess());
                    ChatUtil.sendConsoleInfo(proxyPlayer.getUsername() + " has logged in successfully.");

                    // Alt account tracking
                    if (plugin.getAltAccountManager() != null) {
                        plugin.getAltAccountManager().processPlayerRecord(proxyPlayer, ip);
                    }

                    completeAuthentication(true);
                }
            } else if (result.getError() == ErrorCode.USER_NOT_FOUND) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getLogin().getAccountNotFound());
                showFormLater(FORM_RETRY_MILLIS);
            } else if (result.getError() == ErrorCode.WRONG_PASSWORD) {
                if (plugin.getConfigFile().getSettings().isKickOnWrongPassword()) {
                    proxyPlayer.disconnect(Component.join(JoinConfiguration.newlines(),
                            ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickWrongPassword(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                    return;
                }

                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getLogin().getIncorrectPassword());
                showFormLater(FORM_RETRY_MILLIS);
            } else {
                Shared.getDebugAPI().send("An unexpected error occurred during login: " + result, true);
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
                showFormLater(FORM_RETRY_MILLIS);
            }
        });
    }

    private void attemptRegister(String password, String secondArg) {
        // Prevent trying to register if already authenticated
        if (session.getState() == SessionState.AUTHENTICATED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAlreadyAuthenticated());
            return;
        }

        // Prevent trying to register if TFA is required
        if (session.getState() == SessionState.TFA_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getRequired());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        // Prevent trying to register if need to login
        if (session.getState() == SessionState.LOGIN_REQUIRED || session.getState() == SessionState.HAS_SESSION) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getLogin().getMessage());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        RegisterSecondArg secondArgType = plugin.getConfigFile().getSettings().getRegisterSecondArg();

        // Check if second arg is confirmation and if it matches
        if (secondArgType == RegisterSecondArg.PASSWORD_CONFIRM && !password.equals(secondArg)) {
            ChatUtil.sendMessage(proxyPlayer,
                    plugin.getLangFile().getMessages().getRegister().getPasswordMismatch());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        // Check if second arg is email and if it is valid
        if (secondArgType == RegisterSecondArg.EMAIL && !ValidationUtil.isValidEmail(secondArg)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getInvalidEmail());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        int minPasswordLength = Math.max(plugin.getConfigFile().getSettings().getMinPasswordLength(), 4);
        if (password.length() < minPasswordLength) {
            ChatUtil.sendMessage(proxyPlayer,
                    ChatUtil.replacePlaceholders(
                            plugin.getLangFile().getMessages().getRegister().getPasswordTooShort(),
                            new Placeholder("{min}", minPasswordLength + "")));
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        int maxPasswordLength = 32;
        if (password.length() > maxPasswordLength) {
            ChatUtil.sendMessage(proxyPlayer,
                    ChatUtil.replacePlaceholders(
                            plugin.getLangFile().getMessages().getRegister().getPasswordTooLong(),
                            new Placeholder("{max}", maxPasswordLength + "")));
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        if (plugin.getConfigFile().getSettings().getUnsafePasswords().contains(password)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getUnsafePassword());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        if (!requestInFlight.compareAndSet(false, true)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getWait());
            return;
        }

        String email = secondArgType == RegisterSecondArg.EMAIL ? secondArg : null;
        String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

        RegistrationDecision registrationDecision = plugin.getAltAccountManager()
                .reserveRegistration(ip, proxyPlayer.getUsername());
        if (!registrationDecision.isAllowed()) {
            requestInFlight.set(false);
            ChatUtil.sendMessage(proxyPlayer,
                    plugin.getLangFile().getMessages().getRegister().getRegisterLimit());
            return;
        }

        AuthUtil.register(proxyPlayer.getUsername(), password, email, ip, userAgent).whenComplete((result, ex) -> {
            requestInFlight.set(false);
            if (ex != null) {
                plugin.getAltAccountManager().cancelRegistration(
                        registrationDecision.getReservationToken());
                ex.printStackTrace();
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
                showFormLater(FORM_RETRY_MILLIS);
                return;
            }

            if (result.isStatus()) {
                if (!plugin.getAltAccountManager().completeRegistration(
                        registrationDecision.getReservationToken(), proxyPlayer, ip)) {
                    plugin.getLogger().error("Registration succeeded remotely but its security record "
                            + "could not be committed for " + proxyPlayer.getUsername() + ".");
                }
                plugin.getAltAccountManager().processPlayerRecord(proxyPlayer, ip);

                // Kick the player if email verification is required
                if (result.isEmailVerificationRequired()
                        && plugin.getConfigFile().getSettings().getEmailVerification().isKickAfterRegister()) {
                    proxyPlayer.disconnect(Component.join(JoinConfiguration.newlines(),
                            ChatUtil.replacePlaceholders(
                                    plugin.getLangFile().getMessages().getKickEmailNotVerified(),
                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                    return;
                }

                // Set session token
                session.setToken(result.getToken());

                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getSuccess());
                ChatUtil.sendConsoleInfo(proxyPlayer.getUsername() + " has registered successfully.");

                completeAuthentication(true);
            } else {
                plugin.getAltAccountManager().cancelRegistration(
                        registrationDecision.getReservationToken());
                showFormLater(FORM_RETRY_MILLIS);
            }

            if (!result.isStatus() && result.getError() == ErrorCode.USERNAME_ALREADY_EXIST) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getRegister().getAlreadyRegistered());
            } else if (!result.isStatus() && result.getError() == ErrorCode.REGISTER_LIMIT) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getRegister().getRegisterLimit());
            } else if (!result.isStatus() && result.getError() == ErrorCode.INVALID_USERNAME) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getRegister().getInvalidName());
            } else if (!result.isStatus() && result.getError() == ErrorCode.INVALID_EMAIL) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getRegister().getInvalidEmail());
            } else if (!result.isStatus() && result.getError() == ErrorCode.EMAIL_ALREADY_EXIST) {
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getRegister().getEmailInUse());
            } else if (!result.isStatus() && result.getError() == ErrorCode.INVALID_PASSWORD) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getRegister().getInvalidPassword());
            } else if (!result.isStatus()) {
                Shared.getDebugAPI().send("An unexpected error occurred during register: " + result, true);
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
            }
        });
    }

    private void attemptTfa(String code) {
        // Prevent trying to verify TFA if already authenticated
        if (session.getState() == SessionState.AUTHENTICATED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAlreadyAuthenticated());
            return;
        }

        // Prevent trying to verify TFA if not TFA required
        if (session.getState() != SessionState.TFA_REQUIRED) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getNotRequired());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        // Check if code is exactly 6 characters long and numeric
        if (code.length() != 6 || !code.matches("\\d+")) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
            showFormLater(FORM_RETRY_MILLIS);
            return;
        }

        if (!requestInFlight.compareAndSet(false, true)) {
            ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getWait());
            return;
        }

        AuthUtil.verifyTfa(code, session.getToken()).whenComplete((result, ex) -> {
            requestInFlight.set(false);
            if (ex != null) {
                ex.printStackTrace();
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
                showFormLater(FORM_RETRY_MILLIS);
                return;
            }

            if (result.isStatus()) {
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getSuccess());
                ChatUtil.sendConsoleInfo(
                        proxyPlayer.getUsername() + " has completed TFA verification successfully.");

                // Alt account tracking
                if (plugin.getAltAccountManager() != null) {
                    plugin.getAltAccountManager().processPlayerRecord(proxyPlayer, ip);
                }

                completeAuthentication(true);
            } else if (result.getError() == ErrorCode.WRONG_CODE) {
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
                showFormLater(FORM_RETRY_MILLIS);
            } else if (result.getError() == ErrorCode.SESSION_NOT_FOUND) {
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getTfa().getSessionNotFound());
            } else if (result.getError() == ErrorCode.TFA_VERIFICATION_FAILED) {
                ChatUtil.sendMessage(proxyPlayer,
                        plugin.getLangFile().getMessages().getTfa().getVerificationFailed());
                showFormLater(FORM_RETRY_MILLIS);
            } else {
                Shared.getDebugAPI().send("An unexpected error occurred during TFA verification: " + result, true);
                ChatUtil.sendMessage(proxyPlayer, plugin.getLangFile().getMessages().getAnErrorOccurred());
                showFormLater(FORM_RETRY_MILLIS);
            }
        });
    }

    /**
     * The panel accepted the password (and TFA) or the registration: leave the limbo.
     */
    private void completeAuthentication(boolean passwordVerified) {
        session.setState(SessionState.AUTHENTICATED);
        // Mark the connection authenticated before leaving the limbo: the first server connection
        // follows immediately and would otherwise be refused.
        plugin.setAuthenticated(proxyPlayer, true);
        if (passwordVerified && plugin.getBedrockTrust() != null) {
            plugin.getBedrockTrust().rememberVerified(proxyPlayer);
        }
        this.limboPlayer.getScheduledExecutor().schedule(() -> this.limboPlayer.disconnect(), 500,
                TimeUnit.MILLISECONDS);
    }

    private void showFormLater(long delayMillis) {
        if (forms == null || limboPlayer == null) {
            return;
        }
        limboPlayer.getScheduledExecutor().schedule(this::showForm, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    private void showForm() {
        BedrockAuthForms current = forms;
        if (current == null || !proxyPlayer.isActive() || requestInFlight.get()
                || session.getState() == SessionState.AUTHENTICATED) {
            return;
        }
        current.show(session.getState(), plugin.getLangFile().getMessages().getBedrockForms(),
                plugin.getConfigFile().getSettings().getRegisterSecondArg() == RegisterSecondArg.EMAIL);
    }

    @Override
    public void onDisconnect() {
        forms = null;
        if (authMainTask != null && !authMainTask.isCancelled()) {
            authMainTask.cancel(true);
        }

        if (plugin.getConfigFile().getSettings().isShowTitle()) {
            proxyPlayer.clearTitle();
        }

        if (bossBar != null) {
            proxyPlayer.hideBossBar(bossBar);
        }
    }
}
