package com.brait.pokercraft.registry;

import com.brait.pokercraft.PokerCraft;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

@SuppressWarnings("unused")
public final class ModItemGroups {
    public static final ItemGroup POKERCRAFT = Registry.register(
            Registries.ITEM_GROUP, PokerCraft.id("pokercraft"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.pokercraft"))
                    .icon(() -> new ItemStack(ModBlocks.POKER_TABLE))
                    .entries((context, entries) -> {
                        entries.add(ModBlocks.POKER_TABLE);
                        entries.add(ModBlocks.POKER_TABLE_LIGHT);
                        entries.add(ModItems.PLAYING_CARD);
                        entries.add(ModItems.CARD_DECK);
                        entries.add(ModItems.WHITE_CHIP);
                        entries.add(ModItems.RED_CHIP);
                        entries.add(ModItems.BLUE_CHIP);
                        entries.add(ModItems.GREEN_CHIP);
                        entries.add(ModItems.HOST_CHIP);
                    }).build());

    private ModItemGroups() {}

    public static void initialize() {}
}
