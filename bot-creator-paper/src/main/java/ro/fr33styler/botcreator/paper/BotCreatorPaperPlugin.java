package ro.fr33styler.botcreator.paper;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BotCreatorPaperPlugin extends JavaPlugin {

    private EventLoopGroup workerGroup;
    private BotNetworkManager manager;

    @Override
    public void onEnable() {
        createDefaultConfigsIfNeeded();

        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        manager = new BotNetworkManager(this, workerGroup);
        manager.reload();

        BotCreatorCommand commandHandler = new BotCreatorCommand(this, manager);
        PluginCommand command = getCommand("botcreator");
        if (command == null) {
            throw new IllegalStateException("botcreator command is missing from plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getPluginManager().registerEvents(new GodmodeListener(this, manager), this);

        getLogger().info("BotCreator Paper enabled. Configure targets in plugins/BotCreator/servers/.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    private void createDefaultConfigsIfNeeded() {
        File serversDirectory = new File(getDataFolder(), "servers");
        if (!serversDirectory.exists() && !serversDirectory.mkdirs()) {
            getLogger().warning("Could not create target server config directory.");
            return;
        }

        File[] configs = serversDirectory.listFiles((directory, name) -> name.endsWith(".yml"));
        if (configs == null || configs.length == 0) {
            saveResource("servers/server1.yml", false);
            saveResource("servers/server2.yml", false);
            saveResource("servers/server3.yml", false);
            getLogger().info("Created 3 default isolated target configs with 250 generated bot names each.");
        }
    }
}
