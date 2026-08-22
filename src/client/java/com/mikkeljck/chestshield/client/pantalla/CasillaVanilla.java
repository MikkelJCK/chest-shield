package com.mikkeljck.chestshield.client.pantalla;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Una casilla igual que la de vanilla, pero con la etiqueta sin sombra y en
 * gris oscuro.
 *
 * La Checkbox del juego dibuja su texto en blanco y con sombra, que es lo
 * correcto sobre el fondo oscuro de las opciones. Sobre el panel gris claro de
 * un contenedor desentona: ahi vanilla escribe en #404040 y sin sombra, como en
 * los rotulos "Inventario" o "Encantar".
 *
 * La caja se dibuja con los mismos sprites del juego, asi que un resource pack
 * la sigue cambiando.
 */
public class CasillaVanilla extends AbstractButton {

	private static final Identifier CAJA = Identifier.withDefaultNamespace("widget/checkbox");
	private static final Identifier CAJA_MARCADA = Identifier.withDefaultNamespace("widget/checkbox_selected");
	private static final Identifier CAJA_FOCO = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
	private static final Identifier CAJA_MARCADA_FOCO =
			Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");

	private static final int SEPARACION = 4;

	private final Consumer<Boolean> alCambiar;
	private boolean marcada;

	public CasillaVanilla(final int x, final int y, final Component mensaje, final Font fuente,
			final boolean marcada, final Consumer<Boolean> alCambiar) {
		super(x, y,
				Checkbox.getBoxSize(fuente) + SEPARACION + fuente.width(mensaje),
				Checkbox.getBoxSize(fuente),
				mensaje);
		this.marcada = marcada;
		this.alCambiar = alCambiar;
	}

	public boolean estaMarcada() {
		return this.marcada;
	}

	@Override
	public void onPress(final InputWithModifiers entrada) {
		this.marcada = !this.marcada;
		this.alCambiar.accept(this.marcada);
	}

	@Override
	protected void extractContents(final GuiGraphicsExtractor graficos, final int ratonX, final int ratonY,
			final float parcial) {
		Font fuente = Minecraft.getInstance().font;
		int lado = Checkbox.getBoxSize(fuente);

		Identifier sprite;
		if (this.marcada) {
			sprite = this.isFocused() ? CAJA_MARCADA_FOCO : CAJA_MARCADA;
		} else {
			sprite = this.isFocused() ? CAJA_FOCO : CAJA;
		}
		graficos.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
				this.getX(), this.getY(), lado, lado, ARGB.white(this.alpha));

		// El false final es lo que quita la sombra.
		graficos.text(fuente, this.getMessage(),
				this.getX() + lado + SEPARACION,
				this.getY() + (lado - fuente.lineHeight) / 2 + 1,
				this.active ? PanelVanilla.TEXTO : PanelVanilla.TEXTO_APAGADO,
				false);
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput salida) {
		salida.add(NarratedElementType.TITLE, this.getMessage());
		salida.add(NarratedElementType.USAGE, this.marcada
				? CommonComponents.OPTION_ON
				: CommonComponents.OPTION_OFF);
	}
}
