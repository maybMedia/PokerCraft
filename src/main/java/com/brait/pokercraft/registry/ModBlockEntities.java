package com.brait.pokercraft.registry;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<PokerTableLightBlockEntity> POKER_TABLE_LIGHT = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            PokerCraft.id("poker_table_light"), BlockEntityType.Builder.create(PokerTableLightBlockEntity::new, ModBlocks.POKER_TABLE_LIGHT).build(null));
    public static final BlockEntityType<PokerTableBlockEntity> POKER_TABLE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            PokerCraft.id("poker_table"), BlockEntityType.Builder.create(PokerTableBlockEntity::new, ModBlocks.POKER_TABLE).build(null));
    private ModBlockEntities() {}
    public static void initialize() {}
}
