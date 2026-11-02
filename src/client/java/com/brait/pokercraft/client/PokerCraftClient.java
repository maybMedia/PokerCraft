package com.brait.pokercraft.client;

import net.fabricmc.api.ClientModInitializer;
import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import com.brait.pokercraft.registry.ModBlockEntities;
import com.brait.pokercraft.registry.ModBlocks;
import com.brait.pokercraft.registry.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.math.BlockPos;

public final class PokerCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PokerClient.initialize();
        PokerControls.initialize();
        PokerExperience.initialize();
        PokerTableRenderer renderer = new PokerTableRenderer();
        BlockEntityRendererFactories.register(ModBlockEntities.POKER_TABLE, context -> renderer);
        PokerTableLightRenderer lightRenderer = new PokerTableLightRenderer();
        BlockEntityRendererFactories.register(ModBlockEntities.POKER_TABLE_LIGHT, context -> lightRenderer);
        PokerTableLightBlockEntity itemLight = new PokerTableLightBlockEntity(BlockPos.ORIGIN, ModBlocks.POKER_TABLE_LIGHT.getDefaultState());
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.POKER_TABLE_LIGHT.asItem(),
                (stack, mode, matrices, vertices, light, overlay) -> MinecraftClient.getInstance().getBlockEntityRenderDispatcher()
                        .renderEntity(itemLight, matrices, vertices, light, overlay));
        EntityRendererRegistry.register(ModEntities.POKER_SEAT, PokerSeatRenderer::new);
        EntityRendererRegistry.register(ModEntities.THROWN_PLAYING_CARD, ThrownPlayingCardRenderer::new);
        PokerTableBlockEntity itemTable = new PokerTableBlockEntity(BlockPos.ORIGIN, ModBlocks.POKER_TABLE.getDefaultState());
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.POKER_TABLE.asItem(),
                (stack, mode, matrices, vertices, light, overlay) -> MinecraftClient.getInstance().getBlockEntityRenderDispatcher()
                        .renderEntity(itemTable, matrices, vertices, light, overlay));
    }
}
