package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.MaterialCacheInvoker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides schematic blocks whose required item is not in the player's inventory.
 * The schematic chunks are built on worker threads, so they only read a snapshot of the inventory
 * taken on the client thread.
 */
public class InventoryRenderFilter {
    // Items that run out only count as missing after this long, so items that refill themselves
    // (or are briefly moved around) don't make the schematic blink
    private static final long MISSING_ITEM_DELAY_MS = 5000;

    private static final Map<BlockState, Item> REQUIRED_ITEMS = new ConcurrentHashMap<>();
    private static final Map<Item, Long> LAST_SEEN = new HashMap<>();
    private static volatile Set<Item> availableItems = Set.of();
    private static volatile boolean enabled = false;

    public static void onTick(LocalPlayer player) {
        boolean wasEnabled = enabled;
        enabled = Configs.RENDER_ONLY_INVENTORY_BLOCKS.getBooleanValue();

        if (!enabled) {
            if (wasEnabled) {
                LAST_SEEN.clear();
                SchematicWorldRefresher.INSTANCE.updateAll();
            }
            return;
        }

        long now = System.currentTimeMillis();
        for (Item item : getInventoryItems(player)) {
            LAST_SEEN.put(item, now);
        }
        LAST_SEEN.values().removeIf(lastSeen -> now - lastSeen > MISSING_ITEM_DELAY_MS);

        Set<Item> items = Set.copyOf(LAST_SEEN.keySet());

        if (!wasEnabled || !items.equals(availableItems)) {
            availableItems = items;
            SchematicWorldRefresher.INSTANCE.updateAll();
        }
    }

    /**
     * Called from the schematic chunk render threads.
     */
    public static boolean shouldHide(BlockState schematicState) {
        if (!enabled || schematicState.isAir()) {
            return false;
        }

        Item item = REQUIRED_ITEMS.computeIfAbsent(schematicState, InventoryRenderFilter::getRequiredItem);

        // Blocks that can't be placed with an item (piston heads, portals...) stay visible
        return item != Items.AIR && !availableItems.contains(item);
    }

    private static Item getRequiredItem(BlockState state) {
        // Same as MaterialCache#getRequiredBuildItemForState(), without writing to its non-thread-safe cache
        ItemStack stack = ((MaterialCacheInvoker) MaterialCache.getInstance()).invokeGetStateToItemOverride(state);

        if (stack == null) {
            return state.getBlock().asItem();
        }

        return stack.isEmpty() ? Items.AIR : stack.getItem();
    }

    private static Set<Item> getInventoryItems(LocalPlayer player) {
        Set<Item> items = new HashSet<>();
        boolean includeShulkers = fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCK_SHULKERS.getBooleanValue();

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            addItem(items, stack, includeShulkers);
        }
        addItem(items, player.getOffhandItem(), includeShulkers);

        return items;
    }

    private static void addItem(Set<Item> items, ItemStack stack, boolean includeShulkers) {
        if (stack.isEmpty()) {
            return;
        }

        items.add(stack.getItem());

        // Litematica can pick blocks out of shulker boxes when this is enabled
        if (includeShulkers) {
            for (ItemStack stored : fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(stack)) {
                if (!stored.isEmpty()) {
                    items.add(stored.getItem());
                }
            }
        }
    }
}
