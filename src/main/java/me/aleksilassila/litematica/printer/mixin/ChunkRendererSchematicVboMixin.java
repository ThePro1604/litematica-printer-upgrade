package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.render.schematic.FluidModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
import me.aleksilassila.litematica.printer.InventoryRenderFilter;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public class ChunkRendererSchematicVboMixin {
    @Shadow
    protected ChunkCacheSchematic schematicWorldView;

    // Skips both the block and its overlay, like a position outside the render layer range
    @Inject(method = "renderBlocksAndOverlay", at = @At("HEAD"), cancellable = true)
    private void renderOnlyInventoryBlocks(BlockModelRendererSchematic blockRenderer, FluidModelRendererSchematic fluidRenderer,
                                           BlockPos pos, ChunkRenderDataSchematic data, ChunkMeshDataSchematic chunkMeshData,
                                           ChunkRenderDispatcherBuffers pack, IBlockOutputSchematic blockOutput, Vec3 offset,
                                           VisGraph visGraph, CallbackInfo ci) {
        if (InventoryRenderFilter.shouldHide(this.schematicWorldView.getBlockState(pos))) {
            ci.cancel();
        }
    }
}
