package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.game.GameState;
import me.jeyor.cs4m.item.MatchItems;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.ui.PlayerPresentation;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WeaponController {
    private final Cs4mConfig config;
    private final WeaponCatalog catalog;
    private final MatchRoster roster;
    private final PlayerPresentation presentation;
    private final Map<UUID, GunState> states = new HashMap<>();

    public WeaponController(Cs4mConfig config, WeaponCatalog catalog, MatchRoster roster, PlayerPresentation presentation) {
        this.config = config;
        this.catalog = catalog;
        this.roster = roster;
        this.presentation = presentation;
    }

    public WeaponCatalog catalog() {
        return catalog;
    }

    public void reload(Map<String, Object> weapons) {
        catalog.reload(weapons);
    }

    public void tick(GameState state) {
        for (CsPlayer csPlayer : roster.players()) {
            if (!csPlayer.online()) {
                continue;
            }
            ServerPlayer player = csPlayer.player();
            GunState gun = state(player);
            tickReload(player, gun);
            if (canShoot(state, player) && player.isUsingItem() && WeaponItems.isWeapon(player.getUseItem())) {
                tryFire(player, gun, false);
            } else {
                gun.holding = false;
            }
            gun.spread = Math.max(0.0F, gun.spread - 0.18F);
        }
    }

    public boolean onUseWeapon(ServerPlayer player, GameState state) {
        if (!WeaponItems.isWeapon(player.getItemInHand(InteractionHand.MAIN_HAND))) {
            return false;
        }
        if (!canShoot(state, player)) {
            return true;
        }
        GunState gun = state(player);
        if (gun.reloading) {
            return true;
        }
        if (player.isUsingItem() && WeaponItems.isWeapon(player.getUseItem())) {
            return true;
        }
        player.startUsingItem(InteractionHand.MAIN_HAND);
        tryFire(player, gun, true);
        return true;
    }

    public boolean onReload(ServerPlayer player, GameState state) {
        if (!WeaponItems.isWeapon(player.getMainHandItem()) || roster.find(player).isEmpty()) {
            return false;
        }
        if (state != GameState.RUN && state != GameState.PLANTED && state != GameState.SHOP) {
            return true;
        }
        beginReload(player, state(player), true);
        return true;
    }

    public void clear(UUID uuid) {
        states.remove(uuid);
    }

    public void clearAll() {
        states.clear();
    }

    private void tryFire(ServerPlayer player, GunState gun, boolean justPressed) {
        Optional<WeaponDefinition> definition = catalog.of(player.getMainHandItem());
        if (definition.isEmpty()) {
            return;
        }
        WeaponDefinition weapon = definition.get();
        if (gun.reloading) {
            return;
        }
        if (weapon.semiAutomatic() && gun.holding && !justPressed) {
            return;
        }
        if (player.tickCount < gun.nextShotTick) {
            gun.holding = true;
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (WeaponItems.ammo(stack, weapon) <= 0) {
            beginReload(player, gun, false);
            gun.holding = true;
            return;
        }
        fire(player, stack, weapon, gun);
        gun.holding = true;
        gun.nextShotTick = player.tickCount + weapon.fireIntervalTicks();
        gun.spread = Math.min(weapon.baseSpread() * 4.0F, gun.spread + weapon.baseSpread() * 0.65F);
    }

    private void fire(ServerPlayer player, ItemStack stack, WeaponDefinition weapon, GunState gun) {
        ServerLevel level = player.level();
        WeaponItems.setAmmo(stack, weapon, WeaponItems.ammo(stack, weapon) - 1);
        player.containerMenu.broadcastChanges();
        float spread = weapon.baseSpread() + gun.spread + movementSpread(player);
        Vec3 direction = Hitscan.spread(player.getLookAngle(), spread, player.getRandom());
        HitResult hit = Hitscan.trace(player, direction, weapon.range());
        Vec3 end = hit.getLocation();
        Hitscan.trail(level, player.getEyePosition(), end);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            Hitscan.impact(level, end, living);
            float damage = weapon.damage();
            if (Hitscan.headshot(living, end)) {
                damage *= 2.0F;
            }
            living.invulnerableTime = 0;
            living.hurtServer(level, player.damageSources().playerAttack(player), damage);
        } else {
            Hitscan.impact(level, end, null);
        }
        playShot(level, player, weapon);
        if (config.recoilAnimationEnabled()) {
            float pitch = Mth.clamp(player.getXRot() - 0.8F - gun.spread * 0.15F, -90.0F, 90.0F);
            player.teleportTo(level, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), pitch, false);
        }
    }

    private void beginReload(ServerPlayer player, GunState gun, boolean manual) {
        Optional<WeaponDefinition> definition = catalog.of(player.getMainHandItem());
        if (definition.isEmpty() || gun.reloading) {
            return;
        }
        WeaponDefinition weapon = definition.get();
        ItemStack stack = player.getMainHandItem();
        int missing = weapon.magazineCapacity() - WeaponItems.ammo(stack, weapon);
        if (missing <= 0) {
            if (manual) {
                presentation.sendActionBar(player, PlayerPresentation.colored("Magazine is full.", ChatFormatting.YELLOW));
            }
            return;
        }
        if (WeaponItems.reserve(stack) <= 0) {
            presentation.sendActionBar(player, PlayerPresentation.colored("Out of ammo.", ChatFormatting.RED));
            return;
        }
        gun.reloading = true;
        gun.reloadTicks = 0;
        gun.reloadAmmo = WeaponItems.ammo(stack, weapon);
        gun.reloadStartDamage = stack.getDamageValue();
        gun.reloadDuration = Math.max(1, Math.round(weapon.reloadSeconds() * 20.0F));
        player.stopUsingItem();
        presentation.sendActionBar(player, PlayerPresentation.colored("Reloading...", ChatFormatting.YELLOW));
    }

    private void tickReload(ServerPlayer player, GunState gun) {
        if (!gun.reloading) {
            return;
        }
        Optional<WeaponDefinition> definition = catalog.of(player.getMainHandItem());
        if (definition.isEmpty()) {
            gun.reloading = false;
            return;
        }
        WeaponDefinition weapon = definition.get();
        ItemStack stack = player.getMainHandItem();
        gun.reloadTicks++;
        float progress = gun.reloadTicks / (float) gun.reloadDuration;
        stack.setDamageValue(Math.round(Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F), gun.reloadStartDamage, 0)));
        if (gun.reloadTicks % 2 == 0) {
            player.containerMenu.broadcastChanges();
        }
        if (gun.reloadTicks < gun.reloadDuration) {
            return;
        }
        int missing = weapon.magazineCapacity() - gun.reloadAmmo;
        int take = Math.min(missing, WeaponItems.reserve(stack));
        WeaponItems.setReserve(stack, weapon, WeaponItems.reserve(stack) - take);
        WeaponItems.setAmmo(stack, weapon, gun.reloadAmmo + take);
        gun.reloading = false;
        player.containerMenu.broadcastChanges();
        presentation.sendActionBar(player, PlayerPresentation.colored("Reloaded.", ChatFormatting.GREEN));
    }

    private boolean canShoot(GameState state, ServerPlayer player) {
        return (state == GameState.RUN || state == GameState.PLANTED)
                && player.gameMode() != GameType.SPECTATOR
                && !player.isDeadOrDying()
                && roster.find(player).isPresent()
                && !MatchItems.is(player.getMainHandItem(), MatchItems.SHOP);
    }

    private static float movementSpread(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            return -0.35F;
        }
        if (!player.onGround()) {
            return 1.6F;
        }
        if (player.isSprinting()) {
            return 0.9F;
        }
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
            return 0.4F;
        }
        return 0.0F;
    }

    private static void playShot(ServerLevel level, ServerPlayer player, WeaponDefinition weapon) {
        if (weapon.semiAutomatic() && weapon.fireIntervalTicks() >= 20) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
        } else if (weapon.type() == WeaponType.PISTOL) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.9F);
        }
    }

    private GunState state(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), uuid -> new GunState());
    }

    private static final class GunState {
        private boolean holding;
        private boolean reloading;
        private int nextShotTick;
        private int reloadTicks;
        private int reloadAmmo;
        private int reloadStartDamage;
        private int reloadDuration;
        private float spread;
    }
}
