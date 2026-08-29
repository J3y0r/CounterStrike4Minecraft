package me.jeyor.cs4m.player;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.Locale;
import java.util.Optional;

public final class TeamColor {
    public static final String WHITE = "WHITE";
    public static final String BLUE = "BLUE";
    public static final String RED = "RED";
    public static final String GREEN = "GREEN";
    public static final String YELLOW = "YELLOW";
    public static final String GOLD = "GOLD";
    public static final String AQUA = "AQUA";

    private TeamColor() {
    }

    public static Optional<String> fromBlock(Block block) {
        String material = BuiltInRegistries.BLOCK.getKey(block).getPath().toUpperCase(Locale.ROOT);
        if (material.contains("CYAN") || material.contains("BLUE")) {
            return Optional.of(BLUE);
        }
        if (material.contains("RED") || material.contains("PINK")) {
            return Optional.of(RED);
        }
        if (material.contains("GREEN") || material.contains("LIME")) {
            return Optional.of(GREEN);
        }
        if (material.contains("YELLOW")) {
            return Optional.of(YELLOW);
        }
        if (material.contains("GOLD")) {
            return Optional.of(GOLD);
        }
        if (material.contains("AQUA")) {
            return Optional.of(AQUA);
        }
        return Optional.empty();
    }

    public static ChatFormatting formatting(String colour) {
        if (colour == null) {
            return ChatFormatting.WHITE;
        }
        return switch (colour) {
            case BLUE -> ChatFormatting.BLUE;
            case RED -> ChatFormatting.RED;
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.YELLOW;
            case GOLD -> ChatFormatting.GOLD;
            case AQUA -> ChatFormatting.AQUA;
            default -> ChatFormatting.WHITE;
        };
    }

    public static int leatherRgb(String colour) {
        return switch (colour) {
            case RED -> 0xFF5555;
            case BLUE -> 0x5555FF;
            case GREEN -> 0x55FF55;
            case AQUA -> 0x55FFFF;
            default -> 0xFFFF55;
        };
    }
}
