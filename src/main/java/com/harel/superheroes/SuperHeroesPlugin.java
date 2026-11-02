package com.harel.superheroes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.*;

public final class SuperHeroesPlugin extends JavaPlugin implements Listener, TabExecutor {

    record Snapshot(Location loc, double health) {}

    // shared state used by Abilities
    final Map<UUID, Double> kinetic = new HashMap<>();
    final Map<UUID, Long> shieldUntil = new HashMap<>();
    final Map<UUID, Long> braceletsUntil = new HashMap<>();
    final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    final Map<UUID, Long> awaitingCoords = new java.util.concurrent.ConcurrentHashMap<>();

    private final Map<UUID, Hero> heroes = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Double> charge = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> healingFactor = new HashMap<>();
    private final Map<UUID, Long> senseUntil = new HashMap<>();
    private final Set<UUID> boosting = new HashSet<>();

    private NamespacedKey heroKey, itemKey, killsKey;
    private Abilities abilities;
    private long tick;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // ---------------------------------------------------------------- lifecycle
    @Override public void onEnable() {
        saveDefaultConfig();
        heroKey = new NamespacedKey(this, "hero");
        itemKey = new NamespacedKey(this, "hero_item");
        killsKey = new NamespacedKey(this, "hero_kills");
        abilities = new Abilities(this);
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("hero");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        Bukkit.getScheduler().runTaskTimer(this, this::mainTick, 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) restore(p);
        getLogger().info("SuperHeroes " + getPluginMeta().getVersion() + " enabled!");
    }

    @Override public void onDisable() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }

    // ---------------------------------------------------------------- config helpers
    double damageMult(Player p) {
        Hero h = heroes.get(p.getUniqueId());
        double m = getConfig().getDouble("settings.damage-multiplier", 1.0);
        if (h != null) m *= getConfig().getDouble("heroes." + h.name() + ".damage-multiplier", 1.0);
        return m;
    }

    private double cdMult(Hero h) {
        return getConfig().getDouble("settings.cooldown-multiplier", 1.0) * getConfig().getDouble("heroes." + h.name() + ".cooldown-multiplier", 1.0);
    }

    private boolean enabled(Hero h) { return getConfig().getBoolean("heroes." + h.name() + ".enabled", true); }
    private double chargeMax() { return getConfig().getDouble("settings.ultimate-charge-max", 100); }

    boolean canHurt(Player attacker, LivingEntity e) {
        if (e.isDead() || !e.isValid()) return false;
        if (e instanceof Player o) {
            if (!getConfig().getBoolean("settings.pvp-abilities", true)) return false;
            return survivalLike(o);
        }
        return true;
    }

    private boolean survivalLike(Player p) { return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE; }

    private boolean powersAllowed(Player p) {
        return !getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName());
    }

    // ---------------------------------------------------------------- hero state
    Hero getHero(Player p) { return heroes.get(p.getUniqueId()); }

    private void restore(Player p) {
        String s = p.getPersistentDataContainer().get(heroKey, PersistentDataType.STRING);
        Hero h = Hero.parse(s);
        if (h != null && enabled(h)) equip(p, h);
    }

    void equip(Player p, Hero h) {
        clearHero(p, false);
        UUID id = p.getUniqueId();
        heroes.put(id, h);
        p.getPersistentDataContainer().set(heroKey, PersistentDataType.STRING, h.name());
        p.getInventory().setHelmet(heroHead(h));
        p.getInventory().setChestplate(armor(Material.LEATHER_CHESTPLATE, h, h.main, "Suit"));
        p.getInventory().setLeggings(armor(Material.LEATHER_LEGGINGS, h, h.second, "Leggings"));
        p.getInventory().setBoots(armor(Material.LEATHER_BOOTS, h, h.second, "Boots"));
        giveItem(p, h);
        applyEffects(p, h);
        charge.put(id, 0.0);
        history.put(id, new ArrayDeque<>());
        if (h.flight && survivalLike(p)) { p.setAllowFlight(true); p.setFlySpeed(flySpeed(h)); }
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.3);
        String tip = h.flight ? " Double-jump to fly, sprint while flying to boost!" : "";
        p.sendMessage(h.chat + "" + ChatColor.BOLD + "You are now " + h.display + "!" + ChatColor.RESET + ChatColor.GRAY
                + "\nRight-click the " + h.itemName + " for " + h.chat + h.primary + ChatColor.GRAY
                + ", sneak + right-click for " + h.chat + h.secondary + ChatColor.GRAY
                + ", press F for your ULTIMATE " + h.chat + h.ultimate + ChatColor.GRAY + "." + tip);
    }

    void clearHero(Player p, boolean message) {
        UUID id = p.getUniqueId();
        Hero h = heroes.remove(id);
        removeHeroItems(p);
        if (h != null) {
            for (PotionEffect e : h.effects) p.removePotionEffect(e.getType());
            if (h.flight && survivalLike(p)) { p.setFlying(false); p.setAllowFlight(false); p.setFlySpeed(0.1f); }
        }
        p.removePotionEffect(PotionEffectType.STRENGTH);
        BossBar b = bars.remove(id);
        if (b != null) b.removeAll();
        charge.remove(id); kinetic.remove(id); history.remove(id); boosting.remove(id);
        if (message) {
            p.getPersistentDataContainer().remove(heroKey);
            p.sendMessage(ChatColor.GRAY + "You are a normal human again.");
        }
    }

    private void applyEffects(Player p, Hero h) { for (PotionEffect e : h.effects) p.addPotionEffect(e); }

    private float flySpeed(Hero h) {
        return switch (h) { case SUPERMAN -> 0.16f; case IRONMAN, THOR, GREENLANTERN -> 0.12f; default -> 0.11f; };
    }

    private double boostSpeed(Hero h) {
        return switch (h) { case SUPERMAN -> 2.2; case IRONMAN -> 1.7; default -> 1.4; };
    }

    // ---------------------------------------------------------------- items
    private ItemStack armor(Material m, Hero h, Color c, String piece) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        meta.setDisplayName(h.chat + "" + ChatColor.BOLD + h.display + " " + piece);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_DYE);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** The hero's head, worn instead of a helmet. Configure in config.yml under "heads". */
    private ItemStack heroHead(Hero h) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        String src = getConfig().getString("heads." + h.name(), "");
        try {
            if (src.length() > 60) { // base64 texture value (from minecraft-heads.com "Value")
                PlayerProfile prof = Bukkit.createProfile(UUID.nameUUIDFromBytes(("superheroes:" + h.name()).getBytes()), h.name());
                prof.setProperty(new ProfileProperty("textures", src));
                meta.setPlayerProfile(prof);
            } else if (!src.isBlank()) { // a Minecraft username whose skin face is used
                meta.setPlayerProfile(Bukkit.createProfile(src));
            }
        } catch (Exception ex) {
            getLogger().warning("Could not build head for " + h.name() + ": " + ex.getMessage());
        }
        meta.setDisplayName(h.chat + "" + ChatColor.BOLD + h.display + " Head");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    void startCooldown(Player p, boolean secondary) {
        Hero h = getHero(p);
        if (h == null) return;
        long ticks = secondary ? h.cooldownSecondary : h.cooldownPrimary;
        cooldowns.put(p.getUniqueId() + (secondary ? ":s" : ":p"), System.currentTimeMillis() + (long) (ticks * 50 * cdMult(h)));
    }

    private ItemStack powerItem(Hero h) {
        ItemStack it = new ItemStack(h.icon);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(h.chat + "" + ChatColor.BOLD + h.itemName + " Power");
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click: " + h.chat + h.primary,
                ChatColor.GRAY + "Sneak + Right-click: " + h.chat + h.secondary,
                ChatColor.GRAY + "Press F: " + h.chat + h.ultimate + ChatColor.GRAY + " (needs full charge)"));
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private void giveItem(Player p, Hero h) {
        ItemStack it = powerItem(h);
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    boolean isHeroItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    private void removeHeroItems(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isHeroItem(inv.getItem(i))) inv.setItem(i, null);
        if (isHeroItem(inv.getItemInOffHand())) inv.setItemInOffHand(null);
        if (isHeroItem(inv.getHelmet())) inv.setHelmet(null);
        if (isHeroItem(inv.getChestplate())) inv.setChestplate(null);
        if (isHeroItem(inv.getLeggings())) inv.setLeggings(null);
        if (isHeroItem(inv.getBoots())) inv.setBoots(null);
    }

    // ---------------------------------------------------------------- ability use / cooldowns
    private void use(Player p, boolean secondary) {
        Hero h = getHero(p);
        if (h == null || !powersAllowed(p)) return;
        String key = p.getUniqueId() + (secondary ? ":s" : ":p");
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(key);
        if (until != null && until > now) {
            p.sendMessage(ChatColor.RED + "Cooldown: " + String.format("%.1f", (until - now) / 1000.0) + "s");
            return;
        }
        boolean ok = secondary ? abilities.secondary(p, h) : abilities.primary(p, h);
        if (ok) {
            long ticks = secondary ? h.cooldownSecondary : h.cooldownPrimary;
            cooldowns.put(key, now + (long) (ticks * 50 * cdMult(h)));
        }
    }

    private void useUltimate(Player p) {
        Hero h = getHero(p);
        if (h == null || !powersAllowed(p)) return;
        double c = charge.getOrDefault(p.getUniqueId(), 0.0);
        if (c < chargeMax()) {
            p.sendMessage(ChatColor.RED + "Ultimate charging: " + (int) (100 * c / chargeMax()) + "% — deal damage to charge it!");
            return;
        }
        charge.put(p.getUniqueId(), 0.0);
        abilities.ultimate(p, h);
    }

    private void addCharge(Player p, double amount) {
        if (getHero(p) == null) return;
        charge.merge(p.getUniqueId(), amount, (a, b) -> Math.min(chargeMax(), a + b));
        double cur = charge.get(p.getUniqueId());
        if (cur > chargeMax()) charge.put(p.getUniqueId(), chargeMax());
    }

    private String cdText(Player p, boolean secondary) {
        Long until = cooldowns.get(p.getUniqueId() + (secondary ? ":s" : ":p"));
        long left = until == null ? 0 : until - System.currentTimeMillis();
        return left <= 0 ? ChatColor.GREEN + "READY" : ChatColor.RED + String.format("%.1fs", left / 1000.0);
    }

    // ---------------------------------------------------------------- main loop
    private void mainTick() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Hero h = heroes.get(p.getUniqueId());
            if (h == null) continue;
            // flight boost
            if (h.flight && p.isFlying() && p.isSprinting() && powersAllowed(p)) {
                p.setVelocity(p.getLocation().getDirection().multiply(boostSpeed(h)));
                if (boosting.add(p.getUniqueId())) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.4f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.2, 0.2, 0.2, 0.01);
            } else boosting.remove(p.getUniqueId());
            if (tick % 10 == 0) passive(p, h);
            if (tick % 20 == 0) snapshot(p, h);
            if (tick % 100 == 0) { // re-apply effects removed by milk etc.
                for (PotionEffect e : h.effects) if (!p.hasPotionEffect(e.getType())) p.addPotionEffect(e);
            }
            if (tick % 5 == 0) hud(p, h);
        }
    }

    private void snapshot(Player p, Hero h) {
        if (h != Hero.DOCTORSTRANGE) return;
        Deque<Snapshot> d = history.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        d.addLast(new Snapshot(p.getLocation().clone(), p.getHealth()));
        while (d.size() > 5) d.pollFirst();
    }

    private void passive(Player p, Hero h) {
        if (h == Hero.HULK) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            int amp = Math.min(4, 1 + (int) ((max - p.getHealth()) / 5.0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, amp, false, false, false));
        } else if (h == Hero.SPIDERMAN) {
            long now = System.currentTimeMillis();
            if (senseUntil.getOrDefault(p.getUniqueId(), 0L) < now) {
                for (var e : p.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof Projectile pr && !p.equals(pr.getShooter())) {
                        p.sendMessage(ChatColor.RED + "~ Spider-Sense tingling! ~");
                        p.playSound(p.getLocation(), Sound.ENTITY_CAT_HISS, 1f, 1.4f);
                        senseUntil.put(p.getUniqueId(), now + 3000);
                        break;
                    }
                }
            }
        } else if (h == Hero.BLACKPANTHER) {
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0);
        }
    }

    private void hud(Player p, Hero h) {
        double c = charge.getOrDefault(p.getUniqueId(), 0.0);
        double max = chargeMax();
        boolean ready = c >= max;
        String line = h.chat + h.primary + ": " + cdText(p, false) + ChatColor.DARK_GRAY + " | " + h.chat + h.secondary + ": " + cdText(p, true)
                + ChatColor.DARK_GRAY + " | " + (ready ? ChatColor.GOLD + "" + ChatColor.BOLD + "ULT READY [F]" : ChatColor.YELLOW + "ULT " + (int) (100 * c / max) + "%");
        if (h == Hero.BLACKPANTHER) line += ChatColor.DARK_GRAY + " | " + ChatColor.LIGHT_PURPLE + "Kinetic " + (int) (double) kinetic.getOrDefault(p.getUniqueId(), 0.0);
        p.sendActionBar(LEGACY.deserialize(line));

        if (getConfig().getBoolean("settings.show-bossbar", true)) {
            BossBar bar = bars.computeIfAbsent(p.getUniqueId(), k -> { BossBar b = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10); b.addPlayer(p); return b; });
            bar.setColor(ready ? BarColor.GREEN : BarColor.YELLOW);
            bar.setProgress(Math.max(0, Math.min(1, c / max)));
            bar.setTitle(h.chat + "" + ChatColor.BOLD + h.ultimate + ChatColor.RESET + (ready ? ChatColor.GOLD + " — press F!" : ChatColor.GRAY + " " + (int) (100 * c / max) + "%"));
        }
    }

    // ---------------------------------------------------------------- events
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) restore(p); }, 5L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Hero h = heroes.get(id);
        if (h != null) { // strip gear so nothing duplicates; hero is restored on join from PDC
            String keep = h.name();
            clearHero(p, false);
            p.getPersistentDataContainer().set(heroKey, PersistentDataType.STRING, keep);
        }
        cooldowns.keySet().removeIf(k -> k.startsWith(id.toString()));
        awaitingCoords.remove(id); shieldUntil.remove(id); braceletsUntil.remove(id); healingFactor.remove(id); senseUntil.remove(id);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (getHero(p) == null || !isHeroItem(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        use(p, p.isSneaking());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Long until = awaitingCoords.get(p.getUniqueId());
        if (until == null) return;
        e.setCancelled(true);
        e.viewers().clear();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        UUID id = p.getUniqueId();
        awaitingCoords.remove(id);
        if (until < System.currentTimeMillis()) { Bukkit.getScheduler().runTask(this, () -> p.sendMessage(ChatColor.RED + "Portal request expired.")); return; }
        if (text.equalsIgnoreCase("cancel")) { Bukkit.getScheduler().runTask(this, () -> p.sendMessage(ChatColor.GRAY + "Portal cancelled.")); return; }
        String[] parts = text.replace(",", " ").trim().split("\\s+");
        double[] n = new double[parts.length];
        try { for (int i = 0; i < parts.length; i++) n[i] = Double.parseDouble(parts[i]); }
        catch (NumberFormatException ex) {
            Bukkit.getScheduler().runTask(this, () -> p.sendMessage(ChatColor.RED + "Invalid coordinates. Right-click again and type: x y z"));
            return;
        }
        if (n.length != 2 && n.length != 3) {
            Bukkit.getScheduler().runTask(this, () -> p.sendMessage(ChatColor.RED + "Type 3 numbers (x y z) or 2 numbers (x z)."));
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline() || getHero(p) != Hero.DOCTORSTRANGE || !powersAllowed(p)) return;
            boolean ok = n.length == 3 ? abilities.portalTo(p, n[0], n[1], n[2]) : abilities.portalTo(p, n[0], null, n[1]);
            if (ok) startCooldown(p, false);
        });
    }

    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (getHero(p) == null) return;
        if (isHeroItem(p.getInventory().getItemInMainHand()) || isHeroItem(p.getInventory().getItemInOffHand())) {
            e.setCancelled(true);
            useUltimate(p);
        }
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        if (isHeroItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof HeroMenu) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
            ItemStack it = e.getCurrentItem();
            if (it == null) return;
            if (it.getType() == Material.BARRIER) { clearHero(p, true); p.closeInventory(); return; }
            for (Hero h : Hero.values()) if (h.icon == it.getType() && enabled(h) && it.getItemMeta() != null
                    && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)
                    && h.name().equals(it.getItemMeta().getPersistentDataContainer().get(heroKey, PersistentDataType.STRING))) {
                p.closeInventory();
                equip(p, h);
                return;
            }
            return;
        }
        if (e.getWhoClicked() instanceof Player p && getHero(p) != null) {
            if (e.getSlotType() == InventoryType.SlotType.ARMOR && isHeroItem(e.getCurrentItem())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Hero h = getHero(p);
        if (h == null) return;
        DamageCause cause = e.getCause();
        boolean noFall = h.flight || h == Hero.HULK || h == Hero.SPIDERMAN || h == Hero.BLACKPANTHER || h == Hero.BATMAN || h == Hero.FLASH;
        if (noFall && (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL)) { e.setCancelled(true); return; }
        if ((h == Hero.THOR || h == Hero.STORM) && cause == DamageCause.LIGHTNING) { e.setCancelled(true); return; }
        long now = System.currentTimeMillis();
        if (braceletsUntil.getOrDefault(p.getUniqueId(), 0L) > now && cause == DamageCause.PROJECTILE) { e.setCancelled(true); return; }
        if (shieldUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
            e.setDamage(e.getDamage() * 0.2);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
            p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0.2);
        }
        if (h == Hero.BLACKPANTHER) {
            kinetic.merge(p.getUniqueId(), e.getDamage(), (a, b) -> Math.min(30.0, a + b));
            e.setDamage(e.getDamage() * 0.75);
        }
        if (h == Hero.DEADPOOL && p.getHealth() - e.getFinalDamage() <= 4 && healingFactor.getOrDefault(p.getUniqueId(), 0L) < now) {
            healingFactor.put(p.getUniqueId(), now + 60_000);
            e.setCancelled(true);
            p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), 14));
            p.sendMessage(ChatColor.RED + "Healing factor! " + ChatColor.GRAY + "\"Nope. Not today.\"");
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 12, 0.5, 0.5, 0.5, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHitCharge(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player pl) attacker = pl;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) attacker = pl;
        if (attacker == null || e.getEntity().equals(attacker)) return;
        addCharge(attacker, e.getFinalDamage() * getConfig().getDouble("settings.ultimate-charge-per-damage", 2.5));
    }

    @EventHandler public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || getHero(k) == null) return;
        addCharge(k, getConfig().getDouble("settings.ultimate-charge-per-kill", 25));
        var pdc = k.getPersistentDataContainer();
        pdc.set(killsKey, PersistentDataType.INTEGER, pdc.getOrDefault(killsKey, PersistentDataType.INTEGER, 0) + 1);
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isHeroItem);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Hero h = getHero(p);
        if (h == null) return;
        if (getConfig().getBoolean("settings.keep-hero-on-death", true)) {
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) equip(p, h); }, 2L);
        } else clearHero(p, true);
    }

    // ---------------------------------------------------------------- menu
    static final class HeroMenu implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private void openMenu(Player p) {
        HeroMenu holder = new HeroMenu();
        Inventory inv = Bukkit.createInventory(holder, 27, LEGACY.deserialize(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Choose your hero"));
        holder.inv = inv;
        int[] slots = {1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16};
        Hero[] all = Hero.values();
        for (int i = 0; i < all.length && i < slots.length; i++) {
            Hero h = all[i];
            if (!enabled(h)) continue;
            ItemStack it = new ItemStack(h.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(h.chat + "" + ChatColor.BOLD + h.display);
            m.setLore(List.of(
                    ChatColor.GRAY + "Primary: " + h.chat + h.primary,
                    ChatColor.GRAY + "Secondary: " + h.chat + h.secondary,
                    ChatColor.GRAY + "Ultimate: " + h.chat + h.ultimate,
                    ChatColor.GRAY + (h.flight ? "Can fly" : "Ground hero"),
                    "", ChatColor.YELLOW + "Click to become " + h.display));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            m.setEnchantmentGlintOverride(getHero(p) == h);
            m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().set(heroKey, PersistentDataType.STRING, h.name());
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        ItemStack none = new ItemStack(Material.BARRIER);
        ItemMeta nm = none.getItemMeta();
        nm.setDisplayName(ChatColor.RED + "Become a normal human");
        none.setItemMeta(nm);
        inv.setItem(22, none);
        p.openInventory(inv);
    }

    // ---------------------------------------------------------------- commands
    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) openMenu(p); else s.sendMessage(ChatColor.RED + "Players only. Try /hero list");
            return true;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "menu" -> { if (s instanceof Player p) openMenu(p); else s.sendMessage(ChatColor.RED + "Players only."); }
            case "list" -> {
                StringBuilder sb = new StringBuilder(ChatColor.GOLD + "Heroes: ");
                for (Hero h : Hero.values()) if (enabled(h)) sb.append(h.chat).append(h.display).append(ChatColor.GRAY).append(", ");
                s.sendMessage(sb.toString());
            }
            case "info" -> {
                Hero h = a.length > 1 ? Hero.parse(a[1]) : (s instanceof Player p ? getHero(p) : null);
                if (h == null) { s.sendMessage(ChatColor.RED + "Usage: /hero info <hero>"); return true; }
                s.sendMessage(h.chat + "" + ChatColor.BOLD + h.display + ChatColor.RESET + ChatColor.GRAY
                        + "\n Primary: " + h.chat + h.primary + ChatColor.GRAY + " (cd " + h.cooldownPrimary / 20.0 + "s)"
                        + "\n Secondary: " + h.chat + h.secondary + ChatColor.GRAY + " (cd " + h.cooldownSecondary / 20.0 + "s)"
                        + "\n Ultimate: " + h.chat + h.ultimate + ChatColor.GRAY + " (charge by dealing damage)"
                        + "\n Flight: " + (h.flight ? "yes" : "no"));
            }
            case "stats" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                Hero h = getHero(p);
                int kills = p.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
                s.sendMessage(ChatColor.GOLD + "Hero: " + (h == null ? ChatColor.GRAY + "none" : h.chat + h.display)
                        + ChatColor.GOLD + " | Kills as hero: " + ChatColor.WHITE + kills
                        + ChatColor.GOLD + " | Ultimate: " + ChatColor.WHITE + (int) (100 * charge.getOrDefault(p.getUniqueId(), 0.0) / chargeMax()) + "%");
            }
            case "ult" -> { if (s instanceof Player p) useUltimate(p); else s.sendMessage("Players only."); }
            case "none" -> { if (s instanceof Player p) clearHero(p, true); else s.sendMessage("Players only."); }
            case "reload" -> {
                if (!s.hasPermission("superheroes.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
                reloadConfig();
                s.sendMessage(ChatColor.GREEN + "SuperHeroes config reloaded.");
            }
            case "set" -> {
                if (!s.hasPermission("superheroes.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (a.length < 3) { s.sendMessage(ChatColor.RED + "Usage: /hero set <player> <hero|none>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(ChatColor.RED + "Player not found."); return true; }
                if (a[2].equalsIgnoreCase("none")) { clearHero(t, true); return true; }
                Hero h = Hero.parse(a[2]);
                if (h == null || !enabled(h)) { s.sendMessage(ChatColor.RED + "Unknown or disabled hero."); return true; }
                equip(t, h);
                s.sendMessage(ChatColor.GREEN + t.getName() + " is now " + h.display + ".");
            }
            default -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                Hero h = Hero.parse(a[0]);
                if (h == null || !enabled(h)) { s.sendMessage(ChatColor.RED + "Unknown hero. Options: " + heroList() + "|none"); return true; }
                equip(p, h);
            }
        }
        return true;
    }

    private String heroList() {
        StringJoiner j = new StringJoiner("|");
        for (Hero h : Hero.values()) j.add(h.name().toLowerCase());
        return j.toString();
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("menu", "list", "info", "stats", "ult", "none"));
            if (s.hasPermission("superheroes.admin")) out.addAll(List.of("reload", "set"));
            for (Hero h : Hero.values()) out.add(h.name().toLowerCase());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("info")) {
            for (Hero h : Hero.values()) out.add(h.name().toLowerCase());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("set")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            out.add("none");
            for (Hero h : Hero.values()) out.add(h.name().toLowerCase());
        }
        String pre = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(pre));
        return out;
    }
}
