package com.brait.pokercraft.network;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public final class PokerNetworking {
    private PokerNetworking() {}
    public record Request(BlockPos pos, String command, NbtCompound data) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(PokerCraft.id("poker_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic((buf, p) -> {
            buf.writeBlockPos(p.pos); buf.writeString(p.command, 32); buf.writeNbt(p.data);
        }, buf -> new Request(buf.readBlockPos(), buf.readString(32), read(buf)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Snapshot(BlockPos pos, NbtCompound data) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(PokerCraft.id("poker_snapshot"));
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.ofStatic((buf, p) -> {
            buf.writeBlockPos(p.pos); buf.writeNbt(p.data);
        }, buf -> new Snapshot(buf.readBlockPos(), read(buf)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static NbtCompound read(RegistryByteBuf buf) { NbtCompound n = buf.readNbt(); return n == null ? new NbtCompound() : n; }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);
        // Fabric invokes payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (packet, context) -> {
            var player = context.player();
            var world = player.getServerWorld();
            if (player.squaredDistanceTo(packet.pos().toCenterPos()) > 100
                    || !world.getChunkManager().isChunkLoaded(packet.pos().getX() >> 4, packet.pos().getZ() >> 4)) return;
            if (world.getBlockEntity(packet.pos()) instanceof PokerTableBlockEntity table)
                table.handle(player, packet.command(), packet.data());
        });
    }
}
