package me.aleksilassila.litematica.printer.materials;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.ILitematicaBlockStatePalette;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.mixin.StandingAndWallBlockItemAccessor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces every block of one material in a placed schematic with another block item,
 * keeping the block state properties both blocks share (facing, half, axis...).
 */
public class MaterialReplacer {
    // Give up waiting for the schematic world rebuild after this long, and recount anyway
    private static final long REBUILD_TIMEOUT_MS = 15000;

    @Nullable private static MaterialListBase pendingMaterialList;
    private static long pendingSince;

    /**
     * @return the number of different block states that were replaced
     */
    public static int replace(SchematicPlacement placement, MaterialListBase materialList, ItemStack material, Item replacement) {
        LitematicaSchematic schematic = placement.getSchematic();
        int replaced = 0;

        for (String region : schematic.getAreaSizes().keySet()) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);

            if (container == null) {
                continue;
            }

            // Swapping palette entries changes every block using them, without touching the block data
            ILitematicaBlockStatePalette palette = container.getPalette();
            List<BlockState> mapping = new ArrayList<>(palette.fromMapping());
            boolean changed = false;

            for (int i = 0; i < mapping.size(); i++) {
                BlockState state = mapping.get(i);

                if (state.isAir() || !ItemStack.isSameItem(MaterialCache.getInstance().getRequiredBuildItemForState(state), material)) {
                    continue;
                }

                BlockState newState = convert(state, replacement);

                if (newState != null && newState != state) {
                    mapping.set(i, newState);
                    changed = true;
                    replaced++;
                }
            }

            if (changed && !palette.setMapping(mapping)) {
                Printer.LOGGER.error("Failed to replace materials in region {} of {}", region, placement.getName());
            }
        }

        if (replaced > 0) {
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
            // The material list is counted from the schematic world, so recount once it has been rebuilt
            pendingMaterialList = materialList;
            pendingSince = System.currentTimeMillis();
        }

        return replaced;
    }

    public static void onTick() {
        if (pendingMaterialList == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - pendingSince;

        // Wait a moment for the rebuild tasks to get queued
        if (elapsed < 250 || (PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks() && elapsed < REBUILD_TIMEOUT_MS)) {
            return;
        }

        MaterialListBase materialList = pendingMaterialList;
        pendingMaterialList = null;
        materialList.reCreateMaterialList();
    }

    /**
     * Finds the replacement block that has the most properties in common with the original state,
     * for example the wall variant of a torch item for a wall torch.
     */
    @Nullable
    public static BlockState convert(BlockState original, Item replacement) {
        if (!(replacement instanceof BlockItem blockItem)) {
            return null;
        }

        List<Block> candidates = new ArrayList<>();
        candidates.add(blockItem.getBlock());

        if (replacement instanceof StandingAndWallBlockItem) {
            candidates.add(((StandingAndWallBlockItemAccessor) replacement).getWallBlock());
        }

        BlockState best = null;
        int bestScore = -1;

        for (Block block : candidates) {
            BlockState state = block.defaultBlockState();
            int score = 0;

            for (Property<?> property : original.getProperties()) {
                Property<?> targetProperty = block.getStateDefinition().getProperty(property.getName());

                if (targetProperty != null) {
                    BlockState copied = copyValue(state, targetProperty, original, property);

                    if (copied != null) {
                        state = copied;
                        score++;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = state;
            }
        }

        return best;
    }

    @Nullable
    private static <T extends Comparable<T>> BlockState copyValue(BlockState target, Property<T> targetProperty,
                                                                  BlockState original, Property<?> property) {
        Optional<T> value = targetProperty.getValue(getValueName(original, property));
        return value.map(v -> target.setValue(targetProperty, v)).orElse(null);
    }

    private static <T extends Comparable<T>> String getValueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
