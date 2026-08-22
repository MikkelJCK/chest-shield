package com.mikkeljck.chestshield.client.boton;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * El boton que abre la configuracion desde el cofre abierto.
 *
 * El candado se dibuja con rectangulos en vez de con una textura: son ocho
 * llamadas a fill() y evita anadir un archivo de imagen, escalarlo y mantenerlo.
 */
public class BotonCandado extends Button {

	public static final int LADO = 20;

	private static final int BLANCO = 0xFFE8E8E8;
	private static final int SOMBRA = 0xFF3F3F3F;

	public BotonCandado(final int x, final int y, final Button.OnPress alPulsar) {
		super(x, y, LADO, LADO, Component.empty(), alPulsar, DEFAULT_NARRATION);
		this.setTooltip(Tooltip.create(Component.translatable("screen.chest_shield.titulo_config")));
	}

	/**
	 * extractRenderState es final en AbstractWidget; el hueco que dejan los
	 * botones para pintarse es este. Se dibuja el fondo de boton de siempre y
	 * encima el candado.
	 */
	@Override
	protected void extractContents(final GuiGraphicsExtractor graficos, final int ratonX, final int ratonY,
			final float parcial) {
		this.extractDefaultSprite(graficos);

		int x = this.getX();
		int y = this.getY();

		// Arco: dos barras verticales y una horizontal arriba.
		graficos.fill(x + 8, y + 5, x + 12, y + 6, BLANCO);
		graficos.fill(x + 7, y + 6, x + 8, y + 10, BLANCO);
		graficos.fill(x + 12, y + 6, x + 13, y + 10, BLANCO);

		// Cuerpo y bocallave.
		graficos.fill(x + 5, y + 10, x + 15, y + 16, BLANCO);
		graficos.fill(x + 9, y + 12, x + 11, y + 15, SOMBRA);
	}
}
