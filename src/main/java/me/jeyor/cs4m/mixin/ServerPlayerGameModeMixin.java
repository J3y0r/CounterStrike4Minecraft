package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void cs4mUseItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime != null && runtime.match().onUseItem(player, hand)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void cs4mUseItemOn(
            ServerPlayer player,
            Level level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (runtime.match().onUseBlock(player, pos, level.getBlockState(pos).getBlock())) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockPos placed = pos.relative(hit.getDirection());
            if (runtime.match().onPlaceBlock(player, placed, blockItem.getBlock(), level.getBlockState(placed.below()).getBlock())) {
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
}
