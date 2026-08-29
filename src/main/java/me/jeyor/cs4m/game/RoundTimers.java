package me.jeyor.cs4m.game;

public final class RoundTimers {
    private int startDelayTicks;
    private int startSeconds = -1;
    private int shopTicks;
    private int shopSeconds = -1;
    private int runTicks;
    private int runSeconds = -1;
    private int restartDelayTicks;
    private int finishDelayTicks;

    public void startMatchCountdown(int seconds) {
        startDelayTicks = 40;
        startSeconds = seconds;
        shopSeconds = -1;
        runSeconds = -1;
    }

    public void startShop(int seconds) {
        shopTicks = 20;
        shopSeconds = seconds;
        startSeconds = -1;
        runSeconds = -1;
    }

    public void startRun(int seconds) {
        runTicks = 20;
        runSeconds = seconds;
        shopSeconds = -1;
        startSeconds = -1;
    }

    public void delayRestart(int seconds) {
        restartDelayTicks = Math.max(1, seconds) * 20;
    }

    public void delayFinish() {
        finishDelayTicks = 20;
    }

    public void stopPhaseTimers() {
        startSeconds = -1;
        shopSeconds = -1;
        runSeconds = -1;
        startDelayTicks = 0;
        shopTicks = 0;
        runTicks = 0;
    }

    public void stopAll() {
        stopPhaseTimers();
        restartDelayTicks = 0;
        finishDelayTicks = 0;
    }

    public boolean tickStartCountdown() {
        if (startSeconds < 0) {
            return false;
        }
        if (startDelayTicks > 0) {
            startDelayTicks--;
            return false;
        }
        startDelayTicks = 20;
        return true;
    }

    public int consumeStartSecond() {
        int remaining = startSeconds;
        startSeconds--;
        return remaining;
    }

    public boolean startFinished() {
        return startSeconds < 0;
    }

    public boolean tickShop() {
        if (shopSeconds < 0) {
            return false;
        }
        if (shopTicks > 0) {
            shopTicks--;
            return false;
        }
        shopTicks = 20;
        return true;
    }

    public int consumeShopSecond() {
        int remaining = shopSeconds;
        shopSeconds--;
        return remaining;
    }

    public boolean shopFinished() {
        return shopSeconds < 0;
    }

    public boolean tickRun() {
        if (runSeconds < 0) {
            return false;
        }
        if (runTicks > 0) {
            runTicks--;
            return false;
        }
        runTicks = 20;
        return true;
    }

    public int consumeRunSecond() {
        runSeconds--;
        return runSeconds;
    }

    public int runSeconds() {
        return runSeconds;
    }

    public boolean tickRestartDelay() {
        if (restartDelayTicks <= 0) {
            return false;
        }
        restartDelayTicks--;
        return restartDelayTicks == 0;
    }

    public boolean tickFinishDelay() {
        if (finishDelayTicks <= 0) {
            return false;
        }
        finishDelayTicks--;
        return finishDelayTicks == 0;
    }
}
