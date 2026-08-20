package com.mikkeljck.reinforcedchests.client;

import com.mikkeljck.reinforcedchests.CofresPersonales;
import com.mikkeljck.reinforcedchests.block.CofrePersonalBlock;
import com.mikkeljck.reinforcedchests.block.CofrePersonalBlockEntity;
import com.mikkeljck.reinforcedchests.client.pantalla.PantallaClave;
import com.mikkeljck.reinforcedchests.client.pantalla.PantallaConfigClave;
import com.mikkeljck.reinforcedchests.client.render.CofrePersonalRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.InteractionResult;

public class CofresPersonalesClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(CofresPersonales.COFRE_PERSONAL_BE, CofrePersonalRenderer::new);

		// Las pantallas se abren en el cliente, sin pedirle nada al servidor: el
		// cliente ya sabe quien es el dueno y si el cofre tiene clave, porque eso
		// viaja en el paquete de sincronizacion del BlockEntity.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(level.getBlockEntity(hitResult.getBlockPos()) instanceof CofrePersonalBlockEntity cofre)) {
				return InteractionResult.PASS;
			}
			// Bloqueado por arriba: no abrimos ninguna pantalla, igual que vanilla.
			if (CofrePersonalBlock.parejaBloqueada(level, hitResult.getBlockPos(),
					level.getBlockState(hitResult.getBlockPos()))) {
				return InteractionResult.PASS;
			}

			// El dueno agachado abre la configuracion de contrasena.
			if (cofre.esPropietario(player) && player.isShiftKeyDown()) {
				Minecraft.getInstance().gui.setScreen(
						new PantallaConfigClave(hitResult.getBlockPos(), cofre.tieneClave()));
				// FAIL, no SUCCESS: SUCCESS cancela el procesado local pero IGUAL
				// manda el paquete al servidor, que abriria el cofre encima de
				// nuestra pantalla.
				return InteractionResult.FAIL;
			}

			// Jugador ajeno + cofre con clave: pedimos la contrasena.
			// FAIL evita que el paquete llegue al servidor hasta que el jugador
			// escriba algo.
			boolean pareceAutorizado = cofre.esPropietario(player)
					|| CofrePersonalBlockEntity.sostieneLlaveMaestra(player);
			if (!pareceAutorizado && cofre.tieneClave()) {
				Minecraft.getInstance().gui.setScreen(
						new PantallaClave(hitResult.getBlockPos(), cofre.getNombrePropietario()));
				return InteractionResult.FAIL;
			}

			return InteractionResult.PASS;
		});
	}
}
