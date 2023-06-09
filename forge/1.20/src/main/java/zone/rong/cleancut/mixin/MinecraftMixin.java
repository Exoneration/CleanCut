package zone.rong.cleancut.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow @Nullable public MultiPlayerGameMode gameMode;
    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Nullable public ClientLevel level;

    @Redirect(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean onAttack(ClientLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        Entity entity = searchEntityForInteraction(state, world, pos);
        if (entity != null) {
            this.gameMode.attack(this.player, entity);
            return true;
        }
        return false;
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn" +
            "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult onItemUse(MultiPlayerGameMode instance, LocalPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        Entity entity = searchEntityForInteraction(this.level.getBlockState(hitResult.getBlockPos()), this.level, hitResult.getBlockPos());
        if (entity != null) {
            InteractionResult actionResult = this.gameMode.interactAt(this.player, entity, new EntityHitResult(entity), hand);
            if (!actionResult.consumesAction()) {
                actionResult = this.gameMode.interact(this.player, entity, hand);
            }
            if (!actionResult.consumesAction()) {
                return InteractionResult.PASS;
            }
            if (actionResult.shouldSwing()) {
                this.player.swing(hand);
                return InteractionResult.FAIL;
            }
        }
        return this.gameMode.useItemOn(this.player, hand, hitResult);
    }

    private Entity searchEntityForInteraction(BlockState state, ClientLevel world, BlockPos pos) {
        if (allowBlock(state, world, pos)) {
            float reach = this.gameMode.getPickRange();
            Vec3 camera = this.player.getEyePosition(1.0F);
            Vec3 rotation = this.player.getViewVector(1.0F);
            Vec3 end = searchClosestBlock(camera, camera.add(rotation.x * reach, rotation.y * reach, rotation.z * reach), world);
            EntityHitResult result = ProjectileUtil.getEntityHitResult(world, this.player, camera, end, new AABB(camera, end), entity -> allowEntity(entity));
            return result == null ? null : result.getEntity();
        }
        return null;
    }

    private boolean allowBlock(BlockState state, ClientLevel world, BlockPos pos) {
        return state.getCollisionShape(world, pos).isEmpty();
    }

    private boolean allowEntity(Entity entity) {
        if (entity.isSpectator()) {
            return false;
        }
        if (!entity.isPickable()) {
            return false;
        }
        if (entity instanceof OwnableEntity) {
            UUID ownedUuid = ((OwnableEntity) entity).getOwnerUUID();
            if (ownedUuid != null && ownedUuid.equals(this.player.getUUID())) {
                return false;
            }
        }
        return !getRidingEntities().contains(entity);
    }

    private Vec3 searchClosestBlock(Vec3 from, Vec3 to, ClientLevel world) {
        HitResult hitResult = world.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, this.player));
        if (hitResult.getType() != Type.MISS) {
            return hitResult.getLocation();
        }
        return to;
    }

    private Collection<Entity> getRidingEntities() {
        Collection<Entity> riding = new HashSet<>();
        Entity entity = this.player;
        while (entity.getVehicle() != null) {
            entity = entity.getVehicle();
            riding.add(entity);
        }
        return riding;
    }

}
