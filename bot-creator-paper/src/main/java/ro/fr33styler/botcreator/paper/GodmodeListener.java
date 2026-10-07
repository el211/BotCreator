package ro.fr33styler.botcreator.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;

final class GodmodeListener implements Listener {

    private final BotCreatorPaperPlugin plugin;
    private final BotNetworkManager manager;

    GodmodeListener(BotCreatorPaperPlugin plugin, BotNetworkManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && manager.isLocalGodmodeBot(player.getName())) {
                player.setInvulnerable(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (manager.isLocalGodmodeBot(player.getName())) {
            event.setCancelled(true);
        }
    }
}
