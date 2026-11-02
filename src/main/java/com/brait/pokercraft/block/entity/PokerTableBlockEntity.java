package com.brait.pokercraft.block.entity;

import com.brait.pokercraft.registry.ModBlockEntities;
import com.brait.pokercraft.entity.PokerSeatEntity;
import com.brait.pokercraft.network.PokerNetworking;
import com.brait.pokercraft.poker.PokerChips;
import com.brait.pokercraft.poker.PokerConfig;
import com.brait.pokercraft.poker.PokerGame;
import com.brait.pokercraft.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public final class PokerTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation PLACE_ANIMATION = RawAnimation.begin().thenPlay("animation.poker_table.place");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private PokerGame game = new PokerGame();
    private UUID host;
    private String hostName = "Unclaimed";
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, List<ItemStack>> owed = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private long savedVersion = -1;

    public PokerTableBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.POKER_TABLE, pos, state); }

    public PokerGame game() { return game; }
    public UUID host() { return host; }
    public boolean canUseSeat(int seat, UUID player) {
        PokerGame.Player registered = game.player(seat);
        return registered == null || registered.id.equals(player);
    }
    private boolean holdsHostChip(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(ModItems.HOST_CHIP) || player.getOffHandStack().isOf(ModItems.HOST_CHIP);
    }
    public boolean canAdmin(ServerPlayerEntity player) { return player.getUuid().equals(host); }

    public void open(ServerPlayerEntity player, boolean claim) {
        if (claim && host == null && holdsHostChip(player)) {
            host = player.getUuid(); hostName = player.getName().getString(); markDirty();
        }
        viewers.add(player.getUuid());
        send(player, true, claim && !canAdmin(player) ? "Only " + hostName + " can administer this table." : "");
    }

    private int seatedIndex(ServerPlayerEntity player) {
        return player.getVehicle() instanceof PokerSeatEntity seat && seat.getTablePos().equals(pos) ? seat.getSeatIndex() : -1;
    }

    public void handle(ServerPlayerEntity player, String command, NbtCompound data) {
        if (world == null || world.isClient || player.squaredDistanceTo(pos.toCenterPos()) > 100 || !player.isAlive()) return;
        if (command.equals("close")) { viewers.remove(player.getUuid()); lastRequest.remove(player.getUuid()); return; }
        long now = world.getTime();
        if (!command.equals("refresh")) {
            if (lastRequest.getOrDefault(player.getUuid(), Long.MIN_VALUE) == now) return;
            lastRequest.put(player.getUuid(), now);
        }
        viewers.add(player.getUuid());
        String error = "";
        try {
            switch (command) {
                case "admin" -> { requireAdmin(player); send(player, true, ""); return; }
                case "refresh" -> { }
                case "join" -> game.join(player.getUuid(), player.getName().getString(), seatedIndex(player));
                case "buyin" -> {
                    PokerGame.Player registered = game.player(player.getUuid());
                    if (seatedIndex(player) < 0 || registered == null) throw new IllegalArgumentException("Sit down and join first.");
                    long amount = PokerChips.buyInAmount(player, game.config(), registered.stack);
                    if (amount == 0) throw new IllegalArgumentException("No chips available, or your stack is already at the maximum buy-in.");
                    game.deposit(player.getUuid(), amount);
                    deliver(player, PokerChips.withdraw(player, amount, game.config()));
                }
                case "leave" -> issuePayout(player.getUuid(), game.leave(player.getUuid()));
                case "configure" -> { requireAdmin(player); game.configure(PokerConfig.fromNbt(data.getCompound("Config"))); }
                case "start" -> { requireAdmin(player); updateAvailability(now); game.start(now); }
                case "next" -> { requireAdmin(player); updateAvailability(now); game.startHand(now); }
                case "stop" -> { requireAdmin(player); game.stop(); }
                case "release" -> {
                    requireAdmin(player);
                    if (game.running() || game.inHand() || !game.players().isEmpty()) throw new IllegalArgumentException("Everyone must leave before releasing host access.");
                    host = null; hostName = "Unclaimed";
                }
                case "fold", "call", "raise", "allin" -> {
                    PokerGame.Player p = game.player(player.getUuid());
                    if (p == null || seatedIndex(player) != p.seat) throw new IllegalArgumentException("Return to your stool to play.");
                    if (data.getLong("Version") != game.version()) throw new IllegalArgumentException("The table changed; choose your action again.");
                    PokerGame.Action action = switch (command) {
                        case "fold" -> PokerGame.Action.FOLD;
                        case "call" -> PokerGame.Action.CHECK_CALL;
                        case "raise" -> PokerGame.Action.RAISE_TO;
                        default -> PokerGame.Action.ALL_IN;
                    };
                    game.act(player.getUuid(), action, data.getLong("Amount"), now);
                }
                default -> throw new IllegalArgumentException("Unknown poker action.");
            }
        } catch (IllegalArgumentException exception) {
            error = exception.getMessage() == null ? "Invalid poker configuration or action." : exception.getMessage();
        }
        markDirty(); savedVersion = game.version();
        send(player, false, error); broadcast(player.getUuid());
    }

    private void requireAdmin(ServerPlayerEntity player) {
        if (!canAdmin(player)) throw new IllegalArgumentException("Only the registered host can do that.");
    }

    private void send(ServerPlayerEntity player, boolean open, String error) {
        if (!ServerPlayNetworking.canSend(player, PokerNetworking.Snapshot.ID)) return;
        NbtCompound view = game.view(player.getUuid(), world == null ? 0 : world.getTime());
        view.putBoolean("Open", open); view.putBoolean("Admin", canAdmin(player)); view.putString("Host", hostName);
        view.putBoolean("OpenAdmin", open && canAdmin(player));
        view.putLong("Version", game.version()); view.putInt("PhysicalSeat", seatedIndex(player)); view.putString("Error", error);
        view.putLong("InventoryChips", PokerChips.inventoryValue(player, game.config()));
        ServerPlayNetworking.send(player, new PokerNetworking.Snapshot(pos, view));
    }

    private void broadcast(UUID except) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        // Nearby spectators also receive a personalized, redacted snapshot for the cards on the table.
        for (ServerPlayerEntity nearby : serverWorld.getPlayers(p -> p.squaredDistanceTo(pos.toCenterPos()) <= 100)) viewers.add(nearby.getUuid());
        viewers.removeIf(id -> {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(id);
            if (player == null || player.getWorld() != world || player.squaredDistanceTo(pos.toCenterPos()) > 100) {
                lastRequest.remove(id); return true;
            }
            if (!id.equals(except)) send(player, false, "");
            return false;
        });
    }

    private void updateAvailability(long now) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        Map<UUID, Boolean> availability = new HashMap<>();
        for (PokerGame.Player p : game.players()) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(p.id);
            availability.put(p.id, player != null && player.getWorld() == world && player.isAlive() && seatedIndex(player) == p.seat);
        }
        game.setAvailability(availability, now);
    }

    public static void serverTick(World world, BlockPos ignoredPos, BlockState ignoredState, PokerTableBlockEntity table) {
        if (world.getTime() % 5 != 0) return;
        long now = world.getTime();
        table.updateAvailability(now); table.game.tick(now);
        if (!table.game.inHand()) {
            for (PokerGame.Player p : table.game.players()) {
                if (!p.available && (table.game.config().mode() == PokerConfig.Mode.CASH || !table.game.running())) {
                    table.issuePayout(p.id, table.game.leave(p.id));
                }
            }
            if (table.game.running() && now - table.game.handEnded() >= 160) {
                try { table.game.startHand(now); } catch (IllegalArgumentException ignored) { /* Wait for funded players to return. */ }
            }
        }
        if (world instanceof ServerWorld serverWorld) {
            for (UUID id : new ArrayList<>(table.owed.keySet())) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(id);
                if (player != null && player.isAlive()) {
                    table.deliver(player, table.owed.remove(id)); table.markDirty();
                }
            }
        }
        if (table.savedVersion != table.game.version()) {
            table.savedVersion = table.game.version(); table.markDirty(); table.broadcast(null);
        } else if (now % 20 == 0) table.broadcast(null);
    }

    private void issuePayout(UUID id, long value) {
        if (value <= 0 || !(world instanceof ServerWorld serverWorld)) return;
        List<ItemStack> chips = PokerChips.payout(value, game.config());
        ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(id);
        if (player == null || !player.isAlive()) owed.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(chips);
        else deliver(player, chips);
        markDirty();
    }

    private void deliver(ServerPlayerEntity player, List<ItemStack> chips) {
        for (ItemStack stack : chips) {
            // Avoid creative-mode insertion deleting an overflow payout.
            for (int slot = 0; slot < player.getInventory().main.size() && !stack.isEmpty(); slot++) {
                ItemStack existing = player.getInventory().getStack(slot);
                if (existing.isEmpty()) { player.getInventory().setStack(slot, stack.copy()); stack.setCount(0); }
                else if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                    int moved = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
                    if (moved > 0) { existing.increment(moved); stack.decrement(moved); }
                }
            }
            if (!stack.isEmpty()) {
                ItemEntity drop = new ItemEntity(player.getWorld(), player.getX(), player.getY() + .5, player.getZ(), stack);
                drop.setOwner(player.getUuid()); player.getWorld().spawnEntity(drop);
            }
        }
        player.getInventory().markDirty();
    }

    public void onBroken() {
        if (world == null || world.isClient) return;
        game.stop();
        if (game.config().mode() == PokerConfig.Mode.CASH) for (PokerGame.Player p : game.players()) issuePayout(p.id, game.leave(p.id));
        for (var entry : owed.entrySet()) for (ItemStack stack : entry.getValue()) {
            ItemEntity drop = new ItemEntity(world, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, stack);
            drop.setOwner(entry.getKey()); world.spawnEntity(drop);
        }
        owed.clear();
        if (world instanceof ServerWorld serverWorld) for (UUID id : viewers) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(id);
            if (player != null && ServerPlayNetworking.canSend(player, PokerNetworking.Snapshot.ID)) {
                NbtCompound n = new NbtCompound(); n.putBoolean("Removed", true);
                ServerPlayNetworking.send(player, new PokerNetworking.Snapshot(pos, n));
            }
        }
        viewers.clear();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries); nbt.put("Poker", game.save());
        if (host != null) nbt.putUuid("Host", host);
        nbt.putString("HostName", hostName);
        NbtList refunds = new NbtList();
        owed.forEach((id, stacks) -> {
            NbtCompound entry = new NbtCompound(); entry.putUuid("Player", id); NbtList items = new NbtList();
            for (ItemStack stack : stacks) items.add(stack.encode(registries));
            entry.put("Items", items); refunds.add(entry);
        });
        nbt.put("Refunds", refunds);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Poker")) game = PokerGame.load(nbt.getCompound("Poker"));
        host = nbt.containsUuid("Host") ? nbt.getUuid("Host") : null;
        hostName = nbt.contains("HostName") ? nbt.getString("HostName") : "Unclaimed";
        owed.clear();
        for (NbtElement element : nbt.getList("Refunds", NbtElement.COMPOUND_TYPE)) {
            NbtCompound e = (NbtCompound) element; List<ItemStack> items = new ArrayList<>();
            for (NbtElement item : e.getList("Items", NbtElement.COMPOUND_TYPE)) ItemStack.fromNbt(registries, item).ifPresent(items::add);
            owed.put(e.getUuid("Player"), items);
        }
        savedVersion = -1;
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // Private game state is sent only through the personalized poker snapshot.
        return new NbtCompound();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "placement", 0, state -> PlayState.STOP)
                .triggerableAnim("place", PLACE_ANIMATION));
    }

    public void playPlacementAnimation() {
        triggerAnim("placement", "place");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
