package vn.hideseek;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Biến người chơi thành block: người chơi tàng hình + BlockDisplay bám theo mỗi tick. */
public final class Disguises {

    private final HideSeekPlugin plugin;
    private final Map<UUID, BlockDisplay> displays = new HashMap<>();

    public Disguises(HideSeekPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
    }

    public void apply(Player p, Material m) {
        removeDisplay(p.getUniqueId());
        Location l = base(p);
        BlockDisplay d = p.getWorld().spawn(l, BlockDisplay.class, e -> {
            e.setBlock(m.createBlockData());
            e.setTransformation(new Transformation(new Vector3f(-0.5f, 0f, -0.5f),
                    new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            e.setTeleportDuration(1);
            e.setPersistent(false);
        });
        p.setInvisible(true);
        displays.put(p.getUniqueId(), d);
    }

    public boolean has(UUID id) { return displays.containsKey(id); }

    public void remove(Player p) {
        removeDisplay(p.getUniqueId());
        p.setInvisible(false);
    }

    public void remove(UUID id) {
        removeDisplay(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) p.setInvisible(false);
    }

    public void removeAll() {
        for (UUID id : displays.keySet().toArray(new UUID[0])) remove(id);
    }

    private void removeDisplay(UUID id) {
        BlockDisplay d = displays.remove(id);
        if (d != null) d.remove();
    }

    private Location base(Player p) {
        Location l = p.getLocation();
        l.setYaw(0f);
        l.setPitch(0f);
        return l;
    }

    private void follow() {
        Iterator<Map.Entry<UUID, BlockDisplay>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            BlockDisplay d = en.getValue();
            if (p == null || !p.isOnline() || !d.isValid()) {
                d.remove();
                it.remove();
                continue;
            }
            d.teleport(base(p));
        }
    }
}
