package vn.hideseek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Đọc/ghi config. Luôn đọc trực tiếp nên /hs reload có hiệu lực ngay. */
public final class Cfg {

    public record MapDef(String id, String name, Location hider, Location seeker) {}

    private final HideSeekPlugin plugin;

    public Cfg(HideSeekPlugin plugin) { this.plugin = plugin; }

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private FileConfiguration f() { return plugin.getConfig(); }

    public int i(String key) { return f().getInt("settings." + key); }
    public double d(String key) { return f().getDouble("settings." + key); }

    public void reload() { plugin.reloadConfig(); }

    // ---------- Location ----------
    private Location loc(ConfigurationSection s) {
        if (s == null) return null;
        String w = s.getString("world");
        if (w == null || w.isEmpty()) return null;
        World world = Bukkit.getWorld(w);
        if (world == null) {
            try { world = Bukkit.createWorld(new WorldCreator(w)); } catch (Exception ignored) {}
        }
        if (world == null) return null;
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public void save(String path, Location l) {
        f().set(path + ".world", l.getWorld().getName());
        f().set(path + ".x", l.getX());
        f().set(path + ".y", l.getY());
        f().set(path + ".z", l.getZ());
        f().set(path + ".yaw", (double) l.getYaw());
        f().set(path + ".pitch", (double) l.getPitch());
        plugin.saveConfig();
    }

    public Location lobby() { return loc(f().getConfigurationSection("lobby")); }
    public void setLobby(Location l) { save("lobby", l); }

    public void setSpawn(String mapId, String role, Location l) { save("maps." + mapId + "." + role, l); }

    public List<MapDef> maps() {
        List<MapDef> out = new ArrayList<>();
        ConfigurationSection sec = f().getConfigurationSection("maps");
        if (sec == null) return out;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection m = sec.getConfigurationSection(id);
            if (m == null) continue;
            Location h = loc(m.getConfigurationSection("hider"));
            Location s = loc(m.getConfigurationSection("seeker"));
            if (h != null && s != null) out.add(new MapDef(id, m.getString("name", id), h, s));
        }
        return out;
    }

    public Set<String> mapIds() {
        ConfigurationSection sec = f().getConfigurationSection("maps");
        return sec == null ? Set.of() : sec.getKeys(false);
    }

    // ---------- Blocks ----------
    private List<Material> blockCache;

    /** Mọi block rắn, trừ blacklist. */
    public List<Material> blocks() {
        if (blockCache != null) return blockCache;
        Set<String> black = new HashSet<>(f().getStringList("settings.blacklist"));
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock() || !m.isItem() || !m.isSolid() || m.isAir()) continue;
            if (black.contains(m.name())) continue;
            list.add(m);
        }
        list.sort((a, b) -> a.name().compareTo(b.name()));
        return blockCache = list;
    }

    public void clearCache() { blockCache = null; }
}
