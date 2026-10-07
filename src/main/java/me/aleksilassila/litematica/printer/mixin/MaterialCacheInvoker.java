package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.materials.MaterialCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = MaterialCache.class, remap = false)
public interface MaterialCacheInvoker {
    @Invoker("getStateToItemOverride")
    ItemStack invokeGetStateToItemOverride(BlockState state);
}
