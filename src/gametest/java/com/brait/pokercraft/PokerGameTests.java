package com.brait.pokercraft;

import com.brait.pokercraft.poker.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.*;

public class PokerGameTests implements FabricGameTest {
    private static final PokerConfig CASH = new PokerConfig(PokerConfig.Mode.CASH, PokerConfig.BlindSchedule.STATIC,
            1, 5, 25, 100, 5, 10, 1000, 10, 100000, 2, 2, 45);
    private static UUID id(int seat) { return new UUID(0, seat + 1); }
    private static PokerGame.Player requirePlayer(PokerGame game, int seat) {
        PokerGame.Player player = game.player(seat);
        if (player == null) throw new AssertionError("Expected a registered player at seat " + seat);
        return player;
    }
    private static PokerGame game(long... stacks) {
        PokerGame g = new PokerGame(new Random(1729)); g.configure(CASH);
        for (int seat = 0; seat < stacks.length; seat++) { g.join(id(seat), "Player" + seat, seat); g.deposit(id(seat), stacks[seat]); }
        return g;
    }
    private static void reject(TestContext c, Runnable action) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        c.assertTrue(rejected, "Invalid action must be rejected");
    }
    private static PokerGame rig(PokerGame g, int[][] hands, int... community) {
        NbtCompound saved = g.save();
        for (NbtElement e : saved.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) e; int seat = p.getInt("Seat");
            p.putInt("First", hands[seat][0]); p.putInt("Second", hands[seat][1]);
        }
        List<Integer> order = new ArrayList<>(List.of(52, community[0], community[1], community[2], 51, community[3], 48, community[4]));
        Collections.reverse(order); saved.putIntArray("Deck", order);
        return PokerGame.load(saved);
    }

    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tableChipPilesRepresentExactStacksAtEveryDenomination(TestContext c) {
        for (long value : new long[]{0, 1, 4, 5, 99, 100, 101, 990, 1000, 2000, 2001, 2100, PokerConfig.MAX_STACK}) {
            var piles = PokerStackDisplay.piles(value, PokerConfig.DEFAULT);
            c.assertEquals(piles.stream().mapToLong(p -> p.count() * p.denomination()).sum(), value, "Displayed chip counts preserve the stack exactly");
            c.assertTrue(piles.stream().allMatch(p -> p.visibleCount() <= PokerStackDisplay.MAX_VISIBLE), "Large balances cannot create unbounded model geometry");
        }
        c.assertTrue(PokerStackDisplay.piles(0, PokerConfig.DEFAULT).stream().allMatch(p -> p.visibleCount() == 0), "Broke players have no chips");
        c.assertFalse(PokerStackDisplay.piles(2000, PokerConfig.DEFAULT).get(3).needsCountLabel(), "Twenty chips render literally");
        c.assertTrue(PokerStackDisplay.piles(2100, PokerConfig.DEFAULT).get(3).needsCountLabel(), "Larger piles carry an exact count label");
        PokerConfig custom = new PokerConfig(PokerConfig.Mode.CASH, PokerConfig.BlindSchedule.STATIC,
                50, 10, 5, 20, 0, 50, 1000, 50, 2000, 1, 2, 45);
        var customPiles = PokerStackDisplay.piles(990, custom);
        c.assertEquals(customPiles.get(0).count(), 19L, "White can be the highest denomination");
        c.assertEquals(customPiles.get(3).count(), 2L, "Custom colours make exact change in the model");
        PokerConfig tournament = new PokerConfig(PokerConfig.Mode.LAST_MAN_STANDING, PokerConfig.BlindSchedule.STATIC,
                50, 10, 5, 20, 0, 50, 1000, 50, 2000, 1, 2, 45);
        var virtual = PokerStackDisplay.piles(1003, tournament);
        c.assertEquals(virtual.stream().mapToLong(p -> p.count() * p.denomination()).sum(), 1003L, "Tournament odd units stay visible despite cash denomination settings");
        c.assertEquals(virtual.getFirst().denomination(), 1, "Tournament chips include a one-unit denomination");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chipBanksFollowTheCorrectPhysicalSeats(TestContext c) {
        for (var facing : net.minecraft.util.math.Direction.Type.HORIZONTAL) for (int seat = 0; seat < 6; seat++) {
            var bank = com.brait.pokercraft.block.PokerTableGeometry.chipBankOffset(seat, facing);
            int nearest = -1; double distance = Double.MAX_VALUE;
            for (int candidate = 0; candidate < 6; candidate++) {
                double next = bank.squaredDistanceTo(com.brait.pokercraft.block.PokerTableGeometry.seatOffset(candidate, facing));
                if (next < distance) { distance = next; nearest = candidate; }
            }
            c.assertEquals(nearest, seat, "Each stack belongs to its nearest stool for " + facing);
        }
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void arbitraryChipColoursAndSingleBlindConserveValue(TestContext c) {
        PokerConfig cfg = new PokerConfig(PokerConfig.Mode.CASH, PokerConfig.BlindSchedule.TIME,
                50, 10, 5, 20, 0, 50, 1000, 50, 2000, 1, 2, 45);
        c.assertEquals(cfg.unit(), 5L, "The smallest denomination defines the unit regardless of colour");
        c.assertEquals(cfg.blind(false, 100, Long.MAX_VALUE), 0L, "A disabled small blind stays disabled even after many blind levels");
        PokerConfig tournament = new PokerConfig(PokerConfig.Mode.LAST_MAN_STANDING, PokerConfig.BlindSchedule.TIME,
                50, 10, 5, 20, 0, 50, 1000, 50, 2000, 1, 2, 45);
        c.assertEquals(tournament.blind(false, 100, Long.MAX_VALUE), 0L, "Increasing tournament blinds cannot re-enable a disabled small blind");
        for (long amount = 0; amount <= 2000; amount += 5) {
            long value = PokerChips.payout(amount, cfg).stream().mapToLong(s -> (long) PokerChips.value(s, cfg) * s.getCount()).sum();
            c.assertEquals(value, amount, "Colour-independent payouts preserve every chip unit");
        }
        PokerGame g = new PokerGame(new Random(7)); g.configure(cfg);
        for (int i = 0; i < 3; i++) { g.join(id(i), "Player" + i, i); g.deposit(id(i), 200); }
        g.start(0);
        c.assertEquals(requirePlayer(g, 1).streetBet, 0L, "No small blind is posted");
        c.assertEquals(requirePlayer(g, 2).streetBet, 50L, "Only the big blind is posted");
        c.assertEquals(g.pot(), 50L, "A single blind funds the pot");
        for (int i = 0; i < 3; i++) g.act(requirePlayer(g, g.actor()).id, PokerGame.Action.CHECK_CALL, 0, i + 1);
        c.assertEquals(g.street(), PokerGame.Street.FLOP, "Single-blind action advances normally");
        c.assertEquals(g.minRaiseTo(), 50L, "The big blind remains the minimum opening bet");
        c.assertEquals(g.totalValue(), 600L, "Single-blind play conserves balances");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void announcementsKeepActionsAndStreetTransitionsAcrossReloads(TestContext c) {
        PokerGame g = game(100, 100); g.start(0);
        long before = g.view(id(0), 0).getLong("EventId");
        g.act(id(0), PokerGame.Action.CHECK_CALL, 0, 1);
        g.act(id(1), PokerGame.Action.CHECK_CALL, 0, 2);
        NbtCompound view = PokerGame.load(g.save()).view(id(0), 2);
        java.util.List<String> messages = new java.util.ArrayList<>();
        for (NbtElement e : view.getList("Events", NbtElement.COMPOUND_TYPE)) {
            NbtCompound event = (NbtCompound) e;
            if (event.getLong("Id") > before) messages.add(event.getString("Message"));
        }
        c.assertTrue(messages.contains("Player0 calls 5."), "Call announcements include the amount");
        c.assertTrue(messages.contains("Player1 checks."), "Free actions announce a check");
        c.assertTrue(messages.contains("The flop is dealt."), "Street changes do not overwrite the player's action announcement");
        g.act(requirePlayer(g, g.actor()).id, PokerGame.Action.FOLD, 0, 3);
        c.assertTrue(g.view(id(0), 3).getList("Events", NbtElement.COMPOUND_TYPE).toString().contains("wins"), "Fold wins announce the winner");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void animationEventsPersistAndDoNotExposePrivateCards(TestContext c) {
        PokerGame g = game(100, 100); g.start(0);
        long firstHand = g.view(id(0), 0).getLong("HandId");
        c.assertTrue(firstHand > 0, "A new deal has a stable identity");
        g.act(id(0), PokerGame.Action.FOLD, 0, 1);
        NbtCompound result = g.view(UUID.randomUUID(), 1);
        c.assertEquals(result.getIntArray("Winners").length, 1, "A fold win identifies the awarded seat");
        c.assertEquals(result.getIntArray("Winners")[0], 1, "Celebrate the player who received the pot");
        for (NbtElement e : result.getList("Players", NbtElement.COMPOUND_TYPE)) {
            c.assertFalse(((NbtCompound) e).contains("First"), "A celebration must not reveal unshown hole cards");
        }
        g = PokerGame.load(g.save());
        c.assertEquals(g.view(id(0), 2).getIntArray("Winners")[0], 1, "Winner events survive reload");
        g.startHand(200);
        c.assertTrue(g.view(id(0), 200).getLong("HandId") > firstHand, "Next hand triggers a new deal");
        c.assertEquals(g.view(id(0), 200).getIntArray("Winners").length, 0, "A new hand clears the previous celebration");
        g.stop();
        c.assertEquals(g.view(id(0), 201).getIntArray("Winners").length, 0, "Cancelling a hand awards no winner effect");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bettingSeatIdsFollowThePhysicalTableClockwise(TestContext c) {
        for (var facing : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            var first = com.brait.pokercraft.block.PokerTableGeometry.seatOffset(0, facing);
            double start = Math.atan2(first.x, -first.z), previous = -1;
            for (int seat = 0; seat < 6; seat++) {
                var offset = com.brait.pokercraft.block.PokerTableGeometry.seatOffset(seat, facing);
                double angle = (Math.atan2(offset.x, -offset.z) - start + Math.PI * 2) % (Math.PI * 2);
                c.assertTrue(angle > previous, "Betting order must go clockwise around the model for " + facing);
                previous = angle;
            }
        }
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void evaluatesAllHandCategoriesAndAceLowStraights(TestContext c) {
        long royal = PokerHandEvaluator.best(1, 10, 11, 12, 13, 16, 29);
        long quads = PokerHandEvaluator.best(1, 14, 27, 40, 13, 24, 34);
        long full = PokerHandEvaluator.best(1, 14, 27, 13, 26, 39, 2);
        long flush = PokerHandEvaluator.best(1, 3, 6, 9, 12, 16, 29);
        long straight = PokerHandEvaluator.best(1, 15, 29, 43, 5, 20, 35);
        long trips = PokerHandEvaluator.best(1, 14, 27, 13, 24, 35, 6);
        long twoPair = PokerHandEvaluator.best(1, 14, 13, 26, 3, 20, 35);
        long pair = PokerHandEvaluator.best(1, 14, 13, 24, 35, 6, 20);
        long high = PokerHandEvaluator.best(1, 16, 31, 47, 9, 24, 39);
        long[] ordered = {high, pair, twoPair, trips, straight, flush, full, quads, royal};
        for (int i = 1; i < ordered.length; i++) c.assertTrue(ordered[i] > ordered[i - 1], "Poker categories must rank correctly at " + i);
        c.assertEquals(PokerHandEvaluator.category(straight), "Straight", "Wheel uses ace as five-high");
        c.assertTrue(PokerHandEvaluator.best(2, 16, 30, 44, 6) > PokerHandEvaluator.best(1, 15, 29, 43, 5), "Six-high beats a wheel");
        reject(c, () -> PokerHandEvaluator.best(1, 1, 2, 3, 4));
        reject(c, () -> PokerHandEvaluator.best(1, 2, 3, 4, 53)); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortHeadsUpBlindRunsOutWithoutAnUncallableExtraBet(TestContext c) {
        PokerGame g = game(100, 10); requirePlayer(g, 1).stack = 3; g.start(0);
        c.assertFalse(g.inHand(), "The other player already covers the short all-in blind; run out automatically");
        c.assertEquals(g.totalValue(), 103L, "Uncalled blind overage is refunded before showdown");
        c.assertEquals(g.completedHands(), 1, "Short-blind hand settles exactly once"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chipValuesCannotChangeWhileCashRemainsBanked(TestContext c) {
        PokerGame g = game(100, 100);
        reject(c, () -> g.configure(PokerConfig.DEFAULT));
        c.assertEquals(g.leave(id(0)), 100L, "First balance cashes out"); c.assertEquals(g.leave(id(1)), 100L, "Second balance cashes out");
        g.configure(PokerConfig.DEFAULT); c.assertEquals(g.totalValue(), 0L, "Configuration changes only after banked cash is cleared");
        c.assertEquals(PokerConfig.fromNbt(PokerConfig.DEFAULT.toNbt()), PokerConfig.DEFAULT, "All configuration fields survive serialization");
        NbtCompound invalid = PokerConfig.DEFAULT.toNbt(); invalid.putInt("White", 3);
        reject(c, () -> PokerConfig.fromNbt(invalid)); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void headsUpBlindsAndBettingOrder(TestContext c) {
        PokerGame g = game(100, 100); g.start(0);
        c.assertEquals(g.dealer(), 0, "First button"); c.assertEquals(requirePlayer(g, 0).streetBet, 5L, "Heads-up button is small blind");
        c.assertEquals(requirePlayer(g, 1).streetBet, 10L, "Other player posts big blind"); c.assertEquals(g.actor(), 0, "Button acts first preflop");
        g.act(id(0), PokerGame.Action.CHECK_CALL, 0, 1); c.assertEquals(g.actor(), 1, "Big blind has its option");
        g.act(id(1), PokerGame.Action.CHECK_CALL, 0, 2); c.assertEquals(g.street(), PokerGame.Street.FLOP, "Flop follows completed preflop");
        c.assertEquals(g.actor(), 1, "Big blind acts first after flop"); c.assertEquals(g.board().size(), 3, "Three flop cards");
        c.assertEquals(g.totalValue(), 200L, "Blinds and calls conserve value"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectsOutOfTurnAndIllegalRaisesWithoutChangingBalances(TestContext c) {
        PokerGame g = game(200, 200, 200); g.start(0);
        reject(c, () -> g.act(id(1), PokerGame.Action.CHECK_CALL, 0, 1));
        reject(c, () -> g.act(id(0), PokerGame.Action.RAISE_TO, 15, 1));
        reject(c, () -> g.act(id(0), PokerGame.Action.RAISE_TO, 999, 1));
        c.assertEquals(g.totalValue(), 600L, "Rejected actions conserve value"); c.assertEquals(g.actor(), 0, "Rejected action does not advance turn");
        g.act(id(0), PokerGame.Action.RAISE_TO, 30, 1); c.assertEquals(g.minRaiseTo(), 50L, "Next raise adds the last full raise size");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shortAllInDoesNotReopenRaising(TestContext c) {
        PokerGame g = game(200, 35, 200); g.start(0);
        g.act(id(0), PokerGame.Action.RAISE_TO, 30, 1); g.act(id(1), PokerGame.Action.ALL_IN, 0, 2);
        g.act(id(2), PokerGame.Action.CHECK_CALL, 0, 3);
        c.assertEquals(g.actor(), 0, "Original raiser faces the short all-in"); c.assertFalse(g.canRaise(requirePlayer(g, 0)), "Short raise does not reopen action");
        reject(c, () -> g.act(id(0), PokerGame.Action.RAISE_TO, 55, 4));
        g.act(id(0), PokerGame.Action.CHECK_CALL, 0, 4); c.assertEquals(g.street(), PokerGame.Street.FLOP, "Original raiser can call");
        c.assertEquals(g.totalValue(), 435L, "Short all-in conserves chips"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cumulativeShortRaisesReopenAction(TestContext c) {
        PokerGame g = game(40, 50, 200, 200); g.start(0);
        c.assertEquals(g.actor(), 3, "Four-player action starts after big blind");
        g.act(id(3), PokerGame.Action.RAISE_TO, 30, 1); g.act(id(0), PokerGame.Action.ALL_IN, 0, 2);
        g.act(id(1), PokerGame.Action.ALL_IN, 0, 3); g.act(id(2), PokerGame.Action.CHECK_CALL, 0, 4);
        c.assertEquals(g.actor(), 3, "Action returns to original raiser"); c.assertTrue(g.canRaise(requirePlayer(g, 3)), "Cumulative full-raise amount reopens betting");
        g.act(id(3), PokerGame.Action.RAISE_TO, 70, 5); c.assertEquals(g.currentBet(), 70L, "Reopened raise succeeds"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allInsAwardMainAndSidePotsToEligiblePlayers(TestContext c) {
        PokerGame g = game(100, 200, 300); g.start(0);
        g = rig(g, new int[][]{{1, 14}, {13, 26}, {12, 25}}, 41, 29, 46, 35, 50);
        g.act(id(0), PokerGame.Action.ALL_IN, 0, 1); g.act(id(1), PokerGame.Action.ALL_IN, 0, 2);
        g.act(id(2), PokerGame.Action.CHECK_CALL, 0, 3);
        c.assertEquals(g.street(), PokerGame.Street.SHOWDOWN, "All-ins run out the board automatically");
        c.assertEquals(requirePlayer(g, 0).stack, 300L, "Best short stack wins main pot only");
        c.assertEquals(requirePlayer(g, 1).stack, 200L, "Next best hand wins side pot");
        c.assertEquals(requirePlayer(g, 2).stack, 100L, "Uncommitted overage remains with the large stack");
        c.assertEquals(g.totalValue(), 600L, "Side pots conserve total value"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void splitPotOddChipGoesClockwiseFromButton(TestContext c) {
        PokerGame g = game(100, 100, 100); g.start(0);
        g = rig(g, new int[][]{{2, 3}, {4, 5}, {6, 7}}, 23, 24, 25, 26, 14);
        g.act(id(0), PokerGame.Action.ALL_IN, 0, 1); g.act(id(1), PokerGame.Action.FOLD, 0, 2); g.act(id(2), PokerGame.Action.CHECK_CALL, 0, 3);
        c.assertEquals(requirePlayer(g, 0).stack, 102L, "Button gets half rounded down"); c.assertEquals(requirePlayer(g, 2).stack, 103L, "First winner left of button gets odd chip");
        c.assertEquals(requirePlayer(g, 1).stack, 95L, "Folded player cannot win their contribution back"); c.assertEquals(g.totalValue(), 300L, "Odd-chip split conserves value"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void simultaneousDeparturesRefundRatherThanChoosingAnArbitraryWinner(TestContext c) {
        PokerGame g = game(100, 100, 100); g.start(0); g.act(id(0), PokerGame.Action.RAISE_TO, 40, 1);
        g.setAvailability(Map.of(id(0), false, id(1), false, id(2), false), 2);
        for (PokerGame.Player p : g.players()) c.assertEquals(p.stack, 100L, "Everyone leaving returns each player's own unsettled wagers");
        c.assertFalse(g.inHand(), "Abandoned hand finishes safely"); c.assertEquals(g.totalValue(), 300L, "Abandoned hand preserves bankroll"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stoppedGameRefundsUnsettledBets(TestContext c) {
        PokerGame g = game(200, 200, 200); g.start(0); g.act(id(0), PokerGame.Action.RAISE_TO, 50, 1); g.stop();
        for (PokerGame.Player p : g.players()) c.assertEquals(p.stack, 200L, "Stopping returns each player's own pending wager");
        c.assertEquals(g.pot(), 0L, "No pot remains after stop"); c.assertFalse(g.running(), "Stopped game is not running");
        c.assertEquals(g.leave(id(0)), 200L, "Cash-out returns exact funded balance"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void saveResumeAndSnapshotsKeepHoleCardsPrivate(TestContext c) {
        PokerGame g = game(200, 200, 200); g.start(0); g.act(id(0), PokerGame.Action.CHECK_CALL, 0, 1);
        PokerGame loaded = PokerGame.load(g.save()); c.assertEquals(loaded.save(), g.save(), "Active hand survives round-trip persistence");
        NbtCompound own = loaded.view(id(0), 1), spectator = loaded.view(UUID.randomUUID(), 1);
        c.assertFalse(own.contains("Deck"), "Snapshot cannot reveal future deck order");
        for (NbtElement e : own.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) e; c.assertEquals(p.contains("First"), p.getInt("Seat") == 0, "Only own hole cards are visible before showdown");
        }
        for (NbtElement e : spectator.getList("Players", NbtElement.COMPOUND_TYPE)) c.assertFalse(((NbtCompound) e).contains("First"), "Spectator sees no hole cards");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blindSchedulesApplyAtHandBoundaries(TestContext c) {
        PokerConfig hands = new PokerConfig(PokerConfig.Mode.LAST_MAN_STANDING, PokerConfig.BlindSchedule.HANDS,
                1, 5, 25, 100, 5, 10, 1000, 10, 10000, 2, 2, 45);
        c.assertEquals(hands.blind(true, 1, 99999), 10L, "Hand ladder waits for configured hand count");
        c.assertEquals(hands.blind(true, 2, 0), 20L, "Hand ladder increases after two completed hands");
        PokerConfig timed = new PokerConfig(PokerConfig.Mode.LAST_MAN_STANDING, PokerConfig.BlindSchedule.TIME,
                1, 5, 25, 100, 5, 10, 1000, 10, 10000, 10, 2, 45);
        PokerGame g = new PokerGame(); g.configure(timed);
        c.assertEquals(g.view(id(0), 999999).getLong("BigBlind"), 10L, "Timed ladder does not run before the host starts the tournament");
        g.join(id(0), "A", 0); g.join(id(1), "B", 1); g.start(0);
        c.assertEquals(g.view(id(0), 500).getLong("BigBlind"), 10L, "Time expiry does not change blinds mid-hand");
        g.act(id(g.actor()), PokerGame.Action.FOLD, 0, 500); g.startHand(500);
        c.assertEquals(g.bigBlind(), 40L, "Next hand applies elapsed timed blind levels");
        c.assertEquals(CASH.blind(true, 10000, 9999999), 10L, "Cash-game blinds remain static"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tournamentEliminationFindsLastPlayerStanding(TestContext c) {
        PokerGame g = new PokerGame();
        g.configure(new PokerConfig(PokerConfig.Mode.LAST_MAN_STANDING, PokerConfig.BlindSchedule.STATIC,
                1, 5, 25, 100, 5, 10, 20, 10, 10000, 1, 2, 45));
        g.join(id(0), "A", 0); g.join(id(1), "B", 1); g.start(0);
        g = rig(g, new int[][]{{1, 14}, {13, 26}}, 41, 29, 46, 35, 50);
        g.act(id(0), PokerGame.Action.ALL_IN, 0, 1); g.act(id(1), PokerGame.Action.CHECK_CALL, 0, 2);
        c.assertEquals(requirePlayer(g, 0).stack, 40L, "Tournament winner has every starting chip");
        c.assertEquals(requirePlayer(g, 1).stack, 0L, "Other player is eliminated");
        c.assertEquals(g.street(), PokerGame.Street.FINISHED, "Tournament finishes with one funded player");
        c.assertFalse(g.running(), "Finished tournament does not auto-deal"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void timeoutChecksWhenFreeAndFoldsFacingABet(TestContext c) {
        PokerGame g = game(100, 100); g.start(0); g.tick(901);
        c.assertFalse(g.inHand(), "Unanswered opening call times out to fold"); c.assertEquals(g.totalValue(), 200L, "Timeout conserves money");
        g.startHand(902); g.act(id(g.actor()), PokerGame.Action.CHECK_CALL, 0, 903);
        int before = g.actor(); g.tick(1804); c.assertEquals(g.street(), PokerGame.Street.FLOP, "Free big-blind option times out to check");
        c.assertTrue(before >= 0, "There was an actionable big blind"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void randomizedHandsConserveBalancesAcrossActionsAndReloads(TestContext c) {
        PokerGame g = game(1000, 1000, 1000, 1000, 1000, 1000); g.start(0);
        long expected = 6000, now = 1; Random choices = new Random(9031);
        for (int hand = 0; hand < 150; hand++) {
            int actions = 0;
            while (g.inHand()) {
                c.assertTrue(++actions < 200, "Betting must make progress");
                PokerGame.Player p = requirePlayer(g, g.actor()); c.assertTrue(p.canAct(), "Turn points to an actionable player");
                int choice = choices.nextInt(8);
                if (choice == 0) g.act(p.id, PokerGame.Action.FOLD, 0, now++);
                else if (choice == 1 && g.canRaise(p) && p.stack + p.streetBet >= g.minRaiseTo())
                    g.act(p.id, PokerGame.Action.RAISE_TO, g.minRaiseTo(), now++);
                else if (choice == 2 && g.canRaise(p)) g.act(p.id, PokerGame.Action.ALL_IN, 0, now++);
                else g.act(p.id, PokerGame.Action.CHECK_CALL, 0, now++);
                c.assertEquals(g.totalValue(), expected, "Every action conserves bankroll");
                for (PokerGame.Player other : g.players()) c.assertTrue(other.stack >= 0 && other.contribution >= 0, "Balances cannot become negative");
                if (actions % 7 == 0) g = PokerGame.load(g.save());
            }
            for (PokerGame.Player p : g.players()) if (p.stack < 100) { g.deposit(p.id, 1000); expected += 1000; }
            g.startHand(now++);
        }
        c.complete();
    }
}
