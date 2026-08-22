package com.mikkeljck.chestshield.client;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.client.boton.BotonCandado;
import com.mikkeljck.chestshield.client.boton.CofreAbiertoActual;
import com.mikkeljck.chestshield.client.pantalla.PantallaClave;
import com.mikkeljck.chestshield.client.pantalla.PantallaConfigCofre;
import com.mikkeljck.chestshield.client.render.CofrePersonalRenderer;
import com.mikkeljck.chestshield.red.RedCofres;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;

public class CofresPersonalesClient implements ClientModInitializer {

	/** Ancho del fondo de la pantalla de cofre en vanilla. */
	private static final int ANCHO_PANEL = 176;

	/** El panel mide 114 px mas 18 por cada fila de huecos. */
	private static final int ALTO_BASE_PANEL = 114;
	private static final int ALTO_FILA = 18;

	/** Separacion del boton respecto al borde izquierdo del panel. */
	private static final int HUECO_BOTON = 4;

	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(CofresPersonales.COFRE_PERSONAL_BE, CofrePersonalRenderer::new);

		// El servidor decide cuando hay que pedir la contrasena; aqui solo se abre
		// la pantalla. Los manejadores van en codigo de cliente porque tocan
		// Minecraft.getInstance(); en comun solo se declaran los tipos de paquete.
		ClientPlayNetworking.registerGlobalReceiver(RedCofres.PedirClave.TIPO, (mensaje, contexto) ->
				contexto.client().gui.setScreen(
						new PantallaClave(mensaje.pos(), mensaje.nombreDueno())));

		ClientPlayNetworking.registerGlobalReceiver(RedCofres.CofreAbierto.TIPO, (mensaje, contexto) ->
				CofreAbiertoActual.establecer(mensaje.pos()));

		ScreenEvents.AFTER_INIT.register((cliente, pantalla, ancho, alto) -> {
			if (pantalla instanceof ContainerScreen cofre) {
				anadirBotonSiEsNuestro(cofre, ancho, alto);
				// Al cerrar el cofre se olvida la posicion, para no arrastrarla a
				// la siguiente pantalla que se abra.
				ScreenEvents.remove(pantalla).register(cerrada -> CofreAbiertoActual.limpiar());
			}
		});
	}

	/**
	 * Anade el candado a la pantalla del cofre, solo si es un cofre blindado
	 * nuestro y quien mira es su dueno.
	 *
	 * Se usa la pantalla de cofre de VANILLA, sin reemplazarla ni crear un
	 * MenuType propio: asi los mods de ordenar inventario la siguen reconociendo.
	 *
	 * El boton va pegado al borde izquierdo, por fuera del marco. Arriba a la
	 * derecha no, que es donde dibujan los suyos Inventory Profiles Next y
	 * companiaa.
	 */
	private static void anadirBotonSiEsNuestro(final ContainerScreen pantalla, final int ancho, final int alto) {
		Minecraft cliente = Minecraft.getInstance();
		BlockPos pos = CofreAbiertoActual.obtener();
		if (pos == null || cliente.level == null || cliente.player == null) {
			return;
		}
		if (!(cliente.level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre)) {
			return;
		}
		if (!cofre.esPropietario(cliente.player)) {
			return;
		}

		// AbstractContainerScreen guarda estas dos posiciones, pero protegidas.
		// Se recalculan igual que hace el juego, y asi nos ahorramos un mixin.
		int altoPanel = ALTO_BASE_PANEL + pantalla.getMenu().getRowCount() * ALTO_FILA;
		int izquierda = (ancho - ANCHO_PANEL) / 2;
		int arriba = (alto - altoPanel) / 2;

		// getWidgets, no getButtons: en fabric-screen-api-v1 5.x se renombro.
		Screens.getWidgets(pantalla).add(new BotonCandado(
				izquierda - BotonCandado.LADO - HUECO_BOTON,
				arriba + 8,
				boton -> cliente.gui.setScreen(new PantallaConfigCofre(pantalla, pos))));
	}
}
