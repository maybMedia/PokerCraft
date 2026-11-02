package com.brait.pokercraft.registry;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.block.PokerTableBlock;
import com.brait.pokercraft.block.PokerTableLightBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block POKER_TABLE_LIGHT = register("poker_table_light", new PokerTableLightBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(1.5F).sounds(BlockSoundGroup.METAL)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).nonOpaque()
                    .luminance(state -> state.get(PokerTableLightBlock.LEVEL) == 1 ? 15 : 0)));
    public static final Block POKER_TABLE = register("poker_table", new PokerTableBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_GREEN)
                    .strength(2.5F)
                    .sounds(BlockSoundGroup.WOOD)
                    .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)
                    .nonOpaque()));

    private ModBlocks() {}

    private static Block register(String name, Block block) {
        Registry.register(Registries.ITEM, PokerCraft.id(name), new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, PokerCraft.id(name), block);
    }

    public static void initialize() {
        // Calling this method registers the static fields above.
    }
}
