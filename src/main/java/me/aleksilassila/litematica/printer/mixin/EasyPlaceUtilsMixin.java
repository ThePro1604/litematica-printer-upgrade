package me.aleksilassila.litematica.printer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import me.aleksilassila.litematica.printer.EasyPlaceHelper;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EasyPlaceUtils.class, remap = false)
public class EasyPlaceUtilsMixin {
    @WrapOperation(method = "handleEasyPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private static InteractionResult autoRotate(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand,
                                                BlockHitResult hitResult, Operation<InteractionResult> original) {
        return EasyPlaceHelper.useItemOnRotated(gameMode, player, hand, hitResult, original);
    }
}
