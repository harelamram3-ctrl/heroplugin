package com.harel.superheroes;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public enum Hero {
    BATMAN("Batman", "Grapnel Gun", "Smoke Bomb", "Dark Knight Rampage", ChatColor.DARK_GRAY,
            Color.fromRGB(35, 35, 40), Color.fromRGB(250, 210, 0), false, Material.CROSSBOW, "Grapnel Launcher", 140, 360,
            List.of(eff(PotionEffectType.NIGHT_VISION, 0), eff(PotionEffectType.JUMP_BOOST, 1), eff(PotionEffectType.SPEED, 0))),
    BLACKPANTHER("Black Panther", "Vibranium Release", "Kinetic Pounce", "Wakanda Forever", ChatColor.DARK_PURPLE,
            Color.fromRGB(20, 20, 30), Color.fromRGB(150, 90, 230), false, Material.AMETHYST_SHARD, "Vibranium Shard", 160, 200,
            List.of(eff(PotionEffectType.SPEED, 1), eff(PotionEffectType.JUMP_BOOST, 1), eff(PotionEffectType.NIGHT_VISION, 0))),
    CAPTAINAMERICA("Captain America", "Shield Throw", "Shield Block", "Avengers Assemble", ChatColor.BLUE,
            Color.fromRGB(25, 60, 170), Color.fromRGB(220, 30, 40), false, Material.SHIELD, "Vibranium Shield", 120, 240,
            List.of(eff(PotionEffectType.STRENGTH, 0), eff(PotionEffectType.RESISTANCE, 0), eff(PotionEffectType.SPEED, 0))),
    DEADPOOL("Deadpool", "Katana Frenzy", "Dual Pistols", "Maximum Effort", ChatColor.RED,
            Color.fromRGB(170, 15, 25), Color.fromRGB(20, 20, 20), false, Material.GOLDEN_SWORD, "Katana", 100, 140,
            List.of(eff(PotionEffectType.REGENERATION, 1), eff(PotionEffectType.SPEED, 0), eff(PotionEffectType.STRENGTH, 0))),
    DOCTORSTRANGE("Doctor Strange", "Portal Teleport", "Eye of Agamotto", "Dark Dimension", ChatColor.GOLD,
            Color.fromRGB(40, 70, 190), Color.fromRGB(190, 40, 40), false, Material.ENDER_EYE, "Eye of Agamotto", 200, 400,
            List.of(eff(PotionEffectType.SLOW_FALLING, 0), eff(PotionEffectType.NIGHT_VISION, 0), eff(PotionEffectType.RESISTANCE, 0))),
    FLASH("The Flash", "Speed Dash", "Speed Force Lightning", "Time Warp", ChatColor.RED,
            Color.fromRGB(200, 20, 20), Color.fromRGB(255, 220, 40), false, Material.BLAZE_ROD, "Speed Force Rod", 60, 200,
            List.of(eff(PotionEffectType.SPEED, 3), eff(PotionEffectType.JUMP_BOOST, 1), eff(PotionEffectType.HASTE, 1))),
    GREENLANTERN("Green Lantern", "Lantern Beam", "Construct Wall", "Emerald Fist", ChatColor.GREEN,
            Color.fromRGB(20, 150, 50), Color.fromRGB(10, 10, 10), true, Material.EMERALD, "Power Ring", 80, 300,
            List.of(eff(PotionEffectType.NIGHT_VISION, 0), eff(PotionEffectType.RESISTANCE, 0))),
    HULK("Hulk", "Hulk Smash", "Gamma Roar", "World Breaker", ChatColor.GREEN,
            Color.fromRGB(60, 160, 50), Color.fromRGB(110, 60, 150), false, Material.SLIME_BALL, "Gamma Core", 140, 300,
            List.of(eff(PotionEffectType.STRENGTH, 1), eff(PotionEffectType.RESISTANCE, 1), eff(PotionEffectType.JUMP_BOOST, 2))),
    IRONMAN("Iron Man", "Repulsor Blast", "Unibeam", "Hulkbuster Barrage", ChatColor.GOLD,
            Color.fromRGB(180, 20, 30), Color.fromRGB(240, 190, 40), true, Material.NETHER_STAR, "Arc Reactor", 60, 260,
            List.of(eff(PotionEffectType.RESISTANCE, 0), eff(PotionEffectType.FIRE_RESISTANCE, 0), eff(PotionEffectType.NIGHT_VISION, 0))),
    SPIDERMAN("Spider-Man", "Web Shot", "Web Swing", "Web Cocoon", ChatColor.RED,
            Color.fromRGB(200, 25, 30), Color.fromRGB(30, 60, 170), false, Material.SPIDER_EYE, "Web-Shooter", 60, 100,
            List.of(eff(PotionEffectType.SPEED, 1), eff(PotionEffectType.JUMP_BOOST, 3), eff(PotionEffectType.SLOW_FALLING, 0))),
    STORM("Storm", "Lightning Storm", "Tornado", "Hurricane", ChatColor.AQUA,
            Color.fromRGB(235, 235, 245), Color.fromRGB(60, 80, 180), true, Material.WIND_CHARGE, "Storm Cyclone", 160, 260,
            List.of(eff(PotionEffectType.RESISTANCE, 0), eff(PotionEffectType.NIGHT_VISION, 0))),
    SUPERMAN("Superman", "Heat Vision", "Freeze Breath", "Solar Flare", ChatColor.BLUE,
            Color.fromRGB(20, 70, 200), Color.fromRGB(215, 30, 40), true, Material.DIAMOND, "Kryptonian Crystal", 40, 220,
            List.of(eff(PotionEffectType.STRENGTH, 1), eff(PotionEffectType.RESISTANCE, 1), eff(PotionEffectType.REGENERATION, 0))),
    THOR("Thor", "Mjolnir Throw", "Lightning Strike", "Thunderclap", ChatColor.AQUA,
            Color.fromRGB(70, 80, 100), Color.fromRGB(190, 30, 30), true, Material.MACE, "Mjolnir", 100, 200,
            List.of(eff(PotionEffectType.STRENGTH, 1), eff(PotionEffectType.RESISTANCE, 1), eff(PotionEffectType.FIRE_RESISTANCE, 0))),
    WONDERWOMAN("Wonder Woman", "Lasso of Truth", "Bracelets of Submission", "Amazon Fury", ChatColor.GOLD,
            Color.fromRGB(190, 30, 40), Color.fromRGB(30, 60, 170), false, Material.NETHERITE_SWORD, "Sword of Athena", 120, 400,
            List.of(eff(PotionEffectType.STRENGTH, 0), eff(PotionEffectType.SPEED, 1), eff(PotionEffectType.RESISTANCE, 0)));

    public final String display, primary, secondary, ultimate, itemName;
    public final ChatColor chat;
    public final Color main, second;
    public final boolean flight;
    public final Material icon;
    public final int cooldownPrimary, cooldownSecondary;
    public final List<PotionEffect> effects;

    Hero(String display, String primary, String secondary, String ultimate, ChatColor chat, Color main, Color second,
         boolean flight, Material icon, String itemName, int cdPrimary, int cdSecondary, List<PotionEffect> effects) {
        this.display = display; this.primary = primary; this.secondary = secondary; this.ultimate = ultimate;
        this.chat = chat; this.main = main; this.second = second; this.flight = flight;
        this.icon = icon; this.itemName = itemName; this.cooldownPrimary = cdPrimary; this.cooldownSecondary = cdSecondary;
        this.effects = effects;
    }

    private static PotionEffect eff(PotionEffectType t, int amp) {
        return new PotionEffect(t, PotionEffect.INFINITE_DURATION, amp, false, false, false);
    }

    public static Hero parse(String s) {
        if (s == null) return null;
        String k = s.toUpperCase().replace(" ", "").replace("-", "").replace("_", "");
        for (Hero h : values()) if (h.name().equals(k) || h.display.toUpperCase().replace(" ", "").replace("-", "").equals(k)) return h;
        return null;
    }
}
