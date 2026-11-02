package com.brait.pokercraft.client;

import com.brait.pokercraft.network.PokerNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import com.brait.pokercraft.entity.PokerSeatEntity;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

public final class PokerClient {
    private static final Map<BlockPos, NbtCompound> TABLES = new HashMap<>();
    private static ClientWorld lastWorld;
    private PokerClient() {}
    public static NbtCompound table(BlockPos pos) { return TABLES.get(pos); }
    public static BlockPos seatedTable() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || !player.isAlive() || !(player.getVehicle() instanceof PokerSeatEntity)) return null;
        return TABLES.entrySet().stream().filter(e -> e.getValue().getInt("PhysicalSeat") >= 0
                        && player.squaredDistanceTo(e.getKey().toCenterPos()) <= 100)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public static BlockPos nearestTable(boolean hostOnly) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        return TABLES.entrySet().stream().filter(e -> (!hostOnly || e.getValue().getBoolean("Admin"))
                        && player.squaredDistanceTo(e.getKey().toCenterPos()) <= 100)
                .map(Map.Entry::getKey).min(java.util.Comparator.comparingDouble(p -> player.squaredDistanceTo(p.toCenterPos()))).orElse(null);
    }
    public static void request(BlockPos pos, String command, NbtCompound data) {
        if (ClientPlayNetworking.canSend(PokerNetworking.Request.ID))
            ClientPlayNetworking.send(new PokerNetworking.Request(pos, command, data));
    }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (lastWorld != client.world) { TABLES.clear(); PokerTableEffects.clear(); PokerExperience.clear(); lastWorld = client.world; }
            PokerTableEffects.tick(client);
        });
        // Fabric invokes payload handlers on the client render thread.
        ClientPlayNetworking.registerGlobalReceiver(PokerNetworking.Snapshot.ID, (packet, ignoredContext) -> {
            var client = MinecraftClient.getInstance();
            if (packet.data().getBoolean("Removed")) {
                TABLES.remove(packet.pos());
                PokerTableEffects.remove(packet.pos());
                PokerExperience.remove(packet.pos());
                if (client.currentScreen instanceof PokerScreen screen && screen.pos().equals(packet.pos())) client.setScreen(null);
                else if (client.currentScreen instanceof PokerAdminScreen admin && admin.pos().equals(packet.pos())) client.setScreen(null);
                return;
            }
            PokerTableEffects.snapshot(packet.pos(), TABLES.get(packet.pos()), packet.data());
            PokerExperience.snapshot(packet.pos(), TABLES.get(packet.pos()), packet.data());
            TABLES.put(packet.pos(), packet.data());
            if (packet.pos().equals(seatedTable())) PokerControls.snapshot(packet.data());
            if (packet.data().getBoolean("Open")) {
                PokerScreen screen = new PokerScreen(packet.pos(), packet.data());
                client.setScreen(packet.data().getBoolean("OpenAdmin") ? new PokerAdminScreen(screen) : screen);
                return;
            }
            if (client.currentScreen instanceof PokerScreen screen && screen.pos().equals(packet.pos())) screen.update(packet.data());
            else if (client.currentScreen instanceof PokerAdminScreen admin && admin.pos().equals(packet.pos())) admin.update(packet.data());
        });
    }
}
