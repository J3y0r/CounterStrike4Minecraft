package me.jeyor.cs4m.event;

import me.jeyor.cs4m.runtime.Cs4mServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.function.Supplier;

public final class PlayerLifecycle {
    private PlayerLifecycle() {
    }

    public static void register(Supplier<Cs4mServer> runtime) {
        ServerPlayerEvents.JOIN.register(player -> {
            Cs4mServer server = runtime.get();
            if (server != null) {
                server.maybePromptSetup(player);
                server.match().onJoin(player);
            }
        });
        ServerPlayerEvents.LEAVE.register(player -> {
            Cs4mServer server = runtime.get();
            if (server != null) {
                server.match().onLeave(player);
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            Cs4mServer server = runtime.get();
            if (server != null) {
                server.match().onWorldChange(player, origin, destination);
            }
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            Cs4mServer server = runtime.get();
            if (server != null) {
                if (server.match().onAttackBlock(serverPlayer, pos, level.getBlockState(pos).getBlock())) {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            Cs4mServer server = runtime.get();
            if (server != null && server.match().onAttackEntity(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return true;
            }
            Cs4mServer server = runtime.get();
            return server == null || !server.match().onBreakBlock(serverPlayer, pos, state.getBlock());
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Cs4mServer server = runtime.get();
            return server == null || server.match().allowDamage(entity, source, amount);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Cs4mServer server = runtime.get();
            if (server != null) {
                server.match().onDeath(entity, source);
                server.match().onEntityKilled(entity, source);
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            Cs4mServer server = runtime.get();
            if (server != null) {
                server.match().onRespawn(oldPlayer, newPlayer);
            }
        });
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
            Cs4mServer server = runtime.get();
            return server == null || server.match().allowChat(sender);
        });
    }
}
