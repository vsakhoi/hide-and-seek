package vn.hideseek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Một trận duy nhất chạy tuần tự: WAITING -> STARTING -> HIDING -> SEEKING -> ENDING -> WAITING. */
public final class Game {

    public enum State { WAITING, STARTING, HIDING, SEEKING, ENDING }

    private final HideSeekPlugin plugin;
    private final Random random = new Random();

    private State state = State.WAITING;
    private int timer;
    private boolean warnedNoMap;

    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> builders = new HashSet<>();
    private final Set<UUID> queued = new HashSet<>();
    private final Map<UUID, Long> gunCooldown = new HashMap<>();
    private Team team;

    public Game(HideSeekPlugin plugin) { this.plugin = plugin; }

    public void startLoop() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        reset();
    }

    // ---------- trạng thái ----------
    public State state() { return state; }
    public boolean isHider(UUID id) { return hiders.contains(id); }
    public boolean isSeeker(UUID id) { return seekers.contains(id); }
    public boolean isInvolved(UUID id) { return hiders.contains(id) || seekers.contains(id) || eliminated.contains(id); }
    public boolean isPlaying(UUID id) { return hiders.contains(id) || seekers.contains(id); }
    public boolean isRunning() { return state == State.HIDING || state == State.SEEKING; }
    public boolean toggleBuilder(UUID id) {
        if (!builders.remove(id)) { builders.add(id); return true; }
        return false;
    }
    public boolean isBuilder(UUID id) { return builders.contains(id); }

    private List<Player> queue() {
        List<Player> list = new ArrayList<>();
        for (UUID id : queued) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !isInvolved(id) && !builders.contains(id)) list.add(p);
        }
        return list;
    }

    public boolean isQueued(UUID id) { return queued.contains(id); }

    public void toggleQueue(Player p) {
        UUID id = p.getUniqueId();
        if (isInvolved(id)) return;
        if (queued.remove(id)) {
            p.sendMessage(Cfg.c("&eBạn đã rời hàng chờ."));
        } else {
            queued.add(id);
            p.sendMessage(Cfg.c("&aBạn đã vào hàng chờ! &7(" + queued.size() + " người)"));
        }
        giveLobbyItems(p);
    }

    /** La bàn ở lobby: chuột phải để vào/ra hàng chờ. */
    public void giveLobbyItems(Player p) {
        boolean in = queued.contains(p.getUniqueId());
        p.getInventory().setItem(4, named(Material.COMPASS,
                in ? "&cRời hàng chờ &7(chuột phải)" : "&aVào hàng chờ chơi &7(chuột phải)"));
        p.getInventory().setHeldItemSlot(4);
    }


    // ---------- vòng lặp 1 giây ----------
    private void tick() {
        Cfg cfg = plugin.cfg();
        switch (state) {
            case WAITING -> {
                if (queue().size() >= cfg.i("min-players")) {
                    state = State.STARTING;
                    timer = cfg.i("lobby-countdown");
                }
            }
            case STARTING -> {
                List<Player> q = queue();
                if (q.size() < cfg.i("min-players")) {
                    state = State.WAITING;
                    for (Player p : q) p.sendMessage(Cfg.c("&cKhông đủ người, huỷ đếm ngược."));
                    return;
                }
                if (q.size() >= cfg.i("max-players") && timer > 5) timer = 5;
                if (timer <= 0) { start(q); return; }
                if (timer <= 5 || timer % 10 == 0) {
                    for (Player p : q) {
                        p.sendActionBar(Cfg.c("&eBắt đầu sau &c" + timer + "s &7(" + q.size() + "/" + cfg.i("max-players") + ")"));
                    }
                }
                timer--;
            }
            case HIDING -> {
                for (UUID id : seekers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendActionBar(Cfg.c("&cBạn bị khoá! Thả sau &e" + timer + "s"));
                }
                for (UUID id : hiders) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.sendActionBar(Cfg.c("&aĐi trốn đi! &e" + timer + "s &7| Chọn block: slot 9 hoặc /hs blocks"));
                }
                if (--timer <= 0) beginSeek();
            }
            case SEEKING -> {
                String msg = "&eCòn lại &c" + timer + "s &7| Người trốn: &a" + hiders.size();
                for (UUID id : hiders) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendActionBar(Cfg.c(msg)); }
                for (UUID id : seekers) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendActionBar(Cfg.c(msg)); }
                if (--timer <= 0) end(false);
            }
            case ENDING -> {
                if (--timer <= 0) reset();
            }
        }
    }

    // ---------- bắt đầu ----------
    private void start(List<Player> q) {
        Cfg cfg = plugin.cfg();
        List<Cfg.MapDef> maps = cfg.maps();
        if (maps.isEmpty() || cfg.lobby() == null) {
            if (!warnedNoMap) {
                warnedNoMap = true;
                Bukkit.broadcast(Cfg.c("&c[HideSeek] Chưa cấu hình lobby/map (cần /hs setlobby và /hs setspawn)."));
            }
            state = State.WAITING;
            return;
        }
        warnedNoMap = false;

        Cfg.MapDef map = maps.get(random.nextInt(maps.size()));
        Collections.shuffle(q, random);
        List<Player> players = new ArrayList<>(q.subList(0, Math.min(q.size(), cfg.i("max-players"))));

        int seekerCount = players.size() >= cfg.i("full-seekers-at") ? cfg.i("seekers-full") : cfg.i("seekers-low");
        seekerCount = Math.max(1, Math.min(seekerCount, players.size() - 1));

        setupTeam();
        int hideTime = cfg.i("hide-time");
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            prepare(p);
            queued.remove(p.getUniqueId());
            team.addEntry(p.getName());
            if (i < seekerCount) {
                seekers.add(p.getUniqueId());
                p.teleport(map.seeker());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hideTime * 20 + 40, 0, false, false));
                p.showTitle(Title.title(Cfg.c("&cNGƯỜI TÌM"), Cfg.c("&7Map: " + map.name())));
            } else {
                hiders.add(p.getUniqueId());
                p.teleport(map.hider());
                List<Material> blocks = cfg.blocks();
                plugin.disguises().apply(p, blocks.get(random.nextInt(blocks.size())));
                p.getInventory().setItem(8, named(Material.NETHER_STAR, "&bChọn block"));
                p.showTitle(Title.title(Cfg.c("&aNGƯỜI TRỐN"), Cfg.c("&7Map: " + map.name())));
            }
        }
        state = State.HIDING;
        timer = hideTime;
        Bukkit.broadcast(Cfg.c("&e[HideSeek] Trận bắt đầu tại &b" + map.name() + "&e! "
                + seekerCount + " người tìm, " + (players.size() - seekerCount) + " người trốn."));
    }

    private void beginSeek() {
        state = State.SEEKING;
        timer = plugin.cfg().i("seek-time");
        for (UUID id : seekers) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.getInventory().setItem(0, gun());
            p.getInventory().setHeldItemSlot(0);
            p.showTitle(Title.title(Cfg.c("&cĐI TÌM!"), Component.empty()));
        }
        for (UUID id : hiders) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Cfg.c("&cNgười tìm đã được thả!"));
        }
    }

    // ---------- súng TNT ----------
    public void shoot(Player p) {
        if (state != State.SEEKING || !seekers.contains(p.getUniqueId())) return;
        Cfg cfg = plugin.cfg();
        long now = System.currentTimeMillis();
        Long last = gunCooldown.get(p.getUniqueId());
        if (last != null && now - last < cfg.i("tnt.cooldown-ms")) return;
        gunCooldown.put(p.getUniqueId(), now);

        Location eye = p.getEyeLocation();
        p.getWorld().playSound(eye, "hideseek:gun.fire", 1.0f, 1.0f);
        p.getWorld().spawn(eye, TNTPrimed.class, t -> {
            t.setFuseTicks(cfg.i("tnt.fuse-ticks"));
            t.setSource(p);
            t.setYield((float) cfg.d("tnt.power"));
            t.setVelocity(eye.getDirection().multiply(cfg.d("tnt.speed")));
        });
    }

    // ---------- loại người trốn ----------
    public void eliminate(Player p) {
        if (!hiders.remove(p.getUniqueId())) return;
        eliminated.add(p.getUniqueId());
        plugin.disguises().remove(p);
        p.setGameMode(GameMode.SPECTATOR);
        p.getInventory().clear();
        p.showTitle(Title.title(Cfg.c("&cBạn đã bị bắt!"), Component.empty()));
        Bukkit.broadcast(Cfg.c("&e[HideSeek] &c" + p.getName() + " &eđã bị bắt! Còn &a" + hiders.size() + " &engười trốn."));
        checkWin();
    }

    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        builders.remove(id);
        queued.remove(id);
        if (!isInvolved(id)) return;
        hiders.remove(id);
        seekers.remove(id);
        eliminated.remove(id);
        plugin.disguises().remove(id);
        if (team != null) team.removeEntry(p.getName());
        p.setInvisible(false);
        if (isRunning()) checkWin();
    }

    private void checkWin() {
        if (!isRunning()) return;
        if (hiders.isEmpty()) end(true);
        else if (seekers.isEmpty()) end(false);
    }

    private void end(boolean seekersWin) {
        state = State.ENDING;
        timer = plugin.cfg().i("end-delay");
        Component title = seekersWin ? Cfg.c("&cNGƯỜI TÌM THẮNG!") : Cfg.c("&aNGƯỜI TRỐN THẮNG!");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInvolved(p.getUniqueId())) p.showTitle(Title.title(title, Component.empty()));
        }
        Bukkit.broadcast(Cfg.c(seekersWin ? "&e[HideSeek] &cNgười tìm đã thắng!" : "&e[HideSeek] &aNgười trốn đã thắng!"));
    }

    // ---------- reset về lobby ----------
    public void reset() {
        Set<UUID> all = new HashSet<>(hiders);
        all.addAll(seekers);
        all.addAll(eliminated);
        Location lobby = plugin.cfg().lobby();
        plugin.disguises().removeAll();
        for (UUID id : all) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            prepare(p);
            if (lobby != null) p.teleport(lobby);
            giveLobbyItems(p);
        }
        if (team != null) {
            for (String e : new ArrayList<>(team.getEntries())) team.removeEntry(e);
        }
        hiders.clear();
        seekers.clear();
        eliminated.clear();
        gunCooldown.clear();
        state = State.WAITING;
    }

    public void forceStart() {
        if (state == State.WAITING || state == State.STARTING) {
            List<Player> q = queue();
            if (!q.isEmpty()) start(q);
        }
    }

    public void forceStop() {
        if (isRunning() || state == State.ENDING) reset();
    }

    /** Đưa người chơi vào lobby ở trạng thái sạch. */
    public void sendToLobby(Player p) {
        prepare(p);
        Location lobby = plugin.cfg().lobby();
        if (lobby != null) p.teleport(lobby);
        giveLobbyItems(p);
    }

    private void prepare(Player p) {
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setInvisible(false);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    private void setupTeam() {
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        team = board.getTeam("hs_game");
        if (team == null) team = board.registerNewTeam("hs_game");
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(false);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    /** Súng: BLAZE_ROD nhưng hiển thị model súng lục của resource pack (không có pack vẫn hiện que lửa). */
    private ItemStack gun() {
        ItemStack it = named(Material.BLAZE_ROD, "&cSúng TNT &7(chuột phải)");
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("hideseek", "pistol"));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Cfg.c(name));
        it.setItemMeta(meta);
        return it;
    }
}
