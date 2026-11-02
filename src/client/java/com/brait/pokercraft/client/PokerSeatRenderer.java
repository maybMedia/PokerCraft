package com.brait.pokercraft.client;

import com.brait.pokercraft.PokerCraft;
import com.brait.pokercraft.entity.PokerSeatEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/** The seat vehicle has no visual; the stool is already part of the table model. */
public final class PokerSeatRenderer extends EntityRenderer<PokerSeatEntity> {
    public PokerSeatRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override
    public Identifier getTexture(PokerSeatEntity seat) { return PokerCraft.id("textures/block/poker_table.png"); }
}
