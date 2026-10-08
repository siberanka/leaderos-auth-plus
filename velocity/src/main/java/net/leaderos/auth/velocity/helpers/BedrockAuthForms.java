package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.leaderos.auth.shared.enums.SessionState;
import net.leaderos.auth.velocity.configuration.Language;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;

/**
 * Login, register and TFA forms for a Bedrock player in the auth limbo. Only created for verified
 * Floodgate players: this class needs Floodgate's form library, so it must never be loaded when
 * Floodgate is not installed.
 */
public final class BedrockAuthForms {

    /** What the auth handler does with a submitted form. */
    public interface Actions {
        SessionState state();

        boolean active();

        /** @return false when the player is sending too fast */
        boolean cooldown();

        void login(String password);

        void register(String password, String secondArgument);

        void tfa(String code);

        void passwordMismatch();

        /** Shows the form for the current state again after a pause. */
        void retryLater();
    }

    private final LimboForms forms;
    private final Actions actions;

    public BedrockAuthForms(Player player, Actions actions) {
        this.forms = new LimboForms(player);
        this.actions = actions;
    }

    public boolean handlePacket(Object packet) {
        return forms.handlePacket(packet);
    }

    /**
     * Shows the form that matches the session state.
     */
    public void show(SessionState state, Language.Messages.BedrockForms texts, boolean emailMode) {
        if (state == SessionState.LOGIN_REQUIRED) {
            forms.show(CustomForm.builder()
                    .title(texts.getLoginForm().getTitle())
                    .label(texts.getLoginForm().getDescription())
                    .input(texts.getLoginForm().getPasswordLabel())
                    .closedOrInvalidResultHandler(actions::retryLater)
                    .validResultHandler(response -> submit(response, state, emailMode))
                    .build());
        } else if (state == SessionState.REGISTER_REQUIRED) {
            CustomForm.Builder builder = CustomForm.builder()
                    .title(texts.getRegisterForm().getTitle())
                    .label(texts.getRegisterForm().getDescription())
                    .input(texts.getRegisterForm().getPasswordLabel())
                    .input(texts.getRegisterForm().getConfirmPasswordLabel());
            if (emailMode) {
                builder.input(texts.getRegisterForm().getEmailLabel());
            }
            forms.show(builder
                    .closedOrInvalidResultHandler(actions::retryLater)
                    .validResultHandler(response -> submit(response, state, emailMode))
                    .build());
        } else if (state == SessionState.TFA_REQUIRED) {
            forms.show(CustomForm.builder()
                    .title(texts.getTfaForm().getTitle())
                    .label(texts.getTfaForm().getDescription())
                    .input(texts.getTfaForm().getCodeLabel())
                    .closedOrInvalidResultHandler(actions::retryLater)
                    .validResultHandler(response -> submit(response, state, emailMode))
                    .build());
        }
    }

    private void submit(CustomFormResponse response, SessionState formState, boolean emailMode) {
        // A form answered after the state changed (e.g. a login form after TFA was requested) is stale.
        if (actions.state() != formState || !actions.active() || !actions.cooldown()) {
            actions.retryLater();
            return;
        }
        String first = trimToNull(response.next());
        if (first == null) {
            actions.retryLater();
            return;
        }
        if (formState == SessionState.LOGIN_REQUIRED) {
            actions.login(first);
        } else if (formState == SessionState.TFA_REQUIRED) {
            actions.tfa(first);
        } else {
            String confirm = trimToNull(response.next());
            if (confirm == null || !first.equals(confirm)) {
                actions.passwordMismatch();
                actions.retryLater();
                return;
            }
            String second = emailMode ? trimToNull(response.next()) : confirm;
            if (second == null) {
                actions.retryLater();
                return;
            }
            actions.register(first, second);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.length() > 128 ? null : trimmed;
    }
}
