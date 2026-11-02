package com.brait.pokercraft;

import com.brait.pokercraft.client.PokerAdminScreen;
import com.brait.pokercraft.client.PokerScreen;
import com.brait.pokercraft.client.PokerControls;
import com.brait.pokercraft.client.PokerTableRenderer;
import com.brait.pokercraft.client.PokerTableLightRenderer;
import com.brait.pokercraft.client.PokerCameraMotion;
import com.brait.pokercraft.client.PokerExperience;
import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import com.brait.pokercraft.registry.ModBlocks;
import com.brait.pokercraft.registry.ModItems;
import com.brait.pokercraft.poker.PokerGame;
import com.brait.pokercraft.poker.PokerConfig;
import com.brait.pokercraft.poker.PokerStackDisplay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

/** Development-only GUI/resource smoke test. This source set is never included in the mod JAR. */
public final class PokerClientSmokeTest implements ClientModInitializer {
    private int ticks, phase;
    private NbtCompound state;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("pokercraft.clientSmoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getOverlay() != null) return;
            try {
                if (++ticks < 15) return;
                if (phase == 0) {
                    verifyFaces(client);
                    PokerGame game = new PokerGame(new Random(7));
                    for (int i = 0; i < 6; i++) { UUID id = new UUID(0, i + 1); game.join(id, "Player " + (i + 1), i); game.deposit(id, 1000); }
                    game.start(0);
                    for (int i = 0; i < 6; i++) {
                        PokerGame.Player actor = game.player(game.actor());
                        if (actor == null) throw new AssertionError("Expected an acting player while preparing the table preview");
                        game.act(actor.id, PokerGame.Action.CHECK_CALL, 0, i + 1);
                    }
                    state = game.view(new UUID(0, 1), 10); state.putBoolean("Admin", true); state.putInt("PhysicalSeat", 0);
                    state.putString("Host", "Test host"); state.putLong("InventoryChips", 250);
                    for (int[] size : new int[][]{{427, 240}, {640, 360}, {960, 540}}) {
                        PokerScreen screen = new PokerScreen(BlockPos.ORIGIN, state);
                        checkLayout(client, screen, size[0], size[1]);
                        checkLayout(client, new PokerAdminScreen(screen), size[0], size[1]);
                    }
                    verifyAdminFields(client);
                    verifyCameraMotion();
                    verifyTurnSound(state);
                    verifyHiddenCardModel(client);
                    for (var item : ModItems.ACTIONS.values()) {
                        var model = client.getItemRenderer().getModel(new net.minecraft.item.ItemStack(item), null, null, 0);
                        if (!model.getParticleSprite().getContents().getId().getPath().startsWith("item/action_"))
                            throw new AssertionError("Missing action item model: " + item);
                    }
                    client.setScreen(new PokerScreen(BlockPos.ORIGIN, state)); phase = 1; ticks = 0;
                } else if (phase == 1) {
                    screenshot(client, "poker-game.png");
                    client.setScreen(new PokerAdminScreen(new PokerScreen(BlockPos.ORIGIN, state))); phase = 2; ticks = 0;
                } else if (phase == 2) {
                    screenshot(client, "poker-admin.png");
                    client.setScreen(new TablePreview(state, 80)); phase = 3; ticks = 0;
                } else if (phase == 3) {
                    if (!(client.currentScreen instanceof TablePreview preview)) throw new AssertionError("Expected table preview for slot checks");
                    preview.verifyChips();
                    screenshot(client, "poker-table-slots.png");
                    client.setScreen(new TablePreview(state, 35)); phase = 4; ticks = 0;
                } else if (phase == 4) {
                    if (!(client.currentScreen instanceof TablePreview preview)) throw new AssertionError("Expected table preview for seated-view checks");
                    preview.verifyChips();
                    screenshot(client, "poker-table-seated.png");
                    client.setScreen(new HudPreview(state)); phase = 5; ticks = 0;
                } else if (phase == 5) {
                    screenshot(client, "poker-card-hud.png");
                    NbtCompound empty = state.copy(); empty.put("Players", new net.minecraft.nbt.NbtList());
                    client.setScreen(new TablePreview(empty, 60)); phase = 6; ticks = 0;
                } else if (phase == 6) {
                    if (!(client.currentScreen instanceof TablePreview preview)) throw new AssertionError("Expected table preview for empty-seat checks");
                    preview.verifyChips();
                    screenshot(client, "poker-table-empty.png");
                    client.setScreen(new LightPreview()); phase = 7; ticks = 0;
                } else if (phase == 7) {
                    if (!(client.currentScreen instanceof LightPreview preview)) throw new AssertionError("Expected hanging-light preview");
                    preview.verifyModel();
                    screenshot(client, "poker-table-light.png");
                    Files.writeString(report(client), "PASS: hanging light model, diffuser and inventory icon rendered; 480 live chip bones and empty-seat clearing; 54 card faces; camera and turn notifications; action icons; UI layouts; table and light screenshots.\n");
                    PokerCraft.LOGGER.info("Poker client smoke tests passed."); phase = 8; client.scheduleStop();
                }
            } catch (Throwable failure) {
                PokerCraft.LOGGER.error("Poker client smoke test failed", failure);
                try { Files.writeString(report(client), "FAIL: " + failure + "\n"); } catch (Exception ignored) { }
                phase = 8; client.scheduleStop();
            }
        });
    }

    private static Path report(MinecraftClient client) { return client.runDirectory.toPath().resolve("client-smoke-result.txt"); }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), text -> PokerCraft.LOGGER.info(text.getString()));
    }
    private static void renderTablePreview(BlockEntityRenderer<PokerTableBlockEntity> renderer,
                                           PokerTableBlockEntity table, DrawContext context) {
        renderer.render(table, 0, context.getMatrices(), context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
    }
    private static void checkLayout(MinecraftClient client, Screen screen, int width, int height) {
        screen.init(client, width, height);
        for (var child : screen.children()) if (child instanceof ClickableWidget widget) {
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width
                    || widget.getY() + widget.getHeight() > height) throw new AssertionError("Offscreen widget " + widget.getMessage() + " at " + width + "x" + height);
            if (widget instanceof TextFieldWidget field && Long.parseLong(field.getText()) < 0)
                throw new AssertionError("Negative configuration value in " + field.getMessage().getString());
        }
    }
    private static void verifyFaces(MinecraftClient client) {
        for (int face = 1; face <= 54; face++) {
            var model = client.getItemRenderer().getModel(PokerScreen.card(face), null, null, face);
            var contents = model.getParticleSprite().getContents();
            if (contents.getWidth() != 256 || contents.getHeight() != 256)
                throw new AssertionError("Face " + face + " does not use the 256px texture atlas");
            String texture = model.getParticleSprite().getContents().getId().toString();
            String expected = expectedFaceTexture(face);
            if (!texture.equals(expected)) throw new AssertionError("Face " + face + " resolves to " + texture + ", expected " + expected);
        }
    }

    private static String expectedFaceTexture(int face) {
        if (face == 1) return "pokercraft:item/playing_card";
        if (face >= 53) return "pokercraft:item/cards/" + (face == 53 ? "red_joker" : "black_joker");
        String[] ranks = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
        String[] suits = {"spades", "hearts", "diamonds", "clubs"};
        return "pokercraft:item/cards/" + ranks[(face - 1) % 13] + "_of_" + suits[(face - 1) / 13];
    }

    private static void verifyAdminFields(MinecraftClient client) {
        NbtCompound setup = new PokerGame().view(UUID.randomUUID(), 0);
        setup.putBoolean("Admin", true);
        PokerAdminScreen admin = new PokerAdminScreen(new PokerScreen(BlockPos.ORIGIN, setup));
        admin.init(client, 640, 360);
        fieldActive(admin, "White chip", true); fieldActive(admin, "Start stack", false);
        fieldActive(admin, "Blind interval", false); fieldActive(admin, "Blind factor", false);
        press(admin, "Mode:");
        fieldActive(admin, "White chip", false); fieldActive(admin, "Min buy-in", false);
        fieldActive(admin, "Start stack", true); fieldActive(admin, "Blind interval", false);
        press(admin, "Blinds:");
        fieldActive(admin, "Blind interval", true); fieldActive(admin, "Blind factor", true);
        setup.putBoolean("Running", true); admin.update(setup);
        fieldActive(admin, "Start stack", false); fieldActive(admin, "Turn seconds", false);
    }

    private static void verifyCameraMotion() {
        var motion = new PokerCameraMotion();
        var normal = new PokerCameraMotion.Pose(new net.minecraft.util.math.Vec3d(2, 2, 1), 179, 20);
        var top = new PokerCameraMotion.Pose(new net.minecraft.util.math.Vec3d(.5, 4, .5), -179, 90);
        motion.update(normal, null, 0);
        var start = motion.update(normal, top, 100);
        if (!start.position().equals(normal.position())) throw new AssertionError("Camera entry snaps");
        var middle = motion.update(normal, top, 225_000_100L);
        if (middle.position().equals(normal.position()) || middle.position().equals(top.position())) throw new AssertionError("Camera transition has no intermediate movement");
        if (Math.abs(middle.yaw() - normal.yaw()) > 2) throw new AssertionError("Camera takes the long route across the yaw boundary");
        var reverse = motion.update(normal, null, 225_000_100L);
        if (!reverse.position().equals(middle.position()) || reverse.yaw() != middle.yaw()) throw new AssertionError("Reversing the camera snaps");
        var restored = motion.update(normal, null, 675_000_100L);
        if (!restored.equals(normal) || motion.isActive()) throw new AssertionError("Camera exit does not restore the normal view");
        motion.update(normal, top, 700_000_100L);
        var arrived = motion.update(normal, top, 1_150_000_100L);
        if (!arrived.equals(top)) throw new AssertionError("Camera fails to reach its overhead target");
        motion.reset();
        if (!motion.update(normal, null, 2_000_000_000L).equals(normal) || motion.isActive()) throw new AssertionError("Camera state leaks between worlds");
    }
    private static void verifyTurnSound(NbtCompound state) {
        NbtCompound turn = state.copy(); turn.putInt("Actor", 0); turn.putInt("MySeat", 0); turn.putInt("PhysicalSeat", 0);
        if (!PokerExperience.beginsMyTurn(null, turn)) throw new AssertionError("Missing first turn chime");
        if (PokerExperience.beginsMyTurn(turn, turn.copy())) throw new AssertionError("Refresh repeats the turn chime");
        NbtCompound next = turn.copy(); next.putLong("TurnStarted", turn.getLong("TurnStarted") + 1);
        if (!PokerExperience.beginsMyTurn(turn, next)) throw new AssertionError("A new turn needs a chime");
        next.putInt("Actor", 1);
        if (PokerExperience.beginsMyTurn(turn, next)) throw new AssertionError("An opponent's turn must be silent");
        next.putInt("Actor", 0); next.putInt("PhysicalSeat", -1);
        if (PokerExperience.beginsMyTurn(turn, next)) throw new AssertionError("An unseated player must not hear the chime");
    }
    private static void verifyHiddenCardModel(MinecraftClient client) {
        var model = client.getItemRenderer().getModel(PokerScreen.card(0), null, null, 0);
        int surfaces = 0;
        for (var quad : model.getQuads(null, null, net.minecraft.util.math.random.Random.create(0))) {
            if (quad.getFace() != net.minecraft.util.math.Direction.UP && quad.getFace() != net.minecraft.util.math.Direction.DOWN) continue;
            surfaces++;
            var sprite = quad.getSprite();
            float min = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * .49F;
            int[] data = quad.getVertexData();
            int stride = data.length / 4;
            for (int i = 0; i < 4; i++) if (Float.intBitsToFloat(data[i * stride + 4]) < min)
                throw new AssertionError("Hidden card exposes front-face UVs on " + quad.getFace());
        }
        if (surfaces != 6) throw new AssertionError("Expected front and back surfaces for all three card elements");
    }
    private static void fieldActive(Screen screen, String label, boolean expected) {
        for (var child : screen.children()) if (child instanceof TextFieldWidget field && field.getMessage().getString().equals(label)) {
            if (field.active != expected) throw new AssertionError("Incorrect enabled state for " + label);
            return;
        }
        throw new AssertionError("Missing field " + label);
    }
    private static void press(Screen screen, String prefix) {
        for (var child : screen.children()) if (child instanceof ButtonWidget button && button.getMessage().getString().startsWith(prefix)) {
            button.onPress(); return;
        }
        throw new AssertionError("Missing button " + prefix);
    }

    private static final class TablePreview extends Screen {
        private final NbtCompound view;
        private final float angle;
        private final PokerTableRenderer renderer;
        private final PokerTableBlockEntity table = new PokerTableBlockEntity(BlockPos.ORIGIN, ModBlocks.POKER_TABLE.getDefaultState());
        TablePreview(NbtCompound view, float angle) {
            super(Text.literal("Table preview")); this.view = view.copy(); this.angle = angle;
            long[] balances = {990, 0, 100000, 13, 250, 1000};
            for (var e : this.view.getList("Players", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                NbtCompound p = (NbtCompound) e; p.putLong("Stack", balances[p.getInt("Seat")]);
            }
            renderer = new PokerTableRenderer(ignored -> this.view);
        }
        private void verifyChips() {
            int[][] expected = new int[6][4];
            var config = PokerConfig.fromNbt(view.getCompound("Config"));
            for (var e : view.getList("Players", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                NbtCompound p = (NbtCompound) e;
                for (var pile : PokerStackDisplay.piles(p.getLong("Stack"), config)) expected[p.getInt("Seat")][pile.color()] = pile.visibleCount();
            }
            for (int seat = 0; seat < 6; seat++) for (int color = 0; color < 4; color++) for (int level = 0; level < PokerStackDisplay.MAX_VISIBLE; level++) {
                String name = PokerStackDisplay.boneName(seat, color, level);
                var bone = renderer.getGeoModel().getBone(name).orElseThrow(() -> new AssertionError("Missing chip bone " + name));
                if (bone.isHidden() != (level >= expected[seat][color])) throw new AssertionError("Incorrect visible chip count for " + name);
            }
        }
        @Override public void renderBackground(DrawContext context, int x, int y, float delta) { }
        @Override public void render(DrawContext context, int x, int y, float delta) {
            context.fill(0, 0, width, height, 0xFF17271F);
            var matrices = context.getMatrices();
            matrices.push(); matrices.translate(width / 2F, height * .43F, 150);
            float scale = Math.min(width / 7F, (height - 110) / 4F);
            matrices.scale(scale, -scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
            matrices.translate(-.5, -.4, -.5);
            renderTablePreview(renderer, table, context);
            renderer.renderCards(table, view, 0, matrices, context.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop(); context.draw();
            context.drawCenteredTextWithShadow(textRenderer, "Table slots / private cards / action hotbar", width / 2, 10, 0xFFE9D99A);
            PokerControls.render(context, view);
        }
    }
    private static final class HudPreview extends Screen {
        private final NbtCompound view;
        HudPreview(NbtCompound view) { super(Text.literal("Poker card HUD")); this.view = view; }
        @Override public void renderBackground(DrawContext context, int x, int y, float delta) { }
        @Override public void render(DrawContext context, int x, int y, float delta) {
            context.fill(0, 0, width, height, 0xFF17271F);
            PokerExperience.render(context, BlockPos.ORIGIN, view, 0);
            PokerControls.render(context, view);
            context.drawCenteredTextWithShadow(textRenderer, "V: overhead table view   |   Right-click: use selected action", width / 2, height / 2, 0xFFD5E1D7);
        }
    }
    private static final class LightPreview extends Screen {
        private final PokerTableLightBlockEntity light = new PokerTableLightBlockEntity(BlockPos.ORIGIN, ModBlocks.POKER_TABLE_LIGHT.getDefaultState());
        LightPreview() { super(Text.literal("Hanging Poker Table Light")); }
        void verifyModel() {
            if (!(MinecraftClient.getInstance().getBlockEntityRenderDispatcher().get(light) instanceof PokerTableLightRenderer renderer))
                throw new AssertionError("Missing hanging-light renderer registration");
            for (String name : new String[]{"housing", "diffuser", "suspension", "ceiling_mount"})
                if (renderer.getGeoModel().getBone(name).isEmpty()) throw new AssertionError("Missing pendant bone " + name);
            if (renderer.getGeoModel().getBone("diffuser").orElseThrow().getCubes().size() != 5)
                throw new AssertionError("Missing rounded luminous diffuser panels");
        }
        @Override public void renderBackground(DrawContext context, int x, int y, float delta) { }
        @Override public void render(DrawContext context, int x, int y, float delta) {
            context.fill(0, 0, width, height, 0xFF33433D);
            context.draw();
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            var matrices = context.getMatrices();
            matrices.push(); matrices.translate(width / 2F, height * .52F, 200);
            float scale = Math.min(width / 4.5F, (height - 48) / 2.6F);
            matrices.scale(scale, -scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-25));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25));
            matrices.translate(-.5, -.25, -.5);
            MinecraftClient.getInstance().getBlockEntityRenderDispatcher().renderEntity(light, matrices,
                    context.getVertexConsumers(), LightmapTextureManager.pack(3, 3), OverlayTexture.DEFAULT_UV);
            matrices.pop(); context.draw();
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFE9D99A);
            context.drawCenteredTextWithShadow(textRenderer, "Ceiling mount / steel wires / luminous opal diffuser", width / 2, height - 22, 0xFFE0E6E0);
            matrices.push(); matrices.translate(width - 66, height - 66, 0); matrices.scale(2.4F, 2.4F, 1);
            context.drawItem(new net.minecraft.item.ItemStack(ModBlocks.POKER_TABLE_LIGHT), 0, 0); matrices.pop();
        }
    }
}
