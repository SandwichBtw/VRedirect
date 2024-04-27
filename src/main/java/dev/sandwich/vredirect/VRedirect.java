package dev.sandwich.vredirect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

@Plugin(id = "vredirect", name = "VRedirect", version = "1.0.0", description = "Redirects on server restarts", authors= "SandwichBtw")
public class VRedirect {

    @Inject
    private ProxyServer proxy;
    @Inject
    private Logger logger;

    private final PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    private final Map<RegisteredServer, List<UUID>> kickedPlayers = new HashMap<>();
    private final Pattern restartRegex = Pattern.compile("^(server closed|server is restarting)$", Pattern.MULTILINE);
    private ScheduledTask task;
    
    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        logger.info("yay");
    }

    @Subscribe
    public void onSutdown(ProxyShutdownEvent event) {
        if (task != null) task.cancel();
        logger.info("boo");
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        String kickMessage = event.getServerKickReason().map(serializer::serialize).orElse("unknown").toLowerCase();

        if (restartRegex.matcher(kickMessage).matches()) {
            RegisteredServer server = event.getServer();
            UUID uuid = event.getPlayer().getUniqueId();

            if (kickedPlayers.values().stream().noneMatch(list -> list.contains(uuid))) {
                // if server is not present in the map, we add the uuid to the existing list
                kickedPlayers.computeIfAbsent(server, key -> new ArrayList<>()).add(uuid);
            
                if (task == null || task.status() == TaskStatus.CANCELLED) {
                    task = proxy.getScheduler().buildTask(this, this::checkServers).repeat(15, TimeUnit.SECONDS).schedule();
                }
            }
        }
    }

    private void checkServers() {
        for (Map.Entry<RegisteredServer, List<UUID>> entry : kickedPlayers.entrySet()) {
            RegisteredServer server = entry.getKey();
            if (isServerOnline(server)) {
                List<UUID> players = entry.getValue();
                for (UUID uuid : players) {
                    proxy.getPlayer(uuid).ifPresent(player -> player.createConnectionRequest(server).fireAndForget());
                }
                kickedPlayers.remove(server);
            }
        }
        
        if (kickedPlayers.isEmpty()) {
                task.cancel();
        }
    }

    private boolean isServerOnline(RegisteredServer server) {
        try {
            server.ping().join();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}