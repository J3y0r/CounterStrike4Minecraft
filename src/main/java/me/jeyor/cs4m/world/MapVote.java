package me.jeyor.cs4m.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MapVote {
    private final Map<UUID, Integer> votes = new HashMap<>();

    public void vote(UUID player, int mapId) {
        votes.put(player, mapId);
    }

    public void clear() {
        votes.clear();
    }

    public boolean empty() {
        return votes.isEmpty();
    }

    public int size() {
        return votes.size();
    }

    public int winningMapId() {
        if (votes.isEmpty()) {
            return 0;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int mapId : votes.values()) {
            counts.merge(mapId, 1, Integer::sum);
        }
        int winner = 0;
        int best = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= best) {
                best = entry.getValue();
                winner = entry.getKey();
            }
        }
        return winner;
    }
}
