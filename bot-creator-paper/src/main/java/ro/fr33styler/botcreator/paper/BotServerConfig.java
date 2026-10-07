package ro.fr33styler.botcreator.paper;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ro.fr33styler.botcreator.bot.protocol.ProtocolVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class BotServerConfig {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    final String id;
    final boolean enabled;
    final String host;
    final int port;
    final ProtocolVersion version;
    final List<String> botNames;
    final long joinDelayMs;
    final boolean autoReconnect;
    final long retryDelayMs;
    final boolean connectOnStartup;
    final boolean godmode;
    final boolean localServer;

    private BotServerConfig(
            String id,
            boolean enabled,
            String host,
            int port,
            ProtocolVersion version,
            List<String> botNames,
            long joinDelayMs,
            boolean autoReconnect,
            long retryDelayMs,
            boolean connectOnStartup,
            boolean godmode,
            boolean localServer
    ) {
        this.id = id;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.version = version;
        this.botNames = List.copyOf(botNames);
        this.joinDelayMs = joinDelayMs;
        this.autoReconnect = autoReconnect;
        this.retryDelayMs = retryDelayMs;
        this.connectOnStartup = connectOnStartup;
        this.godmode = godmode;
        this.localServer = localServer;
    }

    static BotServerConfig load(BotCreatorPaperPlugin plugin, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String fileName = file.getName();
        String id = fileName.substring(0, fileName.length() - 4);

        boolean enabled = yaml.getBoolean("enabled", true);
        String host = yaml.getString("ip", "127.0.0.1");
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("ip cannot be empty");
        }

        int port = yaml.getInt("port", 25565);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }

        String versionName = yaml.getString("version", ProtocolVersion.values()[0].getVersion());
        ProtocolVersion version = ProtocolVersion.getByVersion(versionName);
        if (version == null) {
            throw new IllegalArgumentException("unsupported protocol version: " + versionName);
        }

        Set<String> names = new LinkedHashSet<>();
        for (String name : yaml.getStringList("botnames")) {
            addName(plugin, id, names, name);
        }

        ConfigurationSection generated = yaml.getConfigurationSection("generated-names");
        if (generated != null && generated.getBoolean("enabled", false)) {
            String prefix = generated.getString("prefix", "Bot_");
            int start = generated.getInt("start", 1);
            int amount = Math.max(0, generated.getInt("amount", 0));
            for (int i = 0; i < amount; i++) {
                addName(plugin, id, names, prefix + (start + i));
            }
        }

        long joinDelayMs = Math.max(0L, yaml.getLong("join-delay-ms", 250L));
        long retryDelayMs = Math.max(1000L, yaml.getLong("retry-delay-ms", 5000L));
        boolean autoReconnect = yaml.getBoolean("auto-reconnect", true);
        boolean connectOnStartup = yaml.getBoolean("connect-on-startup", false);
        boolean godmode = yaml.getBoolean("godmode", false);

        boolean inferredLocal = isLoopback(host) && port == Bukkit.getPort();
        boolean localServer = yaml.contains("local-server")
                ? yaml.getBoolean("local-server")
                : inferredLocal;

        if (godmode && !localServer) {
            plugin.getLogger().warning("Server '" + id + "' has godmode enabled but is not marked as local-server. "
                    + "Minecraft damage is server-authoritative, so godmode cannot be enforced on a remote target.");
        }

        return new BotServerConfig(
                id,
                enabled,
                host,
                port,
                version,
                new ArrayList<>(names),
                joinDelayMs,
                autoReconnect,
                retryDelayMs,
                connectOnStartup,
                godmode,
                localServer
        );
    }

    private static void addName(BotCreatorPaperPlugin plugin, String serverId, Set<String> names, String rawName) {
        if (rawName == null) {
            return;
        }

        String name = rawName.trim();
        if (!VALID_NAME.matcher(name).matches()) {
            plugin.getLogger().warning("Ignoring invalid bot name '" + rawName + "' in " + serverId
                    + ". Minecraft Java names must match [A-Za-z0-9_]{3,16}.");
            return;
        }

        String existing = names.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            names.add(name);
        } else {
            plugin.getLogger().warning("Ignoring duplicate bot name '" + name + "' in " + serverId + ".");
        }
    }

    private static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("127.0.0.1")
                || normalized.equals("localhost")
                || normalized.equals("::1")
                || normalized.equals("0:0:0:0:0:0:0:1");
    }
}
