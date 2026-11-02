package com.brait.pokercraft.item;

import com.brait.pokercraft.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** A portable deck whose component saves each stored card, including its face and name. */
public final class CardDeckItem extends Item {
    public static final int CAPACITY = PlayingCardItem.FACE_COUNT;

    public CardDeckItem(Settings settings) {
        super(settings.maxCount(1).component(DataComponentTypes.CONTAINER, fullDeck()));
    }

    private static ContainerComponent fullDeck() {
        List<ItemStack> cards = new ArrayList<>();
        for (int face = 1; face <= CAPACITY; face++) {
            ItemStack card = new ItemStack(ModItems.PLAYING_CARD);
            card.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(face));
            cards.add(card);
        }
        return ContainerComponent.fromStacks(cards);
    }

    private static List<ItemStack> cards(ItemStack deck) {
        return new ArrayList<>(deck.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT)
                .streamNonEmpty().map(ItemStack::copy).toList());
    }

    public static int getCardCount(ItemStack deck) {
        return deck.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT)
                .streamNonEmpty().mapToInt(ItemStack::getCount).sum();
    }

    /** Move loose cards into available deck slots, preserving their complete item data. */
    public static int storeCards(ItemStack deck, ItemStack source, Random random) {
        if (!source.isOf(ModItems.PLAYING_CARD)) return 0;
        List<ItemStack> cards = cards(deck);
        int amount = Math.min(source.getCount(), CAPACITY - cards.size());
        if (amount <= 0) return 0;
        PlayingCardItem.ensureFace(source, random);
        for (int i = 0; i < amount; i++) cards.add(source.copyWithCount(1));
        source.decrement(amount);
        deck.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(cards));
        return amount;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack deck = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(deck, true);
        if (player.isSneaking()) {
            int stored = 0;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                stored += storeCards(deck, player.getInventory().getStack(slot), world.random);
            }
            if (stored == 0) {
                player.sendMessage(Text.translatable(getCardCount(deck) == CAPACITY
                        ? "message.pokercraft.deck_full" : "message.pokercraft.no_loose_cards"), true);
                return TypedActionResult.fail(deck);
            }
            player.getInventory().markDirty();
        } else {
            List<ItemStack> cards = cards(deck);
            if (cards.isEmpty()) {
                player.sendMessage(Text.translatable("message.pokercraft.deck_empty"), true);
                return TypedActionResult.fail(deck);
            }
            int index = world.random.nextInt(cards.size());
            ItemStack drawn = cards.get(index);
            // Check capacity first: creative inventory insertion otherwise silently deletes excess items.
            boolean hasRoom = player.getInventory().main.stream().anyMatch(stack -> stack.isEmpty()
                    || (ItemStack.areItemsAndComponentsEqual(stack, drawn) && stack.getCount() < stack.getMaxCount()));
            if (!hasRoom || !player.getInventory().insertStack(drawn.copy())) {
                player.sendMessage(Text.translatable("message.pokercraft.card_inventory_full"), true);
                return TypedActionResult.fail(deck);
            }
            cards.remove(index);
            deck.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(cards));
        }
        player.getItemCooldownManager().set(this, 4);
        return TypedActionResult.success(deck, false);
    }

    @Override
    public boolean onStackClicked(ItemStack deck, Slot slot, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) return false;
        ItemStack source = slot.getStack();
        if (!source.isEmpty() && !source.isOf(ModItems.PLAYING_CARD)) return false;
        if (player.getWorld().isClient) return true;
        if (!source.isEmpty()) {
            storeCards(deck, source, player.getWorld().random);
            slot.markDirty();
        } else {
            List<ItemStack> cards = cards(deck);
            if (!cards.isEmpty()) {
                int index = player.getRandom().nextInt(cards.size());
                ItemStack drawn = cards.get(index);
                if (slot.canInsert(drawn) && slot.insertStack(drawn.copy()).isEmpty()) {
                    cards.remove(index);
                    deck.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(cards));
                }
            }
        }
        return true;
    }

    @Override
    public boolean onClicked(ItemStack deck, ItemStack cursor, Slot slot, ClickType clickType,
                             PlayerEntity player, StackReference cursorReference) {
        if (clickType != ClickType.RIGHT || !slot.canTakePartial(player)) return false;
        if (!cursor.isEmpty() && !cursor.isOf(ModItems.PLAYING_CARD)) return false;
        if (player.getWorld().isClient) return true;
        if (!cursor.isEmpty()) {
            storeCards(deck, cursor, player.getWorld().random);
            if (cursor.isEmpty()) cursorReference.set(ItemStack.EMPTY);
        } else {
            List<ItemStack> cards = cards(deck);
            if (!cards.isEmpty()) {
                int index = player.getRandom().nextInt(cards.size());
                if (cursorReference.set(cards.get(index).copy())) {
                    cards.remove(index);
                    deck.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(cards));
                }
            }
        }
        slot.markDirty();
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.pokercraft.deck_count", getCardCount(stack), CAPACITY).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.pokercraft.deck_draw").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.pokercraft.deck_gather").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.pokercraft.deck_return").formatted(Formatting.GRAY));
    }
}
