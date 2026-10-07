package ro.fr33styler.botcreator.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class BotCreatorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "list", "reload", "status", "connect", "disconnect", "command", "chat"
    );

    private final BotCreatorPaperPlugin plugin;
    private final BotNetworkManager manager;

    BotCreatorCommand(BotCreatorPaperPlugin plugin, BotNetworkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "reload" -> {
                manager.reload();
                sender.sendMessage("BotCreator configs reloaded.");
            }
            case "status" -> status(sender, args);
            case "connect" -> connect(sender, args);
            case "disconnect" -> disconnect(sender, args);
            case "command" -> sendBotCommand(sender, args);
            case "chat" -> sendChat(sender, args);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void list(CommandSender sender) {
        List<String> ids = manager.serverIds();
        if (ids.isEmpty()) {
            sender.sendMessage("No target server configs found in plugins/BotCreator/servers/.");
            return;
        }

        sender.sendMessage("BotCreator target servers: " + String.join(", ", ids));
    }

    private void status(CommandSender sender, String[] args) {
        ManagedBotServer server = requireServer(sender, args, 1);
        if (server == null) {
            return;
        }

        BotServerConfig config = server.config();
        sender.sendMessage("[" + config.id + "] " + config.host + ":" + config.port
                + " version=" + config.version.getVersion()
                + " enabled=" + config.enabled
                + " logged-in=" + server.loggedInBots() + "/" + server.totalBots()
                + " desired=" + server.desiredBots()
                + " auto-reconnect=" + config.autoReconnect
                + " godmode=" + config.godmode
                + (config.godmode && !config.localServer ? " (remote: not enforceable)" : ""));
    }

    private void connect(CommandSender sender, String[] args) {
        ManagedBotServer server = requireServer(sender, args, 1);
        if (server == null) {
            return;
        }

        String selector = args.length >= 3 ? args[2] : "all";
        if (!server.connect(selector)) {
            sender.sendMessage("Could not connect '" + selector + "'. Check the bot name and that the target is enabled.");
            return;
        }

        sender.sendMessage("Connection requested for " + selector + " on " + server.config().id + ".");
    }

    private void disconnect(CommandSender sender, String[] args) {
        ManagedBotServer server = requireServer(sender, args, 1);
        if (server == null) {
            return;
        }

        String selector = args.length >= 3 ? args[2] : "all";
        if (!server.disconnect(selector)) {
            sender.sendMessage("Unknown bot '" + selector + "'.");
            return;
        }

        sender.sendMessage("Disconnected " + selector + " from " + server.config().id + ".");
    }

    private void sendBotCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /botcreator command <server> <bot|all> <command...>");
            return;
        }

        ManagedBotServer server = requireServer(sender, args, 1);
        if (server == null) {
            return;
        }

        String botCommand = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        int sent = server.sendCommand(args[2], botCommand);
        sender.sendMessage("Sent command to " + sent + " logged-in bot(s).");
    }

    private void sendChat(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /botcreator chat <server> <bot|all> <message...>");
            return;
        }

        ManagedBotServer server = requireServer(sender, args, 1);
        if (server == null) {
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        int sent = server.sendChat(args[2], message);
        sender.sendMessage("Sent chat to " + sent + " logged-in bot(s).");
    }

    private ManagedBotServer requireServer(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage("A target server id is required.");
            return null;
        }

        ManagedBotServer server = manager.get(args[index]);
        if (server == null) {
            sender.sendMessage("Unknown target server '" + args[index] + "'.");
        }
        return server;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("BotCreator commands:");
        sender.sendMessage("/" + label + " list");
        sender.sendMessage("/" + label + " reload");
        sender.sendMessage("/" + label + " status <server>");
        sender.sendMessage("/" + label + " connect <server> [bot|all]");
        sender.sendMessage("/" + label + " disconnect <server> [bot|all]");
        sender.sendMessage("/" + label + " command <server> <bot|all> <command...>");
        sender.sendMessage("/" + label + " chat <server> <bot|all> <message...>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(args[0], SUBCOMMANDS);
        }

        if (args.length == 2 && List.of("status", "connect", "disconnect", "command", "chat")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return complete(args[1], manager.serverIds());
        }

        if (args.length == 3 && List.of("connect", "disconnect", "command", "chat")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            ManagedBotServer server = manager.get(args[1]);
            if (server == null) {
                return List.of();
            }

            List<String> choices = new ArrayList<>();
            choices.add("all");
            choices.addAll(server.botNames());
            return complete(args[2], choices);
        }

        return List.of();
    }

    private static List<String> complete(String prefix, Collection<String> choices) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                results.add(choice);
            }
        }
        results.sort(String.CASE_INSENSITIVE_ORDER);
        return results;
    }
}
