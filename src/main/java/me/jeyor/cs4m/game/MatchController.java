package me.jeyor.cs4m.game;

import me.jeyor.cs4m.bomb.BombController;
import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.item.InventorySlots;
import me.jeyor.cs4m.item.MatchItems;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.CsTeam;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.player.TeamColor;
import me.jeyor.cs4m.player.TeamEnum;
import me.jeyor.cs4m.ui.MatchScoreboard;
import me.jeyor.cs4m.ui.PlayerPresentation;
import me.jeyor.cs4m.world.CsWorldRules;
import me.jeyor.cs4m.world.MapCatalog;
import me.jeyor.cs4m.world.MapVote;
import me.jeyor.cs4m.world.SelectedMap;
import me.jeyor.cs4m.shop.ShopCatalog;
import me.jeyor.cs4m.shop.ShopMenu;
import me.jeyor.cs4m.weapon.WeaponCatalog;
import me.jeyor.cs4m.weapon.WeaponController;
import me.jeyor.cs4m.weapon.WeaponItems;
import me.jeyor.cs4m.world.SerializedLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class MatchController {
    private final MinecraftServer server;
    private final Cs4mConfig config;
    private final MapCatalog maps;
    private final CsWorldRules worldRules;
    private final MatchRoster roster;
    private final PlayerPresentation presentation;
    private final WaitingCounter waiting;
    private final RoundTimers timers = new RoundTimers();
    private final MatchScoreboard scoreboard;
    private final MapVote votes;
    private final BombController bomb;
    private final WeaponController weapons;
    private final Logger logger;
    private GameState state = GameState.LOBBY;
    private CsTeam pendingWinner;
    private CsTeam pendingLoser;
    private int spawnFixTicks = -1;
    private int hudTicks;

    public MatchController(
            MinecraftServer server,
            Cs4mConfig config,
            MapCatalog maps,
            CsWorldRules worldRules,
            MapVote votes,
            Logger logger
    ) {
        this.server = server;
        this.config = config;
        this.maps = maps;
        this.worldRules = worldRules;
        this.votes = votes;
        this.roster = new MatchRoster(config);
        this.presentation = new PlayerPresentation(config);
        this.waiting = new WaitingCounter(config, roster, presentation);
        this.scoreboard = new MatchScoreboard(server, config, roster);
        this.bomb = new BombController(config, roster, presentation);
        this.weapons = new WeaponController(config, new WeaponCatalog(config.weapons(), logger), roster, presentation);
        this.logger = logger;
    }

    public GameState state() {
        return state;
    }

    public MatchRoster roster() {
        return roster;
    }

    public WeaponController weapons() {
        return weapons;
    }

    public int remainingRoundSeconds() {
        return Math.max(0, timers.runSeconds());
    }

    public void tick() {
        if (waiting.active() || state == GameState.WAITING) {
            WaitingCounter.TickResult result = waiting.tick(server, maps.selected(), config.quitExitGame());
            if (result.changed() && result.state() != null) {
                state = result.state();
                if (state == GameState.STARTING) {
                    timers.startMatchCountdown(firstRound() ? config.startCounterDuration() : 6);
                }
            }
        }
        tickStartCountdown();
        tickShop();
        tickRun();
        tickBomb();
        if (timers.tickRestartDelay()) {
            startWaiting();
        }
        if (timers.tickFinishDelay() && pendingWinner != null && pendingLoser != null) {
            finishGame(pendingWinner, pendingLoser);
        }
        if (spawnFixTicks > 0) {
            spawnFixTicks--;
            if (spawnFixTicks == 0) {
                fixSpawns();
            }
        }
        if (state == GameState.SHOP) {
            constrainShopMovement();
            refreshShopItems();
        }
        weapons.tick(state);
        hudTicks++;
        if (hudTicks % 20 == 0 && !roster.players().isEmpty() && state != GameState.LOBBY) {
            scoreboard.update(maps.selected().map(SelectedMap::name).orElse(""));
            updateSpectatorBars();
        }
    }

    public void onJoin(ServerPlayer player) {
        if (!worldRules.isCsWorld(player.level())) {
            presentation.applySurvivalHealth(player);
            return;
        }
        presentation.survival(player);
        Optional<CsPlayer> existing = roster.find(player);
        if (existing.isEmpty() && (state == GameState.LOBBY || state == GameState.WAITING)) {
            sendToLobby(player, true);
            return;
        }
        if (existing.isPresent()) {
            existing.get().setPlayer(player);
            weapons.clear(player.getUUID());
            returnToGame(existing.get());
            return;
        }
        sendToLobby(player, false);
    }

    public void onLeave(ServerPlayer player) {
        if (!worldRules.isCsWorld(player.level())) {
            return;
        }
        roster.find(player).ifPresent(csPlayer -> {
            scoreboard.remove(player);
            weapons.clear(player.getUUID());
            leaveGame(csPlayer);
        });
    }

    public void onWorldChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        boolean fromCs = worldRules.isKnownWorld(origin) && worldRules.isCsWorld(origin);
        boolean toKnown = worldRules.isKnownWorld(destination);
        boolean toCs = worldRules.isCsWorld(destination);
        if (!toKnown) {
            return;
        }
        if (!toCs) {
            if (fromCs) {
                roster.find(player).ifPresent(csPlayer -> {
                    scoreboard.remove(player);
                    roster.remove(csPlayer);
                    logger.debug("{} left CS world {}", player.getScoreboardName(), origin.dimension().identifier());
                });
                presentation.applySurvivalHealth(player);
                presentation.sendTitle(
                        player,
                        PlayerPresentation.colored("Was nice too see you", ChatFormatting.YELLOW),
                        PlayerPresentation.colored("Hope to see you soon @CSMC World.", ChatFormatting.GREEN),
                        1,
                        4,
                        1
                );
            }
            return;
        }
        if (roster.size() >= config.maxPlayers()) {
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored("Welcome to CSMC World", ChatFormatting.YELLOW),
                    PlayerPresentation.colored("The game is full, please try again later.", ChatFormatting.GREEN),
                    1,
                    4,
                    1
            );
            return;
        }
        presentation.sendTitle(
                player,
                PlayerPresentation.colored("Welcome to CSMC World", ChatFormatting.YELLOW),
                PlayerPresentation.colored("Left click to join game.", ChatFormatting.GREEN),
                1,
                4,
                1
        );
        maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> {
            presentation.teleport(player, lobby);
            presentation.survival(player);
            presentation.clearInventory(player);
        });
    }

    public void forceFinish() {
        finishGame(roster.terroristsTeam(), roster.counterTerroristsTeam());
    }

    public boolean onUseItem(ServerPlayer player, InteractionHand hand) {
        if (!worldRules.isCsWorld(player.level())) {
            return false;
        }
        Optional<CsPlayer> existing = roster.find(player);
        if (existing.isEmpty()) {
            return false;
        }
        if (player.gameMode() == GameType.SPECTATOR) {
            return true;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (MatchItems.is(stack, MatchItems.SHOP)) {
            if (state != GameState.SHOP) {
                presentation.sendChat(player, Component.literal("Sorry, not in ShopPhase.").withStyle(ChatFormatting.RED));
                return true;
            }
            ShopCatalog.open(player, existing.get(), weapons.catalog(), compact());
            return true;
        }
        return weapons.onUseWeapon(player, state);
    }

    public boolean onUseBlock(ServerPlayer player, BlockPos pos, Block block) {
        if (!worldRules.isCsWorld(player.level())) {
            return false;
        }
        Optional<CsPlayer> existing = roster.find(player);
        if (existing.isEmpty()) {
            return false;
        }
        if (player.gameMode() == GameType.SPECTATOR) {
            return true;
        }
        ItemStack held = player.getMainHandItem();
        if (MatchItems.is(held, MatchItems.SHOP)) {
            return onUseItem(player, InteractionHand.MAIN_HAND);
        }
        if (weapons.onUseWeapon(player, state)) {
            return true;
        }
        if (bomb.planted() && existing.get().team() == TeamEnum.COUNTER_TERRORISTS) {
            bomb.tryDefuse(player, existing.get());
            return false;
        }
        String name = block.toString();
        if (name.contains("chest") || name.contains("shulker") || name.contains("door") || name.contains("button") || name.contains("plate") || name.contains("lever")) {
            return true;
        }
        return false;
    }

    public boolean onPlaceBlock(ServerPlayer player, BlockPos pos, Block placed, Block below) {
        if (!worldRules.isCsWorld(player.level())) {
            return false;
        }
        Optional<CsPlayer> existing = roster.find(player);
        if (existing.isEmpty()) {
            return true;
        }
        if (placed == net.minecraft.world.level.block.Blocks.TNT && bomb.tryPlant(player, existing.get(), pos, below)) {
            if (bomb.planted()) {
                timers.stopPhaseTimers();
                state = GameState.PLANTED;
            }
            return false;
        }
        return true;
    }

    public boolean onAttackEntity(ServerPlayer player) {
        if (!worldRules.isCsWorld(player.level())) {
            return false;
        }
        return weapons.onReload(player, state);
    }

    public boolean onAttackBlock(ServerPlayer player, BlockPos pos, Block block) {
        if (!worldRules.isCsWorld(player.level())) {
            return false;
        }
        if (handleMapVote(player, pos)) {
            return true;
        }
        if (weapons.onReload(player, state)) {
            return true;
        }
        Optional<CsPlayer> existing = roster.find(player);
        int joinCap = Math.min(config.maxPlayers(), 16);
        if (roster.size() > joinCap && existing.isEmpty()) {
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored("We are sorry", ChatFormatting.YELLOW),
                    PlayerPresentation.colored("The game is full, please try again later.", ChatFormatting.GREEN),
                    1,
                    4,
                    1
            );
            return false;
        }
        if (existing.isPresent()) {
            return false;
        }
        if (!config.allowJoinRunningGame() && (state == GameState.SHOP || state == GameState.RUN || state == GameState.PLANTED)) {
            int remain = remainingRoundSeconds() == 0 ? config.matchDuration() : remainingRoundSeconds();
            int round = roster.terroristsTeam().losses() + roster.terroristsTeam().wins() + 1;
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored("Wait for the end of the current round to join", ChatFormatting.YELLOW),
                    PlayerPresentation.colored(
                            "Current round: " + round + " of " + config.maxRounds() + ". Estimated time for new " + remain + "secs",
                            ChatFormatting.GREEN
                    ),
                    1,
                    4,
                    1
            );
            return false;
        }
        Optional<String> colour = TeamColor.fromBlock(block);
        if (colour.isEmpty()) {
            presentation.sendChat(player, Component.literal("You have to choose one of the floors with colour"));
            return false;
        }
        MatchRoster.JoinResult result = roster.join(player, colour.get());
        if (!result.accepted()) {
            if (!result.alreadyJoined()) {
                presentation.sendChat(
                        player,
                        Component.literal("You have to choose one of the active colours/team in order to join, try  "
                                + roster.terroristsTeam().colour()
                                + "  or  "
                                + roster.counterTerroristsTeam().colour())
                );
            }
            return false;
        }
        announceRole(result.player(), false);
        if (state == GameState.LOBBY || state == GameState.WAITING || state == GameState.STARTING) {
            startWaiting();
        } else {
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored("Waiting for next round to join", ChatFormatting.YELLOW),
                    PlayerPresentation.colored(" please wait", ChatFormatting.GREEN),
                    1,
                    10,
                    1
            );
            maps.selected().flatMap(SelectedMap::siteA).ifPresent(site -> presentation.teleport(player, site));
            presentation.spectator(player);
        }
        return false;
    }

    public void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer victim) || !worldRules.isCsWorld(victim.level())) {
            return;
        }
        Optional<CsPlayer> victimState = roster.find(victim);
        if (victimState.isEmpty()) {
            return;
        }
        CsPlayer csVictim = victimState.get();
        if (state != GameState.RUN && state != GameState.PLANTED) {
            return;
        }
        ChatFormatting victimColour = TeamColor.formatting(csVictim.colour());
        String deadName = victim.getScoreboardName();
        csVictim.setDeaths(csVictim.deaths() + 1);
        if (source.getEntity() instanceof ServerPlayer killer) {
            Optional<CsPlayer> killerState = roster.find(killer);
            if (killerState.isPresent()) {
                CsPlayer csKiller = killerState.get();
                csKiller.setMoney(csKiller.money() + 300, compact());
                presentation.sendChat(killer, PlayerPresentation.colored("+ $300", ChatFormatting.GREEN));
                csKiller.setKills(csKiller.kills() + 1);
                csKiller.setTempmvp(csKiller.tempmvp() + 1);
                csVictim.setLastKiller(killer.getUUID());
                broadcast(Component.literal(deadName).withStyle(victimColour)
                        .append(Component.literal(" was eliminated by ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(killer.getScoreboardName()).withStyle(TeamColor.formatting(csKiller.colour()))));
            } else {
                broadcast(Component.literal(deadName).withStyle(victimColour)
                        .append(Component.literal(" was eliminated...").withStyle(ChatFormatting.YELLOW)));
            }
        } else {
            broadcast(Component.literal(deadName).withStyle(victimColour)
                    .append(Component.literal(" was eliminated...").withStyle(ChatFormatting.YELLOW)));
        }
        presentation.sendChat(victim, PlayerPresentation.colored("Wait until next round for a respawn.", ChatFormatting.RED));
        presentation.sendTitle(
                victim,
                PlayerPresentation.colored("You are eliminated.", ChatFormatting.RED),
                PlayerPresentation.colored("You will respawn in the next round.", ChatFormatting.YELLOW),
                0,
                3,
                1
        );
        bomb.interruptDefuse(victim.getUUID());
        int wipe = teamWipe();
        if (wipe == 1) {
            restartGame(roster.terroristsTeam());
        } else if (wipe == 2 && !bomb.planted()) {
            restartGame(roster.counterTerroristsTeam());
        }
    }

    public void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        roster.find(oldPlayer.getUUID()).ifPresent(csPlayer -> {
            csPlayer.setPlayer(newPlayer);
            if (state == GameState.RUN || state == GameState.PLANTED) {
                presentation.spectator(newPlayer);
                if (csPlayer.lastKiller() != null) {
                    ServerPlayer killer = server.getPlayerList().getPlayer(csPlayer.lastKiller());
                    if (killer != null) {
                        newPlayer.setCamera(killer);
                    }
                }
            }
        });
    }

    public boolean onBreakBlock(ServerPlayer player, BlockPos pos, Block block) {
        if (!worldRules.restrictionsEnabled(player.level())) {
            return false;
        }
        if (roster.contains(player) && state != GameState.PLANTED && block == Blocks.TNT) {
            player.level().getServer().execute(() -> {
                if (player.level().getBlockState(pos).is(Blocks.TNT)) {
                    player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            });
        }
        return true;
    }

    public boolean onPickup(ServerPlayer player, ItemEntity entity) {
        if (!worldRules.restrictionsEnabled(player.level())) {
            return false;
        }
        Optional<CsPlayer> existing = roster.find(player);
        if (existing.isEmpty()) {
            return false;
        }
        ItemStack item = entity.getItem();
        if (MatchItems.bomb(item)) {
            if (existing.get().team() == TeamEnum.COUNTER_TERRORISTS) {
                return true;
            }
            if (player.getInventory().getItem(InventorySlots.BOMB).isEmpty()) {
                ItemStack copy = item.copy();
                copy.setCount(1);
                player.getInventory().setItem(InventorySlots.BOMB, copy);
                entity.discard();
                player.containerMenu.broadcastChanges();
            }
            return true;
        }
        int slot = pickupSlot(item);
        if (slot >= 0 && player.getInventory().getItem(slot).isEmpty()) {
            player.getInventory().setItem(slot, item.copy());
            entity.discard();
            player.containerMenu.broadcastChanges();
        }
        return true;
    }

    public boolean onDropSelected(ServerPlayer player) {
        if (!worldRules.restrictionsEnabled(player.level())) {
            return false;
        }
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return false;
        }
        int slot = player.getInventory().getSelectedSlot();
        return slot == InventorySlots.KNIFE || slot == InventorySlots.SHOP;
    }

    public boolean onInventoryClick(ServerPlayer player, AbstractContainerMenu menu) {
        if (!worldRules.restrictionsEnabled(player.level())) {
            return false;
        }
        return !(menu instanceof ShopMenu);
    }

    public boolean allowChat(ServerPlayer player) {
        if (!worldRules.restrictionsEnabled(player.level()) || !roster.contains(player)) {
            return true;
        }
        return player.gameMode() != GameType.SPECTATOR;
    }

    public boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer damager) || !worldRules.restrictionsEnabled(damager.level())) {
            return true;
        }
        if (state == GameState.LOBBY) {
            return false;
        }
        if (entity instanceof ServerPlayer victim) {
            Optional<CsPlayer> shooter = roster.find(damager);
            Optional<CsPlayer> target = roster.find(victim);
            if (shooter.isEmpty() || target.isEmpty()) {
                return true;
            }
            if (shooter.get().team() == target.get().team() && !config.friendlyFireEnabled()) {
                return false;
            }
        }
        return true;
    }

    public float adjustDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer victim) || !(source.getEntity() instanceof ServerPlayer damager)) {
            return amount;
        }
        if (!worldRules.restrictionsEnabled(damager.level()) || roster.find(damager).isEmpty() || roster.find(victim).isEmpty()) {
            return amount;
        }
        if (amount >= 100.0F) {
            return amount;
        }
        ItemStack held = damager.getMainHandItem();
        if (!MatchItems.is(held, MatchItems.KNIFE) && held.getItem() != Items.IRON_AXE) {
            return amount;
        }
        if (behind(damager, victim)) {
            return 40.0F;
        }
        return amount / 2.0F;
    }

    public boolean onItemDamage(ServerPlayer player) {
        return worldRules.restrictionsEnabled(player.level());
    }

    public boolean onPrimeTnt(ServerLevel level) {
        return worldRules.restrictionsEnabled(level);
    }

    public boolean filterDeathDrops(ServerPlayer player, Inventory inventory) {
        if (!worldRules.restrictionsEnabled(player.level()) || roster.find(player).isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (MatchItems.bomb(stack) || WeaponItems.isWeapon(stack)) {
                player.drop(stack.copy(), true, false);
            }
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
        return true;
    }

    public void onEntityKilled(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Chicken) || !(source.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (!worldRules.restrictionsEnabled(killer.level())) {
            return;
        }
        roster.find(killer).ifPresent(csKiller -> csKiller.setChickenKills(csKiller.chickenKills() + 1));
    }

    public void startWaiting() {
        if (state != GameState.LOBBY && state != GameState.WAITING) {
            return;
        }
        waiting.start();
        state = GameState.WAITING;
    }

    public void stopWaiting() {
        waiting.stop();
        timers.stopAll();
    }

    public void setState(GameState state) {
        this.state = state;
    }

    private void tickStartCountdown() {
        if (state != GameState.STARTING || !timers.tickStartCountdown()) {
            return;
        }
        if (emptyAbort()) {
            finishGame(roster.terroristsTeam(), roster.counterTerroristsTeam());
            return;
        }
        int remaining = timers.consumeStartSecond();
        if (remaining <= 0) {
            startGame();
            return;
        }
        presentation.sendTitleToMatch(
                roster,
                PlayerPresentation.colored("The game will start in " + remaining + " seconds!", ChatFormatting.YELLOW),
                PlayerPresentation.colored("get ready", ChatFormatting.YELLOW),
                0,
                0,
                1
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (roster.contains(player) || !presentation.inLobby(player, maps.selected())) {
                continue;
            }
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored("The game will start in " + remaining + " seconds!", ChatFormatting.RED),
                    PlayerPresentation.colored("you can still join", ChatFormatting.YELLOW),
                    0,
                    0,
                    1
            );
        }
    }

    private void startGame() {
        for (CsPlayer player : roster.balance()) {
            if (player.online()) {
                presentation.sendChat(
                        player.player(),
                        Component.literal("You have been moved to " + player.team().name() + " team in order to balance numbers")
                );
            }
        }
        state = GameState.STARTING;
        prepareMap();
        setupPlayers();
        timers.startShop(config.shopPhaseDuration());
        state = GameState.SHOP;
        scoreboard.ensureTeams();
        logger.info("CS4M round started");
    }

    private void tickShop() {
        if (state != GameState.SHOP || !timers.tickShop()) {
            return;
        }
        if (roster.size() == 0) {
            roster.terroristsTeam().resetScores();
            roster.counterTerroristsTeam().resetScores();
            state = GameState.LOBBY;
            timers.stopPhaseTimers();
            return;
        }
        int remaining = timers.consumeShopSecond();
        if (remaining <= 0) {
            presentation.sendActionBarToMatch(roster, PlayerPresentation.colored("The shop phase has ended!", ChatFormatting.GOLD));
            clearShopItems();
            giveDefaultPistols();
            timers.startRun(config.matchDuration());
            state = GameState.RUN;
            return;
        }
        presentation.sendActionBarToMatch(
                roster,
                Component.literal("The shop phase ends in ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(remaining + " second.").withStyle(ChatFormatting.GREEN))
        );
    }

    private void tickBomb() {
        if (state != GameState.PLANTED) {
            return;
        }
        BombController.TickResult result = bomb.tick();
        if (result == BombController.TickResult.DETONATED) {
            restartGame(roster.terroristsTeam());
        } else if (result == BombController.TickResult.DEFUSED) {
            restartGame(roster.counterTerroristsTeam());
        } else if (result == BombController.TickResult.ABORT) {
            finishGame(roster.terroristsTeam(), roster.counterTerroristsTeam());
        }
    }

    private void tickRun() {
        if (state != GameState.RUN || !timers.tickRun()) {
            return;
        }
        if (emptyAbort()) {
            finishGame(roster.terroristsTeam(), roster.counterTerroristsTeam());
            return;
        }
        int remaining = timers.consumeRunSecond();
        String defenders = compact() ? "The Defenders will win in " : "The Counter Terrorists will win in ";
        presentation.sendActionBarToMatch(roster, PlayerPresentation.colored(defenders + formatTime(remaining), ChatFormatting.YELLOW));
        if (remaining <= 0) {
            restartGame(roster.counterTerroristsTeam());
        }
    }

    private void prepareMap() {
        if (roster.terroristsTeam().wins() + roster.terroristsTeam().losses() == 0) {
            if (!votes.empty()) {
                maps.selectById(votes.winningMapId());
                votes.clear();
            } else {
                maps.selectRandom();
            }
        } else if (config.randomMaps()) {
            maps.selectRandom();
        }
        worldRules.apply(server, maps.selected());
    }

    private boolean handleMapVote(ServerPlayer player, BlockPos pos) {
        if (state != GameState.LOBBY && state != GameState.WAITING && state != GameState.STARTING) {
            return false;
        }
        if (!(player.level().getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return false;
        }
        String line0 = sign.getFrontText().getMessage(0, false).getString();
        String line1 = sign.getFrontText().getMessage(1, false).getString();
        String line2 = sign.getFrontText().getMessage(2, false).getString();
        String line3 = sign.getFrontText().getMessage(3, false).getString();
        if (!line0.equalsIgnoreCase("[CSGo]") && !line0.equalsIgnoreCase("[CSMC]")) {
            return false;
        }
        if (state == GameState.STARTING) {
            presentation.sendChat(player, Component.literal("Game is already starting"));
            return true;
        }
        if (line1.equalsIgnoreCase("Vote for")) {
            try {
                votes.vote(player.getUUID(), Integer.parseInt(line3));
                presentation.sendChat(player, Component.literal("Vote registered for map " + line2));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (line1.isEmpty() || line0.equalsIgnoreCase("Join Lobby") || line1.equalsIgnoreCase("Join Lobby")) {
            maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> presentation.teleport(player, lobby));
            presentation.sendChat(player, Component.literal("Joined Lobby"));
            return true;
        }
        return false;
    }

    private void setupPlayers() {
        for (CsPlayer player : roster.terrorists()) {
            setupPlayer(player, maps.selected().flatMap(SelectedMap::terroristSpawn), true);
        }
        for (CsPlayer player : roster.counterTerrorists()) {
            setupPlayer(player, maps.selected().flatMap(SelectedMap::counterSpawn), false);
        }
        if (!roster.terrorists().isEmpty()) {
            CsPlayer carrier = roster.terrorists().get(ThreadLocalRandom.current().nextInt(roster.terrorists().size()));
            if (carrier.online()) {
                carrier.player().getInventory().setItem(InventorySlots.BOMB, MatchItems.bomb());
            }
        }
        spawnFixTicks = 80;
    }

    private void setupPlayer(CsPlayer csPlayer, Optional<SerializedLocation> spawn, boolean terrorist) {
        ServerPlayer player = csPlayer.player();
        if (player == null || !csPlayer.online()) {
            return;
        }
        csPlayer.setOpponentColour(terrorist ? roster.counterTerroristsTeam().colour() : roster.terroristsTeam().colour());
        spawn.ifPresent(location -> presentation.teleport(player, location, true));
        csPlayer.setTempmvp(0);
        presentation.applyCsHealth(player);
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
        giveLeggings(csPlayer);
        player.getInventory().setItem(InventorySlots.KNIFE, MatchItems.knife());
        player.getInventory().setItem(InventorySlots.BOMB, ItemStack.EMPTY);
        player.containerMenu.broadcastChanges();
    }

    private void clearShopItems() {
        for (CsPlayer csPlayer : roster.players()) {
            if (csPlayer.online()) {
                csPlayer.player().getInventory().setItem(InventorySlots.SHOP, ItemStack.EMPTY);
                csPlayer.player().containerMenu.broadcastChanges();
            }
        }
    }

    private void giveDefaultPistols() {
        for (CsPlayer csPlayer : roster.players()) {
            giveDefaultPistol(csPlayer);
        }
    }

    private void giveDefaultPistol(CsPlayer csPlayer) {
        if (!csPlayer.online()) {
            return;
        }
        ServerPlayer player = csPlayer.player();
        ItemStack current = player.getInventory().getItem(InventorySlots.PISTOL);
        if (!current.isEmpty()) {
            return;
        }
        weapons.catalog().defaultPistol(csPlayer.team()).ifPresent(definition ->
                player.getInventory().setItem(InventorySlots.PISTOL, WeaponItems.create(definition)));
        player.containerMenu.broadcastChanges();
    }

    private void giveLeggings(CsPlayer csPlayer) {
        if (!csPlayer.online()) {
            return;
        }
        ItemStack leggings = new ItemStack(Items.LEATHER_LEGGINGS);
        leggings.set(DataComponents.DYED_COLOR, new DyedItemColor(TeamColor.leatherRgb(csPlayer.colour())));
        csPlayer.player().setItemSlot(EquipmentSlot.LEGS, leggings);
    }

    private void restartGame(CsTeam winner) {
        CsTeam loser = winner == roster.terroristsTeam() ? roster.counterTerroristsTeam() : roster.terroristsTeam();
        bomb.cleanUp();
        timers.stopPhaseTimers();
        for (CsPlayer player : loser.players()) {
            player.setMoney(player.money() + config.moneyOnLoss(), compact());
            if (player.online()) {
                presentation.sendChat(player.player(), PlayerPresentation.colored("+ $" + config.moneyOnLoss(), ChatFormatting.GREEN));
            }
        }
        CsPlayer mvp = null;
        int best = 0;
        for (CsPlayer player : winner.players()) {
            player.setMoney(player.money() + config.moneyOnVictory(), compact());
            if (player.online()) {
                presentation.sendChat(player.player(), PlayerPresentation.colored("+ $" + config.moneyOnVictory(), ChatFormatting.GREEN));
            }
            if (player.tempmvp() > best) {
                best = player.tempmvp();
                mvp = player;
            }
        }
        if (mvp != null) {
            mvp.setMvp(mvp.mvp() + 1);
        }
        winner.addVictory();
        loser.addLoss();
        String mvpText = mvp != null && mvp.online() ? " Round's MVP " + mvp.player().getScoreboardName() : "";
        Component winnerText = Component.literal("Team " + winner.colour() + " wins." + mvpText)
                .withStyle(TeamColor.formatting(winner.colour()));
        if (winner.wins() == (config.maxRounds() / 2) + 1) {
            presentation.sendTitleToMatch(
                    roster,
                    winnerText,
                    PlayerPresentation.colored("They also won the whole game! (Left click to join new game)", ChatFormatting.AQUA),
                    0,
                    10,
                    1
            );
            presentation.sendActionBarToMatch(roster, winnerText);
            pendingWinner = winner;
            pendingLoser = loser;
            timers.delayFinish();
            return;
        }
        if (winner.wins() + winner.losses() == config.maxRounds()) {
            presentation.sendTitleToMatch(
                    roster,
                    winnerText,
                    PlayerPresentation.colored("But scores are even! (Left click to join new game)", ChatFormatting.AQUA),
                    0,
                    10,
                    1
            );
            presentation.sendActionBarToMatch(roster, winnerText);
            pendingWinner = winner;
            pendingLoser = loser;
            timers.delayFinish();
            return;
        }
        presentation.sendTitleToMatch(
                roster,
                winnerText,
                PlayerPresentation.colored("The next round will start shortly.", ChatFormatting.YELLOW),
                0,
                6,
                1
        );
        presentation.sendActionBarToMatch(roster, winnerText);
        state = GameState.LOBBY;
        int delay = 1;
        if (winner.wins() + winner.losses() == Math.round(config.maxRounds() / 2.0)) {
            delay = 5;
            roster.swapSides();
            for (CsPlayer player : roster.players()) {
                player.setMoney(config.startingMoney(), compact());
                if (player.online()) {
                    presentation.clearInventory(player.player());
                    announceRole(player, true);
                }
            }
        }
        teleportMatchToLobby();
        timers.delayRestart(delay);
    }

    private void finishGame(CsTeam winner, CsTeam loser) {
        bomb.cleanUp();
        timers.stopAll();
        state = GameState.LOBBY;
        winner.resetScores();
        loser.resetScores();
        scoreboard.clear();
        for (CsPlayer csPlayer : List.copyOf(roster.players())) {
            ServerPlayer player = csPlayer.player();
            if (player != null) {
                presentation.clearInventory(player);
                presentation.survival(player);
                maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> presentation.teleport(player, lobby));
            }
        }
        roster.clear();
        weapons.clearAll();
        pendingWinner = null;
        pendingLoser = null;
        logger.info("CS4M match finished");
    }

    private void constrainShopMovement() {
        for (CsPlayer csPlayer : roster.players()) {
            if (!csPlayer.online() || csPlayer.player().gameMode() == GameType.SPECTATOR) {
                continue;
            }
            Optional<SerializedLocation> spawn = csPlayer.terrorist()
                    ? maps.selected().flatMap(SelectedMap::terroristSpawn)
                    : maps.selected().flatMap(SelectedMap::counterSpawn);
            if (spawn.isEmpty()) {
                continue;
            }
            ServerPlayer player = csPlayer.player();
            int fromX = (int) Math.floor(spawn.get().x());
            int fromZ = (int) Math.floor(spawn.get().z());
            int toX = player.getBlockX();
            int toZ = player.getBlockZ();
            if (fromX > toX + 4 || fromX < toX - 4 || fromZ > toZ + 4 || fromZ < toZ - 4) {
                presentation.teleport(player, spawn.get());
            }
        }
    }

    private void refreshShopItems() {
        for (CsPlayer csPlayer : roster.players()) {
            if (!csPlayer.online()) {
                continue;
            }
            ServerPlayer player = csPlayer.player();
            boolean inZone = !outOfShopZone(csPlayer);
            ItemStack current = player.getInventory().getItem(InventorySlots.SHOP);
            if (inZone && current.isEmpty()) {
                player.getInventory().setItem(InventorySlots.SHOP, MatchItems.shop());
            } else if (!inZone && !current.isEmpty()) {
                player.getInventory().setItem(InventorySlots.SHOP, ItemStack.EMPTY);
            }
        }
    }

    private boolean outOfShopZone(CsPlayer csPlayer) {
        Optional<SerializedLocation> spawn = csPlayer.terrorist()
                ? maps.selected().flatMap(SelectedMap::terroristSpawn)
                : maps.selected().flatMap(SelectedMap::counterSpawn);
        if (spawn.isEmpty() || !csPlayer.online()) {
            return true;
        }
        ServerPlayer player = csPlayer.player();
        return player.getX() > spawn.get().x() + 7
                || player.getZ() > spawn.get().z() + 7
                || player.getX() < spawn.get().x() - 7
                || player.getZ() < spawn.get().z() - 7;
    }

    private void fixSpawns() {
        for (CsPlayer csPlayer : roster.players()) {
            if (!csPlayer.online() || !outOfShopZone(csPlayer)) {
                continue;
            }
            Optional<SerializedLocation> spawn = csPlayer.terrorist()
                    ? maps.selected().flatMap(SelectedMap::terroristSpawn)
                    : maps.selected().flatMap(SelectedMap::counterSpawn);
            spawn.ifPresent(location -> presentation.teleport(csPlayer.player(), location, true));
        }
    }

    private int teamWipe() {
        if (roster.allEliminated(roster.counterTerrorists())) {
            return 1;
        }
        if (roster.allEliminated(roster.terrorists())) {
            return 2;
        }
        return 0;
    }

    private void sendToLobby(ServerPlayer player, boolean welcome) {
        maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> presentation.teleport(player, lobby));
        if (welcome) {
            if (roster.size() >= config.maxPlayers()) {
                presentation.sendTitle(
                        player,
                        PlayerPresentation.colored("Welcome to CSMC World", ChatFormatting.YELLOW),
                        PlayerPresentation.colored("The game is full, please try again later.", ChatFormatting.RED),
                        1,
                        4,
                        1
                );
            } else {
                presentation.sendTitle(
                        player,
                        PlayerPresentation.colored("Welcome to CSMC World", ChatFormatting.GOLD),
                        PlayerPresentation.colored("Left click to join game.", ChatFormatting.RED),
                        1,
                        4,
                        1
                );
            }
        }
        presentation.clearInventory(player);
    }

    private void returnToGame(CsPlayer csPlayer) {
        ServerPlayer player = csPlayer.player();
        if (player == null) {
            return;
        }
        boolean running = state != GameState.LOBBY && state != GameState.WAITING;
        presentation.applyCsHealth(player);
        if (csPlayer.terrorist()) {
            csPlayer.setOpponentColour(roster.counterTerroristsTeam().colour());
            if (running) {
                maps.selected().flatMap(SelectedMap::terroristSpawn).ifPresent(spawn -> presentation.teleport(player, spawn, true));
            }
        } else if (csPlayer.team() == TeamEnum.COUNTER_TERRORISTS) {
            csPlayer.setOpponentColour(roster.terroristsTeam().colour());
            if (running) {
                maps.selected().flatMap(SelectedMap::counterSpawn).ifPresent(spawn -> presentation.teleport(player, spawn, true));
            }
        }
        if (!running) {
            maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> presentation.teleport(player, lobby));
        }
    }

    private void leaveGame(CsPlayer csPlayer) {
        ServerPlayer player = csPlayer.player();
        if (config.quitExitGame()) {
            roster.remove(csPlayer);
            return;
        }
        if (player != null && csPlayer.terrorist() && (state == GameState.SHOP || state == GameState.RUN)) {
            dropBomb(player);
        }
    }

    private void dropBomb(ServerPlayer player) {
        ItemStack item = player.getInventory().getItem(InventorySlots.BOMB);
        if (item.isEmpty() || !MatchItems.bomb(item)) {
            return;
        }
        player.getInventory().setItem(InventorySlots.BOMB, ItemStack.EMPTY);
        ItemEntity dropped = player.drop(item, false);
        if (dropped != null) {
            dropped.setPickUpDelay(40);
        }
    }

    private void announceRole(CsPlayer csPlayer, boolean switched) {
        ServerPlayer player = csPlayer.player();
        boolean compact = compact();
        String prefix = switched ? "You are NOW " : "You are ";
        if (csPlayer.team() == TeamEnum.COUNTER_TERRORISTS) {
            presentation.sendTitle(
                    player,
                    PlayerPresentation.colored(compact ? prefix + "a Defender" : prefix + "a Counter Terrorist", ChatFormatting.BLUE),
                    PlayerPresentation.colored(
                            compact ? "Defend the sites from Attackers, defuse the bomb." : "Defend the sites from terrorists, defuse the bomb.",
                            ChatFormatting.BLUE
                    ),
                    1,
                    switched ? 5 : 5,
                    1
            );
        } else {
            presentation.sendTitle(
                    player,
                    switched
                            ? PlayerPresentation.colored(compact ? "You are NOW an Attacker" : "You are NOW a Terrorist", ChatFormatting.RED)
                            : Component.literal(compact ? "You are an " : "You are a ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(compact ? "Attacker" : "Terrorist").withStyle(ChatFormatting.RED)),
                    PlayerPresentation.colored("Plant the bomb on the sites, have it explode.", ChatFormatting.RED),
                    1,
                    switched ? 5 : 4,
                    1
            );
        }
    }

    private void teleportMatchToLobby() {
        maps.selected().flatMap(SelectedMap::lobby).ifPresent(lobby -> {
            for (CsPlayer player : roster.players()) {
                if (player.online() && worldRules.isCsWorld(player.player().level())) {
                    presentation.teleport(player.player(), lobby);
                    presentation.survival(player.player());
                }
            }
        });
    }

    private void updateSpectatorBars() {
        for (CsPlayer player : roster.players()) {
            if (!player.online() || player.player().gameMode() != GameType.SPECTATOR) {
                continue;
            }
            Entity camera = player.player().getCamera();
            if (camera != null && camera != player.player()) {
                presentation.sendActionBar(
                        player.player(),
                        Component.literal("Spectating: ").withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(camera.getScoreboardName()).withStyle(ChatFormatting.GREEN))
                );
            }
        }
    }

    private void broadcast(Component message) {
        for (CsPlayer player : roster.players()) {
            if (player.online()) {
                presentation.sendChat(player.player(), message);
            }
        }
    }

    private boolean emptyAbort() {
        return (roster.size() == 0 || server.getPlayerList().getPlayers().isEmpty()) && config.quitExitGame();
    }

    private boolean firstRound() {
        return roster.terroristsTeam().wins() + roster.terroristsTeam().losses() == 0;
    }

    private boolean compact() {
        return config.modeValorant() || config.modeRealms();
    }

    private int pickupSlot(ItemStack item) {
        if (MatchItems.is(item, MatchItems.KNIFE) || item.is(Items.IRON_AXE)) {
            return InventorySlots.KNIFE;
        }
        return weapons.catalog().of(item).map(definition -> definition.slot()).orElse(-1);
    }

    private static boolean behind(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker.blockPosition().equals(victim.blockPosition())) {
            return false;
        }
        return Math.abs(attacker.getYRot() - victim.getYRot()) < 15.0F;
    }

    private static String formatTime(int seconds) {
        int value = Math.abs(seconds);
        int minutes = value / 60;
        int remainder = value % 60;
        String prefix = seconds < 0 ? "-" : "";
        return prefix + minutes + ":" + (remainder < 10 ? "0" + remainder : remainder);
    }
}
