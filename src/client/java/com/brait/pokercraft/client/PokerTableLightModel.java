package com.brait.pokercraft.client;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.block.entity.PokerTableLightBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class PokerTableLightModel extends GeoModel<PokerTableLightBlockEntity> {
    @Override public Identifier getModelResource(PokerTableLightBlockEntity light) { return PokerCraft.id("geo/poker_table_light.geo.json"); }
    @Override public Identifier getTextureResource(PokerTableLightBlockEntity light) { return PokerCraft.id("textures/block/poker_table_light.png"); }
    @Override public Identifier getAnimationResource(PokerTableLightBlockEntity light) { return PokerCraft.id("animations/poker_table_light.animation.json"); }
}
