package io.github.archemedes.betterbooks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.HashMap;
import java.util.List;

public class BookSaveListener
        implements Listener {
    private final BetterBooks plugin;
    HashMap<String, OpenBook> readers = new HashMap<>();

    public BookSaveListener(BetterBooks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block b = event.getClickedBlock();
        Player p = event.getPlayer();

        if (p.getEquipment().getItemInMainHand().getType() == Material.BOOKSHELF || (p.isSneaking() && event.isBlockInHand()))
            return;

        Action a = event.getAction();
        if (((a == Action.RIGHT_CLICK_BLOCK) || (a == Action.LEFT_CLICK_BLOCK)) &&
                (b.getType() == Material.BOOKSHELF) &&
                (event.getBlockFace() != BlockFace.UP) && (event.getBlockFace() != BlockFace.DOWN)) {
            if ((p.isSneaking()) && (this.readers.containsKey(p.getName()))) {
                OpenBook book = this.readers.get(p.getName());
                Location l = book.getLocation();
                if ((l.getWorld() == p.getWorld()) && (l.distance(p.getLocation()) < 5.0D)) {
                    String[] words = book.readNext(a == Action.LEFT_CLICK_BLOCK).split(" ");
                    int i1 = book.getPage() + 1;
                    int i2 = book.getPages();
                    p.sendMessage(ChatColor.GREEN + "--------------------------------------------------");
                    p.sendMessage(ChatColor.GREEN + "Book: " + ChatColor.WHITE + book.getTitle() + " " + ChatColor.GREEN + "By: " + ChatColor.WHITE + book.getAuthor() + ChatColor.GREEN + " (page " + i1 + "/" + i2 + ")");
                    p.sendMessage(ChatColor.GREEN.toString());

                    StringBuilder bl = new StringBuilder(64);
                    for (String word : words) {
                        bl.append(word).append(" ");
                        if (bl.length() >= 50) {
                            p.sendMessage(ChatColor.stripColor(bl.toString()));
                            bl = new StringBuilder(64);
                        }
                    }
                    p.sendMessage(bl.toString());
                    p.sendMessage(ChatColor.GREEN + "--------------------------------------------------");
                }
            } else if (a == Action.RIGHT_CLICK_BLOCK) {
                if (event.isCancelled()) {
                    if (BookShelf.hasBookShelf(b)) {
                        p.sendMessage(ChatColor.RED + "You may only read through the contents of this bookshelf.");
                        BookShelf shelf = BookShelf.getBookshelf(b);
                        shelf.addViewer(p);
                        p.openInventory(shelf.getInventory());
                    } else {
                        p.sendMessage(ChatColor.RED + "You may not access this bookshelf at present.");
                    }
                } else {
                    Inventory inv = BookShelf.getBookshelf(b).getInventory();
                    p.openInventory(inv);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent u) {
        Inventory inv = u.getInventory();
        if ((inv.getHolder() instanceof BookShelf))
            u.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(InventoryClickEvent u) {
        Inventory inv = u.getInventory();
        if (!(inv.getHolder() instanceof BookShelf)) {
            return;
        }
        BookShelf shelf = (BookShelf) inv.getHolder();
        final Player p = (Player) u.getWhoClicked();
        InventoryAction a = u.getAction();

        Material type = null;

        if (shelf.isViewer(p)) {
            u.setCancelled(true);

            Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
                @Override
                public void run() {
                    p.closeInventory();
                }

            });
            if ((a.toString().contains("PICKUP")) && (
                    (u.getCurrentItem().getType() == Material.WRITTEN_BOOK) || (u.getCurrentItem().getType() == Material.BOOK_AND_QUILL))) {
                BookMeta meta = (BookMeta) u.getCurrentItem().getItemMeta();
                String title = meta.getTitle() == null ? "Unknown" : ChatColor.stripColor(meta.getTitle());
                String auth = meta.getAuthor() == null ? "Unknown" : ChatColor.stripColor(meta.getAuthor());
                List<String> pages = meta.getPages();
                boolean noPages = (pages.isEmpty()) || ((pages.size() == 1) && (pages.get(0).isEmpty()));
                int i2 = noPages ? 0 : pages.size();
                p.sendMessage(ChatColor.GREEN + "--------------------------------------------------");
                p.sendMessage(ChatColor.GREEN + "You have selected: " + ChatColor.WHITE + title);
                p.sendMessage(ChatColor.GREEN + "By: " + ChatColor.WHITE + auth);
                p.sendMessage(ChatColor.GREEN + "Pages: " + ChatColor.WHITE + i2);
                p.sendMessage(ChatColor.GREEN + "--------------------------------------------------");

                if (noPages) {
                    p.sendMessage(ChatColor.GRAY + "Sadly, there are no pages for you to read.");
                } else {
                    OpenBook openbook = new OpenBook(title, auth, pages, p.getLocation());
                    OpenBook oldBook = this.readers.put(p.getName(), openbook);
                    p.sendMessage(ChatColor.GOLD + "Right click the containing bookcase, while sneaking, to read through the pages.");

                    if (oldBook != null) {
                        int task = oldBook.getTask();
                        if (task > 0) {
                            Bukkit.getScheduler().cancelTask(task);
                        }

                    }

                    openbook.setTask(Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> BookSaveListener.this.readers.remove(p.getName())
                            , 6000L));
                }

            }

        } else {
            if (a == InventoryAction.NOTHING) return;
            if (a == InventoryAction.COLLECT_TO_CURSOR) {
                u.setCancelled(true);
                return;
            }
            int slot;
            if (u.getRawSlot() <= 8) {
                slot = u.getRawSlot();
            } else {
                if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                    slot = inv.firstEmpty();
                else
                    return;
            }
            if ((a == InventoryAction.MOVE_TO_OTHER_INVENTORY) && (inv.getItem(slot) == null)) {
                type = u.getCurrentItem().getType();
            } else if (a == InventoryAction.HOTBAR_SWAP) {
                ItemStack hotbar = p.getInventory().getItem(u.getHotbarButton());
                type = hotbar == null ?
                        u.getCurrentItem().getType() :
                        p.getInventory().getItem(u.getHotbarButton()).getType();
            } else if (u.getCursor().getType() != Material.AIR) {
                type = u.getCursor().getType();
            }
            if ((type != null) &&
                    (type != Material.BOOK) && (type != Material.PAPER) && (type != Material.WRITTEN_BOOK) &&
                    (type != Material.BOOK_AND_QUILL) && (type != Material.EMPTY_MAP)) {
                u.setCancelled(true);
                return;
            }

            shelf.setChanged();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent u) {
        Inventory inv = u.getInventory();

        if ((inv.getHolder() instanceof BookShelf)) {
            BookShelf shelf = (BookShelf) inv.getHolder();
            Player p = (Player) u.getPlayer();
            shelf.removeViewer(p);

            if (inv.getViewers().size() <= 1)
                shelf.close();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent u) {
        Block b = u.getBlock();

        if (b.getType() != Material.BOOKSHELF) return;
        if (BookShelf.hasBookShelf(b)) {
            BookShelf shelf = BookShelf.getBookshelf(b);
            shelf.remove(!this.plugin.shelvesBurnClean);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent u) {
        Block b = u.getBlock();

        if (b.getType() != Material.BOOKSHELF) return;
        if (BookShelf.hasBookShelf(b)) {
            BookShelf shelf = BookShelf.getBookshelf(b);
            shelf.remove(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent u) {
        u.blockList().stream().filter(b -> (b.getType() == Material.BOOKSHELF) &&
                (BookShelf.hasBookShelf(b))).forEach(b -> {
            BookShelf shelf = BookShelf.getBookshelf(b);
            shelf.remove(true);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBookcasePush(BlockPistonExtendEvent event) {
        List<Block> blocks = event.getBlocks();
        blocks.stream().filter(b -> b.getType() == Material.BOOKSHELF).forEach(b -> event.setCancelled(true));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBookcasePull(BlockPistonRetractEvent event) {
        if (!event.isSticky()) return;

        Block b = event.getBlock().getRelative(event.getDirection(), 2);
        if (b.getType() == Material.BOOKSHELF) event.setCancelled(true);
    }
}