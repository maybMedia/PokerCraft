package com.brait.pokercraft;

import com.brait.pokercraft.registry.ModBlocks;
import com.brait.pokercraft.registry.ModBlockEntities;
import com.brait.pokercraft.registry.ModEntities;
import com.brait.pokercraft.registry.ModItemGroups;
import com.brait.pokercraft.registry.ModItems;
import com.brait.pokercraft.network.PokerNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PokerCraft implements ModInitializer {
    public static final String MOD_ID = "pokercraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModEntities.initialize();
        ModItems.initialize();
        ModItemGroups.initialize();
        PokerNetworking.initialize();
        LOGGER.info("PokerCraft initialized: tables, cards, and chips are ready.");
    }
}
