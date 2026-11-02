package com.brait.pokercraft.registry;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.entity.PokerSeatEntity;
import com.brait.pokercraft.entity.ThrownPlayingCardEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<ThrownPlayingCardEntity> THROWN_PLAYING_CARD = Registry.register(Registries.ENTITY_TYPE,
            PokerCraft.id("thrown_playing_card"), EntityType.Builder.create(
                    ThrownPlayingCardEntity::new, SpawnGroup.MISC)
                    .dimensions(.25F, .1F).maxTrackingRange(8).trackingTickInterval(1)
                    .build("pokercraft:thrown_playing_card"));
    public static final EntityType<PokerSeatEntity> POKER_SEAT = Registry.register(Registries.ENTITY_TYPE,
            PokerCraft.id("poker_seat"), EntityType.Builder.create(PokerSeatEntity::new, SpawnGroup.MISC)
                    .dimensions(.01F, .01F).disableSummon().disableSaving().makeFireImmune()
                    .maxTrackingRange(8).trackingTickInterval(20).build("pokercraft:poker_seat"));
    private ModEntities() {}
    public static void initialize() {}
}
