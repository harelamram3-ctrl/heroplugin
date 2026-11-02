package com.harel.superheroes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SuperHeroesPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, String> playerHeroes = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("hero") != null) {
            getCommand("hero").setExecutor(this);
        }

        // לולאת חלקיקי תעופה שרצה כל 2 טאקים
        Bukkit.getScheduler().runTaskTimer(this, this::playFlightAnimations, 0L, 2L);
        getLogger().info("SuperHeroes 2.0 has been enabled successfully!");
    }

    private void playFlightAnimations() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isFlying()) continue;

            String hero = playerHeroes.getOrDefault(player.getUniqueId(), "default");
            Location loc = player.getLocation();

            switch (hero.toLowerCase()) {
                case "superman":
                    player.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.2, 0.2, 0.2, 0.05);
                    player.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.1, 0.1, 0.1, 0.05);
                    break;
                case "flash":
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 5, 0.3, 0.3, 0.3, 0.1);
                    break;
                case "thor":
                    player.getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.3, 0.1, 0.3, 0.02);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.2, 0.4, 0.2, 0.1);
                    break;
                case "ironman":
                    Location feet = loc.clone().subtract(0, 0.5, 0);
                    player.getWorld().spawnParticle(Particle.SMALL_FLAME, feet, 4, 0.1, 0.1, 0.1, 0.02);
                    break;
                default:
                    player.getWorld().spawnParticle(Particle.END_ROD, loc, 2, 0.2, 0.2, 0.2, 0.01);
                    break;
            }
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() && playerHeroes.containsKey(player.getUniqueId())) {
            Location loc = player.getLocation();
            player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.8f);
            player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 15, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cשימוש נכון: /hero set <superman|flash|thor|ironman> <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§cהשחקן המבוקש אינו מחובר.");
                return true;
            }
            String heroName = args[1].toLowerCase();
            playerHeroes.put(target.getUniqueId(), heroName);
            sender.sendMessage("§aהגיבור של §e" + target.getName() + " §aהוגדר בהצלחה ל-§b" + heroName);
            target.sendMessage("§aהוגדרת כגיבור: §b" + heroName);
        }
        return true;
    }
}
