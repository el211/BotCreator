package ro.fr33styler.botcreator.paper;

import io.netty.channel.EventLoopGroup;
import ro.fr33styler.botcreator.bot.Bot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

final class ManagedBotServer {

    private static final long LOGIN_TIMEOUT_MS = 30_000L;
    private static final long CONNECT_TIMEOUT_MS = 5_000L;
    private static final long LOGIN_POLL_MS = 50L;
    private static final long ONLINE_MONITOR_MS = 1_000L;

    private final BotCreatorPaperPlugin plugin;
    private final BotServerConfig config;
    private final EventLoopGroup workerGroup;
    private final ScheduledExecutorService scheduler;
    private final Map<String, BotHandle> bots = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ManagedBotServer(BotCreatorPaperPlugin plugin, BotServerConfig config, EventLoopGroup workerGroup) {
        this.plugin = plugin;
        this.config = config;
        this.workerGroup = workerGroup;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BotCreator-" + config.id);
            thread.setDaemon(true);
            return thread;
        });

        for (String name : config.botNames) {
            Logger logger = Logger.getLogger("BotCreator." + config.id + "." + name);
            logger.setParent(plugin.getLogger());
            Bot bot = config.version.getProtocol().newBot(logger, name);
            bots.put(name.toLowerCase(Locale.ROOT), new BotHandle(bot));
        }
    }

    BotServerConfig config() {
        return config;
    }

    void startConfiguredBots() {
        if (!config.enabled || !config.connectOnStartup) {
            return;
        }

        int index = 0;
        for (BotHandle handle : bots.values()) {
            handle.desiredConnected.set(true);
            scheduleConnect(handle, (long) index++ * config.joinDelayMs);
        }
    }

    boolean connect(String selector) {
        if (!config.enabled) {
            return false;
        }

        if (selector.equalsIgnoreCase("all")) {
            int index = 0;
            for (BotHandle handle : bots.values()) {
                handle.desiredConnected.set(true);
                scheduleConnect(handle, (long) index++ * config.joinDelayMs);
            }
            return true;
        }

        BotHandle handle = bots.get(selector.toLowerCase(Locale.ROOT));
        if (handle == null) {
            return false;
        }

        handle.desiredConnected.set(true);
        scheduleConnect(handle, 0L);
        return true;
    }

    boolean disconnect(String selector) {
        if (selector.equalsIgnoreCase("all")) {
            for (BotHandle handle : bots.values()) {
                disconnect(handle, "Disconnected by command");
            }
            return true;
        }

        BotHandle handle = bots.get(selector.toLowerCase(Locale.ROOT));
        if (handle == null) {
            return false;
        }

        disconnect(handle, "Disconnected by command");
        return true;
    }

    int sendCommand(String selector, String command) {
        String normalizedCommand = command.startsWith("/") ? command.substring(1) : command;
        return forSelectedLoggedIn(selector, bot -> bot.executeCommand(normalizedCommand));
    }

    int sendChat(String selector, String message) {
        return forSelectedLoggedIn(selector, bot -> bot.sendMessage(message));
    }

    boolean isLoggedIn(String botName) {
        BotHandle handle = bots.get(botName.toLowerCase(Locale.ROOT));
        return handle != null && handle.bot.isLoggedIn();
    }

    List<String> botNames() {
        List<String> names = new ArrayList<>();
        for (BotHandle handle : bots.values()) {
            names.add(handle.bot.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    int totalBots() {
        return bots.size();
    }

    int loggedInBots() {
        int count = 0;
        for (BotHandle handle : bots.values()) {
            if (handle.bot.isLoggedIn()) {
                count++;
            }
        }
        return count;
    }

    int desiredBots() {
        int count = 0;
        for (BotHandle handle : bots.values()) {
            if (handle.desiredConnected.get()) {
                count++;
            }
        }
        return count;
    }

    void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        for (BotHandle handle : bots.values()) {
            handle.desiredConnected.set(false);
            if (handle.bot.isOnline()) {
                handle.bot.disconnect("BotCreator plugin disabled/reloaded");
            }
        }
        scheduler.shutdownNow();
    }

    private void scheduleConnect(BotHandle handle, long delayMs) {
        if (closed.get()) {
            return;
        }
        scheduler.schedule(() -> attemptConnect(handle), delayMs, TimeUnit.MILLISECONDS);
    }

    private void attemptConnect(BotHandle handle) {
        if (closed.get() || !handle.desiredConnected.get() || handle.bot.isLoggedIn()) {
            return;
        }
        if (!handle.connecting.compareAndSet(false, true)) {
            return;
        }

        try {
            if (handle.bot.isOnline()) {
                handle.bot.disconnect("Restarting connection");
            }
            handle.bot.connect(workerGroup, config.host, config.port);
        } finally {
            handle.connecting.set(false);
        }

        long now = System.currentTimeMillis();
        pollLogin(handle, now + LOGIN_TIMEOUT_MS, now + CONNECT_TIMEOUT_MS, false);
    }

    private void pollLogin(BotHandle handle, long loginDeadline, long connectDeadline, boolean wasOnline) {
        if (closed.get() || !handle.desiredConnected.get()) {
            if (handle.bot.isOnline()) {
                handle.bot.disconnect("Connection cancelled");
            }
            return;
        }

        if (handle.bot.isLoggedIn()) {
            plugin.getLogger().info("[" + config.id + "] " + handle.bot.getName() + " logged in.");
            scheduleMonitor(handle);
            return;
        }

        long now = System.currentTimeMillis();
        boolean online = handle.bot.isOnline();
        if ((!online && (wasOnline || now >= connectDeadline)) || now >= loginDeadline) {
            onConnectionLost(handle, "Did not finish logging in");
            return;
        }

        scheduler.schedule(
                () -> pollLogin(handle, loginDeadline, connectDeadline, online),
                LOGIN_POLL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void scheduleMonitor(BotHandle handle) {
        scheduler.schedule(() -> {
            if (closed.get() || !handle.desiredConnected.get()) {
                return;
            }

            if (!handle.bot.isLoggedIn()) {
                onConnectionLost(handle, "Connection lost");
                return;
            }

            scheduleMonitor(handle);
        }, ONLINE_MONITOR_MS, TimeUnit.MILLISECONDS);
    }

    private void onConnectionLost(BotHandle handle, String reason) {
        if (handle.bot.isOnline()) {
            handle.bot.disconnect(reason);
        }

        if (!closed.get() && handle.desiredConnected.get() && config.autoReconnect) {
            plugin.getLogger().warning("[" + config.id + "] " + handle.bot.getName()
                    + " disconnected; retrying in " + config.retryDelayMs + "ms.");
            scheduleConnect(handle, config.retryDelayMs);
        }
    }

    private void disconnect(BotHandle handle, String reason) {
        handle.desiredConnected.set(false);
        if (handle.bot.isOnline()) {
            handle.bot.disconnect(reason);
        }
    }

    private int forSelectedLoggedIn(String selector, BotAction action) {
        if (selector.equalsIgnoreCase("all")) {
            int count = 0;
            for (BotHandle handle : bots.values()) {
                if (handle.bot.isLoggedIn()) {
                    action.run(handle.bot);
                    count++;
                }
            }
            return count;
        }

        BotHandle handle = bots.get(selector.toLowerCase(Locale.ROOT));
        if (handle == null || !handle.bot.isLoggedIn()) {
            return 0;
        }

        action.run(handle.bot);
        return 1;
    }

    private static final class BotHandle {
        private final Bot bot;
        private final AtomicBoolean desiredConnected = new AtomicBoolean(false);
        private final AtomicBoolean connecting = new AtomicBoolean(false);

        private BotHandle(Bot bot) {
            this.bot = bot;
        }
    }

    @FunctionalInterface
    private interface BotAction {
        void run(Bot bot);
    }
}
