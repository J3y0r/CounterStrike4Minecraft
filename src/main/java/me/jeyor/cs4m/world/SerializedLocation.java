package me.jeyor.cs4m.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public record SerializedLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
    public static Optional<SerializedLocation> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = trimmed.split(",");
        if (parts.length < 6) {
            return Optional.empty();
        }
        String worldName = parts[0].trim();
        if (worldName.isEmpty()) {
            return Optional.empty();
        }
        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = Float.parseFloat(parts[4].trim());
            float pitch = Float.parseFloat(parts[5].trim());
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                return Optional.empty();
            }
            return Optional.of(new SerializedLocation(worldName, x, y, z, yaw, pitch));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public String serialize() {
        return worldName + "," + x + "," + y + "," + z + "," + yaw + "," + pitch;
    }

    public Vec3 position() {
        return new Vec3(x, y, z);
    }

    public Optional<ServerLevel> resolveLevel(MinecraftServer server) {
        return WorldNames.resolve(server, worldName);
    }
}
