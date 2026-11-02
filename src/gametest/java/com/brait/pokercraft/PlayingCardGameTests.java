package com.brait.pokercraft;

import com.brait.pokercraft.entity.ThrownPlayingCardEntity;
import com.brait.pokercraft.item.PlayingCardItem;
import com.brait.pokercraft.registry.ModEntities;
import com.brait.pokercraft.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.random.Random;
import net.minecraft.text.Text;

import java.util.BitSet;
import java.util.UUID;

public class PlayingCardGameTests implements FabricGameTest {
    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void looseCardStacksKeepTheirFaceWhenThrown(TestContext context) {
        Vec3d origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            PlayerEntity player = context.createMockPlayer(mode);
            mode.setAbilities(player.getAbilities());
            player.setPosition(origin);
            for (Hand hand : Hand.values()) {
                ItemStack stack = new ItemStack(ModItems.PLAYING_CARD, mode == GameMode.SURVIVAL ? 59 : 1);
                stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(2));
                player.setStackInHand(hand, stack);
                BitSet faces = new BitSet();
                for (int i = 0; i < 12; i++) {
                    int visibleFace = PlayingCardItem.getFace(stack);
                    faces.set(visibleFace);
                    ModItems.PLAYING_CARD.use(context.getWorld(), player, hand);
                    var thrown = context.getWorld().getEntitiesByClass(ThrownPlayingCardEntity.class,
                            new Box(origin, origin).expand(2), Entity::isAlive);
                    context.assertEquals(thrown.size(), 1, "One card leaves the stack per throw");
                    context.assertEquals(PlayingCardItem.getFace(thrown.getFirst().getCardStack()), visibleFace,
                            "Projectile keeps the face shown before throwing");
                    context.assertEquals(stack.getCount(), mode == GameMode.SURVIVAL ? 58 - i : 1,
                            "Only survival cards are consumed");
                    int nextFace = PlayingCardItem.getFace(stack);
                    ModItems.PLAYING_CARD.inventoryTick(stack, context.getWorld(), player, 0, hand == Hand.MAIN_HAND);
                    context.assertEquals(PlayingCardItem.getFace(stack), nextFace, "Next face stays stable between throws");
                    thrown.getFirst().discard();
                }
                context.assertEquals(faces.cardinality(), 1, "Loose cards retain their identity in " + mode + " / " + hand);
            }
        }
        context.complete();
    }

    private static ThrownPlayingCardEntity card(TestContext context, Vec3d position, Vec3d velocity) {
        ThrownPlayingCardEntity card = new ThrownPlayingCardEntity(ModEntities.THROWN_PLAYING_CARD, context.getWorld());
        card.setPosition(position);
        card.setVelocity(velocity);
        context.getWorld().spawnEntity(card);
        return card;
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void throwingConsumesSurvivalCardsButNotCreativeCards(TestContext context) {
        Vec3d origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            PlayerEntity player = context.createMockPlayer(mode);
            // Vanilla's mock overrides isCreative(), but does not initialize its ability flags.
            mode.setAbilities(player.getAbilities());
            player.setPosition(origin);
            for (Hand hand : Hand.values()) {
                ItemStack held = new ItemStack(ModItems.PLAYING_CARD);
                player.setStackInHand(hand, held);
                ModItems.PLAYING_CARD.inventoryTick(held, context.getWorld(), player, 0, hand == Hand.MAIN_HAND);
                int face = PlayingCardItem.getFace(held);
                context.assertTrue(ModItems.PLAYING_CARD.use(context.getWorld(), player, hand).getResult().isAccepted(), "Card should throw");
                context.assertEquals(player.getStackInHand(hand).getCount(), mode == GameMode.SURVIVAL ? 0 : 1, "Consumption depends on game mode");
                var cards = context.getWorld().getEntitiesByClass(ThrownPlayingCardEntity.class, new Box(origin, origin).expand(2), Entity::isAlive);
                context.assertEquals(cards.size(), 1, "One projectile per use");
                context.assertTrue(cards.getFirst().getOwner() == player, "Thrower owns the projectile");
                context.assertEquals(PlayingCardItem.getFace(cards.getFirst().getCardStack()), face, "Thrown face must match the card in either hand");
                context.assertTrue(cards.getFirst().getVelocity().length() > 1, "Projectile launches forward");
                context.assertTrue(player.getItemCooldownManager().isCoolingDown(ModItems.PLAYING_CARD), "Throw has a short cooldown");
                cards.getFirst().discard();
            }
        }
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void randomFacesAreAssignedOnceAndPersistInItems(TestContext context) {
        Random random = Random.create(372);
        BitSet faces = new BitSet();
        for (int i = 0; i < 1024; i++) {
            ItemStack stack = new ItemStack(ModItems.PLAYING_CARD);
            PlayingCardItem.ensureFace(stack, random);
            int face = PlayingCardItem.getFace(stack);
            context.assertTrue(face >= 1 && face <= PlayingCardItem.FACE_COUNT, "Face must have an existing model");
            faces.set(face);
            PlayingCardItem.ensureFace(stack, random);
            context.assertEquals(PlayingCardItem.getFace(stack), face, "Assigned face must not reroll");
            ItemStack loaded = ItemStack.fromNbt(context.getWorld().getRegistryManager(),
                    stack.encode(context.getWorld().getRegistryManager())).orElseThrow();
            context.assertEquals(PlayingCardItem.getFace(loaded), face, "Face survives item serialization");
        }
        context.assertEquals(faces.cardinality(), PlayingCardItem.FACE_COUNT, "Seeded draws exercise all 54 generated faces");
        context.assertEquals(new ItemStack(ModItems.PLAYING_CARD).getMaxCount(), 64, "Cards can be carried in stacks");
        ItemStack invalid = new ItemStack(ModItems.PLAYING_CARD);
        invalid.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(999));
        PlayingCardItem.ensureFace(invalid, random);
        context.assertTrue(PlayingCardItem.getFace(invalid) <= PlayingCardItem.FACE_COUNT, "Invalid face is repaired");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void collectedCardsKeepTheirFaceAfterProjectileReload(TestContext context) {
        BlockPos wall = context.getAbsolutePos(new BlockPos(1, 2, 3));
        context.getWorld().setBlockState(wall, Blocks.STONE.getDefaultState());
        ItemStack stack = new ItemStack(ModItems.PLAYING_CARD);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(54));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Lucky joker"));
        ThrownPlayingCardEntity card = card(context, Vec3d.ofCenter(wall).add(0, 0, -2), new Vec3d(0, 0, 3));
        card.setCardStack(stack);
        card.tick();
        NbtCompound saved = new NbtCompound();
        card.writeNbt(saved);
        card.discard();
        ThrownPlayingCardEntity loaded = new ThrownPlayingCardEntity(ModEntities.THROWN_PLAYING_CARD, context.getWorld());
        loaded.readNbt(saved);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        context.assertTrue(loaded.interact(player, Hand.MAIN_HAND).isAccepted(), "Embedded card can be collected");
        ItemStack collected = player.getInventory().getStack(0);
        context.assertEquals(PlayingCardItem.getFace(collected), 54, "Collected card keeps its original face");
        context.assertEquals(collected.getName().getString(), "Lucky joker", "Other item components are preserved too");
        context.assertTrue(loaded.isRemoved(), "Collecting removes the embedded card");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockImpactEmbedsAndSurvivesReload(TestContext context) {
        BlockPos wall = context.getAbsolutePos(new BlockPos(1, 2, 3));
        context.getWorld().setBlockState(wall, Blocks.STONE.getDefaultState());
        ThrownPlayingCardEntity card = card(context, Vec3d.ofCenter(wall).add(0, 0, -2), new Vec3d(0, 0, 3));
        ItemStack face = new ItemStack(ModItems.PLAYING_CARD);
        PlayingCardItem.ensureFace(face, Random.create(17));
        card.setCardStack(face);
        card.tick();
        context.assertTrue(card.isStuck(), "Fast card must hit the wall without tunnelling");
        context.assertEquals(card.getVelocity(), Vec3d.ZERO, "Embedded card stops moving");
        context.assertTrue(Math.abs(card.getZ() - (wall.getZ() - .12)) < .001, "Leading edge embeds in the wall");
        NbtCompound saved = new NbtCompound();
        card.writeNbt(saved);
        ThrownPlayingCardEntity loaded = new ThrownPlayingCardEntity(ModEntities.THROWN_PLAYING_CARD, context.getWorld());
        loaded.readNbt(saved);
        Vec3d position = loaded.getPos();
        loaded.tick();
        context.assertTrue(loaded.isStuck(), "Attachment survives saving and loading");
        context.assertEquals(loaded.getPos(), position, "Embedded card stays stationary");
        context.getWorld().setBlockState(wall, Blocks.AIR.getDefaultState());
        card.tick();
        context.assertTrue(card.isRemoved(), "Removing the support releases the card");
        var drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(position, position).expand(1),
                item -> item.getStack().isOf(ModItems.PLAYING_CARD));
        context.assertEquals(drops.size(), 1, "Released survival card drops once");
        context.assertEquals(PlayingCardItem.getFace(drops.getFirst().getStack()), PlayingCardItem.getFace(face), "Released card keeps its face");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mobCardsStayAtTheirActualImpactPoints(TestContext context) {
        Vec3d origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 2, 2)));
        var target = context.spawnMob(EntityType.ZOMBIE, new Vec3d(1.5, 2, 2.5));
        target.setPosition(origin);
        target.setAiDisabled(true);
        target.setNoGravity(true);
        target.setVelocity(Vec3d.ZERO);
        float health = target.getHealth();
        for (double height : new double[]{.25, 1, 1.65}) {
            target.setPosition(origin);
            target.bodyYaw = 0;
            Vec3d expected = new Vec3d(origin.x + .1, origin.y + height, target.getBoundingBox().minZ - .12);
            ThrownPlayingCardEntity card = card(context, origin.add(.1, height, -2), new Vec3d(0, 0, 3));
            card.tick();
            context.assertTrue(card.isStuck(), "Mob should catch the card at height " + height);
            context.assertTrue(card.getPos().distanceTo(expected) < .001,
                    "Card must embed at the surface intersection at height " + height + ", got " + card.getPos());
            Vec3d localOffset = expected.subtract(origin);
            target.setPosition(origin.add(2, .5, 0));
            target.bodyYaw = 90;
            card.tick();
            Vec3d moved = target.getPos().add(localOffset.rotateY(-(float) Math.PI / 2));
            context.assertTrue(card.getPos().distanceTo(moved) < .001, "Mob movement must preserve the original impact point");
            context.assertEquals(target.getHealth(), health, "Mob takes no damage");
            context.assertEquals(target.getVelocity(), Vec3d.ZERO, "Mob receives no knockback");
            card.discard();
        }
        target.discard();
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playerImpactDoesNoDamageAndFollowsMovementAndRotation(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "card-test"), false);
        ServerPlayerEntity target = new ServerPlayerEntity(server, world, data.gameProfile(), data.syncedOptions());
        var connection = new ClientConnection(NetworkSide.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        context.assertTrue(channel.isActive(), "Mock player connection is active");
        server.getPlayerManager().onPlayerConnect(connection, target, data);
        target.changeGameMode(GameMode.SURVIVAL);
        Vec3d origin = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 2, 2)));
        target.setPosition(origin);
        target.bodyYaw = 0;
        target.setVelocity(Vec3d.ZERO);
        float health = target.getHealth();
        ThrownPlayingCardEntity card = card(context, origin.add(0, 1, -2), new Vec3d(0, 0, 3));
        card.tick();
        context.assertTrue(card.isStuck(), "Card sticks into a player");
        context.assertTrue(Math.abs(card.getY() - (origin.y + 1)) < .001, "Player attachment preserves the impact height");
        context.assertTrue(Math.abs(card.getZ() - (target.getBoundingBox().minZ - .12)) < .001,
                "Player attachment preserves the surface intersection");
        context.assertEquals(target.getHealth(), health, "Impact must cause no damage");
        context.assertEquals(target.getVelocity(), Vec3d.ZERO, "Impact must cause no knockback");
        Vec3d offset = card.getPos().subtract(target.getPos());
        float yaw = card.getYaw();
        target.setPosition(origin.add(2, 0, 0));
        target.bodyYaw = 90;
        card.tick();
        Vec3d expected = target.getPos().add(offset.rotateY(-(float) Math.PI / 2));
        context.assertTrue(card.getPos().distanceTo(expected) < .001, "Attachment follows player translation and body rotation");
        context.assertTrue(Math.abs(card.getYaw() - (yaw - 90)) < .001, "Embedded orientation follows body rotation");
        context.assertEquals(target.getHealth(), health, "Attachment must remain harmless");
        NbtCompound saved = new NbtCompound();
        card.writeNbt(saved);
        ThrownPlayingCardEntity loaded = new ThrownPlayingCardEntity(ModEntities.THROWN_PLAYING_CARD, context.getWorld());
        loaded.readNbt(saved);
        loaded.tick();
        context.assertTrue(loaded.getPos().distanceTo(expected) < .001, "Saved attachment resolves the player by UUID");
        card.discard();
        target.discard();
        context.complete();
    }
}
