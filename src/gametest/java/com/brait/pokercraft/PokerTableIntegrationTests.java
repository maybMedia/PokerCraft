package com.brait.pokercraft;

import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.poker.*;
import com.brait.pokercraft.registry.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class PokerTableIntegrationTests implements FabricGameTest {
    private static PokerTableBlockEntity table(TestContext c) {
        PokerTableBlockEntity table = new PokerTableBlockEntity(c.getAbsolutePos(new BlockPos(2, 2, 2)), ModBlocks.POKER_TABLE.getDefaultState());
        table.setWorld(c.getWorld()); return table;
    }

    private static ServerPlayerEntity creativePlayer(TestContext c) {
        var world = c.getWorld();
        var server = world.getServer();
        var data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "poker-test"), false);
        var player = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        c.assertTrue(channel.isActive(), "Mock player connection is active");
        server.getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.CREATIVE);
        return player;
    }

    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cappedBuyInsPreserveExcessAndMakeChange(TestContext c) {
        PokerTableBlockEntity table = table(c);
        table.game().configure(new PokerConfig(PokerConfig.Mode.CASH, PokerConfig.BlindSchedule.STATIC,
                100, 5, 50, 10, 0, 10, 1000, 10, 150, 10, 2, 45));
        ServerPlayerEntity player = creativePlayer(c);
        player.getInventory().clear();
        var seatPos = com.brait.pokercraft.block.PokerTableGeometry.seatPosition(table.getPos(), net.minecraft.util.math.Direction.NORTH, 0);
        player.setPosition(seatPos.add(0, 1, 0));
        c.assertTrue(com.brait.pokercraft.entity.PokerSeatEntity.sit(c.getWorld(), table.getPos(), net.minecraft.util.math.Direction.NORTH, 0, player), "Player sits to buy in");
        table.game().join(player.getUuid(), "Buyer", 0);
        player.getInventory().setStack(0, new ItemStack(ModItems.WHITE_CHIP, 3));
        table.handle(player, "buyin", new NbtCompound());
        PokerGame.Player buyer = table.game().player(player.getUuid());
        if (buyer == null) throw new AssertionError("Buyer must remain registered after buying in");
        c.assertEquals(buyer.stack, 150L, "An oversized inventory deposits only up to the maximum");
        c.assertEquals(PokerChips.inventoryValue(player, table.game().config()), 150L, "Excess value and change remain in the inventory, including in creative");
        c.assertEquals(player.getInventory().getStack(0).getCount(), 1, "Unneeded original chips are untouched");
        c.assertEquals(PokerChips.buyInAmount(player, table.game().config(), 125), 25L, "A top-up is capped by the existing stack too");
        player.stopRiding(); player.discard(); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hostOwnershipPersistsWithoutHoldingChip(TestContext c) {
        PokerTableBlockEntity table = table(c);
        ServerPlayerEntity host = creativePlayer(c);
        ServerPlayerEntity other = creativePlayer(c);
        host.setPosition(table.getPos().toCenterPos().add(0, 2, 0)); other.setPosition(host.getPos());
        host.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.HOST_CHIP));
        other.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.HOST_CHIP));
        table.open(host, true); table.open(other, true);
        c.assertEquals(table.host(), host.getUuid(), "First host-chip user owns this table");
        c.assertFalse(table.canAdmin(other), "Another host chip cannot take over a claimed table");
        NbtCompound config = PokerConfig.DEFAULT.toNbt(); config.putString("Mode", "LAST_MAN_STANDING");
        NbtCompound request = new NbtCompound(); request.put("Config", config);
        table.handle(other, "configure", request);
        c.assertEquals(table.game().config().mode(), PokerConfig.Mode.CASH, "Non-owner configuration is rejected");
        host.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        table.handle(host, "configure", request);
        c.assertEquals(table.game().config().mode(), PokerConfig.Mode.LAST_MAN_STANDING, "Owner can configure with empty hands");
        PokerTableBlockEntity loaded = table(c); loaded.read(table.createNbt(c.getWorld().getRegistryManager()), c.getWorld().getRegistryManager());
        c.assertEquals(loaded.host(), host.getUuid(), "Host access survives world persistence");
        c.assertTrue(loaded.toInitialChunkDataNbt(c.getWorld().getRegistryManager()).isEmpty(), "Public chunk data cannot expose the saved deck or hole cards");
        host.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        c.assertTrue(table.canAdmin(host), "Registered owner retains admin access without the Host Chip");
        c.assertTrue(loaded.canAdmin(host), "Loaded ownership permits chip-free access");
        host.discard(); other.discard(); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cashChipConversionsAreExactForCustomDenominations(TestContext c) {
        PokerConfig cfg = new PokerConfig(PokerConfig.Mode.CASH, PokerConfig.BlindSchedule.STATIC,
                2, 6, 14, 50, 6, 14, 100, 14, 10000, 1, 2, 45);
        for (long amount = 0; amount <= 1000; amount += 2) {
            long paid = PokerChips.payout(amount, cfg).stream().mapToLong(s -> (long) PokerChips.value(s, cfg) * s.getCount()).sum();
            c.assertEquals(paid, amount, "Every whole cash unit converts exactly into chip items");
        }
        var player = c.createMockPlayer(GameMode.SURVIVAL);
        player.getInventory().setStack(0, new ItemStack(ModItems.GREEN_CHIP, 2));
        player.getInventory().setStack(1, new ItemStack(ModItems.WHITE_CHIP, 3));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.RED_CHIP, 2));
        c.assertEquals(PokerChips.inventoryValue(player, cfg), 118L, "Inventory valuation includes the offhand");
        PokerChips.removeInventoryChips(player, cfg);
        c.assertEquals(PokerChips.inventoryValue(player, cfg), 0L, "Deposited chips are removed once");
        c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingTableRefundsPendingCashBetsExactlyOnce(TestContext c) {
        PokerTableBlockEntity table = table(c); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        table.game().join(a, "A", 0); table.game().join(b, "B", 1);
        table.game().deposit(a, 100); table.game().deposit(b, 100); table.game().start(0);
        table.game().act(a, PokerGame.Action.RAISE_TO, 50, 1);
        table.onBroken(); table.onBroken();
        long drops = c.getWorld().getEntitiesByClass(ItemEntity.class, new Box(table.getPos()).expand(2), e -> true).stream()
                .mapToLong(e -> (long) PokerChips.value(e.getStack(), PokerConfig.DEFAULT) * e.getStack().getCount()).sum();
        c.assertEquals(drops, 200L, "Destroying a table returns both banked stacks including unawarded wagers, once only");
        c.assertEquals(table.game().players().size(), 0, "Refunded accounts are cleared"); c.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadPlayersReceiveRefundsOnlyAfterTheyAreAlive(TestContext c) {
        PokerTableBlockEntity table = table(c);
        ServerPlayerEntity player = creativePlayer(c);
        player.getInventory().clear(); player.setHealth(0);
        table.game().join(player.getUuid(), "Dead player", 0); table.game().deposit(player.getUuid(), 100);
        c.runAtEveryTick(() -> {
            if (c.getWorld().getTime() % 5 != 0) return;
            PokerTableBlockEntity.serverTick(c.getWorld(), table.getPos(), table.getCachedState(), table);
            c.assertEquals(PokerChips.inventoryValue(player, PokerConfig.DEFAULT), 0L, "Do not deposit refunds into an inventory that is being lost on death");
            c.assertEquals(table.createNbt(c.getWorld().getRegistryManager()).getList("Refunds", NbtElement.COMPOUND_TYPE).size(), 1, "Refund remains saved while player is dead");
            player.setHealth(20);
            PokerTableBlockEntity.serverTick(c.getWorld(), table.getPos(), table.getCachedState(), table);
            c.assertEquals(PokerChips.inventoryValue(player, PokerConfig.DEFAULT), 100L, "Alive player receives the preserved refund");
            player.discard(); c.complete();
        });
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void disconnectedCashOutRemainsSavedUntilClaimed(TestContext c) {
        PokerTableBlockEntity table = table(c); UUID offline = UUID.randomUUID();
        table.game().join(offline, "Offline", 0); table.game().deposit(offline, 100);
        c.runAtEveryTick(() -> {
            if (c.getWorld().getTime() % 5 != 0) return;
            PokerTableBlockEntity.serverTick(c.getWorld(), table.getPos(), table.getCachedState(), table);
            NbtCompound saved = table.createNbt(c.getWorld().getRegistryManager());
            c.assertTrue(table.game().player(offline) == null, "Disconnected cash account vacates its seat");
            c.assertEquals(saved.getList("Refunds", NbtElement.COMPOUND_TYPE).size(), 1, "Offline chips are retained as a saved refund");
            PokerTableBlockEntity loaded = table(c); loaded.read(saved, c.getWorld().getRegistryManager());
            c.assertEquals(loaded.createNbt(c.getWorld().getRegistryManager()).getList("Refunds", NbtElement.COMPOUND_TYPE).size(), 1, "Refund survives reload");
            c.complete();
        });
    }
}
