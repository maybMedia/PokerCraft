package com.brait.pokercraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Shared result panel for the seated HUD and the table overview. */
public final class PokerShowdownView {
    private PokerShowdownView() {}

    public static boolean render(DrawContext context, BlockPos pos, NbtCompound state,
                                 int x, int y, int width, int height, float delta) {
        if (state.getBoolean("InHand") || height < 58) return false;
        List<NbtCompound> winners = new ArrayList<>();
        for (NbtElement e : state.getList("WinningHands", NbtElement.COMPOUND_TYPE)) {
            NbtCompound hand = (NbtCompound) e;
            if (PokerTableEffects.reveal(pos, hand.getInt("Seat"), 0, delta) >= 0) winners.add(hand);
        }
        if (winners.isEmpty()) return false;
        var client = MinecraftClient.getInstance();
        int rows = Math.min(winners.size(), height / 58), pages = (winners.size() + rows - 1) / rows;
        long tick = client.world == null ? 0 : client.world.getTime();
        int page = Math.floorMod(tick / 20, pages);
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= winners.size()) break;
            NbtCompound hand = winners.get(index);
            int top = y + row * 58;
            context.fill(x, top, x + width, top + 56, 0xE51A3026);
            context.fill(x, top, x + 2, top + 56, 0xFFE9D99A);
            String title = "Winner " + (index + 1) + "/" + winners.size() + ": " + hand.getString("Name")
                    + " - " + hand.getString("Category") + " - wins " + hand.getLong("Amount");
            context.drawCenteredTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(title, width - 12),
                    x + width / 2, top + 4, 0xFFE9D99A);
            int cardsX = x + (width - 204) / 2;
            context.drawTextWithShadow(client.textRenderer, "Hole cards", cardsX, top + 17, 0xFFBECBC1);
            context.drawTextWithShadow(client.textRenderer, "Best five", cardsX + 72, top + 17, 0xFFE9D99A);
            drawCard(context, hand.getInt("First"), cardsX + 12, top + 42);
            drawCard(context, hand.getInt("Second"), cardsX + 34, top + 42);
            int[] best = hand.getIntArray("BestFive");
            for (int i = 0; i < best.length; i++) drawCard(context, best[i], cardsX + 84 + i * 22, top + 42);
        }
        return true;
    }

    private static void drawCard(DrawContext context, int face, int x, int y) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 40);
        context.getMatrices().scale(2.7F, 2.7F, 1);
        context.getMatrices().translate(-8, -8, 0);
        context.drawItem(PokerScreen.card(face), 0, 0);
        context.getMatrices().pop();
    }
}
