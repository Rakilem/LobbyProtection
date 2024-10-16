package com.rakilem;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerBucketEmptyEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LobbyProtection extends PluginBase implements Listener {

    private List<ProtectedArea> protectedAreas = new ArrayList<>();
    private List<String> exemptPlayers = new ArrayList<>();
    private Config messagesConfig;

    @Override
    public void onEnable() {
        // Cargar configuración
        this.saveDefaultConfig();
        Config config = this.getConfig();

        // Cargar lista de jugadores exentos
        exemptPlayers = config.getStringList("exempt-players");

        // Cargar mensajes desde messages.yml
        File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            this.saveResource("messages.yml", false); // Copia el archivo desde los recursos si no existe
        }
        messagesConfig = new Config(messagesFile);

        // Leer áreas protegidas desde el config.yml
        List<Map> areas = config.getMapList("protected-areas");
        for (Map<String, Object> area : areas) {
            String worldName = (String) area.get("world");
            Map<String, Object> corner1Map = (Map<String, Object>) area.get("corner1");
            Map<String, Object> corner2Map = (Map<String, Object>) area.get("corner2");

            Vector3 corner1 = new Vector3(
                    (int) corner1Map.get("x"),
                    (int) corner1Map.get("y"),
                    (int) corner1Map.get("z")
            );
            Vector3 corner2 = new Vector3(
                    (int) corner2Map.get("x"),
                    (int) corner2Map.get("y"),
                    (int) corner2Map.get("z")
            );
            Level level = this.getServer().getLevelByName(worldName);
            if (level != null) {
                protectedAreas.add(new ProtectedArea(corner1, corner2, level));
            } else {
                this.getLogger().warning("El mundo " + worldName + " no se pudo encontrar.");
            }
        }

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    // Método para obtener mensajes desde messages.yml
    private String getMessage(String key) {
        return messagesConfig.getString("messages." + key, messagesConfig.getString("messages.default", "¡Acción no permitida en el área protegida!"));
    }

    // Método para comprobar si un jugador está exento
    private boolean isExempt(Player player) {
        return exemptPlayers.contains(player.getName());
    }

    // Método para comprobar si un jugador está en cualquier área protegida
    private boolean isInProtectedArea(Player player) {
        if (isExempt(player)) {
            return false;  // Si está exento, no se le aplican las protecciones
        }

        for (ProtectedArea area : protectedAreas) {
            if (area.isInArea(player)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isInProtectedArea(player)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("block_break"));
            spawnSmokeParticles(player);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isInProtectedArea(player)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("block_place"));
            spawnSmokeParticles(player);
        }
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (isInProtectedArea(player)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("bucket_empty"));
            spawnSmokeParticles(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isInProtectedArea(player)) {
            // Bloquear el uso de mecheros
            String itemName = player.getInventory().getItemInHand().getName();
            if (itemName.equals("Flint and Steel")) {
                event.setCancelled(true);
                player.sendMessage(getMessage("use_flinsteel"));
                spawnSmokeParticles(player);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (isInProtectedArea(player)) {
                // Desactivar PvP en áreas protegidas
                event.setCancelled(true);
                player.sendMessage(getMessage("pvp_disabled"));
                spawnSmokeParticles(player);
            }
        }
    }

    // Método para generar partículas de humo (negro) en la posición del jugador
    private void spawnSmokeParticles(Player player) {
        Vector3 position = player.getPosition();
        Level level = player.getLevel();
        for (int i = 0; i < 5; i++) {
            level.addParticle(new SmokeParticle(position.add(Math.random() * 2 - 1, Math.random() * 2, Math.random() * 2 - 1)));
        }
    }
}

// Clase para manejar las áreas protegidas
class ProtectedArea {
    private Vector3 corner1;
    private Vector3 corner2;
    private Level level;

    public ProtectedArea(Vector3 corner1, Vector3 corner2, Level level) {
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.level = level;
    }

    public boolean isInArea(Player player) {
        if (player.getLevel() != level) {
            return false;
        }
        Vector3 pos = player.getPosition();
        return (pos.x >= Math.min(corner1.x, corner2.x) && pos.x <= Math.max(corner1.x, corner2.x)) &&
                (pos.y >= Math.min(corner1.y, corner2.y) && pos.y <= Math.max(corner1.y, corner2.y)) &&
                (pos.z >= Math.min(corner1.z, corner2.z) && pos.z <= Math.max(corner1.z, corner2.z));
    }
}
