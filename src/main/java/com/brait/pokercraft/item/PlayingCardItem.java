package com.brait.pokercraft.item;

import com.brait.pokercraft.entity.ThrownPlayingCardEntity;
import com.brait.pokercraft.registry.ModEntities;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.util.math.random.Random;

import java.util.List;

public final class PlayingCardItem extends Item {
    public static final int FACE_COUNT = 54;
    private static final String[] RANKS = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
    private static final String[] SUITS = {"spades", "hearts", "diamonds", "clubs"};

    public PlayingCardItem(Settings settings) {
        super(settings);
    }

    public static int getFace(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelDataComponent.DEFAULT).value();
    }

    public static void randomizeFace(ItemStack stack, Random random) {
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(1 + random.nextInt(FACE_COUNT)));
    }

    public static Text getFaceName(ItemStack stack) {
        int face = getFace(stack);
        if (face < 1 || face > FACE_COUNT) return Text.translatable("tooltip.pokercraft.random_card");
        String name = face == 53 ? "red_joker" : face == 54 ? "black_joker"
                : RANKS[(face - 1) % 13] + "_of_" + SUITS[(face - 1) / 13];
        return Text.translatable("card.pokercraft." + name);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(getFaceName(stack).copy().formatted(Formatting.GRAY));
    }

    /** A loose card's identity remains fixed, including when returned to a deck. */
    public static void ensureFace(ItemStack stack, Random random) {
        int face = getFace(stack);
        if (face < 1 || face > FACE_COUNT) {
            randomizeFace(stack, random);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (!world.isClient) ensureFace(stack, world.random);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            ensureFace(stack, world.random);
            ThrownPlayingCardEntity card = new ThrownPlayingCardEntity(ModEntities.THROWN_PLAYING_CARD, world);
            card.setCardStack(stack);
            card.setOwner(player);
            card.setPosition(player.getX(), player.getEyeY() - .1, player.getZ());
            card.setRecoverable(!player.isInCreativeMode());
            card.setVelocity(player, player.getPitch(), player.getYaw(), 0, 1.6F, .5F);
            if (!world.spawnEntity(card)) return TypedActionResult.fail(stack);
            stack.decrementUnlessCreative(1, player);
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        player.getItemCooldownManager().set(this, 6);
        return TypedActionResult.success(stack, world.isClient);
    }
}
