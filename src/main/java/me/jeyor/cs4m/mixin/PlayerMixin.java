package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void cs4mAttack(Entity entity, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime != null && runtime.match().onAttackEntity(player)) {
            ci.cancel();
        }
    }
}
