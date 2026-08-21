package com.mikkeljck.chestshield.client;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.block.CofrePersonalBlock;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.client.pantalla.PantallaClave;
import com.mikkeljck.chestshield.client.pantalla.PantallaConfigClave;
import com.mikkeljck.chestshield.client.render.CofrePersonalRenderer;
import com.mikkeljck.chestshield.red.RedCofres;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.InteractionResult;

public class CofresPersonalesClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(CofresPersonales.COFRE_PERSONAL_BE, CofrePersonalRenderer::new);

		// El servidor decide cuando hay que pedir la contrasena; aqui solo se
		// abre la pantalla. El manejador va en codigo de cliente porque toca
		// Minecraft.getInstance(); en comun solo se declara el paquete.
		RedCofres.CANAL.registerClientbound(RedCofres.PedirClave.class, (mensaje, acceso) ->
				Minecraft.getInstance().gui.setScreen(
						new PantallaClave(mensaje.pos(), mensaje.nombreDueno())));

		// Lo unico que el cliente sigue interceptando es el atajo de configuracion
		// del dueno. Todo lo demas (abrir, pedir clave, avisar de cofre ajeno) lo
		// decide el servidor, que es el unico con datos siempre frescos.
		// TODO 1.1: este atajo desaparece cuando la configuracion pase a estar
		// dentro de la pantalla del cofre.
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

			// Nada mas que decidir aqui. El click sigue su camino al servidor,
			// que abrira el cofre, pedira la clave o avisara de que es ajeno.
			return InteractionResult.PASS;
		});
	}
}
