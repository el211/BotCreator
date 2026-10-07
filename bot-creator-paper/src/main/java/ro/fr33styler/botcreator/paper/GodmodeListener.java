package ro.fr33styler.botcreator.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

final class GodmodeListener implements Listener {

    private final BotNetworkManager manager;

    GodmodeListener(BotNetworkManager manager) {
        this.manager = manager;
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
