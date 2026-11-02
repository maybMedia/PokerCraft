package com.brait.pokercraft;

import com.brait.pokercraft.item.CardDeckItem;
import com.brait.pokercraft.item.PlayingCardItem;
import com.brait.pokercraft.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ClickType;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.BitSet;

public class CardDeckGameTests implements FabricGameTest {
    private static ItemStack card(int face, int count) {
        ItemStack stack = new ItemStack(ModItems.PLAYING_CARD, count);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(face));
        return stack;
    }

    private static ItemStack emptyDeck() {
        ItemStack deck = new ItemStack(ModItems.CARD_DECK);
        deck.set(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);
        return deck;
    }

    private static PlayerEntity player(TestContext context, GameMode mode) {
        PlayerEntity player = context.createMockPlayer(mode);
        mode.setAbilities(player.getAbilities());
        return player;
    }

    @SuppressWarnings("unused") // Minecraft discovers GameTest methods through reflection.
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void freshDeckDrawsAll54FacesWithoutReplacement(TestContext context) {
        for (Hand hand : Hand.values()) {
            PlayerEntity player = player(context, GameMode.SURVIVAL);
            ItemStack deck = new ItemStack(ModItems.CARD_DECK);
            player.setStackInHand(hand, deck);
            context.assertEquals(CardDeckItem.getCardCount(deck), 54, "New deck contains 54 cards");
            BitSet faces = new BitSet();
            for (int i = 0; i < 54; i++) {
                context.assertTrue(ModItems.CARD_DECK.use(context.getWorld(), player, hand).getResult().isAccepted(), "Draw succeeds");
                ItemStack drawn = ItemStack.EMPTY;
                for (int slot = 0; slot < player.getInventory().size(); slot++) {
                    if (player.getInventory().getStack(slot).isOf(ModItems.PLAYING_CARD)) {
                        drawn = player.getInventory().removeStack(slot);
                        break;
                    }
                }
                context.assertFalse(drawn.isEmpty(), "Drawing gives a loose card");
                int face = PlayingCardItem.getFace(drawn);
                context.assertTrue(face >= 1 && face <= 54, "Drawn card has a valid face");
                context.assertFalse(faces.get(face), "A fresh deck never repeats a face");
                faces.set(face);
                context.assertEquals(CardDeckItem.getCardCount(deck), 53 - i, "Drawing consumes one stored card");
            }
            context.assertEquals(faces.cardinality(), 54, "All 52 standard cards and both jokers are present");
            context.assertFalse(ModItems.CARD_DECK.use(context.getWorld(), player, hand).getResult().isAccepted(), "Empty deck cannot create cards");
        }
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void returningCardsPreservesComponentsAndRespectsCapacity(TestContext context) {
        ItemStack deck = emptyDeck();
        ItemStack cards = card(25, 60);
        cards.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Lucky queen"));
        context.assertEquals(CardDeckItem.storeCards(deck, cards, context.getWorld().random), 54, "Only 54 cards fit");
        context.assertEquals(cards.getCount(), 6, "Overflow stays outside the deck");
        context.assertEquals(CardDeckItem.storeCards(deck, cards, context.getWorld().random), 0, "Full deck rejects more cards");
        ItemStack loaded = ItemStack.fromNbt(context.getWorld().getRegistryManager(),
                deck.encode(context.getWorld().getRegistryManager())).orElseThrow();
        context.assertEquals(CardDeckItem.getCardCount(loaded), 54, "Stored cards survive item serialization");
        ContainerComponent contents = loaded.get(DataComponentTypes.CONTAINER);
        if (contents == null) throw new AssertionError("Loaded deck must retain its stored-card container");
        for (ItemStack stored : contents.iterateNonEmptyCopy()) {
            context.assertEquals(PlayingCardItem.getFace(stored), 25, "Returned face is preserved");
            context.assertEquals(stored.getName().getString(), "Lucky queen", "Returned custom name is preserved");
            context.assertEquals(stored.getCount(), 1, "Each stored entry is one card");
        }
        ItemStack stone = new ItemStack(Items.STONE, 3);
        context.assertEquals(CardDeckItem.storeCards(emptyDeck(), stone, context.getWorld().random), 0, "Deck stores only playing cards");
        context.assertEquals(stone.getCount(), 3, "Unrelated items are untouched");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryRightClicksReturnAndDrawCards(TestContext context) {
        PlayerEntity player = player(context, GameMode.SURVIVAL);
        ItemStack deck = emptyDeck();
        SimpleInventory inventory = new SimpleInventory(4);
        inventory.setStack(0, card(54, 3));
        Slot source = new Slot(inventory, 0, 0, 0);
        context.assertTrue(ModItems.CARD_DECK.onStackClicked(deck, source, ClickType.RIGHT, player), "Deck on cursor accepts loose cards");
        context.assertTrue(inventory.getStack(0).isEmpty(), "Returned cards leave the source slot");
        context.assertEquals(CardDeckItem.getCardCount(deck), 3, "All three loose cards are stored");
        Slot blocked = new Slot(inventory, 1, 0, 0) {
            @Override public boolean canInsert(ItemStack stack) { return false; }
        };
        ModItems.CARD_DECK.onStackClicked(deck, blocked, ClickType.RIGHT, player);
        context.assertEquals(CardDeckItem.getCardCount(deck), 3, "Rejected extraction cannot lose cards");
        ModItems.CARD_DECK.onStackClicked(deck, new Slot(inventory, 1, 0, 0), ClickType.RIGHT, player);
        context.assertEquals(PlayingCardItem.getFace(inventory.getStack(1)), 54, "Empty slot receives the actual card");
        context.assertEquals(CardDeckItem.getCardCount(deck), 2, "Inventory extraction consumes one stored card");
        inventory.setStack(2, deck);
        Slot deckSlot = new Slot(inventory, 2, 0, 0);
        inventory.setStack(3, card(54, 1));
        StackReference cursor = StackReference.of(inventory, 3);
        ModItems.CARD_DECK.onClicked(deck, cursor.get(), deckSlot, ClickType.RIGHT, player, cursor);
        context.assertTrue(cursor.get().isEmpty(), "Card on cursor returns to deck");
        context.assertEquals(CardDeckItem.getCardCount(deck), 3, "Returning through the opposite click path adds one card");
        ModItems.CARD_DECK.onClicked(deck, cursor.get(), deckSlot, ClickType.RIGHT, player, cursor);
        context.assertEquals(PlayingCardItem.getFace(cursor.get()), 54, "Empty cursor draws one card");
        context.assertEquals(CardDeckItem.getCardCount(deck), 2, "Drawing to cursor removes exactly one card");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullInventoryDoesNotConsumeCardsEvenInCreative(TestContext context) {
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL, GameMode.CREATIVE}) {
            PlayerEntity player = player(context, mode);
            for (int slot = 0; slot < player.getInventory().main.size(); slot++) {
                player.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
            }
            ItemStack deck = new ItemStack(ModItems.CARD_DECK);
            player.setStackInHand(Hand.MAIN_HAND, deck);
            context.assertFalse(ModItems.CARD_DECK.use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted(), "Full inventory rejects drawing");
            context.assertEquals(CardDeckItem.getCardCount(deck), 54, "Rejected draw leaves the deck intact");
        }
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sneakUseGathersLooseCardsAndDeckDoesNotRefillOnReload(TestContext context) {
        PlayerEntity player = player(context, GameMode.SURVIVAL);
        ItemStack deck = emptyDeck();
        player.setStackInHand(Hand.MAIN_HAND, deck);
        player.getInventory().setStack(1, card(1, 2));
        player.setStackInHand(Hand.OFF_HAND, card(54, 1));
        player.setSneaking(true);
        context.assertTrue(ModItems.CARD_DECK.use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted(), "Sneak use gathers cards");
        context.assertEquals(CardDeckItem.getCardCount(deck), 3, "Different faces are stored together");
        context.assertTrue(player.getInventory().getStack(1).isEmpty(), "Gather consumes loose inventory cards");
        context.assertTrue(player.getOffHandStack().isEmpty(), "Gather includes the offhand");
        ItemStack loaded = ItemStack.fromNbt(context.getWorld().getRegistryManager(),
                deck.encode(context.getWorld().getRegistryManager())).orElseThrow();
        context.assertEquals(CardDeckItem.getCardCount(loaded), 3, "Partial deck does not refill on reload");
        ItemStack empty = emptyDeck();
        ItemStack loadedEmpty = ItemStack.fromNbt(context.getWorld().getRegistryManager(),
                empty.encode(context.getWorld().getRegistryManager())).orElseThrow();
        context.assertEquals(CardDeckItem.getCardCount(loadedEmpty), 0, "Empty deck does not refill on reload");
        context.complete();
    }

    @SuppressWarnings("unused")
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cardTooltipsDescribeTheirFaces(TestContext context) {
        int[] faces = {1, 25, 52, 53, 54};
        String[] names = {"ace_of_spades", "queen_of_hearts", "king_of_clubs", "red_joker", "black_joker"};
        for (int i = 0; i < faces.length; i++) {
            ArrayList<Text> lines = new ArrayList<>();
            ModItems.PLAYING_CARD.appendTooltip(card(faces[i], 1), Item.TooltipContext.DEFAULT, lines, TooltipType.BASIC);
            context.assertEquals(lines.size(), 1, "Face appears as an additional tooltip line");
            context.assertEquals(((TranslatableTextContent) lines.getFirst().getContent()).getKey(),
                    "card.pokercraft." + names[i], "Tooltip uses the correct rank and suit");
        }
        ArrayList<Text> deckTooltip = new ArrayList<>();
        ModItems.CARD_DECK.appendTooltip(new ItemStack(ModItems.CARD_DECK), Item.TooltipContext.DEFAULT, deckTooltip, TooltipType.BASIC);
        Object[] count = ((TranslatableTextContent) deckTooltip.getFirst().getContent()).getArgs();
        context.assertEquals(count[0], 54, "Deck tooltip includes the remaining card count");
        context.assertEquals(count[1], 54, "Deck tooltip includes its capacity");
        context.complete();
    }
}
