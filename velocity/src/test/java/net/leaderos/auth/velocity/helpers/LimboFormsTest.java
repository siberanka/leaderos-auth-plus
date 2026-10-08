package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.util.FormType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimboFormsTest {

    private final List<byte[]> sent = new ArrayList<>();
    private final List<String> channels = new ArrayList<>();

    private Player player() {
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendPluginMessage")) {
                        channels.add(((ChannelIdentifier) args[0]).getId());
                        sent.add((byte[]) args[1]);
                        return true;
                    }
                    if (method.getName().equals("getUsername")) {
                        return "tester";
                    }
                    return null;
                });
    }

    @Test
    void sendsFloodgateFormatAndAcceptsOnlyTheMatchingAnswerOnce() {
        LimboForms forms = new LimboForms(player());
        List<String> passwords = new ArrayList<>();
        assertTrue(forms.show(CustomForm.builder().title("Login").label("text").input("Password")
                .validResultHandler(response -> passwords.add(response.next()))
                .build()));

        assertEquals(LimboForms.CHANNEL, channels.get(0));
        byte[] data = sent.get(0);
        assertEquals(FormType.CUSTOM_FORM.ordinal(), data[0]);
        assertTrue((data[1] & 0x80) != 0, "proxy form ids carry the high bit");
        assertTrue(new String(data, 3, data.length - 3, StandardCharsets.UTF_8).contains("\"Login\""));

        assertFalse(forms.handle(LimboForms.CHANNEL, answer((byte) (data[1] ^ 0x01), data[2], "[null,\"x\"]")),
                "another form id is ignored");
        assertFalse(forms.handle("other:channel", answer(data[1], data[2], "[null,\"x\"]")));
        assertTrue(forms.handle(LimboForms.CHANNEL, answer(data[1], data[2], "[null,\"Secret123\"]")));
        assertEquals(1, passwords.size());
        assertEquals("Secret123", passwords.get(0));
        assertFalse(forms.handle(LimboForms.CHANNEL, answer(data[1], data[2], "[null,\"again\"]")),
                "an answer is accepted once");
        assertEquals(1, passwords.size());
    }

    @Test
    void closedFormRunsTheClosedHandler() {
        LimboForms forms = new LimboForms(player());
        AtomicInteger closed = new AtomicInteger();
        forms.show(CustomForm.builder().title("Login").input("Password")
                .closedOrInvalidResultHandler(closed::incrementAndGet)
                .validResultHandler(response -> { })
                .build());
        byte[] data = sent.get(0);
        assertTrue(forms.handle(LimboForms.CHANNEL, answer(data[1], data[2], "null")));
        assertEquals(1, closed.get());
    }

    @Test
    void newFormReplacesThePendingOneAndIdsAdvance() {
        LimboForms forms = new LimboForms(player());
        forms.show(CustomForm.builder().title("A").input("x").build());
        forms.show(CustomForm.builder().title("B").input("x").build());
        byte[] first = sent.get(0);
        byte[] second = sent.get(1);
        assertFalse(first[1] == second[1] && first[2] == second[2]);
        assertFalse(forms.handle(LimboForms.CHANNEL, answer(first[1], first[2], "[\"late\"]")));
        assertTrue(forms.handle(LimboForms.CHANNEL, answer(second[1], second[2], "[\"ok\"]")));
    }

    @Test
    void rejectsMalformedAnswers() {
        LimboForms forms = new LimboForms(player());
        assertFalse(forms.handle(LimboForms.CHANNEL, null));
        assertFalse(forms.handle(LimboForms.CHANNEL, new byte[1]));
        assertFalse(forms.handle(LimboForms.CHANNEL, new byte[40_000]));
        assertFalse(forms.handlePacket(new Object()));
    }

    private static byte[] answer(byte high, byte low, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[body.length + 2];
        data[0] = high;
        data[1] = low;
        System.arraycopy(body, 0, data, 2, body.length);
        return data;
    }
}
