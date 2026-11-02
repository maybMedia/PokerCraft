package com.brait.pokercraft.client;

import com.brait.pokercraft.registry.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/** Client-only action palette. Real inventory stacks and the selected inventory slot never change. */
public final class PokerControls {
    private static int selected;
    private static long raiseTo, lastVersion = -1;
    private static BlockPos lastTable;
    private static boolean waiting;
    private static long sentAt;
    private static String error = "";
    private static KeyBinding menu;

    private PokerControls() {}

    public static void initialize() {
        menu = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pokercraft.menu", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P, "category.pokercraft"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menu.wasPressed()) {
                if (client.currentScreen != null || client.player == null) continue;
                BlockPos pos = PokerClient.nearestTable(true);
                if (pos != null) PokerClient.request(pos, "admin", new NbtCompound());
            }
            BlockPos pos = PokerClient.seatedTable();
            var world = client.world;
            if (pos == null || world == null) { lastTable = null; waiting = false; return; }
            NbtCompound state = PokerClient.table(pos);
            if (!pos.equals(lastTable)) { lastTable = pos; selected = 6; raiseTo = 0; lastVersion = -1; waiting = false; error = ""; }
            if (state.getLong("Version") != lastVersion) {
                lastVersion = state.getLong("Version");
                raiseTo = state.getLong("MinRaiseTo");
            }
            raiseTo = clampRaise(raiseTo, state);
            if (waiting && world.getTime() - sentAt > 60) waiting = false;
        });
    }

    public static boolean active() { return PokerClient.seatedTable() != null; }
    public static void select(int slot) { selected = Math.floorMod(slot, 9); }
    public static void scroll(double amount) { if (amount != 0) select(selected - (amount > 0 ? 1 : -1)); }

    public static void snapshot(NbtCompound state) {
        waiting = false;
        error = state.getString("Error");
    }

    public static void consumeInventoryKeys(MinecraftClient client) {
        if (!active() || client.currentScreen != null) return;
        for (int i = 0; i < 9; i++) while (client.options.hotbarKeys[i].wasPressed()) select(i);
        // These keys must not manipulate the hidden inventory while the palette is shown.
        for (KeyBinding key : new KeyBinding[]{client.options.dropKey, client.options.swapHandsKey,
                client.options.inventoryKey, client.options.pickItemKey}) {
            // wasPressed() consumes each queued press; the loop deliberately performs no action.
            //noinspection StatementWithEmptyBody
            while (key.wasPressed()) { }
        }
    }

    private static String action(int slot, NbtCompound state) {
        if (state.getBoolean("CanShow")) {
            if (slot == 0) return "muck";
            if (slot == 1) return "show";
        }
        return switch (slot) {
            case 0 -> "fold"; case 1 -> "call"; case 2 -> "raise"; case 3 -> "minus";
            case 4 -> "plus"; case 5 -> "allin";
            case 6 -> state.getInt("MySeat") < 0 || !state.getCompound("Config").getString("Mode").equals("CASH") ? "join" : "buyin";
            case 7 -> state.getBoolean("Admin") ? "admin" : "info";
            default -> "leave";
        };
    }

    public static boolean enabled(int slot, NbtCompound s) {
        boolean hand = s.getBoolean("InHand"), cash = s.getCompound("Config").getString("Mode").equals("CASH");
        boolean turn = !waiting && hand && s.getInt("MySeat") >= 0 && s.getInt("MySeat") == s.getInt("Actor");
        return switch (slot) {
            case 0, 1 -> turn || !waiting && s.getBoolean("CanShow");
            case 2, 3, 4 -> turn && s.getBoolean("CanRaise") && maxRaise(s) >= s.getLong("MinRaiseTo");
            case 5 -> turn && (s.getBoolean("CanRaise") || myPlayer(s).getLong("Stack") <= s.getLong("ToCall"));
            case 6 -> !waiting && !hand && (s.getInt("MySeat") < 0
                    ? !s.getBoolean("Running") || cash : cash && buyInAmount(s) > 0);
            case 7 -> true;
            default -> !waiting && !hand && s.getInt("MySeat") >= 0 && (!s.getBoolean("Running") || cash);
        };
    }

    public static NbtCompound myPlayer(NbtCompound s) {
        for (NbtElement e : s.getList("Players", NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) e;
            if (p.getInt("Seat") == s.getInt("MySeat")) return p;
        }
        return new NbtCompound();
    }
    private static long maxRaise(NbtCompound s) { NbtCompound p = myPlayer(s); return p.getLong("Stack") + p.getLong("StreetBet"); }
    private static long clampRaise(long amount, NbtCompound state) {
        long minimum = state.getLong("MinRaiseTo");
        // Keep the minimum displayed when the player's stack is too short to raise.
        return Math.clamp(amount, minimum, Math.max(minimum, maxRaise(state)));
    }
    private static long buyInAmount(NbtCompound s) {
        return Math.clamp(s.getCompound("Config").getLong("MaxBuyIn") - myPlayer(s).getLong("Stack"), 0L, s.getLong("InventoryChips"));
    }

    public static void activate() {
        MinecraftClient client = MinecraftClient.getInstance();
        var world = client.world;
        BlockPos pos = PokerClient.seatedTable();
        if (pos == null || world == null || client.currentScreen != null) return;
        NbtCompound state = PokerClient.table(pos);
        if (!enabled(selected, state)) return;
        String command = action(selected, state);
        if (command.equals("info")) { client.setScreen(new PokerScreen(pos, state)); return; }
        if (selected == 3 || selected == 4) {
            long unit = com.brait.pokercraft.poker.PokerConfig.fromNbt(state.getCompound("Config")).unit();
            // Fine adjustment by one chip unit; sprint key increases by one big blind.
            long step = client.options.sprintKey.isPressed() ? Math.max(unit, state.getLong("BigBlind")) : unit;
            raiseTo = clampRaise(raiseTo + (selected == 3 ? -step : step), state);
            return;
        }
        NbtCompound data = new NbtCompound(); data.putLong("Version", state.getLong("Version"));
        if (command.equals("raise")) data.putLong("Amount", raiseTo);
        waiting = true; sentAt = world.getTime(); error = "";
        PokerClient.request(pos, command, data);
    }

    private static String label(int slot, NbtCompound s) {
        return switch (slot) {
            case 0 -> s.getBoolean("CanShow") ? "Muck hand (keep private)" : "Fold";
            case 1 -> s.getBoolean("CanShow") ? "Show hand to the table" : s.getLong("ToCall") == 0 ? "Check" : "Call " + s.getLong("ToCall");
            case 2 -> "Raise to " + raiseTo;
            case 3 -> "Lower raise: " + raiseTo;
            case 4 -> "Increase raise: " + raiseTo;
            case 5 -> "All-in";
            case 6 -> s.getInt("MySeat") < 0 ? "Join table" : s.getCompound("Config").getString("Mode").equals("CASH")
                    ? "Buy in: " + buyInAmount(s) : "Joined: ready for the host";
            case 7 -> s.getBoolean("Admin") ? "Host controls" : "Table overview";
            default -> "Leave / cash out";
        };
    }

    public static void render(DrawContext context) {
        BlockPos pos = PokerClient.seatedTable();
        if (pos != null) render(context, PokerClient.table(pos));
    }

    /** Also used by the development screenshot test. */
    public static void render(DrawContext context, NbtCompound state) {
        MinecraftClient client = MinecraftClient.getInstance();
        int center = context.getScaledWindowWidth() / 2, bottom = context.getScaledWindowHeight();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        int left = center - 91, y = bottom - 23;
        context.fill(left - 1, y - 1, left + 183, bottom, 0xED13241E);
        for (int slot = 0; slot < 9; slot++) {
            int x = left + 2 + slot * 20;
            context.fill(x - 1, y, x + 18, y + 21, selected == slot ? 0xFFE9D99A : 0xFF43594B);
            context.fill(x, y + 1, x + 17, y + 20, 0xFF1C3029);
            context.drawItem(new ItemStack(ModItems.ACTIONS.get(action(slot, state))), x + 1, y + 2);
            context.getMatrices().push(); context.getMatrices().translate(0, 0, 200);
            if (!enabled(slot, state)) context.fill(x, y + 1, x + 17, y + 20, 0xBB17221B);
            context.drawTextWithShadow(client.textRenderer, Integer.toString(slot + 1), x + 1, y + 13, 0xFFBCCABF);
            context.getMatrices().pop();
        }
        String status = state.getBoolean("InHand") ? "Pot " + state.getLong("Pot") + "  |  "
                + (state.getInt("Actor") == state.getInt("MySeat") ? "Your turn" : "Waiting") + "  " + state.getInt("SecondsLeft") + "s"
                : state.getString("Result");
        line(context, status, center, bottom - 81, 0xFFE9D99A);
        line(context, "Stack " + myPlayer(state).getLong("Stack") + "  |  " + label(selected, state), center, bottom - 67,
                enabled(selected, state) ? 0xFFFFFFFF : 0xFF87978C);
        String hint = state.getBoolean("CanShow") ? "Optional reveal: slot 2 shows your hand / slot 1 mucks it"
                : selected == 3 || selected == 4 ? "Right-click: adjust  |  Ctrl: larger steps  |  Slot 3: confirm"
                : "1-9 / scroll: select  |  Right-click: use  |  V: top view";
        line(context, error.isEmpty() ? hint : error,
                center, bottom - 53, error.isEmpty() ? 0xFFBCCABF : 0xFFFF9990);
        context.getMatrices().pop();
    }
    private static void line(DrawContext context, String text, int x, int y, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        String clipped = font.trimToWidth(text, context.getScaledWindowWidth() - 16);
        int half = font.getWidth(clipped) / 2;
        context.fill(x - half - 4, y - 2, x + half + 4, y + 10, 0xB013241E);
        context.drawCenteredTextWithShadow(font, clipped, x, y, color);
    }
}
