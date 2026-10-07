package ro.fr33styler.botcreator.paper;

import io.netty.channel.EventLoopGroup;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BotNetworkManager {

    private final BotCreatorPaperPlugin plugin;
    private final EventLoopGroup workerGroup;
    private final Map<String, ManagedBotServer> servers = new ConcurrentHashMap<>();

    BotNetworkManager(BotCreatorPaperPlugin plugin, EventLoopGroup workerGroup) {
        this.plugin = plugin;
        this.workerGroup = workerGroup;
    }

    void reload() {
        shutdownServers();

        File serversDirectory = new File(plugin.getDataFolder(), "servers");
        if (!serversDirectory.exists() && !serversDirectory.mkdirs()) {
            plugin.getLogger().severe("Could not create " + serversDirectory.getAbsolutePath());
            return;
        }

        File[] files = serversDirectory.listFiles((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning("Could not list target server configs.");
            return;
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : sortedFiles) {
            try {
                BotServerConfig config = BotServerConfig.load(plugin, file);
                ManagedBotServer server = new ManagedBotServer(plugin, config, workerGroup);
                servers.put(config.id.toLowerCase(Locale.ROOT), server);
            } catch (Exception exception) {
                plugin.getLogger().severe("Could not load " + file.getName() + ": " + exception.getMessage());
            }
        }

        for (ManagedBotServer server : servers.values()) {
            server.startConfiguredBots();
        }

        plugin.getLogger().info("Loaded " + servers.size() + " BotCreator target server(s).");
    }

    ManagedBotServer get(String id) {
        return servers.get(id.toLowerCase(Locale.ROOT));
    }

    List<String> serverIds() {
        List<String> ids = new ArrayList<>();
        for (ManagedBotServer server : servers.values()) {
            ids.add(server.config().id);
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    boolean isLocalGodmodeBot(String playerName) {
        for (ManagedBotServer server : servers.values()) {
            BotServerConfig config = server.config();
            if (config.godmode && config.localServer && server.isLoggedIn(playerName)) {
                return true;
            }
        }
        return false;
    }

    void shutdown() {
        shutdownServers();
    }

    private void shutdownServers() {
        for (ManagedBotServer server : servers.values()) {
            server.shutdown();
        }
        servers.clear();
    }
}
