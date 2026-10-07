package me.aleksilassila.litematica.printer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.PlacementHandler;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.BlockItemInvoker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Additions to Litematica's own Easy Place mode.
 */
public class EasyPlaceHelper {
    private static final float[] CANDIDATE_PITCHES = {0, 90, -90};
    private static final Direction[] CANDIDATE_YAWS = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    /**
     * When ignoring NBT, swaps the schematic's required stack for an inventory stack of the same item,
     * so Litematica's component-sensitive slot lookups find it.
     */
    public static ItemStack getStackToPick(Player player, ItemStack required) {
        if (!Configs.EASY_PLACE_IGNORE_NBT.getBooleanValue() || required.isEmpty() || player.hasInfiniteMaterials()) {
            return required;
        }

        Inventory inventory = player.getInventory();

        // An exact match exists, Litematica will find it on its own
        if (inventory.findSlotMatchingItem(required) != -1) {
            return required;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (ItemStack.isSameItem(mainHand, required)) {
            return mainHand.copy();
        }

        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, required)) {
                return stack.copy();
            }
        }

        return required;
    }

    /**
     * Wraps Easy Place's {@code useItemOn} call, rotating the player and picking the clicked face first
     * so the placed block matches the schematic's orientation.
     */
    public static InteractionResult useItemOnRotated(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand,
                                                     BlockHitResult hitResult, Operation<InteractionResult> original) {
        if (!Configs.EASY_PLACE_AUTO_ROTATE.getBooleanValue()) {
            return original.call(gameMode, player, hand, hitResult);
        }

        // The accurate placement protocols encode the orientation into the hit vector instead
        EasyPlaceProtocol protocol = PlacementHandler.getEffectiveProtocolVersion();
        if (protocol != EasyPlaceProtocol.NONE && protocol != EasyPlaceProtocol.SLAB_ONLY) {
            return original.call(gameMode, player, hand, hitResult);
        }

        Placement placement = findPlacement(player, hand, hitResult);
        if (placement == null) {
            return original.call(gameMode, player, hand, hitResult);
        }

        Printer.printDebug("Easy Place auto rotate: {} for {}", placement, hitResult.getBlockPos());

        if (!placement.rotate()) {
            return original.call(gameMode, player, hand, placement.hitResult());
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();

        sendRotation(player, placement.yaw(), placement.pitch());
        // Also rotate client side, so the client predicts the same block state as the server
        player.setYRot(placement.yaw());
        player.setXRot(placement.pitch());

        try {
            return original.call(gameMode, player, hand, placement.hitResult());
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
            sendRotation(player, yaw, pitch);
        }
    }

    private record Placement(BlockHitResult hitResult, boolean rotate, float yaw, float pitch) {
        @Override
        public String toString() {
            return "Placement{clickPos=" + hitResult.getBlockPos() + ", side=" + hitResult.getDirection()
                    + ", hit=" + hitResult.getLocation() + (rotate ? ", yaw=" + yaw + ", pitch=" + pitch : "") + "}";
        }
    }

    /**
     * Brute forces a clicked face and a yaw and pitch that produce the schematic block state.
     * Some blocks (logs, stairs, trapdoors...) depend on the clicked face, others on the player's rotation.
     * Returns null if Easy Place's own click with the current rotation is already the best one.
     */
    private static @Nullable Placement findPlacement(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        ItemStack stack = player.getItemInHand(hand);

        if (schematic == null || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        BlockPos targetPos = new BlockPlaceContext(player, hand, stack, hitResult).getClickedPos();
        BlockState targetState = schematic.getBlockState(targetPos);
        if (targetState.isAir()) {
            return null;
        }

        int maxScore = targetState.getProperties().size();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Placement best = null;

        try {
            int bestScore = getScore(player, hand, stack, blockItem, hitResult, targetPos, targetState);

            for (BlockHitResult candidateHit : getCandidateHits(player, hitResult, targetPos, targetState)) {
                for (int i = -1; i < CANDIDATE_PITCHES.length * CANDIDATE_YAWS.length; i++) {
                    if (bestScore == maxScore) {
                        return best;
                    }

                    // i == -1 tries the player's current rotation
                    boolean rotate = i >= 0;
                    float candidateYaw = rotate ? CANDIDATE_YAWS[i % CANDIDATE_YAWS.length].toYRot() : yaw;
                    float candidatePitch = rotate ? CANDIDATE_PITCHES[i / CANDIDATE_YAWS.length] : pitch;

                    player.setYRot(candidateYaw);
                    player.setXRot(candidatePitch);
                    int score = getScore(player, hand, stack, blockItem, candidateHit, targetPos, targetState);

                    if (score > bestScore) {
                        bestScore = score;
                        best = new Placement(candidateHit, rotate, candidateYaw, candidatePitch);
                    }
                }
            }
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }

        return best;
    }

    /**
     * Clicks that place a block at the target position. Easy Place's own click comes first.
     */
    private static List<BlockHitResult> getCandidateHits(LocalPlayer player, BlockHitResult hitResult,
                                                         BlockPos targetPos, BlockState targetState) {
        List<BlockHitResult> hits = new ArrayList<>();
        hits.add(hitResult);

        // Litematica handles slabs, including the second click of double slabs, on its own
        if (targetState.getBlock() instanceof SlabBlock) {
            return hits;
        }

        Level level = player.level();

        if (hitResult.getBlockPos().equals(targetPos)) {
            // Clicking the target position itself, any face can be used
            Vec3 location = hitResult.getLocation();
            Vec3[] locations = {
                    location,
                    new Vec3(location.x, targetPos.getY() + 0.25, location.z),
                    new Vec3(location.x, targetPos.getY() + 0.75, location.z),
            };

            for (Direction side : Direction.values()) {
                for (Vec3 candidateLocation : locations) {
                    hits.add(new BlockHitResult(candidateLocation, side, targetPos, false));
                }
            }
        } else {
            // Clicking an adjacent block, so only adjacent blocks that can be clicked safely
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = targetPos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);

                if (neighborState.canBeReplaced() || neighborState.getShape(level, neighborPos).isEmpty()
                        || isInteractive(neighborState.getBlock())
                        || !player.isWithinBlockInteractionRange(neighborPos, 0)) {
                    continue;
                }

                Direction side = direction.getOpposite();
                Vec3 faceCenter = Vec3.atCenterOf(targetPos).add(Vec3.atLowerCornerOf(direction.getUnitVec3i()).scale(0.5));
                hits.add(new BlockHitResult(faceCenter, side, neighborPos, false));

                if (side.getAxis().isHorizontal()) {
                    hits.add(new BlockHitResult(faceCenter.add(0, -0.25, 0), side, neighborPos, false));
                    hits.add(new BlockHitResult(faceCenter.add(0, 0.25, 0), side, neighborPos, false));
                }
            }
        }

        return hits;
    }

    private static boolean isInteractive(Block block) {
        if (block instanceof EntityBlock) {
            return true;
        }

        for (Class<?> clazz : BlockHelper.interactiveBlocks) {
            if (clazz.isInstance(block)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Number of target properties the placed state would match with the player's current rotation, or -1 if
     * the wrong block would be placed, or it would be placed somewhere else.
     */
    private static int getScore(LocalPlayer player, InteractionHand hand, ItemStack stack, BlockItem blockItem,
                                BlockHitResult hitResult, BlockPos targetPos, BlockState targetState) {
        BlockPlaceContext context = blockItem.updatePlacementContext(new BlockPlaceContext(player, hand, stack, hitResult));
        if (context == null || !context.getClickedPos().equals(targetPos)) {
            return -1;
        }

        BlockState result = ((BlockItemInvoker) blockItem).invokeGetPlacementState(context);
        if (result == null || result.getBlock() != targetState.getBlock()) {
            return -1;
        }

        int score = 0;
        for (Property<?> property : targetState.getProperties()) {
            if (result.getValue(property).equals(targetState.getValue(property))) {
                score++;
            }
        }

        return score;
    }

    private static void sendRotation(LocalPlayer player, float yaw, float pitch) {
        player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
    }
}
