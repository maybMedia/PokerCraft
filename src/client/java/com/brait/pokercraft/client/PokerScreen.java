package com.brait.pokercraft.client;

import com.brait.pokercraft.registry.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class PokerScreen extends Screen {
    private final BlockPos tablePos;
    private NbtCompound state;
    private TextFieldWidget raise;
    private ButtonWidget fold, call, allIn, bet, join, buyIn, leave, admin;
    private int left, top, panelWidth, panelHeight, actionY, lowerY, rowStart, rowStep, ticks;
    private boolean waiting;
    private String error = "";
    private ItemStack hoveredCard = ItemStack.EMPTY;
    private int renderMouseX, renderMouseY;

    public PokerScreen(BlockPos pos, NbtCompound state) { super(Text.literal("PokerCraft · No-limit Hold’em")); this.tablePos = pos; this.state = state; }
    public BlockPos pos() { return tablePos; }
    public NbtCompound state() { return state; }
    public void update(NbtCompound next) {
        long previousMin = Math.max(1, state.getLong("MinRaiseTo"));
        state = next; waiting = false;
        if (raise != null && !raise.isFocused() && raise.getText().equals(Long.toString(previousMin)))
            raise.setText(Long.toString(Math.max(1, state.getLong("MinRaiseTo"))));
        if (!next.getString("Error").isEmpty()) error = next.getString("Error");
        updateButtons();
    }

    @Override protected void init() {
        panelWidth = Math.min(540, width - 16); panelHeight = Math.min(300, height - 8);
        left = (width - panelWidth) / 2; top = Math.max(4, (height - panelHeight) / 2);
        boolean compact = panelHeight < 270;
        rowStart = compact ? 64 : 80;
        rowStep = Math.clamp((panelHeight - rowStart - 96) / 6, 12, 18);
        // Reserve a separate line and padding below all six seat rows.
        actionY = rowStart + 6 * rowStep + 26;
        lowerY = actionY + 26;
        int gap = 4, cell = (panelWidth - 24 - gap * 4) / 5, y = top + actionY;
        fold = button("Fold", left + 12, y, cell, () -> send(state.getBoolean("CanShow") ? "muck" : "fold"));
        call = button("Check", left + 12 + (cell + gap), y, cell, () -> send(state.getBoolean("CanShow") ? "show" : "call"));
        allIn = button("All-in", left + 12 + (cell + gap) * 2, y, cell, () -> send("allin"));
        raise = new TextFieldWidget(textRenderer, left + 12 + (cell + gap) * 3, y, cell, 20, Text.literal("Raise total"));
        raise.setMaxLength(10); raise.setText(Long.toString(Math.max(1, state.getLong("MinRaiseTo"))));
        raise.setTooltip(Tooltip.of(Text.literal("Total street bet after raising, not an additional amount."))); addDrawableChild(raise);
        bet = button("Raise to", left + 12 + (cell + gap) * 4, y, cell, () -> send("raise"));
        cell = (panelWidth - 36) / 4; y = top + lowerY;
        join = button("Join", left + 12, y, cell, () -> send("join"));
        buyIn = button("Buy in", left + 16 + cell, y, cell, () -> send("buyin"));
        leave = button("Leave", left + 20 + cell * 2, y, cell, () -> send("leave"));
        leave.setTooltip(Tooltip.of(Text.literal("Leave between hands. Cash balances are returned as physical chips.")));
        admin = button("Admin", left + 24 + cell * 3, y, cell, () -> {
            if (client != null) client.setScreen(new PokerAdminScreen(this));
        });
        updateButtons();
    }

    private ButtonWidget button(String text, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> action.run()).dimensions(x, y, w, 20).build());
    }
    private void send(String command) {
        NbtCompound data = new NbtCompound(); data.putLong("Version", state.getLong("Version"));
        if (command.equals("raise")) {
            try { data.putLong("Amount", Long.parseLong(raise.getText())); }
            catch (NumberFormatException exception) { error = "Enter a whole-number raise-to amount."; return; }
        }
        waiting = true; error = ""; updateButtons(); PokerClient.request(tablePos, command, data);
    }
    private void updateButtons() {
        if (fold == null) return;
        int mine = state.getInt("MySeat"); boolean hand = state.getBoolean("InHand");
        boolean turn = !waiting && hand && mine >= 0 && mine == state.getInt("Actor");
        boolean optionalShow = !waiting && state.getBoolean("CanShow");
        fold.active = call.active = turn || optionalShow;
        fold.setMessage(Text.literal(state.getBoolean("CanShow") ? "Muck" : "Fold"));
        call.setMessage(Text.literal(state.getBoolean("CanShow") ? "Show hand" : state.getLong("ToCall") == 0 ? "Check" : "Call " + state.getLong("ToCall")));
        allIn.active = turn && (state.getBoolean("CanRaise") || myStack() <= state.getLong("ToCall"));
        bet.active = turn && state.getBoolean("CanRaise");
        join.active = !waiting && mine < 0 && state.getInt("PhysicalSeat") >= 0 && !hand;
        buyIn.active = !waiting && mine >= 0 && !hand && state.getCompound("Config").getString("Mode").equals("CASH")
                && myStack() < state.getCompound("Config").getLong("MaxBuyIn") && state.getLong("InventoryChips") > 0;
        NbtCompound config = state.getCompound("Config");
        buyIn.setTooltip(Tooltip.of(Text.literal("Deposit up to the maximum buy-in; excess chips stay in your inventory. Available: " + state.getLong("InventoryChips")
                + " value. Resulting stack: " + config.getLong("MinBuyIn") + "–" + config.getLong("MaxBuyIn")
                + ". White " + config.getInt("White") + ", red " + config.getInt("Red") + ", blue "
                + config.getInt("Blue") + ", green " + config.getInt("Green") + ".")));
        raise.setTooltip(Tooltip.of(Text.literal("Raise to a total street bet. Minimum: " + state.getLong("MinRaiseTo") + "; stack available: " + myStack() + ".")));
        leave.active = !waiting && mine >= 0 && !hand && (!state.getBoolean("Running") || state.getCompound("Config").getString("Mode").equals("CASH"));
        admin.active = state.getBoolean("Admin");
    }
    private long myStack() {
        for (NbtElement e : state.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) e; if (p.getInt("Seat") == state.getInt("MySeat")) return p.getLong("Stack");
        }
        return 0;
    }
    @Override public void tick() {
        if (++ticks % 20 == 0) PokerClient.request(tablePos, "refresh", new NbtCompound());
    }
    @Override public void close() {
        PokerClient.request(tablePos, "close", new NbtCompound());
        if (client != null) client.setScreen(null);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // This screen paints its own background before its content; avoid a second blur over the labels.
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        hoveredCard = ItemStack.EMPTY; renderMouseX = mouseX; renderMouseY = mouseY;
        context.fill(0, 0, width, height, 0xB0101815);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xF01C3029);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 8, 0xE9D99A);
        String mode = state.getCompound("Config").getString("Mode").equals("CASH") ? "Cash game" : "Last man standing";
        context.drawTextWithShadow(textRenderer, mode + " · " + state.getString("Street") + " · Hand " + (state.getInt("Hands") + (state.getBoolean("InHand") ? 1 : 0)), left + 12, top + 25, 0xD0DAD2);
        int[] board = state.getIntArray("Board"); int cardX = left + 12;
        for (int i = 0; i < 5; i++) drawCard(context, i < board.length ? board[i] : -1, cardX + i * 32, top + (panelHeight < 270 ? 35 : 41), 1.6F);
        context.drawTextWithShadow(textRenderer, "Pot: " + state.getLong("Pot"), left + panelWidth / 2, top + 43, 0xF6DF8C);
        context.drawTextWithShadow(textRenderer, "Blinds: " + state.getLong("SmallBlind") + "/" + state.getLong("BigBlind"), left + panelWidth / 2, top + 57, 0xE0E6E0);
        boolean results = PokerShowdownView.render(context, tablePos, state, left + 10, top + rowStart - 2,
                panelWidth - 20, 6 * rowStep, delta);
        if (!results) for (int seat = 0; seat < 6; seat++) {
            NbtCompound p = null;
            for (NbtElement e : state.getList("Players", NbtElement.COMPOUND_TYPE)) if (((NbtCompound) e).getInt("Seat") == seat) p = (NbtCompound) e;
            int row = top + rowStart + seat * rowStep; boolean acting = state.getBoolean("InHand") && state.getInt("Actor") == seat;
            context.fill(left + 10, row - 2, left + panelWidth - 10, row + rowStep - 3, acting ? 0x804F6C4A : 0x40111C17);
            if (p == null) { context.drawTextWithShadow(textRenderer, (seat + 1) + ". Empty stool", left + 14, row + 2, 0x82958A); continue; }
            String status = p.getBoolean("Folded") ? " · folded" : p.getBoolean("InHand") && p.getLong("Stack") == 0 ? " · all-in" : "";
            if (!p.getBoolean("Available")) status += " · away";
            String line = (seat + 1) + ". " + p.getString("Name") + (state.getInt("Dealer") == seat ? " [D]" : "")
                    + "  " + p.getLong("Stack") + "  bet " + p.getLong("StreetBet") + status;
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, panelWidth - 90), left + 14, row + 2, acting ? 0xFFF1A0 : 0xE0E6E0);
            if (p.getBoolean("InHand")) {
                boolean revealing = !state.getBoolean("InHand") && PokerTableEffects.reveal(tablePos, seat, 0, delta) < 0;
                drawCard(context, revealing && state.getInt("MySeat") != seat ? 0 : p.getInt("First"), left + panelWidth - 60, row - 1, rowStep < 18 ? .8F : 1F);
                drawCard(context, revealing && state.getInt("MySeat") != seat ? 0 : p.getInt("Second"), left + panelWidth - 36, row - 1, rowStep < 18 ? .8F : 1F);
            }
        }
        String turn = state.getBoolean("InHand") ? "Turn timer: " + state.getInt("SecondsLeft") + "s · Raise input is the total street bet."
                : state.getBoolean("CanShow") ? "Optional: Show hand to reveal your cards, or Muck to keep them private."
                : "Host: " + state.getString("Host") + " · Sit on a stool, then join.";
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(turn, panelWidth - 24), left + 12, top + actionY - 18, 0xBECBC1);
        super.render(context, mouseX, mouseY, delta);
        String message = error.isEmpty() ? state.getString("Result") : error;
        context.enableScissor(left + 12, top + lowerY + 24, left + panelWidth - 12, top + panelHeight - 6);
        if (panelHeight < 270) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, panelWidth - 24), left + 12, top + lowerY + 26, error.isEmpty() ? 0xD9E5DA : 0xFF9990);
        else context.drawTextWrapped(textRenderer, Text.literal(message), left + 12, top + lowerY + 26, panelWidth - 24, error.isEmpty() ? 0xD9E5DA : 0xFF9990);
        context.disableScissor();
        if (!hoveredCard.isEmpty()) context.drawItemTooltip(textRenderer, hoveredCard, mouseX, mouseY);
    }

    public static ItemStack card(int face) {
        if (face <= 0) return new ItemStack(ModItems.CARD_BACK);
        ItemStack stack = new ItemStack(ModItems.PLAYING_CARD);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(face)); return stack;
    }
    private void drawCard(DrawContext context, int face, int x, int y, float scale) {
        if (face <= 0) {
            int w = (int) (11 * scale), h = (int) (15 * scale);
            context.fill(x + 3, y + 1, x + 3 + w, y + 1 + h, face < 0 ? 0x404F6C54 : 0xFFD5C9A6);
            if (face == 0) context.fill(x + 4, y + 2, x + 2 + w, y + h, 0xFF872C37);
            return;
        }
        ItemStack stack = card(face);
        if (renderMouseX >= x && renderMouseX <= x + 18 * scale && renderMouseY >= y && renderMouseY <= y + 18 * scale) hoveredCard = stack;
        context.getMatrices().push(); context.getMatrices().translate(x - 4 * scale, y - 4 * scale, 0); context.getMatrices().scale(scale * 1.5F, scale * 1.5F, 1);
        context.drawItem(stack, 0, 0); context.getMatrices().pop();
    }
}
