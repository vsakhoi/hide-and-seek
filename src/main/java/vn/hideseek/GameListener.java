package vn.hideseek;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class GameListener implements Listener {

    private final HideSeekPlugin plugin;

    public GameListener(HideSeekPlugin plugin) { this.plugin = plugin; }

    private Game game() { return plugin.game(); }

    /** Admin bật /hs build + creative mới được sửa map. */
    private boolean canBuild(Player p) {
        return p.hasPermission("hs.admin") && game().isBuilder(p.getUniqueId()) && p.getGameMode() == GameMode.CREATIVE;
    }

    // ---------- join / quit ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url != null && !url.isBlank()) {
            String sha1 = plugin.getConfig().getString("resource-pack.sha1", "").trim();
            boolean force = plugin.getConfig().getBoolean("resource-pack.required", false);
            try {
                if (sha1.isEmpty()) p.setResourcePack(url);
                else p.setResourcePack(url, sha1, force);
            } catch (Exception ex) {
                plugin.getLogger().warning("Không gửi được resource pack: " + ex.getMessage());
            }
        }
        if (game().isBuilder(p.getUniqueId())) return;
        game().sendToLobby(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { game().handleQuit(e.getPlayer()); }

    // ---------- block KHÔNG phá/đặt được ----------
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) { e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) { e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().clear(); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().clear(); }

    // ---------- sát thương ----------
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = game();
        if (!g.isPlaying(victim.getUniqueId())) { e.setCancelled(true); return; }
        if (g.state() != Game.State.SEEKING || g.isSeeker(victim.getUniqueId())) { e.setCancelled(true); return; }

        // victim là người trốn đang trong pha tìm
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity damager = be.getDamager();
            if (damager instanceof TNTPrimed tnt) {
                if (!(tnt.getSource() instanceof Player src) || !g.isSeeker(src.getUniqueId())) { e.setCancelled(true); return; }
                e.setDamage(plugin.cfg().d("tnt.damage"));
            } else if (damager instanceof Player att && g.isSeeker(att.getUniqueId())) {
                // đấm tay: giữ nguyên sát thương
            } else {
                e.setCancelled(true);
                return;
            }
        } else {
            e.setCancelled(true); // rơi, vực, lửa... đều vô hiệu
            return;
        }

        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            g.eliminate(victim);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) { e.setCancelled(true); }

    // ---------- súng + menu ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            if (!canBuild(p)) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (p.getInventory().getItemInMainHand().getType() == Material.COMPASS && !game().isInvolved(p.getUniqueId())) {
            e.setCancelled(true);
            game().toggleQueue(p);
            return;
        }
        if (game().isSeeker(p.getUniqueId()) && p.getInventory().getItemInMainHand().getType() == Material.BLAZE_ROD) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setCancelled(true);
            game().shoot(p);
        }
    }

    /** Người trốn tay phải trống (để không lộ), chuyển sang slot 9 = mở menu chọn block. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!game().isHider(p.getUniqueId())) return;
        if (e.getNewSlot() == 8) {
            e.setCancelled(true);
            BlockMenu.open(plugin, p, 0);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof BlockMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getInventory()) menu.click(p, e.getSlot());
            return;
        }
        if (!canBuild(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && !canBuild(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) { if (!canBuild(e.getPlayer())) e.setCancelled(true); }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && !canBuild(p)) e.setCancelled(true);
    }

    // ---------- đóng băng người tìm lúc người trốn đi trốn ----------
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (game().state() != Game.State.HIDING || !game().isSeeker(p.getUniqueId())) return;
        var from = e.getFrom();
        var to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            e.setTo(to);
        }
    }
}
