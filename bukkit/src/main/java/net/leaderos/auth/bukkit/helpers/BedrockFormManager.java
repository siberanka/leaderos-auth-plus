package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import net.leaderos.auth.bukkit.configuration.Language;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.ErrorCode;
import net.leaderos.auth.shared.enums.RegisterSecondArg;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Bedrock/Floodgate form-based authentication.
 * All form text is loaded from the language configuration — no hardcoded messages.
 */
public class BedrockFormManager {

    private static final Set<UUID> pendingForms = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final long FORM_COOLDOWN_MS = 2000L;
    private static final ConcurrentHashMap<UUID, Long> lastSubmitTime = new ConcurrentHashMap<>();

    public static boolean isFloodgateAvailable() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return org.bukkit.Bukkit.getPluginManager().getPlugin("floodgate") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!isFloodgateAvailable()) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendAuthForm(Player player) {
        Bukkit plugin = Bukkit.getInstance();
        GameSessionResponse session = plugin.getSessions().get(player.getName());
        if (session == null || session.isAuthenticated()) return;

        if (session.getState() == SessionState.LOGIN_REQUIRED) {
            sendLoginForm(player);
        } else if (session.getState() == SessionState.REGISTER_REQUIRED) {
            sendRegisterForm(player);
        } else if (session.getState() == SessionState.TFA_REQUIRED) {
            sendTfaForm(player);
        }
    }

    public static void sendLoginForm(Player player) {
        if (!acquireFormLock(player)) return;

        Bukkit plugin = Bukkit.getInstance();
        Language.Messages.BedrockForms.LoginForm formLang =
                plugin.getLangFile().getMessages().getBedrockForms().getLoginForm();

        CustomForm form = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getPasswordLabel())
                .closedOrInvalidResultHandler(() -> releaseFormLock(player))
                .validResultHandler((response) -> {
                    releaseFormLock(player);
                    if (!checkCooldown(player)) return;
                    if (!player.isOnline()) return;

                    GameSessionResponse session = plugin.getSessions().get(player.getName());
                    if (session == null || session.getState() != SessionState.LOGIN_REQUIRED) return;

                    String password = response.next();
                    if (password == null || password.trim().isEmpty()) return;
                    if (player.getAddress() == null) return;

                    String ip = player.getAddress().getAddress().getHostAddress();
                    String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

                    AuthUtil.login(player.getName(), password, ip, userAgent).whenComplete((result, ex) -> {
                        plugin.getFoliaLib().getScheduler().runNextTick((task) -> {
                            if (!player.isOnline()) return;

                            if (ex != null) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                                resendFormLater(player);
                                return;
                            }

                            if (result.isStatus()) {
                                session.setToken(result.getToken());

                                if (result.isTfaRequired()) {
                                    session.setState(SessionState.TFA_REQUIRED);
                                    if (plugin.getConfigFile().getSettings().isShowTitle()) {
                                        TitleUtil.sendTitle(player,
                                                ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getTitle()),
                                                ChatUtil.color(plugin.getLangFile().getMessages().getTfa().getSubtitle()),
                                                0, plugin.getConfigFile().getSettings().getAuthTimeout() * 20, 10);
                                    }
                                    ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getRequired());
                                    resendFormLater(player);
                                } else {
                                    ChatUtil.sendConsoleInfo(player.getName() + " has logged in successfully.");
                                    ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getLogin().getSuccess());
                                    plugin.forceAuthenticate(player);

                                    if (plugin.getConfigFile().getSettings().getSendAfterAuth().isEnabled()) {
                                        plugin.getFoliaLib().getScheduler().runLater(() -> {
                                            plugin.sendPlayerToServer(player, plugin.getConfigFile().getSettings().getSendAfterAuth().getServer());
                                        }, 20L);
                                    }
                                }
                            } else if (result.getError() == ErrorCode.USER_NOT_FOUND) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getLogin().getAccountNotFound());
                                resendFormLater(player);
                            } else if (result.getError() == ErrorCode.WRONG_PASSWORD) {
                                plugin.getAuthMeCompatBridge().callFailedLogin(player);
                                if (plugin.getConfigFile().getSettings().isKickOnWrongPassword()) {
                                    player.kickPlayer(String.join("\n",
                                            ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickWrongPassword(),
                                                    new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                                    return;
                                }
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getLogin().getIncorrectPassword());
                                resendFormLater(player);
                            } else {
                                Shared.getDebugAPI().send("Bedrock login error: " + result, true);
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                                resendFormLater(player);
                            }
                        });
                    });
                })
                .build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send login form to " + player.getName() + ": " + e.getMessage(), true);
        }
    }

    public static void sendRegisterForm(Player player) {
        if (!acquireFormLock(player)) return;

        Bukkit plugin = Bukkit.getInstance();
        Language.Messages.BedrockForms.RegisterForm formLang =
                plugin.getLangFile().getMessages().getBedrockForms().getRegisterForm();

        RegisterSecondArg secondArgType = plugin.getConfigFile().getSettings().getRegisterSecondArg();

        CustomForm.Builder builder = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getPasswordLabel())
                .input(formLang.getConfirmPasswordLabel());

        if (secondArgType == RegisterSecondArg.EMAIL) {
            builder.input(formLang.getEmailLabel());
        }

        builder.closedOrInvalidResultHandler(() -> releaseFormLock(player));
        builder.validResultHandler((response) -> {
            releaseFormLock(player);
            if (!checkCooldown(player)) return;
            if (!player.isOnline()) return;

            GameSessionResponse session = plugin.getSessions().get(player.getName());
            if (session == null || session.getState() != SessionState.REGISTER_REQUIRED) return;
            if (player.getAddress() == null) return;

            String password = response.next();
            String confirmPassword = response.next();

            if (password == null || password.trim().isEmpty()) return;
            if (confirmPassword == null || confirmPassword.trim().isEmpty()) return;

            if (secondArgType == RegisterSecondArg.PASSWORD_CONFIRM && !password.equals(confirmPassword)) {
                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getPasswordMismatch());
                resendFormLater(player);
                return;
            }

            int minPasswordLength = Math.max(plugin.getConfigFile().getSettings().getMinPasswordLength(), 4);
            if (password.length() < minPasswordLength) {
                ChatUtil.sendMessage(player, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getRegister().getPasswordTooShort(),
                        new Placeholder("{min}", String.valueOf(minPasswordLength))));
                resendFormLater(player);
                return;
            }

            int maxPasswordLength = 32;
            if (password.length() > maxPasswordLength) {
                ChatUtil.sendMessage(player, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getRegister().getPasswordTooLong(),
                        new Placeholder("{max}", String.valueOf(maxPasswordLength))));
                resendFormLater(player);
                return;
            }

            if (plugin.getConfigFile().getSettings().getUnsafePasswords().contains(password)) {
                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getUnsafePassword());
                resendFormLater(player);
                return;
            }

            String email = null;
            if (secondArgType == RegisterSecondArg.EMAIL) {
                email = response.next();
                if (email == null || email.trim().isEmpty()) return;
            }

            String ip = player.getAddress().getAddress().getHostAddress();
            String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

            final String finalEmail = email;
            AuthUtil.register(player.getName(), password, finalEmail, ip, userAgent).whenComplete((result, ex) -> {
                plugin.getFoliaLib().getScheduler().runNextTick((task) -> {
                    if (!player.isOnline()) return;

                    if (ex != null) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                        resendFormLater(player);
                        return;
                    }

                    if (result.isStatus()) {
                        if (result.isEmailVerificationRequired() && plugin.getConfigFile().getSettings().getEmailVerification().isKickAfterRegister()) {
                            player.kickPlayer(String.join("\n",
                                    ChatUtil.replacePlaceholders(plugin.getLangFile().getMessages().getKickEmailNotVerified(),
                                            new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                            return;
                        }

                        session.setToken(result.getToken());
                        plugin.getSessions().put(player.getName(), session);

                        plugin.getAuthMeCompatBridge().callRegister(player);
                        plugin.forceAuthenticate(player);

                        ChatUtil.sendConsoleInfo(player.getName() + " has registered successfully.");
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getSuccess());

                        if (plugin.getConfigFile().getSettings().getSendAfterAuth().isEnabled()) {
                            plugin.getFoliaLib().getScheduler().runLater(() -> {
                                plugin.sendPlayerToServer(player, plugin.getConfigFile().getSettings().getSendAfterAuth().getServer());
                            }, 20L);
                        }
                    } else if (result.getError() == ErrorCode.USERNAME_ALREADY_EXIST) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getAlreadyRegistered());
                    } else if (result.getError() == ErrorCode.REGISTER_LIMIT) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getRegisterLimit());
                    } else if (result.getError() == ErrorCode.INVALID_USERNAME) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getInvalidName());
                    } else if (result.getError() == ErrorCode.INVALID_EMAIL) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getInvalidEmail());
                    } else if (result.getError() == ErrorCode.EMAIL_ALREADY_EXIST) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getEmailInUse());
                    } else if (result.getError() == ErrorCode.INVALID_PASSWORD) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getInvalidPassword());
                    } else {
                        Shared.getDebugAPI().send("Bedrock register error: " + result, true);
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                    }
                    if (!result.isStatus()) {
                        resendFormLater(player);
                    }
                });
            });
        });

        CustomForm form = builder.build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send register form to " + player.getName() + ": " + e.getMessage(), true);
        }
    }

    public static void sendTfaForm(Player player) {
        if (!acquireFormLock(player)) return;

        Bukkit plugin = Bukkit.getInstance();
        Language.Messages.BedrockForms.TfaForm formLang =
                plugin.getLangFile().getMessages().getBedrockForms().getTfaForm();

        CustomForm form = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getCodeLabel())
                .closedOrInvalidResultHandler(() -> releaseFormLock(player))
                .validResultHandler((response) -> {
                    releaseFormLock(player);
                    if (!checkCooldown(player)) return;
                    if (!player.isOnline()) return;

                    GameSessionResponse session = plugin.getSessions().get(player.getName());
                    if (session == null || session.getState() != SessionState.TFA_REQUIRED) return;

                    String code = response.next();
                    if (code == null || code.trim().isEmpty()) return;

                    if (code.length() != 6 || !code.matches("\\d+")) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
                        resendFormLater(player);
                        return;
                    }

                    AuthUtil.verifyTfa(code, session.getToken()).whenComplete((result, ex) -> {
                        plugin.getFoliaLib().getScheduler().runNextTick((task) -> {
                            if (!player.isOnline()) return;

                            if (ex != null) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                                resendFormLater(player);
                                return;
                            }

                            if (result.isStatus()) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getSuccess());
                                ChatUtil.sendConsoleInfo(player.getName() + " has completed TFA verification successfully.");
                                plugin.forceAuthenticate(player);

                                if (plugin.getConfigFile().getSettings().getSendAfterAuth().isEnabled()) {
                                    plugin.getFoliaLib().getScheduler().runLater(() -> {
                                        plugin.sendPlayerToServer(player, plugin.getConfigFile().getSettings().getSendAfterAuth().getServer());
                                    }, 20L);
                                }
                            } else if (result.getError() == ErrorCode.WRONG_CODE) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
                                resendFormLater(player);
                            } else if (result.getError() == ErrorCode.SESSION_NOT_FOUND) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getSessionNotFound());
                            } else if (result.getError() == ErrorCode.TFA_VERIFICATION_FAILED) {
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getVerificationFailed());
                                resendFormLater(player);
                            } else {
                                Shared.getDebugAPI().send("Bedrock TFA error: " + result, true);
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                                resendFormLater(player);
                            }
                        });
                    });
                })
                .build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send TFA form to " + player.getName() + ": " + e.getMessage(), true);
        }
    }

    private static void resendFormLater(Player player) {
        Bukkit plugin = Bukkit.getInstance();
        plugin.getFoliaLib().getScheduler().runLater(() -> {
            if (!player.isOnline() || plugin.isAuthenticated(player)) return;
            sendAuthForm(player);
        }, 40L);
    }

    private static boolean acquireFormLock(Player player) {
        return pendingForms.add(player.getUniqueId());
    }

    private static void releaseFormLock(Player player) {
        pendingForms.remove(player.getUniqueId());
    }

    private static boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastSubmitTime.get(player.getUniqueId());
        if (last != null && (now - last) < FORM_COOLDOWN_MS) {
            return false;
        }
        lastSubmitTime.put(player.getUniqueId(), now);
        return true;
    }

    public static void cleanup(Player player) {
        pendingForms.remove(player.getUniqueId());
        lastSubmitTime.remove(player.getUniqueId());
    }
}
