package vn.hideseek;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** GUI chọn block để biến hình (mọi block rắn, có phân trang). */
public final class BlockMenu implements InventoryHolder {

    private static final int PER_PAGE = 45;

    private final HideSeekPlugin plugin;
    private final int page;
    private final int pages;
    private final Inventory inv;

    private BlockMenu(HideSeekPlugin plugin, int page) {
        this.plugin = plugin;
        List<Material> all = plugin.cfg().blocks();
        this.pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inv = Bukkit.createInventory(this, 54, Cfg.c("&8Chọn block &7(" + (this.page + 1) + "/" + pages + ")"));

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            inv.setItem(i, new ItemStack(all.get(start + i)));
        }
        if (this.page > 0) inv.setItem(45, named(Material.ARROW, "&e« Trang trước"));
        if (this.page < pages - 1) inv.setItem(53, named(Material.ARROW, "&eTrang sau »"));
        inv.setItem(49, named(Material.BARRIER, "&cĐóng"));
    }

    public static void open(HideSeekPlugin plugin, Player p, int page) {
        p.openInventory(new BlockMenu(plugin, page).inv);
    }

    private static ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Cfg.c(name));
        it.setItemMeta(meta);
        return it;
    }

    public void click(Player p, int slot) {
        if (slot == 45 && page > 0) { open(plugin, p, page - 1); return; }
        if (slot == 53 && page < pages - 1) { open(plugin, p, page + 1); return; }
        if (slot == 49) { p.closeInventory(); return; }
        if (slot < 0 || slot >= PER_PAGE) return;
        ItemStack it = inv.getItem(slot);
        if (it == null || it.getType().isAir()) return;
        if (!plugin.game().isHider(p.getUniqueId())) {
            p.sendMessage(Cfg.c("&cChỉ người trốn mới biến hình được."));
            return;
        }
        plugin.disguises().apply(p, it.getType());
        p.closeInventory();
        p.sendActionBar(Component.text("Bạn đã biến thành " + it.getType().name().toLowerCase().replace('_', ' ')));
    }

    @Override
    public Inventory getInventory() { return inv; }
}
