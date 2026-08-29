package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class FoodDataMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cs4mTick(ServerPlayer player, CallbackInfo ci) {
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime != null && runtime.worldRules().restrictionsEnabled(player.level())) {
            ci.cancel();
        }
    }
}
