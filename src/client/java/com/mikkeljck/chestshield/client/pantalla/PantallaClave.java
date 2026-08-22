package com.mikkeljck.chestshield.client.pantalla;

import com.mikkeljck.chestshield.proteccion.Proteccion;
import com.mikkeljck.chestshield.red.RedCofres;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Le pide la contrasena a alguien que no es el dueno.
 *
 * La abre el SERVIDOR mandando un paquete: el cliente no decide cuando aparece.
 * Widgets de vanilla, para que se vea como cualquier otra pantalla del juego.
 */
public class PantallaClave extends Screen {

	private static final int ANCHO_CAMPO = 200;
	private static final int ANCHO_PANEL = 244;
	private static final int ALTO_PANEL = 122;

	private final BlockPos pos;
	private final String nombreDueno;
	private EditBox campo;

	public PantallaClave(final BlockPos pos, final String nombreDueno) {
		super(Component.translatable("screen.chest_shield.titulo_clave"));
		this.pos = pos;
		this.nombreDueno = nombreDueno;
	}

	/** El paquete trae el nombre crudo: si viene vacio, se traduce aqui. */
	private Component nombreMostrado() {
		return this.nombreDueno.isEmpty()
				? Component.translatable("message.chest_shield.dueno_desconocido")
				: Component.literal(this.nombreDueno);
	}

	@Override
	protected void init() {
		int centroX = this.width / 2;
		int centroY = this.height / 2;

		this.campo = new EditBox(this.font, centroX - ANCHO_CAMPO / 2, centroY - 10, ANCHO_CAMPO, 20,
				Component.translatable("screen.chest_shield.escribe_clave"));
		this.campo.setMaxLength(Proteccion.LONGITUD_MAXIMA_CLAVE);
		this.addRenderableWidget(this.campo);
		this.setInitialFocus(this.campo);

		this.addRenderableWidget(Button.builder(
						Component.translatable("screen.chest_shield.abrir"), boton -> this.enviar())
				.bounds(centroX - ANCHO_CAMPO / 2, centroY + 18, 98, 20)
				.build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, boton -> this.onClose())
				.bounds(centroX + 4, centroY + 18, 98, 20)
				.build());
	}

	private void enviar() {
		if (this.campo.getValue().isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new RedCofres.IntentoClave(this.pos, this.campo.getValue()));
		this.onClose();
	}

	@Override
	public boolean keyPressed(final KeyEvent evento) {
		if (evento.key() == InputConstants.KEY_RETURN || evento.key() == InputConstants.KEY_NUMPADENTER) {
			this.enviar();
			return true;
		}
		return super.keyPressed(evento);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graficos, final int ratonX, final int ratonY,
			final float parcial) {
		int centroX = this.width / 2;
		int centroY = this.height / 2;

		// Antes de super, para que el panel quede debajo de los widgets.
		PanelVanilla.dibujar(graficos, centroX - ANCHO_PANEL / 2, centroY - 72, ANCHO_PANEL, ALTO_PANEL);
		super.extractRenderState(graficos, ratonX, ratonY, parcial);
		PanelVanilla.textoCentrado(graficos, this.font, this.title, centroX, centroY - 62, PanelVanilla.TEXTO);
		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("screen.chest_shield.cofre_de", this.nombreMostrado()),
				centroX, centroY - 46, PanelVanilla.TEXTO_SUAVE);
		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("screen.chest_shield.escribe_clave"),
				centroX, centroY - 26, PanelVanilla.TEXTO_SUAVE);
	}

	/** El mundo sigue corriendo detras: esto no es una pausa. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Igual que la de configuracion: sin desenfoque ni fondo de menu. */
	@Override
	public boolean isInGameUi() {
		return true;
	}
}
