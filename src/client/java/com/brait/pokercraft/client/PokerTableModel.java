package com.brait.pokercraft.client;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.block.entity.PokerTableBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public final class PokerTableModel extends GeoModel<PokerTableBlockEntity> {
    @Override
    public Identifier getModelResource(PokerTableBlockEntity table) { return PokerCraft.id("geo/six_seat_poker_table.geo.json"); }
    @Override
    public Identifier getTextureResource(PokerTableBlockEntity table) { return PokerCraft.id("textures/block/poker_table.png"); }
    @Override
    public Identifier getAnimationResource(PokerTableBlockEntity table) { return PokerCraft.id("animations/poker_table.animation.json"); }
}
