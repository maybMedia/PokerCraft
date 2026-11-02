package com.brait.pokercraft.poker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standard 52-card high poker; IDs match the existing playing-card models. Jokers are excluded. */
public final class PokerHandEvaluator {
    private PokerHandEvaluator() {}
    public static final String[] CATEGORIES = {"High card", "One pair", "Two pair", "Three of a kind", "Straight",
            "Flush", "Full house", "Four of a kind", "Straight flush"};

    public record Hand(long score, List<Integer> cards) {
        public Hand { cards = List.copyOf(cards); }
    }

    public static long best(int... cards) {
        return evaluate(cards).score();
    }

    /** Returns the best five cards, ordered by rank groups (with an ace last in a wheel). */
    public static Hand evaluate(int... cards) {
        if (cards.length < 5 || cards.length > 7) throw new IllegalArgumentException("Evaluate five to seven cards.");
        long seen = 0;
        for (int card : cards) {
            if (card < 1 || card > 52 || (seen & (1L << card)) != 0) throw new IllegalArgumentException("Invalid or duplicate card.");
            seen |= 1L << card;
        }
        long score = -1;
        List<Integer> selected = List.of();
        for (int a = 0; a < cards.length - 4; a++) for (int b = a + 1; b < cards.length - 3; b++)
            for (int c = b + 1; c < cards.length - 2; c++) for (int d = c + 1; d < cards.length - 1; d++)
                for (int e = d + 1; e < cards.length; e++) {
                    long candidate = five(cards[a], cards[b], cards[c], cards[d], cards[e]);
                    if (candidate > score) {
                        score = candidate;
                        selected = List.of(cards[a], cards[b], cards[c], cards[d], cards[e]);
                    }
                }
        int[] counts = new int[15];
        for (int card : selected) counts[rank(card)]++;
        int category = (int) (score / 759375);
        boolean wheel = (category == 4 || category == 8) && counts[14] == 1 && counts[5] == 1;
        List<Integer> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.<Integer>comparingInt(card -> counts[rank(card)]).reversed()
                .thenComparing(Comparator.<Integer>comparingInt(card -> wheel && rank(card) == 14 ? 1 : rank(card)).reversed()));
        return new Hand(score, ordered);
    }

    private static int rank(int card) { int rank = (card - 1) % 13; return rank == 0 ? 14 : rank + 1; }

    public static String category(long score) { return CATEGORIES[(int) (score / 759375)]; }

    private static long five(int... cards) {
        int[] count = new int[15];
        boolean flush = true;
        int suit = (cards[0] - 1) / 13;
        for (int card : cards) {
            int rank = (card - 1) % 13;
            count[rank == 0 ? 14 : rank + 1]++;
            flush &= (card - 1) / 13 == suit;
        }
        int straight = 0;
        for (int high = 14; high >= 5; high--) {
            boolean matches = true;
            for (int i = 0; i < 5; i++) matches &= count[high - i == 1 ? 14 : high - i] > 0;
            if (matches) { straight = high; break; }
        }
        List<Integer> ranks = new ArrayList<>();
        for (int r = 14; r >= 2; r--) if (count[r] > 0) ranks.add(r);
        ranks.sort(Comparator.<Integer>comparingInt(r -> count[r]).reversed().thenComparing(Comparator.reverseOrder()));
        int first = ranks.getFirst(), second = ranks.size() > 1 ? ranks.get(1) : 0;
        if (flush && straight > 0) return encode(8, straight);
        if (count[first] == 4) return encode(7, first, second);
        if (count[first] == 3 && count[second] == 2) return encode(6, first, second);
        if (flush) return encode(5, ranks.stream().mapToInt(Integer::intValue).toArray());
        if (straight > 0) return encode(4, straight);
        if (count[first] == 3) return encode(3, ranks.stream().mapToInt(Integer::intValue).toArray());
        if (count[first] == 2 && count[second] == 2) return encode(2, ranks.stream().mapToInt(Integer::intValue).toArray());
        return encode(count[first] == 2 ? 1 : 0, ranks.stream().mapToInt(Integer::intValue).toArray());
    }

    private static long encode(int category, int... kickers) {
        long result = category;
        for (int i = 0; i < 5; i++) result = result * 15 + (i < kickers.length ? kickers[i] : 0);
        return result;
    }
}
