package net.leaderos.auth.bukkit.helpers;

import net.leaderos.auth.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class DiscordWebhook {

    private final Bukkit plugin;

    public DiscordWebhook(Bukkit plugin) {
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

        plugin.getFoliaLib().getScheduler().runAsync((task) -> {
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
                boolean debugEnabled = plugin.getConfigFile().getSettings().getDatabase().isDebug();

                if (responseCode == 204 || responseCode == 200) {
                    if (debugEnabled) {
                        plugin.getLogger()
                                .info("[Discord Debug] Webhook sent successfully. Response code: " + responseCode);
                    }
                } else {
                    // Read error response body
                    StringBuilder responseBody = new StringBuilder();
                    try (InputStream errorStream = connection.getErrorStream()) {
                        if (errorStream != null) {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    responseBody.append(line);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    plugin.getLogger().warning("Discord webhook returned response code: " + responseCode);
                    if (debugEnabled && responseBody.length() > 0) {
                        plugin.getLogger().warning("[Discord Debug] Response body: " + responseBody);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
                if (plugin.getConfigFile().getSettings().getDatabase().isDebug()) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String applyPlaceholders(String template, Player player, String content) {
        if (template == null)
            return "";
        String serverName = plugin.getLangFile().getMessages().getDiscord().getMcServerName();
        String value = template.replace("{player}", player.getName())
                .replace("{creator}", player.getName())
                .replace("{content}", content == null ? "" : content)
                .replace("{server}", serverName == null ? "" : serverName);

        if (Bukkit.getInstance().getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            value = resolvePlaceholderApi(value, player);
        }
        return value;
    }

    private String resolvePlaceholderApi(String text, Player player) {
        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object result = placeholderApiClass
                    .getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, player, text);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception ignored) {
        }
        return text;
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
