package com.mikkeljck.reinforcedchests.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Los datos que el renderizador necesita por frame, extraidos del BlockEntity. */
public class CofrePersonalRenderState extends BlockEntityRenderState {
	public Direction orientacion = Direction.SOUTH;
	public ChestType tipo = ChestType.SINGLE;
	public float apertura;
}
