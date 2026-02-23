package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.leaderos.auth.shared.Shared;
import net.leaderos.auth.shared.enums.ErrorCode;
import net.leaderos.auth.shared.enums.RegisterSecondArg;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.shared.helpers.AuthUtil;
import net.leaderos.auth.shared.helpers.Placeholder;
import net.leaderos.auth.shared.helpers.UserAgentUtil;
import net.leaderos.auth.shared.model.response.GameSessionResponse;
import net.leaderos.auth.velocity.Velocity;
import net.leaderos.auth.velocity.configuration.Language;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages Bedrock/Floodgate form-based authentication.
 */
public class BedrockFormManager {

    private static final Set<UUID> pendingForms = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final long FORM_COOLDOWN_MS = 2000L;
    private static final ConcurrentHashMap<UUID, Long> lastSubmitTime = new ConcurrentHashMap<>();

    public static boolean isFloodgateAvailable() {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return Velocity.getInstance().getServer().getPluginManager().isLoaded("floodgate");
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (!isFloodgateAvailable())
            return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    public static void sendAuthForm(Player player, String ip, GameSessionResponse session) {
        if (session == null || session.isAuthenticated())
            return;

        if (session.getState() == SessionState.LOGIN_REQUIRED) {
            sendLoginForm(player, ip, session);
        } else if (session.getState() == SessionState.REGISTER_REQUIRED) {
            sendRegisterForm(player, ip, session);
        } else if (session.getState() == SessionState.TFA_REQUIRED) {
            sendTfaForm(player, ip, session);
        }
    }

    public static void sendLoginForm(Player player, String ip, GameSessionResponse session) {
        if (!acquireFormLock(player))
            return;

        Velocity plugin = Velocity.getInstance();
        Language.Messages.BedrockForms.LoginForm formLang = plugin.getLangFile().getMessages().getBedrockForms()
                .getLoginForm();

        CustomForm form = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getPasswordLabel())
                .closedOrInvalidResultHandler(() -> {
                    releaseFormLock(player);
                })
                .validResultHandler((response) -> {
                    releaseFormLock(player);
                    if (!checkCooldown(player))
                        return;
                    if (!player.isActive())
                        return;

                    if (session.getState() != SessionState.LOGIN_REQUIRED)
                        return;

                    String password = response.next();
                    if (password == null || password.trim().isEmpty())
                        return;

                    String userAgent = UserAgentUtil
                            .generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

                    AuthUtil.login(player.getUsername(), password, ip, userAgent).whenComplete((result, ex) -> {
                        if (!player.isActive())
                            return;

                        if (ex != null) {
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                            return;
                        }

                        if (result.isStatus()) {
                            session.setToken(result.getToken());

                            if (result.isTfaRequired()) {
                                session.setState(SessionState.TFA_REQUIRED);
                                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getRequired());
                            } else {
                                session.setState(SessionState.AUTHENTICATED);
                                ChatUtil.sendConsoleInfo(player.getUsername() + " has logged in successfully.");
                                ChatUtil.sendMessage(player,
                                        plugin.getLangFile().getMessages().getLogin().getSuccess());

                                if (plugin.getAltAccountManager() != null) {
                                    plugin.getAltAccountManager().processPlayerRecord(player, ip);
                                }

                                plugin.getServer().getScheduler()
                                        .buildTask(plugin,
                                                () -> plugin.getLimboServer().getLimboPlayer(player).disconnect())
                                        .delay(500, TimeUnit.MILLISECONDS)
                                        .schedule();
                            }
                        } else if (result.getError() == ErrorCode.USER_NOT_FOUND) {
                            ChatUtil.sendMessage(player,
                                    plugin.getLangFile().getMessages().getLogin().getAccountNotFound());
                        } else if (result.getError() == ErrorCode.WRONG_PASSWORD) {
                            if (plugin.getConfigFile().getSettings().isKickOnWrongPassword()) {
                                player.disconnect(Component.join(JoinConfiguration.newlines(),
                                        ChatUtil.replacePlaceholders(
                                                plugin.getLangFile().getMessages().getKickWrongPassword(),
                                                new Placeholder("{prefix}",
                                                        plugin.getLangFile().getMessages().getPrefix()))));
                                return;
                            }
                            ChatUtil.sendMessage(player,
                                    plugin.getLangFile().getMessages().getLogin().getIncorrectPassword());
                        } else {
                            Shared.getDebugAPI().send("Bedrock login error: " + result, true);
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                        }
                    });
                })
                .build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send login form to " + player.getUsername() + ": " + e.getMessage(),
                    true);
        }
    }

    public static void sendRegisterForm(Player player, String ip, GameSessionResponse session) {
        if (!acquireFormLock(player))
            return;

        Velocity plugin = Velocity.getInstance();
        Language.Messages.BedrockForms.RegisterForm formLang = plugin.getLangFile().getMessages().getBedrockForms()
                .getRegisterForm();

        RegisterSecondArg secondArgType = plugin.getConfigFile().getSettings().getRegisterSecondArg();

        CustomForm.Builder builder = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getPasswordLabel())
                .input(formLang.getConfirmPasswordLabel());

        if (secondArgType == RegisterSecondArg.EMAIL) {
            builder.input(formLang.getEmailLabel());
        }

        builder.closedOrInvalidResultHandler(() -> {
            releaseFormLock(player);
        });

        builder.validResultHandler((response) -> {
            releaseFormLock(player);
            if (!checkCooldown(player))
                return;
            if (!player.isActive())
                return;

            if (session.getState() != SessionState.REGISTER_REQUIRED)
                return;

            String password = response.next();
            String confirmPassword = response.next();

            if (password == null || password.trim().isEmpty())
                return;
            if (confirmPassword == null || confirmPassword.trim().isEmpty())
                return;

            if (secondArgType == RegisterSecondArg.PASSWORD_CONFIRM && !password.equals(confirmPassword)) {
                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getPasswordMismatch());
                return;
            }

            int minPasswordLength = Math.max(plugin.getConfigFile().getSettings().getMinPasswordLength(), 4);
            if (password.length() < minPasswordLength) {
                ChatUtil.sendMessage(player, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getRegister().getPasswordTooShort(),
                        new Placeholder("{min}", String.valueOf(minPasswordLength))));
                return;
            }

            int maxPasswordLength = 32;
            if (password.length() > maxPasswordLength) {
                ChatUtil.sendMessage(player, ChatUtil.replacePlaceholders(
                        plugin.getLangFile().getMessages().getRegister().getPasswordTooLong(),
                        new Placeholder("{max}", String.valueOf(maxPasswordLength))));
                return;
            }

            if (plugin.getConfigFile().getSettings().getUnsafePasswords().contains(password)) {
                ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getUnsafePassword());
                return;
            }

            String email = null;
            if (secondArgType == RegisterSecondArg.EMAIL) {
                email = response.next();
                if (email == null || email.trim().isEmpty())
                    return;
            }

            String userAgent = UserAgentUtil.generateUserAgent(!plugin.getConfigFile().getSettings().isSession());

            final String finalEmail = email;
            AuthUtil.register(player.getUsername(), password, finalEmail, ip, userAgent).whenComplete((result, ex) -> {
                if (!player.isActive())
                    return;

                if (ex != null) {
                    ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                    return;
                }

                if (result.isStatus()) {
                    if (result.isEmailVerificationRequired()
                            && plugin.getConfigFile().getSettings().getEmailVerification().isKickAfterRegister()) {
                        player.disconnect(Component.join(JoinConfiguration.newlines(),
                                ChatUtil.replacePlaceholders(
                                        plugin.getLangFile().getMessages().getKickEmailNotVerified(),
                                        new Placeholder("{prefix}", plugin.getLangFile().getMessages().getPrefix()))));
                        return;
                    }

                    session.setToken(result.getToken());

                    ChatUtil.sendConsoleInfo(player.getUsername() + " has registered successfully.");
                    ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getRegister().getSuccess());

                    if (plugin.getAltAccountManager() != null) {
                        plugin.getAltAccountManager().processPlayerRecord(player, ip);
                        plugin.getAltAccountManager().incrementRegistration(ip);
                    }

                    plugin.getServer().getScheduler()
                            .buildTask(plugin, () -> plugin.getLimboServer().getLimboPlayer(player).disconnect())
                            .delay(500, TimeUnit.MILLISECONDS)
                            .schedule();
                } else if (result.getError() == ErrorCode.USERNAME_ALREADY_EXIST) {
                    ChatUtil.sendMessage(player,
                            plugin.getLangFile().getMessages().getRegister().getAlreadyRegistered());
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
            });
        });

        CustomForm form = builder.build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send register form to " + player.getUsername() + ": " + e.getMessage(),
                    true);
        }
    }

    public static void sendTfaForm(Player player, String ip, GameSessionResponse session) {
        if (!acquireFormLock(player))
            return;

        Velocity plugin = Velocity.getInstance();
        Language.Messages.BedrockForms.TfaForm formLang = plugin.getLangFile().getMessages().getBedrockForms()
                .getTfaForm();

        CustomForm form = CustomForm.builder()
                .title(formLang.getTitle())
                .label(formLang.getDescription())
                .input(formLang.getCodeLabel())
                .closedOrInvalidResultHandler(() -> {
                    releaseFormLock(player);
                })
                .validResultHandler((response) -> {
                    releaseFormLock(player);
                    if (!checkCooldown(player))
                        return;
                    if (!player.isActive())
                        return;

                    if (session.getState() != SessionState.TFA_REQUIRED)
                        return;

                    String code = response.next();
                    if (code == null || code.trim().isEmpty())
                        return;

                    if (code.length() != 6 || !code.matches("\\d+")) {
                        ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
                        return;
                    }

                    AuthUtil.verifyTfa(code, session.getToken()).whenComplete((result, ex) -> {
                        if (!player.isActive())
                            return;

                        if (ex != null) {
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                            return;
                        }

                        if (result.isStatus()) {
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getSuccess());
                            ChatUtil.sendConsoleInfo(
                                    player.getUsername() + " has completed TFA verification successfully.");

                            if (plugin.getAltAccountManager() != null) {
                                plugin.getAltAccountManager().processPlayerRecord(player, ip);
                            }

                            plugin.getServer().getScheduler()
                                    .buildTask(plugin,
                                            () -> plugin.getLimboServer().getLimboPlayer(player).disconnect())
                                    .delay(500, TimeUnit.MILLISECONDS)
                                    .schedule();

                        } else if (result.getError() == ErrorCode.WRONG_CODE) {
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getTfa().getInvalidCode());
                        } else if (result.getError() == ErrorCode.SESSION_NOT_FOUND) {
                            ChatUtil.sendMessage(player,
                                    plugin.getLangFile().getMessages().getTfa().getSessionNotFound());
                        } else if (result.getError() == ErrorCode.TFA_VERIFICATION_FAILED) {
                            ChatUtil.sendMessage(player,
                                    plugin.getLangFile().getMessages().getTfa().getVerificationFailed());
                        } else {
                            Shared.getDebugAPI().send("Bedrock TFA error: " + result, true);
                            ChatUtil.sendMessage(player, plugin.getLangFile().getMessages().getAnErrorOccurred());
                        }
                    });
                })
                .build();

        try {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
        } catch (Exception e) {
            releaseFormLock(player);
            Shared.getDebugAPI().send("Failed to send TFA form to " + player.getUsername() + ": " + e.getMessage(),
                    true);
        }
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
