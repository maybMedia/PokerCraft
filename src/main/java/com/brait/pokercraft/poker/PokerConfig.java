package com.brait.pokercraft.poker;

import net.minecraft.nbt.NbtCompound;

/** All values are integral; chip colours do not determine denomination order. */
public record PokerConfig(Mode mode, BlindSchedule schedule, int white, int red, int blue, int green,
                          long smallBlind, long bigBlind, long startingStack, long minBuyIn, long maxBuyIn,
                          int interval, int multiplier, int turnSeconds) {
    public enum Mode { CASH, LAST_MAN_STANDING }
    public enum BlindSchedule { STATIC, HANDS, TIME }
    public static final long MAX_STACK = 1_000_000_000L;
    public static final PokerConfig DEFAULT = new PokerConfig(Mode.CASH, BlindSchedule.STATIC,
            1, 5, 25, 100, 5, 10, 1000, 100, 100000, 10, 2, 45);

    public PokerConfig {
        if (mode == null || schedule == null) throw new IllegalArgumentException("Select a game mode and blind schedule.");
        int smallest = Math.min(Math.min(white, red), Math.min(blue, green));
        if (smallest < 1 || white > 1_000_000 || red > 1_000_000 || blue > 1_000_000 || green > 1_000_000
                || white % smallest != 0 || red % smallest != 0 || blue % smallest != 0 || green % smallest != 0)
            throw new IllegalArgumentException("Chip values must be positive multiples of the smallest chip (maximum 1,000,000). Colours can be in any order.");
        long unit = mode == Mode.CASH ? smallest : 1;
        if (smallBlind < 0 || bigBlind < unit || bigBlind < smallBlind || bigBlind > MAX_STACK || smallBlind % unit != 0 || bigBlind % unit != 0)
            throw new IllegalArgumentException("Big blind must be positive and >= small; blinds must be chip-unit multiples. Small blind 0 uses a single blind.");
        if (mode == Mode.LAST_MAN_STANDING ? startingStack < bigBlind || startingStack > MAX_STACK
                : minBuyIn < bigBlind || maxBuyIn < minBuyIn || maxBuyIn > MAX_STACK || minBuyIn % unit != 0 || maxBuyIn % unit != 0)
            throw new IllegalArgumentException("Starting stack and buy-in limits must cover the big blind (maximum 1 billion).");
        if (interval < 1 || interval > 86400 || multiplier < 2 || multiplier > 10 || turnSeconds < 10 || turnSeconds > 300)
            throw new IllegalArgumentException("Interval: 1–86400; blind multiplier: 2–10; turn timer: 10–300 seconds.");
    }

    public int smallestChip() { return Math.min(Math.min(white, red), Math.min(blue, green)); }
    public long unit() { return mode == Mode.CASH ? smallestChip() : 1; }

    public long blind(boolean big, int completedHands, long elapsedTicks) {
        long value = big ? bigBlind : smallBlind;
        if (value == 0) return 0;
        long levels = mode == Mode.CASH || schedule == BlindSchedule.STATIC ? 0
                : schedule == BlindSchedule.HANDS ? completedHands / interval : Math.max(0, elapsedTicks) / (interval * 20L);
        long cap = MAX_STACK / unit() * unit();
        for (long i = 0; i < levels && value < cap; i++) value = value > cap / multiplier ? cap : value * multiplier;
        return value;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("Mode", mode.name()); n.putString("Schedule", schedule.name());
        n.putInt("White", white); n.putInt("Red", red); n.putInt("Blue", blue); n.putInt("Green", green);
        n.putLong("Small", smallBlind); n.putLong("Big", bigBlind); n.putLong("Starting", startingStack);
        n.putLong("MinBuyIn", minBuyIn); n.putLong("MaxBuyIn", maxBuyIn);
        n.putInt("Interval", interval); n.putInt("Multiplier", multiplier); n.putInt("TurnSeconds", turnSeconds);
        return n;
    }

    public static PokerConfig fromNbt(NbtCompound n) {
        return new PokerConfig(Mode.valueOf(n.getString("Mode")), BlindSchedule.valueOf(n.getString("Schedule")),
                n.getInt("White"), n.getInt("Red"), n.getInt("Blue"), n.getInt("Green"), n.getLong("Small"),
                n.getLong("Big"), n.getLong("Starting"), n.getLong("MinBuyIn"), n.getLong("MaxBuyIn"),
                n.getInt("Interval"), n.getInt("Multiplier"), n.getInt("TurnSeconds"));
    }
}
