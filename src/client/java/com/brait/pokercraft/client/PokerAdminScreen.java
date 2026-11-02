package com.brait.pokercraft.client;

import com.brait.pokercraft.poker.PokerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PokerAdminScreen extends Screen {
    private final PokerScreen parent;
    private NbtCompound state;
    private final Map<String, TextFieldWidget> inputs = new LinkedHashMap<>();
    private PokerConfig.Mode mode;
    private PokerConfig.BlindSchedule schedule;
    private NbtCompound draft;
    private int left, top, panelWidth, panelHeight, fieldStart, fieldStep, backY;
    private String error = "";
    private ButtonWidget modeButton, scheduleButton, saveButton, startButton, nextButton, stopButton, releaseButton;
    public PokerAdminScreen(PokerScreen parent) {
        super(Text.literal("Poker table · Host controls")); this.parent = parent; this.state = parent.state();
        draft = state.getCompound("Config").copy();
        mode = PokerConfig.Mode.valueOf(draft.getString("Mode")); schedule = PokerConfig.BlindSchedule.valueOf(draft.getString("Schedule"));
    }
    public BlockPos pos() { return parent.pos(); }
    public void update(NbtCompound next) { state = next; parent.update(next); if (!next.getString("Error").isEmpty()) error = next.getString("Error"); updateFields(); }

    @Override protected void init() {
        panelWidth = Math.min(540, width - 16); panelHeight = Math.min(292, height - 8);
        left = (width - panelWidth) / 2; top = Math.max(4, (height - panelHeight) / 2);
        boolean compact = panelHeight < 280; fieldStart = compact ? 50 : 58; fieldStep = compact ? 19 : 23;
        int actionY = compact ? panelHeight - 65 : 202; backY = compact ? panelHeight - 41 : 229;
        int col = (panelWidth - 32) / 2;
        modeButton = button(modeLabel(), left + 12, top + 28, col, b -> { mode = mode == PokerConfig.Mode.CASH ? PokerConfig.Mode.LAST_MAN_STANDING : PokerConfig.Mode.CASH; b.setMessage(Text.literal(modeLabel())); updateFields(); });
        scheduleButton = button(scheduleLabel(), left + 20 + col, top + 28, col, b -> { schedule = PokerConfig.BlindSchedule.values()[(schedule.ordinal() + 1) % 3]; b.setMessage(Text.literal(scheduleLabel())); updateFields(); });
        field("White", "White chip", 0, 0, "Value of one white chip. Colours can have any value; all denominations must be multiples of the smallest chip.");
        field("Red", "Red chip", 1, 0, "Cash value of one red chip.");
        field("Blue", "Blue chip", 0, 1, "Cash value of one blue chip.");
        field("Green", "Green chip", 1, 1, "Cash value of one green chip.");
        field("Small", "Small blind", 0, 2, "Initial small blind. Set to 0 to play with a single big blind.");
        field("Big", "Big blind", 1, 2, "Initial big blind / minimum opening bet.");
        field("Starting", "Start stack", 0, 3, "Virtual starting stack for each last-man-standing player.");
        field("TurnSeconds", "Turn seconds", 1, 3, "After 10–300 seconds, the server checks if free, otherwise folds.");
        field("MinBuyIn", "Min buy-in", 0, 4, "Minimum funded stack in a cash game.");
        field("MaxBuyIn", "Max buy-in", 1, 4, "Maximum cash-game buy-in / top-up stack.");
        field("Interval", "Blind interval", 0, 5, "For timed blinds: seconds. For hand-based blinds: completed hands. Static ignores this.");
        field("Multiplier", "Blind factor", 1, 5, "Multiply both blinds by 2–10 at each level. Last man standing only; changes apply next hand.");
        int cell = (panelWidth - 36) / 4;
        saveButton = button("Save settings", left + 12, top + actionY, cell, b -> save());
        startButton = button("Start game", left + 16 + cell, top + actionY, cell, b -> send("start"));
        nextButton = button("Next hand", left + 20 + cell * 2, top + actionY, cell, b -> send("next"));
        stopButton = button("Stop / refund", left + 24 + cell * 3, top + actionY, cell, b -> send("stop"));
        button("Back to table", left + 12, top + backY, col, b -> close());
        releaseButton = button("Release host access", left + 20 + col, top + backY, col, b -> send("release"));
        updateFields();
    }
    private String modeLabel() { return mode == PokerConfig.Mode.CASH ? "Mode: Cash game" : "Mode: Last man standing"; }
    private String scheduleLabel() { return "Blinds: " + (mode == PokerConfig.Mode.CASH ? "Static" : switch (schedule) { case STATIC -> "Static"; case HANDS -> "Every N hands"; case TIME -> "Every N seconds"; }); }
    private ButtonWidget button(String label, int x, int y, int w, ButtonWidget.PressAction action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, 20).build());
    }
    private void updateFields() {
        if (saveButton == null) return;
        boolean admin = state.getBoolean("Admin"), running = state.getBoolean("Running"), hand = state.getBoolean("InHand");
        long balances = state.getLong("Pot");
        int ready = 0;
        for (var e : state.getList("Players", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) e;
            balances += p.getLong("Stack");
            if (p.getBoolean("Available") && (state.getCompound("Config").getString("Mode").equals("LAST_MAN_STANDING") && !running || p.getLong("Stack") > 0)) ready++;
        }
        boolean editable = admin && !running && !hand && (!state.getCompound("Config").getString("Mode").equals("CASH") || balances == 0);
        boolean cash = mode == PokerConfig.Mode.CASH;
        for (var e : inputs.entrySet()) {
            boolean relevant = switch (e.getKey()) {
                case "White", "Red", "Blue", "Green", "MinBuyIn", "MaxBuyIn" -> cash;
                case "Starting" -> !cash;
                case "Interval", "Multiplier" -> !cash && schedule != PokerConfig.BlindSchedule.STATIC;
                default -> true;
            };
            e.getValue().active = editable && relevant;
            e.getValue().setEditable(editable && relevant);
            e.getValue().setEditableColor(0xE0E6E0);
            e.getValue().setUneditableColor(0x66756B);
            if (!e.getValue().active) e.getValue().setFocused(false);
        }
        modeButton.active = saveButton.active = editable;
        scheduleButton.active = editable && !cash;
        scheduleButton.setMessage(Text.literal(scheduleLabel()));
        startButton.active = admin && !running && !hand && ready >= 2;
        nextButton.active = admin && running && !hand && ready >= 2;
        stopButton.active = admin && (running || hand);
        releaseButton.active = admin && !running && !hand && state.getList("Players", net.minecraft.nbt.NbtElement.COMPOUND_TYPE).isEmpty();
    }
    private void field(String key, String label, int column, int row, String help) {
        int col = (panelWidth - 32) / 2, x = left + 12 + column * (col + 8), y = top + fieldStart + row * fieldStep;
        TextFieldWidget old = inputs.get(key);
        TextFieldWidget input = new TextFieldWidget(textRenderer, x + col - 78, y, 78, 18, Text.literal(label));
        input.setMaxLength(10); input.setText(old == null ? Long.toString(draft.getLong(key)) : old.getText());
        input.setTooltip(Tooltip.of(Text.literal(help))); inputs.put(key, input); addDrawableChild(input);
    }
    private void save() {
        NbtCompound n = draft.copy(); n.putString("Mode", mode.name()); n.putString("Schedule", schedule.name());
        try {
            for (var e : inputs.entrySet()) {
                if (!e.getValue().active) continue;
                long value = Long.parseLong(e.getValue().getText());
                if (e.getKey().equals("Small") || e.getKey().equals("Big") || e.getKey().equals("Starting")
                        || e.getKey().equals("MinBuyIn") || e.getKey().equals("MaxBuyIn")) n.putLong(e.getKey(), value);
                else n.putInt(e.getKey(), Math.toIntExact(value));
            }
            PokerConfig.fromNbt(n); draft = n;
            NbtCompound payload = new NbtCompound(); payload.put("Config", n); error = "";
            PokerClient.request(pos(), "configure", payload);
        } catch (IllegalArgumentException | ArithmeticException exception) { error = exception.getMessage() == null ? "Enter whole numbers in every field." : exception.getMessage(); }
    }
    private void send(String command) { error = ""; PokerClient.request(pos(), command, new NbtCompound()); }
    @Override public void close() {
        if (client != null) client.setScreen(client.world == null ? parent : null);
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The panel background is already painted; the default background would blur its labels.
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0101815); context.fill(left, top, left + panelWidth, top + panelHeight, 0xF01C3029);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 9, 0xE9D99A);
        String[] labels = {"White chip", "Red chip", "Blue chip", "Green chip", "Small blind", "Big blind", "Start stack", "Turn seconds", "Min buy-in", "Max buy-in", "Blind interval", "Blind factor"};
        int col = (panelWidth - 32) / 2;
        int labelIndex = 0;
        for (var input : inputs.values()) {
            int i = labelIndex++;
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(labels[i], col - 82),
                    left + 12 + (i % 2) * (col + 8), top + fieldStart + 5 + (i / 2) * fieldStep, input.active ? 0xD5E1D7 : 0x66756B);
        }
        String message = !state.getBoolean("Admin") ? "Only the registered host can administer this table." : error.isEmpty()
                ? "Save before starting. Stop and cash out all balances before changing chip values." : error;
        if (panelHeight < 280) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, panelWidth - 24), left + 12, top + backY + 24, error.isEmpty() ? 0xCCD9CF : 0xFF9990);
        else context.drawTextWrapped(textRenderer, Text.literal(message), left + 12, top + 257, panelWidth - 24, error.isEmpty() ? 0xCCD9CF : 0xFF9990);
        super.render(context, mouseX, mouseY, delta);
    }
}
