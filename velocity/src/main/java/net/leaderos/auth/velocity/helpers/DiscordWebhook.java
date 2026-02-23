package net.leaderos.auth.velocity.helpers;

import com.velocitypowered.api.proxy.Player;
import net.leaderos.auth.velocity.Velocity;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class DiscordWebhook {

    private final Velocity plugin;

    public DiscordWebhook(Velocity plugin) {
        this.plugin = plugin;
    }

    public void sendAltMessage(final String content, final Player player) {
        if (!plugin.getConfigFile().getSettings().getDiscord().isEnabled()) {
            return;
        }

        final String webhookUrl = plugin.getConfigFile().getSettings().getDiscord().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            try {
                Map<String, Object> jsonMap = new HashMap<>();

                String authorName = applyPlaceholders(plugin.getLangFile().getMessages().getDiscord().getMcServerName(),
                        player, content);
                String title = applyPlaceholders(plugin.getLangFile().getMessages().getDiscord().getEmbedTitle(),
                        player, content);
                String description = applyPlaceholders(
                        plugin.getLangFile().getMessages().getDiscord().getEmbedDescription(), player, content);
                String avatarUrl = applyPlaceholders(plugin.getConfigFile().getSettings().getDiscord().getAvatarUrl(),
                        player, content);
                String username = applyPlaceholders(plugin.getLangFile().getMessages().getDiscord().getUsername(),
                        player, content);

                if (username != null && !username.isEmpty()) {
                    jsonMap.put("username", username);
                }

                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    jsonMap.put("avatar_url", avatarUrl);
                }

                Map<String, Object> embed = new HashMap<>();
                embed.put("title", title);
                embed.put("description", description);
                embed.put("color", plugin.getConfigFile().getSettings().getDiscord().getEmbedColor());

                String thumbnail = applyPlaceholders(
                        plugin.getConfigFile().getSettings().getDiscord().getEmbedThumbnailUrl(),
                        player, content);
                if (thumbnail != null && !thumbnail.isEmpty()) {
                    Map<String, Object> thumbMap = new HashMap<>();
                    thumbMap.put("url", thumbnail);
                    embed.put("thumbnail", thumbMap);
                }

                Map<String, Object> author = new HashMap<>();
                author.put("name", authorName);
                embed.put("author", author);

                embed.put("timestamp", OffsetDateTime.now().toString());

                jsonMap.put("embeds", new Object[] { embed });

                String jsonString = mapToJson(jsonMap);

                URL url = new URI(webhookUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream outputStream = connection.getOutputStream()) {
                    byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
                    outputStream.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 204 && responseCode != 200) {
                    plugin.getLogger().warn("Discord webhook returned response code: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().warn("Failed to send Discord webhook: " + e.getMessage());
            }
        }).schedule();
    }

    private String applyPlaceholders(String template, Player player, String content) {
        if (template == null)
            return "";
        String serverName = plugin.getLangFile().getMessages().getDiscord().getMcServerName();
        return template.replace("{player}", player.getUsername())
                .replace("{creator}", player.getUsername())
                .replace("{content}", content == null ? "" : content)
                .replace("{server}", serverName == null ? "" : serverName);
    }

    private String mapToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first)
                json.append(",");
            first = false;
            json.append("\"").append(entry.getKey()).append("\":");
            appendValueAsJson(json, entry.getValue());
        }
        json.append("}");
        return json.toString();
    }

    private void appendValueAsJson(StringBuilder json, Object value) {
        if (value instanceof String) {
            json.append("\"").append(escapeJsonString((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedMap = (Map<String, Object>) value;
            json.append(mapToJson(nestedMap));
        } else if (value instanceof Object[]) {
            appendArrayAsJson(json, (Object[]) value);
        } else {
            json.append("null");
        }
    }

    private void appendArrayAsJson(StringBuilder json, Object[] array) {
        json.append("[");
        boolean arrayFirst = true;
        for (Object item : array) {
            if (!arrayFirst)
                json.append(",");
            arrayFirst = false;
            appendValueAsJson(json, item);
        }
        json.append("]");
    }

    private String escapeJsonString(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
