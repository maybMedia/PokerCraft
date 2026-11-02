package com.brait.pokercraft.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Local camera, card HUD, public announcements, and private turn notifications. */
public final class PokerExperience {
    private static final Map<BlockPos, Feedback> FEEDBACK = new HashMap<>();
    private static boolean overhead;
    private static float savedYaw, savedPitch;
    private static KeyBinding overheadKey;
    private static final class Feedback {
        final ArrayDeque<String> queue = new ArrayDeque<>();
        String current = "";
        long started;
    }
    private PokerExperience() {}
    public static void initialize() {
        overheadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pokercraft.overhead", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, "category.pokercraft"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (PokerClient.seatedTable() == null && overhead) setOverhead(false);
            while (overheadKey.wasPressed()) if (client.currentScreen == null && PokerClient.seatedTable() != null) setOverhead(!overhead);
            if (client.world == null) return;
            FEEDBACK.entrySet().removeIf(e -> client.player == null || client.player.squaredDistanceTo(e.getKey().toCenterPos()) > 16384);
            for (Feedback f : FEEDBACK.values()) {
                long age = client.world.getTime() - f.started;
                if (f.current.isEmpty() || age >= (f.queue.isEmpty() ? 75 : 32)) {
                    f.current = f.queue.isEmpty() ? "" : f.queue.removeFirst();
                    f.started = client.world.getTime();
                }
            }
        });
        HudRenderCallback.EVENT.register((context, ticks) -> {
            BlockPos pos = PokerClient.seatedTable();
            if (pos != null && !MinecraftClient.getInstance().options.hudHidden)
                render(context, pos, PokerClient.table(pos), ticks.getTickDelta(false));
        });
    }
    public static BlockPos cameraTable() { return overhead ? PokerClient.seatedTable() : null; }
    private static void setOverhead(boolean enabled) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            if (enabled) { savedYaw = player.getYaw(); savedPitch = player.getPitch(); }
            else { player.setYaw(savedYaw); player.setPitch(savedPitch); player.prevYaw = savedYaw; player.prevPitch = savedPitch; }
        }
        overhead = enabled;
    }
    public static void clear() { FEEDBACK.clear(); overhead = false; }
    public static void remove(BlockPos pos) { FEEDBACK.remove(pos); }

    public static boolean beginsMyTurn(NbtCompound before, NbtCompound after) {
        if (!after.getBoolean("InHand") || after.getInt("MySeat") < 0 || after.getInt("PhysicalSeat") != after.getInt("MySeat")
                || after.getInt("Actor") != after.getInt("MySeat")) return false;
        return before == null || !before.getBoolean("InHand") || before.getInt("Actor") != after.getInt("Actor")
                || before.getLong("TurnStarted") != after.getLong("TurnStarted") || before.getLong("HandId") != after.getLong("HandId")
                || !before.getString("Street").equals(after.getString("Street")) || before.getInt("PhysicalSeat") != after.getInt("PhysicalSeat");
    }
    public static void snapshot(BlockPos pos, NbtCompound previous, NbtCompound next) {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Feedback f = FEEDBACK.computeIfAbsent(pos, ignored -> new Feedback());
        if (previous != null) {
            boolean priority = false;
            for (NbtElement e : next.getList("Events", NbtElement.COMPOUND_TYPE)) {
                NbtCompound event = (NbtCompound) e;
                if (event.getLong("Id") <= previous.getLong("EventId")) continue;
                String message = event.getString("Message");
                if (!priority && (message.contains(" wins ") || message.contains("last player standing"))) {
                    f.queue.clear(); f.current = ""; priority = true;
                }
                f.queue.addLast(message);
            }
            while (f.queue.size() > 6) f.queue.removeFirst();
        }
        if (beginsMyTurn(previous, next))
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.15F, .22F));
    }

    public static void render(DrawContext context, BlockPos pos, NbtCompound state, float delta) {
        if (!state.getBoolean("Running") && !state.getBoolean("InHand") && state.getIntArray("Board").length == 0) return;
        var client = MinecraftClient.getInstance();
        int center = context.getScaledWindowWidth() / 2;
        context.getMatrices().push(); context.getMatrices().translate(0, 0, 600);
        Feedback feedback = FEEDBACK.get(pos);
        String announcement = feedback == null || feedback.current.isEmpty() ? state.getString("Result") : feedback.current;
        String line = client.textRenderer.trimToWidth(announcement, context.getScaledWindowWidth() - 24);
        int half = client.textRenderer.getWidth(line) / 2;
        float age = feedback == null || feedback.current.isEmpty() || client.world == null ? 20 : client.world.getTime() - feedback.started + delta;
        int enter = (int) (Math.max(0, 1 - age / 5) * -10);
        context.fill(center - half - 7, 6 + enter, center + half + 7, 22 + enter, 0xC015291F);
        context.drawCenteredTextWithShadow(client.textRenderer, line, center, 10 + enter, 0xFFE9D99A);
        int boardX = center - 115, handX = center + 52;
        context.drawTextWithShadow(client.textRenderer, "Community", boardX, 29, 0xFFBECBC1);
        context.drawTextWithShadow(client.textRenderer, "Your hand", handX, 29, 0xFFE9D99A);
        context.enableScissor(0, 41, context.getScaledWindowWidth(), 80);
        int[] board = state.getIntArray("Board");
        for (int i = 0; i < 5; i++) {
            int x = boardX + i * 29;
            if (i < board.length) drawCard(context, board[i], x, PokerTableEffects.board(pos, i, delta));
            else context.fill(x + 2, 44, x + 22, 74, 0x402D4A3B);
        }
        NbtCompound me = PokerControls.myPlayer(state);
        if (me.getBoolean("InHand")) {
            for (int i = 0; i < 2; i++) drawCard(context, me.getInt(i == 0 ? "First" : "Second"), handX + i * 29,
                    PokerTableEffects.hole(pos, me.getInt("Seat"), i, delta));
        }
        context.disableScissor();
        context.getMatrices().pop();
    }
    private static void drawCard(DrawContext context, int face, int x, float progress) {
        if (progress < 0 || face <= 0) return;
        float drop = (1 - progress) * -30;
        context.getMatrices().push();
        context.getMatrices().translate(x + 12, 60 + drop, 0);
        context.getMatrices().scale(3.1F, 3.1F, 1);
        context.getMatrices().translate(-8, -8, 0);
        context.drawItem(PokerScreen.card(face), 0, 0);
        context.getMatrices().pop();
    }
}
