package com.brait.pokercraft.poker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Exact denomination counts for the table model, with bounded visual pile heights. */
public final class PokerStackDisplay {
    public static final int MAX_VISIBLE = 20;
    public static final List<String> COLORS = List.of("white", "red", "blue", "green");
    private static final String[][][] BONES = new String[PokerGame.SEATS][4][MAX_VISIBLE];
    static {
        for (int seat = 0; seat < PokerGame.SEATS; seat++)
            for (int color = 0; color < COLORS.size(); color++)
                for (int level = 0; level < MAX_VISIBLE; level++)
                    BONES[seat][color][level] = "stack_" + seat + "_" + COLORS.get(color) + "_" + level;
    }
    public record Pile(int color, int denomination, long count) {
        public int visibleCount() { return (int) Math.min(count, MAX_VISIBLE); }
        public boolean needsCountLabel() { return count > MAX_VISIBLE; }
    }
    private PokerStackDisplay() {}
    public static String boneName(int seat, int color, int level) { return BONES[seat][color][level]; }

    public static List<Pile> piles(long value, PokerConfig config) {
        if (value < 0 || value % config.unit() != 0) throw new IllegalArgumentException("Invalid table stack value.");
        // Tournament stacks use virtual units, including odd chips from split pots.
        PokerConfig denominations = config.mode() == PokerConfig.Mode.CASH ? config : PokerConfig.DEFAULT;
        int[] values = {denominations.white(), denominations.red(), denominations.blue(), denominations.green()};
        Integer[] order = {0, 1, 2, 3};
        Arrays.sort(order, Comparator.comparingInt((Integer color) -> values[color]).reversed());
        long[] counts = new long[4];
        for (int color : order) {
            counts[color] = value / values[color];
            value %= values[color];
        }
        List<Pile> result = new ArrayList<>(4);
        for (int color = 0; color < 4; color++) result.add(new Pile(color, values[color], counts[color]));
        return List.copyOf(result);
    }
}
