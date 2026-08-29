package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void cs4mDropSelected(boolean all, CallbackInfo ci) {
        Cs4mServer runtime = Cs4mAccess.runtime();
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (runtime != null && runtime.match().onDropSelected(player)) {
            player.inventoryMenu.broadcastChanges();
            ci.cancel();
        }
    }
}
