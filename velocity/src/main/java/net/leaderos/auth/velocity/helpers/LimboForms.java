package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.leaderos.auth.shared.Shared;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormDefinition;
import org.geysermc.cumulus.form.impl.FormDefinitions;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bedrock forms for players in the auth limbo.
 *
 * <p>LimboAPI handles the connection while a player is in the limbo: the player is not registered
 * with Velocity yet and its plugin messages never reach Velocity's event bus, so Floodgate's own
 * form API cannot deliver forms or their responses there. This class speaks Floodgate's
 * {@code floodgate:form} format directly with Geyser - type, 16-bit id (high bit marks proxy forms),
 * JSON - and only accepts the response to the single form it is waiting for.</p>
 */
public final class LimboForms {

    public static final String CHANNEL = "floodgate:form";
    private static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from(CHANNEL);
    private static final int MAX_RESPONSE = 32 * 1024;

    private final Player player;
    private final AtomicInteger nextId = new AtomicInteger();
    private volatile short pendingId;
    private volatile Form pendingForm;

    public LimboForms(Player player) {
        this.player = player;
    }

    /**
     * Shows a form, replacing any form still waiting for an answer.
     *
     * @return true when the form was sent
     */
    public synchronized boolean show(Form form) {
        byte[] data = encode(form);
        if (data == null) {
            return false;
        }
        pendingForm = form;
        pendingId = (short) (((data[1] & 0xff) << 8) | (data[2] & 0xff));
        return player.sendPluginMessage(IDENTIFIER, data);
    }

    public boolean isWaiting() {
        return pendingForm != null;
    }

    /**
     * Handles a plugin message from the client.
     *
     * @return true when it answered the pending form (its handlers have run)
     */
    public boolean handle(String channel, byte[] data) {
        if (!CHANNEL.equals(channel) || data == null || data.length < 2 || data.length > MAX_RESPONSE) {
            return false;
        }
        Form form;
        synchronized (this) {
            short id = (short) (((data[0] & 0xff) << 8) | (data[1] & 0xff));
            if (pendingForm == null || id != pendingId) {
                return false;
            }
            form = pendingForm;
            pendingForm = null;
        }
        try {
            FormDefinition<Form, ?, ?> definition = FormDefinitions.instance().definitionFor(form);
            definition.handleFormResponse(form, new String(data, 2, data.length - 2, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            Shared.getDebugAPI().send("Could not handle the Bedrock form response of " + player.getUsername()
                    + ": " + exception.getMessage(), true);
        }
        return true;
    }

    /**
     * Handles a raw packet LimboAPI passed to the session handler; only {@code floodgate:form} plugin
     * messages are looked at.
     */
    public boolean handlePacket(Object packet) {
        PluginMessage message = PluginMessage.of(packet);
        return message != null && handle(message.channel, message.data);
    }

    byte[] encode(Form form) {
        try {
            FormDefinition<Form, ?, ?> definition = FormDefinitions.instance().definitionFor(form);
            byte[] json = definition.codec().jsonData(form).getBytes(StandardCharsets.UTF_8);
            // Same id space as Floodgate on a proxy: the high bit marks forms from the proxy.
            short id = (short) ((nextId.getAndIncrement() & 0x7fff) | 0x8000);
            byte[] data = new byte[json.length + 3];
            data[0] = (byte) definition.formType().ordinal();
            data[1] = (byte) (id >> 8 & 0xff);
            data[2] = (byte) (id & 0xff);
            System.arraycopy(json, 0, data, 3, json.length);
            return data;
        } catch (RuntimeException | LinkageError exception) {
            Shared.getDebugAPI().send("Could not build a Bedrock form: " + exception, true);
            return null;
        }
    }

    /** Reads Velocity's internal PluginMessagePacket without compiling against the proxy. */
    static final class PluginMessage {
        private static volatile Class<?> type;
        private static volatile Method getChannel;
        private static volatile Method content;

        final String channel;
        final byte[] data;

        private PluginMessage(String channel, byte[] data) {
            this.channel = channel;
            this.data = data;
        }

        static PluginMessage of(Object packet) {
            if (packet == null || !packet.getClass().getSimpleName().equals("PluginMessagePacket")) {
                return null;
            }
            try {
                if (type != packet.getClass()) {
                    getChannel = packet.getClass().getMethod("getChannel");
                    content = packet.getClass().getMethod("content");
                    type = packet.getClass();
                }
                String channel = (String) getChannel.invoke(packet);
                if (!CHANNEL.equals(channel)) {
                    return null;
                }
                Object buffer = content.invoke(packet);
                // Resolve the methods on the public ByteBuf type; implementations may be package-private.
                Class<?> byteBuf = publicByteBuf(buffer.getClass());
                if (byteBuf == null) {
                    return null;
                }
                int readable = (Integer) byteBuf.getMethod("readableBytes").invoke(buffer);
                if (readable < 0 || readable > MAX_RESPONSE) {
                    return null;
                }
                int readerIndex = (Integer) byteBuf.getMethod("readerIndex").invoke(buffer);
                byte[] bytes = new byte[readable];
                byteBuf.getMethod("getBytes", int.class, byte[].class).invoke(buffer, readerIndex, bytes);
                return new PluginMessage(channel, bytes);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return null;
            }
        }

        private static Class<?> publicByteBuf(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().endsWith(".buffer.ByteBuf")) {
                    return current;
                }
            }
            return null;
        }
    }
}
