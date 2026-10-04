package vn.hideseek;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Commands implements CommandExecutor, TabCompleter {

    private final HideSeekPlugin plugin;

    public Commands(HideSeekPlugin plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(Cfg.c(m)); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();

        if (sub.equals("blocks")) {
            if (!(s instanceof Player p)) { msg(s, "&cChỉ dùng trong game."); return true; }
            if (!plugin.game().isHider(p.getUniqueId())) { msg(s, "&cChỉ người trốn mới dùng được."); return true; }
            BlockMenu.open(plugin, p, 0);
            return true;
        }

        if (!s.hasPermission("hs.admin")) { msg(s, "&cBạn không có quyền."); return true; }

        switch (sub) {
            case "setlobby" -> {
                if (!(s instanceof Player p)) { msg(s, "&cChỉ dùng trong game."); return true; }
                plugin.cfg().setLobby(p.getLocation());
                msg(s, "&aĐã đặt lobby.");
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) { msg(s, "&cChỉ dùng trong game."); return true; }
                if (a.length < 3 || !(a[2].equalsIgnoreCase("hider") || a[2].equalsIgnoreCase("seeker"))) {
                    msg(s, "&eDùng: /hs setspawn <1|2|3> <hider|seeker>");
                    return true;
                }
                String id = a[1].matches("\\d+") ? "map" + a[1] : a[1];
                if (!plugin.cfg().mapIds().contains(id)) { msg(s, "&cKhông có map &e" + id); return true; }
                Location l = p.getLocation();
                plugin.cfg().setSpawn(id, a[2].toLowerCase(), l);
                msg(s, "&aĐã đặt spawn &e" + a[2].toLowerCase() + " &acủa &e" + id + " &a(world " + l.getWorld().getName() + ").");
            }
            case "start" -> { plugin.game().forceStart(); msg(s, "&aĐã thử bắt đầu trận."); }
            case "stop" -> { plugin.game().forceStop(); msg(s, "&aĐã dừng trận."); }
            case "build" -> {
                if (!(s instanceof Player p)) { msg(s, "&cChỉ dùng trong game."); return true; }
                boolean on = plugin.game().toggleBuilder(p.getUniqueId());
                if (on) {
                    p.setGameMode(org.bukkit.GameMode.CREATIVE);
                    msg(s, "&aChế độ xây map: BẬT (không tính vào hàng chờ). Dùng lại để tắt.");
                } else {
                    plugin.game().sendToLobby(p);
                    msg(s, "&eChế độ xây map: TẮT.");
                }
            }
            case "reload" -> {
                plugin.cfg().reload();
                plugin.cfg().clearCache();
                msg(s, "&aĐã reload config.");
            }
            case "status" -> {
                msg(s, "&eTrạng thái: &f" + plugin.game().state()
                        + " &7| Lobby: " + (plugin.cfg().lobby() != null ? "&aok" : "&cchưa đặt")
                        + " &7| Map hợp lệ: &f" + plugin.cfg().maps().size() + "/" + plugin.cfg().mapIds().size());
            }
            default -> {
                msg(s, "&6/hs setlobby &7- đặt lobby");
                msg(s, "&6/hs setspawn <1|2|3> <hider|seeker> &7- đặt spawn map");
                msg(s, "&6/hs start | stop | status | reload | build | blocks");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("blocks", "setlobby", "setspawn", "start", "stop", "status", "reload", "build")) {
                if (x.startsWith(a[0].toLowerCase())) out.add(x);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("setspawn")) {
            out.addAll(List.of("1", "2", "3"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("setspawn")) {
            out.addAll(List.of("hider", "seeker"));
        }
        return out;
    }
}
