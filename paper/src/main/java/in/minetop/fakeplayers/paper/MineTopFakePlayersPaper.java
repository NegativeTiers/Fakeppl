package in.minetop.fakeplayers.paper;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MineTopFakePlayersPaper extends JavaPlugin {
    private final Map<String, Integer> npcIds = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> followTasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadIds();
        Objects.requireNonNull(getCommand("fakeplayer")).setTabCompleter((s,c,l,a) -> complete(a));
        getLogger().info("MineTopFakePlayers Pro enabled. Citizens detected.");
    }

    @Override
    public void onDisable() {
        followTasks.values().forEach(BukkitTask::cancel);
        followTasks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minetop.fakeplayers.admin")) return msg(sender, "No permission.", NamedTextColor.RED);
        if (args.length == 0) return help(sender);
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "tphere" -> tpHere(sender, args);
            case "tp" -> tpTo(sender, args);
            case "skin" -> skin(sender, args);
            case "follow" -> follow(sender, args);
            case "sit" -> sit(sender, args);
            case "animation", "anim" -> animation(sender, args);
            case "list" -> list(sender);
            case "tabexport" -> tabExport(sender);
            case "reload" -> { reloadConfig(); loadIds(); yield msg(sender, "Reloaded.", NamedTextColor.GREEN); }
            default -> help(sender);
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Run in-game.", NamedTextColor.RED);
        if (args.length < 2) return msg(sender, "Usage: /fake create <name>", NamedTextColor.YELLOW);
        String name = clean(args[1]);
        if (name == null) return msg(sender, "Name must be 1-16 letters, numbers or _.", NamedTextColor.RED);
        if (find(name) != null) return msg(sender, "Fake player already exists.", NamedTextColor.RED);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.spawn(player.getLocation());
        npc.data().setPersistent("minetop-fake", true);
        npc.data().setPersistent("minetop-name", name);
        npcIds.put(name.toLowerCase(Locale.ROOT), npc.getId());
        saveIds();
        return msg(sender, "Created " + name + ".", NamedTextColor.GREEN);
    }

    private boolean remove(CommandSender sender, String[] args) {
        NPC npc = need(sender, args); if (npc == null) return true;
        String key = npc.getName().toLowerCase(Locale.ROOT);
        stopFollow(key);
        npc.destroy();
        npcIds.remove(key);
        saveIds();
        return msg(sender, "Removed fake player.", NamedTextColor.GREEN);
    }

    private boolean tpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Run in-game.", NamedTextColor.RED);
        NPC npc = need(sender, args); if (npc == null) return true;
        npc.teleport(player.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        return msg(sender, "Teleported " + npc.getName() + " to you.", NamedTextColor.GREEN);
    }

    private boolean tpTo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Run in-game.", NamedTextColor.RED);
        NPC npc = need(sender, args); if (npc == null) return true;
        if (!npc.isSpawned()) return msg(sender, "NPC is not spawned.", NamedTextColor.RED);
        player.teleport(npc.getEntity().getLocation());
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean skin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return msg(sender, "Usage: /fake skin <name> <MinecraftName>", NamedTextColor.YELLOW);
        }

        NPC npc = find(args[1]);
        if (npc == null) {
            return msg(sender, "Fake player not found.", NamedTextColor.RED);
        }

        try {
            Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
            Object skinTrait = npc.getOrAddTrait((Class) skinTraitClass);
            skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, args[2]);

            if (npc.isSpawned()) {
                Location location = npc.getEntity().getLocation();
                npc.despawn();
                npc.spawn(location);
            }

            return msg(sender, "Skin updated.", NamedTextColor.GREEN);
        } catch (ClassNotFoundException e) {
            getLogger().warning("Citizens SkinTrait class was not found. Install a compatible Citizens build.");
            return msg(sender, "Skin support is unavailable with this Citizens version.", NamedTextColor.RED);
        } catch (ReflectiveOperationException e) {
            getLogger().warning("Could not apply NPC skin: " + e.getMessage());
            return msg(sender, "Could not update skin. Check console.", NamedTextColor.RED);
        }
    }

    private boolean follow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return msg(sender, "Run in-game.", NamedTextColor.RED);
        NPC npc = need(sender, args); if (npc == null) return true;
        String key = npc.getName().toLowerCase(Locale.ROOT);
        if (args.length >= 3 && args[2].equalsIgnoreCase("off")) {
            stopFollow(key); npc.getNavigator().cancelNavigation();
            return msg(sender, "Follow disabled.", NamedTextColor.GREEN);
        }
        stopFollow(key);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || !npc.isSpawned()) { stopFollow(key); return; }
            double distance = getConfig().getDouble("follow-distance", 2.2);
            if (!Objects.equals(player.getWorld(), npc.getEntity().getWorld())) {
                npc.teleport(player.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else if (npc.getEntity().getLocation().distanceSquared(player.getLocation()) > distance * distance) {
                npc.getNavigator().setTarget(player, false);
            } else npc.getNavigator().cancelNavigation();
        }, 1L, 10L);
        followTasks.put(key, task);
        return msg(sender, "Now following you. Use /fake follow " + npc.getName() + " off", NamedTextColor.GREEN);
    }

    private boolean sit(CommandSender sender, String[] args) {
        NPC npc = need(sender, args); if (npc == null) return true;
        // Citizens exposes sitting through its own command; select this NPC for sender then execute it.
        if (!(sender instanceof Player player)) return msg(sender, "Run in-game.", NamedTextColor.RED);
        CitizensAPI.getDefaultNPCSelector().select(player, npc);
        Bukkit.dispatchCommand(player, "npc sit");
        return true;
    }

    private boolean animation(CommandSender sender, String[] args) {
        if (args.length < 3) return msg(sender, "Usage: /fake animation <name> <swing|hurt|jump>", NamedTextColor.YELLOW);
        NPC npc = find(args[1]);
        if (npc == null || !npc.isSpawned()) return msg(sender, "Fake player not found/spawned.", NamedTextColor.RED);
        if (!(npc.getEntity() instanceof Player p)) return true;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "swing" -> p.swingMainHand();
            case "hurt" -> p.damage(0.01);
            case "jump" -> p.setVelocity(p.getVelocity().setY(0.42));
            default -> { return msg(sender, "Animation: swing, hurt, jump", NamedTextColor.YELLOW); }
        }
        return msg(sender, "Animation played.", NamedTextColor.GREEN);
    }

    private boolean list(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (String key : npcIds.keySet()) { NPC n = find(key); if (n != null) names.add(n.getName()); }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return msg(sender, "Fake players (" + names.size() + "): " + (names.isEmpty() ? "none" : String.join(", ", names)), NamedTextColor.AQUA);
    }

    private boolean tabExport(CommandSender sender) {
        File out = new File(getDataFolder(), "TAB-layout-snippet.yml");
        List<String> names = new ArrayList<>();
        for (String key : npcIds.keySet()) { NPC n = find(key); if (n != null) names.add(n.getName()); }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        StringBuilder y = new StringBuilder("# Paste fixed-slots into TAB's layout section on Velocity\nfixed-slots:\n");
        int slot = 1;
        for (String n : names) {
            if (slot > 80) break;
            y.append("  - '").append(slot++).append("|&7").append(n).append("|player:").append(n).append("'\n");
        }
        try { Files.createDirectories(out.toPath().getParent()); Files.writeString(out.toPath(), y.toString()); }
        catch (IOException e) { getLogger().warning(e.getMessage()); return msg(sender, "Could not export TAB file.", NamedTextColor.RED); }
        return msg(sender, "Exported plugins/MineTopFakePlayers/TAB-layout-snippet.yml", NamedTextColor.GREEN);
    }

    private NPC need(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "A fake-player name is required.", NamedTextColor.YELLOW); return null; }
        NPC npc = find(args[1]);
        if (npc == null) msg(sender, "Fake player not found.", NamedTextColor.RED);
        return npc;
    }

    private NPC find(String name) {
        Integer id = npcIds.get(name.toLowerCase(Locale.ROOT));
        if (id == null) return null;
        return CitizensAPI.getNPCRegistry().getById(id);
    }

    private void stopFollow(String key) {
        BukkitTask task = followTasks.remove(key);
        if (task != null) task.cancel();
    }

    private void loadIds() {
        npcIds.clear();
        var section = getConfig().getConfigurationSection("fake-players");
        if (section != null) for (String key : section.getKeys(false)) npcIds.put(key.toLowerCase(Locale.ROOT), section.getInt(key));
    }

    private void saveIds() {
        getConfig().set("fake-players", null);
        npcIds.forEach((k,v) -> getConfig().set("fake-players." + k, v));
        saveConfig();
    }

    private List<String> complete(String[] args) {
        if (args.length == 1) return starts(List.of("create","remove","tphere","tp","skin","follow","sit","animation","list","tabexport","reload"), args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) return starts(new ArrayList<>(npcIds.keySet()), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("animation")) return starts(List.of("swing","hurt","jump"), args[2]);
        return List.of();
    }

    private List<String> starts(List<String> in, String value) { String v=value.toLowerCase(Locale.ROOT); return in.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(v)).toList(); }
    private String clean(String s) { return s.matches("[A-Za-z0-9_]{1,16}") ? s : null; }
    private boolean help(CommandSender s) { return msg(s, "/fake create/remove/tphere/tp/skin/follow/sit/animation/list/tabexport", NamedTextColor.YELLOW); }
    private boolean msg(CommandSender s, String text, NamedTextColor color) { s.sendMessage(Component.text(text, color)); return true; }
}
