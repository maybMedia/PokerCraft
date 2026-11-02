package com.brait.pokercraft.poker;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.security.SecureRandom;
import java.util.*;

/** Server-owned no-limit Hold'em state machine. Monetary mutations happen only here. */
public final class PokerGame {
    public enum Street { WAITING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, FINISHED }
    public enum Action { FOLD, CHECK_CALL, RAISE_TO, ALL_IN }
    public static final int SEATS = 6;

    public static final class Player {
        public final UUID id;
        public final String name;
        public final int seat;
        public long stack, streetBet, contribution, raiseUnlockedAt;
        public boolean inHand, folded, available = true;
        public int firstCard, secondCard;
        private Player(UUID id, String name, int seat) { this.id = id; this.name = name; this.seat = seat; }
        public boolean canAct() { return inHand && !folded && stack > 0; }
    }

    private PokerConfig config = PokerConfig.DEFAULT;
    private final Player[] players = new Player[SEATS];
    private final List<Integer> board = new ArrayList<>(), deck = new ArrayList<>();
    private final Set<Integer> pending = new HashSet<>();
    private final Random random;
    private Street street = Street.WAITING;
    private boolean running;
    private int dealer = -1, actor = -1, completedHands;
    private long currentBet, minRaise, smallBlind, bigBlind, sessionStarted, actionStarted, handEnded, version;
    private String result = "Waiting for players.";
    private long handId;
    private final Set<Integer> winningSeats = new LinkedHashSet<>();
    private long eventId;
    private final NbtList events = new NbtList();

    private void announce(String message) {
        NbtCompound event = new NbtCompound(); event.putLong("Id", ++eventId); event.putString("Message", message);
        events.add(event);
        while (events.size() > 16) events.removeFirst();
    }

    public PokerGame() { this(new SecureRandom()); }
    public PokerGame(Random random) { this.random = random; }
    public PokerConfig config() { return config; }
    public Street street() { return street; }
    public boolean running() { return running; }
    public boolean inHand() { return street.ordinal() >= Street.PREFLOP.ordinal() && street.ordinal() <= Street.RIVER.ordinal(); }
    public int actor() { return actor; }
    public int dealer() { return dealer; }
    public int completedHands() { return completedHands; }
    public long currentBet() { return currentBet; }
    public long minRaiseTo() { return currentBet == 0 ? bigBlind : currentBet + minRaise; }
    public long bigBlind() { return inHand() ? bigBlind : config.blind(true, completedHands, 0); }
    public long handEnded() { return handEnded; }
    public long version() { return version; }
    public String result() { return result; }
    public List<Integer> board() { return List.copyOf(board); }
    public Player player(int seat) { return seat < 0 || seat >= SEATS ? null : players[seat]; }
    public Player player(UUID id) { for (Player p : players) if (p != null && p.id.equals(id)) return p; return null; }
    public List<Player> players() { return Arrays.stream(players).filter(Objects::nonNull).toList(); }
    public long pot() { return players().stream().mapToLong(p -> p.contribution).sum(); }
    public long totalValue() { return players().stream().mapToLong(p -> p.stack + p.contribution).sum(); }
    public long toCall(Player p) { return p == null ? 0 : Math.clamp(currentBet - p.streetBet, 0L, p.stack); }
    public boolean canRaise(Player p) {
        return p != null && p.canAct() && currentBet >= p.raiseUnlockedAt && p.stack + p.streetBet > currentBet
                && players().stream().filter(Player::canAct).count() > 1;
    }

    public void configure(PokerConfig next) {
        require(!running && !inHand(), "Stop the game before changing configuration.");
        require(config.mode() != PokerConfig.Mode.CASH || totalValue() == 0, "Cash out every cash-game balance before changing configuration.");
        config = next;
        for (Player p : players()) { p.stack = 0; clearHand(p); }
        street = Street.WAITING; board.clear(); winningSeats.clear(); completedHands = 0; result = "Configuration saved."; version++;
        announce(result);
    }

    public Player join(UUID id, String name, int seat) {
        require(seat >= 0 && seat < SEATS, "Sit on a stool before joining.");
        Player old = player(id);
        if (old != null) {
            require(old.seat == seat, "Return to your registered stool or leave this table first.");
            return old;
        }
        require(!inHand(), "Join between hands.");
        require(!running || config.mode() == PokerConfig.Mode.CASH, "The last-man-standing game has already started.");
        require(players[seat] == null, "This seat has a registered player.");
        Player p = new Player(id, name, seat); players[seat] = p; version++;
        announce(name + " joins the table.");
        return p;
    }

    public void deposit(UUID id, long amount) {
        Player p = requirePlayer(id);
        require(config.mode() == PokerConfig.Mode.CASH && !inHand(), "Buy in between cash-game hands.");
        require(amount > 0 && amount % config.unit() == 0 && p.stack + amount >= config.minBuyIn()
                && p.stack + amount <= config.maxBuyIn(), "The resulting stack must be within the configured buy-in limits.");
        p.stack += amount; version++;
        announce(p.name + " buys in for " + amount + ".");
    }

    public long leave(UUID id) {
        Player p = requirePlayer(id);
        require(!inHand(), "Wait until the hand finishes before leaving or cashing out.");
        require(!running || config.mode() == PokerConfig.Mode.CASH, "Tournament players stay registered until the game ends.");
        long value = config.mode() == PokerConfig.Mode.CASH ? p.stack : 0;
        players[p.seat] = null; version++;
        announce(p.name + " leaves the table.");
        return value;
    }

    public void start(long now) {
        require(!running && !inHand(), "The game is already running.");
        require(players().size() >= 2, "At least two joined players are required.");
        if (config.mode() == PokerConfig.Mode.LAST_MAN_STANDING) {
            for (Player p : players()) p.stack = config.startingStack();
        }
        require(players().stream().filter(p -> p.stack > 0 && p.available).count() >= 2, "At least two seated players need funded stacks.");
        running = true; completedHands = 0; sessionStarted = now; dealer = -1;
        startHand(now);
    }

    public void startHand(long now) {
        require(running && !inHand(), "Start the game or finish the current hand first.");
        List<Player> funded = players().stream().filter(p -> p.stack > 0
                && (config.mode() == PokerConfig.Mode.LAST_MAN_STANDING || p.available)).toList();
        require(funded.size() >= 2 && funded.stream().anyMatch(p -> p.available), "Waiting for funded, seated players.");
        board.clear(); deck.clear(); pending.clear(); winningSeats.clear(); handId++;
        for (int i = 1; i <= 52; i++) deck.add(i);
        Collections.shuffle(deck, random);
        for (Player p : players()) { clearHand(p); p.inHand = funded.contains(p); }
        dealer = nextSeat(dealer, p -> p.inHand);
        // Deal clockwise from the button, one card per pass.
        for (int pass = 0; pass < 2; pass++) {
            int seat = dealer;
            for (int i = 0; i < funded.size(); i++) {
                seat = nextSeat(seat, p -> p.inHand);
                if (pass == 0) players[seat].firstCard = draw(); else players[seat].secondCard = draw();
            }
        }
        smallBlind = config.blind(false, completedHands, now - sessionStarted);
        bigBlind = config.blind(true, completedHands, now - sessionStarted);
        minRaise = bigBlind;
        int small = funded.size() == 2 ? dealer : nextSeat(dealer, p -> p.inHand);
        int big = nextSeat(small, p -> p.inHand);
        pay(players[small], Math.min(players[small].stack, smallBlind));
        pay(players[big], Math.min(players[big].stack, bigBlind));
        // A short big blind does not reduce the full opening call/raise amount.
        currentBet = bigBlind;
        street = Street.PREFLOP;
        for (Player p : funded) {
            if (!p.available && p.stack > 0) p.folded = true;
            if (p.canAct()) pending.add(p.seat);
        }
        actor = nextSeat(big, p -> pending.contains(p.seat)); actionStarted = now;
        result = "Hand " + (completedHands + 1) + ": blinds " + smallBlind + "/" + bigBlind + ".";
        announce("Hand " + (completedHands + 1) + " dealt. " + (smallBlind == 0 ? "Single blind " + bigBlind : "Blinds " + smallBlind + "/" + bigBlind) + ".");
        version++;
        advance(now);
    }

    public void act(UUID id, Action action, long raiseTo, long now) {
        Player p = requirePlayer(id);
        require(inHand() && actor == p.seat && pending.contains(p.seat) && p.canAct(), "It is not your turn.");
        long callAmount = toCall(p);
        boolean wasAllIn = action == Action.ALL_IN;
        if (action == Action.ALL_IN && p.streetBet + p.stack <= currentBet) action = Action.CHECK_CALL;
        if (action == Action.FOLD) {
            p.folded = true;
        } else if (action == Action.CHECK_CALL) {
            pay(p, toCall(p));
        } else {
            long target = action == Action.ALL_IN ? p.streetBet + p.stack : raiseTo;
            require(canRaise(p), "Betting has not reopened for you, or no opponent can call a raise.");
            require(target > currentBet && target <= p.streetBet + p.stack && target % config.unit() == 0, "Invalid raise-to amount.");
            long increase = target - currentBet;
            boolean fullRaise = currentBet == 0 ? target >= bigBlind : increase >= minRaise;
            require(fullRaise || target == p.streetBet + p.stack, "A smaller raise is allowed only when all-in.");
            pay(p, target - p.streetBet);
            if (fullRaise) minRaise = currentBet == 0 ? target : increase;
            currentBet = target;
            for (Player other : players()) if (other.canAct() && other != p && other.streetBet < currentBet) pending.add(other.seat);
        }
        announce(p.name + (action == Action.FOLD ? " folds." : wasAllIn || p.stack == 0 ? " goes all-in for " + p.streetBet + "."
                : action == Action.CHECK_CALL ? callAmount == 0 ? " checks." : " calls " + callAmount + "."
                : " raises to " + p.streetBet + "."));
        p.raiseUnlockedAt = currentBet == 0 ? 0 : currentBet + minRaise;
        pending.remove(p.seat);
        actor = nextSeat(p.seat, other -> pending.contains(other.seat) && other.canAct());
        actionStarted = now; version++;
        advance(now);
    }

    /** Apply one world tick atomically, so simultaneous departures cannot award a pot by seat iteration order. */
    public void setAvailability(Map<UUID, Boolean> availability, long now) {
        boolean changed = false;
        int previousActor = actor;
        for (Player p : players()) {
            boolean available = availability.getOrDefault(p.id, p.available);
            if (p.available == available) continue;
            p.available = available; changed = true;
            if (!available && inHand() && p.inHand && !p.folded && p.stack > 0) {
                p.folded = true; pending.remove(p.seat);
                announce(p.name + " folds (left their seat).");
            }
        }
        if (changed) {
            version++;
            if (inHand() && (actor < 0 || !pending.contains(actor))) {
                actor = nextSeat(previousActor, other -> pending.contains(other.seat) && other.canAct()); actionStarted = now;
            }
            advance(now);
        }
    }

    public void tick(long now) {
        if (inHand() && actor >= 0 && now - actionStarted >= config.turnSeconds() * 20L) {
            Player p = players[actor];
            act(p.id, currentBet <= p.streetBet ? Action.CHECK_CALL : Action.FOLD, 0, now);
        }
    }

    /** Stop/cancel returns all unsettled contributions, never awards an unfinished pot. */
    public void stop() {
        for (Player p : players()) { p.stack += p.contribution; clearHand(p); }
        running = false; actor = -1; pending.clear(); board.clear(); winningSeats.clear(); street = Street.WAITING;
        result = "Game stopped. Unsettled bets were returned."; version++;
        announce(result);
    }

    private void advance(long now) {
        if (!inHand()) return;
        List<Player> live = players().stream().filter(p -> p.inHand && !p.folded).toList();
        if (live.size() <= 1) { settle(now, false); return; }
        pending.removeIf(seat -> !players[seat].canAct());
        List<Player> able = live.stream().filter(Player::canAct).toList();
        long actualBet = live.stream().mapToLong(p -> p.streetBet).max().orElse(0);
        if (able.size() <= 1 && (able.isEmpty() || able.getFirst().streetBet >= actualBet)) pending.clear();
        if (!pending.isEmpty()) {
            if (actor < 0 || !pending.contains(actor)) actor = nextSeat(dealer, p -> pending.contains(p.seat));
            return;
        }
        refundUncalled();
        if (street == Street.RIVER) { settle(now, true); return; }
        draw(); // Burn before each community street.
        if (street == Street.PREFLOP) { street = Street.FLOP; for (int i = 0; i < 3; i++) board.add(draw()); }
        else { board.add(draw()); street = street == Street.FLOP ? Street.TURN : Street.RIVER; }
        announce(switch (street) { case FLOP -> "The flop is dealt."; case TURN -> "The turn is dealt."; default -> "The river is dealt."; });
        currentBet = 0; minRaise = bigBlind;
        for (Player p : players()) {
            p.streetBet = 0; p.raiseUnlockedAt = 0;
            if (p.canAct()) pending.add(p.seat);
        }
        actor = nextSeat(dealer, p -> pending.contains(p.seat)); actionStarted = now;
        advance(now);
    }

    private void refundUncalled() {
        List<Player> sorted = new ArrayList<>(players());
        sorted.sort(Comparator.comparingLong((Player p) -> p.streetBet).reversed());
        if (sorted.size() < 2) return;
        Player top = sorted.getFirst();
        long refund = top.streetBet - sorted.get(1).streetBet;
        if (refund > 0 && !top.folded) { top.streetBet -= refund; top.contribution -= refund; top.stack += refund; }
    }

    private void settle(long now, boolean showdown) {
        List<Player> live = players().stream().filter(p -> p.inHand && !p.folded).toList();
        Map<Player, Long> scores = new HashMap<>(), payouts = new LinkedHashMap<>();
        if (showdown) for (Player p : live) {
            int[] cards = new int[7]; cards[0] = p.firstCard; cards[1] = p.secondCard;
            for (int i = 0; i < 5; i++) cards[i + 2] = board.get(i);
            scores.put(p, PokerHandEvaluator.best(cards));
        }
        if (live.isEmpty()) {
            for (Player p : players()) p.stack += p.contribution;
            result = "Everyone left; unsettled bets returned.";
        } else {
            long previous = 0;
            TreeSet<Long> levels = new TreeSet<>();
            for (Player p : players()) if (p.contribution > 0) levels.add(p.contribution);
            for (long level : levels) {
                long amount = (level - previous) * players().stream().filter(p -> p.contribution >= level).count();
                List<Player> eligible = live.stream().filter(p -> p.contribution >= level).toList();
                if (eligible.isEmpty()) {
                    // A departing player can be folded after making an unmatched wager; return that layer.
                    for (Player p : players()) if (p.contribution >= level) p.stack += level - previous;
                } else {
                    long best = showdown ? eligible.stream().mapToLong(scores::get).max().orElseThrow() : 0;
                    List<Player> winners = new ArrayList<>(eligible.stream().filter(p -> !showdown || scores.get(p) == best).toList());
                    winners.sort(Comparator.comparingInt(p -> Math.floorMod(p.seat - dealer - 1, SEATS)));
                    long units = amount / config.unit(), share = units / winners.size(), odd = units % winners.size();
                    for (int i = 0; i < winners.size(); i++) {
                        long won = (share + (i < odd ? 1 : 0)) * config.unit();
                        Player p = winners.get(i); p.stack += won; payouts.merge(p, won, Long::sum);
                        if (won > 0) winningSeats.add(p.seat);
                    }
                }
                previous = level;
            }
            result = payouts.entrySet().stream().map(e -> e.getKey().name + " wins " + e.getValue()
                    + (showdown ? " (" + PokerHandEvaluator.category(scores.get(e.getKey())) + ")" : ""))
                    .reduce((a, b) -> a + "; " + b).orElse("Hand completed.");
        }
        for (Player p : players()) { p.streetBet = 0; p.contribution = 0; }
        street = showdown ? Street.SHOWDOWN : Street.WAITING; actor = -1; pending.clear();
        completedHands++; handEnded = now; version++;
        if (config.mode() == PokerConfig.Mode.LAST_MAN_STANDING) {
            List<Player> remaining = players().stream().filter(p -> p.stack > 0).toList();
            if (remaining.size() <= 1) {
                running = false; street = Street.FINISHED;
                result = remaining.isEmpty() ? "Tournament ended." : remaining.getFirst().name + " is the last player standing!";
            }
        }
        if (payouts.isEmpty()) announce(result);
        else for (var payout : payouts.entrySet()) if (payout.getValue() > 0)
            announce(payout.getKey().name + " wins " + payout.getValue()
                    + (showdown ? " (" + PokerHandEvaluator.category(scores.get(payout.getKey())) + ")" : "") + ".");
        if (street == Street.FINISHED && !payouts.isEmpty()) announce(result);
    }

    private void clearHand(Player p) {
        p.streetBet = p.contribution = p.raiseUnlockedAt = 0;
        p.firstCard = p.secondCard = 0; p.inHand = false; p.folded = false;
    }
    private void pay(Player p, long amount) { p.stack -= amount; p.streetBet += amount; p.contribution += amount; }
    private int draw() { return deck.removeLast(); }
    private int nextSeat(int after, java.util.function.Predicate<Player> predicate) {
        for (int i = 1; i <= SEATS; i++) {
            int seat = Math.floorMod(after + i, SEATS);
            if (players[seat] != null && predicate.test(players[seat])) return seat;
        }
        return -1;
    }
    private Player requirePlayer(UUID id) { Player p = player(id); require(p != null, "Join this table first."); return p; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    /** Full persistence only. Never send this to clients: it contains the shuffled deck and all hole cards. */
    public NbtCompound save() {
        NbtCompound n = new NbtCompound(); n.put("Config", config.toNbt());
        n.putString("Street", street.name()); n.putBoolean("Running", running); n.putInt("Dealer", dealer); n.putInt("Actor", actor);
        n.putInt("Hands", completedHands); n.putLong("Bet", currentBet); n.putLong("MinRaise", minRaise);
        n.putLong("SmallBlind", smallBlind); n.putLong("BigBlind", bigBlind); n.putLong("Started", sessionStarted);
        n.putLong("ActionStarted", actionStarted); n.putLong("HandEnded", handEnded); n.putString("Result", result);
        n.putIntArray("Board", board); n.putIntArray("Deck", deck); n.putIntArray("Pending", new ArrayList<>(pending));
        n.putLong("HandId", handId); n.putIntArray("Winners", new ArrayList<>(winningSeats));
        n.putLong("EventId", eventId); n.put("Events", events.copy());
        NbtList list = new NbtList();
        for (Player p : players()) {
            NbtCompound e = new NbtCompound(); e.putUuid("Id", p.id); e.putString("Name", p.name); e.putInt("Seat", p.seat);
            e.putLong("Stack", p.stack); e.putLong("StreetBet", p.streetBet); e.putLong("Contribution", p.contribution);
            e.putLong("RaiseUnlocked", p.raiseUnlockedAt); e.putBoolean("InHand", p.inHand); e.putBoolean("Folded", p.folded);
            e.putBoolean("Available", p.available); e.putInt("First", p.firstCard); e.putInt("Second", p.secondCard); list.add(e);
        }
        n.put("Players", list); return n;
    }

    public static PokerGame load(NbtCompound n) {
        PokerGame g = new PokerGame(); g.config = PokerConfig.fromNbt(n.getCompound("Config"));
        g.street = Street.valueOf(n.getString("Street")); g.running = n.getBoolean("Running");
        g.dealer = n.getInt("Dealer"); g.actor = n.getInt("Actor"); g.completedHands = n.getInt("Hands");
        g.currentBet = n.getLong("Bet"); g.minRaise = n.getLong("MinRaise"); g.smallBlind = n.getLong("SmallBlind"); g.bigBlind = n.getLong("BigBlind");
        g.sessionStarted = n.getLong("Started"); g.actionStarted = n.getLong("ActionStarted"); g.handEnded = n.getLong("HandEnded"); g.result = n.getString("Result");
        for (int card : n.getIntArray("Board")) g.board.add(card);
        for (int card : n.getIntArray("Deck")) g.deck.add(card);
        for (int seat : n.getIntArray("Pending")) g.pending.add(seat);
        g.handId = n.contains("HandId") ? n.getLong("HandId") : g.completedHands + (g.inHand() ? 1L : 0L);
        for (int seat : n.getIntArray("Winners")) g.winningSeats.add(seat);
        g.eventId = n.getLong("EventId"); g.events.addAll(n.getList("Events", NbtElement.COMPOUND_TYPE).copy());
        for (NbtElement element : n.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound e = (NbtCompound) element; int seat = e.getInt("Seat");
            if (seat < 0 || seat >= SEATS) throw new IllegalArgumentException("Invalid saved poker seat.");
            Player p = new Player(e.getUuid("Id"), e.getString("Name"), seat);
            p.stack = e.getLong("Stack"); p.streetBet = e.getLong("StreetBet"); p.contribution = e.getLong("Contribution");
            p.raiseUnlockedAt = e.getLong("RaiseUnlocked"); p.inHand = e.getBoolean("InHand"); p.folded = e.getBoolean("Folded");
            p.available = e.getBoolean("Available"); p.firstCard = e.getInt("First"); p.secondCard = e.getInt("Second"); g.players[seat] = p;
        }
        return g;
    }

    /** A personalized snapshot, excluding the future deck and other players' hidden cards. */
    public NbtCompound view(UUID viewer, long now) {
        NbtCompound n = new NbtCompound(); n.put("Config", config.toNbt()); n.putString("Street", street.name());
        n.putBoolean("Running", running); n.putBoolean("InHand", inHand()); n.putInt("Dealer", dealer); n.putInt("Actor", actor);
        n.putInt("Hands", completedHands); n.putLong("Pot", pot()); n.putLong("Bet", currentBet); n.putLong("MinRaiseTo", minRaiseTo());
        long elapsed = running ? Math.max(0, now - sessionStarted) : 0;
        int handsForLevel = running ? completedHands : 0;
        n.putLong("SmallBlind", inHand() ? smallBlind : config.blind(false, handsForLevel, elapsed));
        n.putLong("BigBlind", inHand() ? bigBlind : config.blind(true, handsForLevel, elapsed));
        n.putInt("SecondsLeft", inHand() ? (int) Math.max(0, config.turnSeconds() - (now - actionStarted) / 20) : 0);
        n.putString("Result", result); n.putIntArray("Board", board);
        n.putLong("HandId", handId); n.putIntArray("Winners", new ArrayList<>(winningSeats));
        n.putLong("EventId", eventId); n.put("Events", events.copy()); n.putLong("TurnStarted", actionStarted);
        Player me = player(viewer); n.putInt("MySeat", me == null ? -1 : me.seat);
        n.putLong("ToCall", toCall(me)); n.putBoolean("CanRaise", canRaise(me));
        NbtList list = new NbtList();
        for (Player p : players()) {
            NbtCompound e = new NbtCompound(); e.putString("Name", p.name); e.putInt("Seat", p.seat); e.putLong("Stack", p.stack);
            e.putLong("StreetBet", p.streetBet); e.putBoolean("InHand", p.inHand); e.putBoolean("Folded", p.folded); e.putBoolean("Available", p.available);
            if (p.id.equals(viewer) || ((street == Street.SHOWDOWN || street == Street.FINISHED) && p.inHand && !p.folded)) {
                e.putInt("First", p.firstCard); e.putInt("Second", p.secondCard);
            }
            list.add(e);
        }
        n.put("Players", list); return n;
    }
}
