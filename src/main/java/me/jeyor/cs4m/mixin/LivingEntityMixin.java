package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import me.jeyor.cs4m.weapon.WeaponItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float cs4mAdjustDamage(float damage, ServerLevel level, DamageSource source) {
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime == null) {
            return damage;
        }
        return runtime.match().adjustDamage((LivingEntity) (Object) this, source, damage);
    }

    @Inject(method = "getItemBlockingWith", at = @At("HEAD"), cancellable = true)
    private void cs4mDisableGunBlock(CallbackInfoReturnable<ItemStack> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (WeaponItems.isWeapon(self.getUseItem())) {
            cir.setReturnValue(null);
        }
    }
}
