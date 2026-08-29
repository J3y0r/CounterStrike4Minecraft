package me.jeyor.cs4m.bomb;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.item.MatchItems;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.player.TeamEnum;
import me.jeyor.cs4m.ui.PlayerPresentation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;
import java.util.UUID;

public final class BombController {
    private final Cs4mConfig config;
    private final MatchRoster roster;
    private final PlayerPresentation presentation;

    private ServerLevel level;
    private BlockPos pos;
    private ArmorStand hologram;
    private int countdown = -1;
    private int tickCounter;
    private float defuseLeft = -1.0F;
    private UUID defuser;
    private int explodePulses = -1;

    public BombController(Cs4mConfig config, MatchRoster roster, PlayerPresentation presentation) {
        this.config = config;
        this.roster = roster;
        this.presentation = presentation;
    }

    public boolean planted() {
        return countdown >= 0 || explodePulses >= 0;
    }

    public boolean tryPlant(ServerPlayer player, CsPlayer csPlayer, BlockPos planted, Block below) {
        if (!MatchItems.bomb(player.getMainHandItem()) && !MatchItems.bomb(player.getOffhandItem())) {
            return false;
        }
        if (!below.equals(bombBlock())) {
            return false;
        }
        if (player.position().distanceToSqr(planted.getX() + 0.5, planted.getY() + 0.5, planted.getZ() + 0.5) > 9.0) {
            return false;
        }
        csPlayer.setMoney(csPlayer.money() + 300, compact());
        presentation.sendChat(player, PlayerPresentation.colored("+ $300", ChatFormatting.GREEN));
        csPlayer.setTempmvp(csPlayer.tempmvp() + 2);
        if (MatchItems.bomb(player.getMainHandItem())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        player.containerMenu.broadcastChanges();
        start(player.level(), planted);
        return true;
    }

    public void start(ServerLevel plantedLevel, BlockPos planted) {
        cleanUp();
        level = plantedLevel;
        pos = planted.immutable();
        countdown = config.bombTimer();
        tickCounter = 20;
        defuseLeft = -1.0F;
        defuser = null;
        explodePulses = -1;
        plantedLevel.setBlock(pos, Blocks.TNT.defaultBlockState(), 3);
        hologram = new ArmorStand(plantedLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        hologram.setInvisible(true);
        hologram.setNoGravity(true);
        hologram.setNoBasePlate(true);
        hologram.setCustomNameVisible(true);
        hologram.setInvulnerable(true);
        hologram.setSilent(true);
        label(Component.literal("Exploding in " + countdown + " seconds.").withStyle(ChatFormatting.YELLOW));
        plantedLevel.addFreshEntity(hologram);
        presentation.sendTitleToMatch(
                roster,
                PlayerPresentation.colored("The bomb has been planted", ChatFormatting.YELLOW),
                PlayerPresentation.colored("It will explode in " + config.bombTimer() + " seconds", ChatFormatting.YELLOW),
                0,
                2,
                0
        );
    }

    public TickResult tick() {
        if (explodePulses >= 0) {
            return tickExplosion();
        }
        if (countdown < 0 || level == null || pos == null) {
            return TickResult.NONE;
        }
        if (emptyAbort()) {
            cleanUp();
            return TickResult.ABORT;
        }
        TickResult defuse = tickDefuse();
        if (defuse != TickResult.NONE) {
            return defuse;
        }
        tickCounter--;
        if (tickCounter > 0) {
            return TickResult.NONE;
        }
        tickCounter = 20;
        countdown--;
        if (countdown <= 0) {
            beginExplosion();
            return TickResult.NONE;
        }
        if (countdown <= 5) {
            label(Component.literal("Exploding in " + countdown + " seconds.").withStyle(ChatFormatting.RED));
            presentation.sendTitleToMatch(
                    roster,
                    PlayerPresentation.colored("It is going to Explode!!", ChatFormatting.RED),
                    PlayerPresentation.colored("Run for your lives", ChatFormatting.YELLOW),
                    0,
                    4,
                    1
            );
        } else if (defuser == null) {
            label(Component.literal("Exploding in " + countdown + " seconds.").withStyle(ChatFormatting.YELLOW));
        }
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 2.0F, 1.0F);
        presentation.sendActionBarToMatch(
                roster,
                PlayerPresentation.colored("The bomb will explode in " + countdown + " seconds.", ChatFormatting.RED)
        );
        return TickResult.NONE;
    }

    public void tryDefuse(ServerPlayer player, CsPlayer csPlayer) {
        if (countdown < 0 || explodePulses >= 0 || csPlayer.team() != TeamEnum.COUNTER_TERRORISTS) {
            return;
        }
        if (defuser != null) {
            return;
        }
        defuser = csPlayer.uuid();
        defuseLeft = (float) config.bombDefuseTime();
    }

    public void interruptDefuse(UUID uuid) {
        if (uuid != null && uuid.equals(defuser)) {
            clearDefuser();
        }
    }

    public void cleanUp() {
        if (hologram != null && !hologram.isRemoved()) {
            hologram.setCustomName(Component.empty());
            hologram.discard();
        }
        hologram = null;
        if (level != null && pos != null && level.getBlockState(pos).is(Blocks.TNT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level = null;
        pos = null;
        countdown = -1;
        tickCounter = 0;
        defuseLeft = -1.0F;
        defuser = null;
        explodePulses = -1;
    }

    private TickResult tickExplosion() {
        explodePulses--;
        if (level != null && pos != null) {
            level.explode(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 22.0F, false, Level.ExplosionInteraction.NONE);
        }
        if (explodePulses <= 0) {
            cleanUp();
            return TickResult.DETONATED;
        }
        return TickResult.NONE;
    }

    private void beginExplosion() {
        if (hologram != null) {
            hologram.setCustomName(Component.empty());
            hologram.setCustomNameVisible(false);
        }
        if (level != null && pos != null && level.getBlockState(pos).is(Blocks.TNT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        explodePulses = 25;
        countdown = -1;
        defuser = null;
    }

    private TickResult tickDefuse() {
        if (defuser == null || level == null || pos == null) {
            return TickResult.NONE;
        }
        CsPlayer csPlayer = roster.find(defuser).orElse(null);
        if (csPlayer == null || !csPlayer.online()) {
            clearDefuser();
            return TickResult.NONE;
        }
        ServerPlayer player = csPlayer.player();
        if (player.gameMode() == GameType.SPECTATOR
                || player.isDeadOrDying()
                || player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 4.0
                || player.getMainHandItem().getItem() != Items.IRON_AXE) {
            clearDefuser();
            return TickResult.NONE;
        }
        if (defuseLeft <= 0.0F) {
            csPlayer.setTempmvp(csPlayer.tempmvp() + 3);
            cleanUp();
            return TickResult.DEFUSED;
        }
        label(Component.literal("DEFUSING: " + String.format(Locale.ROOT, "%.2f", defuseLeft) + " s.").withStyle(ChatFormatting.WHITE));
        defuseLeft -= 5.0F / 20.0F;
        return TickResult.NONE;
    }

    private void clearDefuser() {
        defuser = null;
        defuseLeft = (float) config.bombDefuseTime();
        if (countdown >= 0) {
            label(Component.literal("(Get nearby and Right click AXE to defuse)").withStyle(ChatFormatting.GRAY));
        }
    }

    private void label(Component text) {
        if (hologram != null) {
            hologram.setCustomName(text);
            hologram.setCustomNameVisible(true);
        }
    }

    private boolean emptyAbort() {
        return (roster.size() == 0 || (level != null && level.getServer().getPlayerList().getPlayers().isEmpty())) && config.quitExitGame();
    }

    private boolean compact() {
        return config.modeValorant() || config.modeRealms();
    }

    private Block bombBlock() {
        Identifier identifier = Identifier.tryParse(config.bombBlock().contains(":")
                ? config.bombBlock().toLowerCase(Locale.ROOT)
                : "minecraft:" + config.bombBlock().toLowerCase(Locale.ROOT));
        if (identifier == null) {
            return Blocks.OBSIDIAN;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        return block == null ? Blocks.OBSIDIAN : block;
    }

    public enum TickResult {
        NONE,
        DETONATED,
        DEFUSED,
        ABORT
    }
}
