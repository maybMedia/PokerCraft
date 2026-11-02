package com.brait.pokercraft.registry;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.item.PlayingCardItem;
import com.brait.pokercraft.item.CardDeckItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item PLAYING_CARD = Registry.register(Registries.ITEM, PokerCraft.id("playing_card"),
            new PlayingCardItem(new Item.Settings()));
    public static final Item CARD_DECK = Registry.register(Registries.ITEM, PokerCraft.id("card_deck"),
            new CardDeckItem(new Item.Settings()));
    public static final Item WHITE_CHIP = register("white_chip", new Item.Settings());
    public static final Item RED_CHIP = register("red_chip", new Item.Settings());
    public static final Item BLUE_CHIP = register("blue_chip", new Item.Settings());
    public static final Item GREEN_CHIP = register("green_chip", new Item.Settings());
    public static final Item HOST_CHIP = register("host_chip", new Item.Settings().maxCount(1));
    public static final Item CARD_BACK = register("card_back", new Item.Settings().maxCount(1));
    // Display-only items for the virtual poker hotbar. Never inserted into player inventories.
    public static final java.util.Map<String, Item> ACTIONS = new java.util.LinkedHashMap<>();
    static {
        for (String action : new String[]{"fold", "call", "raise", "minus", "plus", "allin", "join", "buyin", "admin", "info", "leave"})
            ACTIONS.put(action, register("action_" + action, new Item.Settings().maxCount(1)));
    }

    private ModItems() {}

    private static Item register(String name, Item.Settings settings) {
        return Registry.register(Registries.ITEM, PokerCraft.id(name), new Item(settings));
    }

    public static void initialize() {
        // Trigger static item registration.
    }
}
