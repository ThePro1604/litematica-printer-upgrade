package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.util.InventoryUtils;
import me.aleksilassila.litematica.printer.EasyPlaceHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = InventoryUtils.class, remap = false)
public class LitematicaInventoryUtilsMixin {
    // Easy Place picks its item through here in both the old and the rewritten code paths
    @ModifyVariable(method = "schematicWorldPickBlock", at = @At("HEAD"), argsOnly = true)
    private static ItemStack ignoreNbt(ItemStack stack, ItemStack stackArg, BlockPos pos, Level schematicWorld, Minecraft mc) {
        return mc.player == null ? stack : EasyPlaceHelper.getStackToPick(mc.player, stack);
    }
}
