package com.mikkeljck.chestshield.client.pantalla;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * El panel gris claro de los contenedores de vanilla.
 *
 * Es el sprite que el juego usa para el recuadro que aparece sobre el libro de
 * recetas: mismo cuerpo gris (#C6C6C6), mismo bisel y mismas esquinas que un
 * cofre. Y es "nine slice" con borde de 4, asi que las esquinas se dibujan tal
 * cual y el resto se estira: sirve para cualquier tamano.
 *
 * Se descartaron dos alternativas. La textura del cofre (generic_54.png) es de
 * tamano fijo y no se puede estirar sin deformarla. Y dibujar el panel a mano
 * con rectangulos daba un resultado plano que no pegaba con el resto del juego.
 *
 * Al ser una textura de vanilla, un resource pack la cambia tambien aqui, y las
 * pantallas del mod siguen combinando con el resto.
 */
public final class PanelVanilla {

	private static final Identifier FONDO =
			Identifier.withDefaultNamespace("recipe_book/overlay_recipe");

	/** Sobre gris claro hay que escribir en oscuro, no en blanco. */
	public static final int TEXTO = 0xFF404040;
	public static final int TEXTO_SUAVE = 0xFF6E6E6E;
	public static final int TEXTO_APAGADO = 0xFF9A9A9A;

	private PanelVanilla() {
	}

	public static void dibujar(final GuiGraphicsExtractor graficos, final int x, final int y,
			final int ancho, final int alto) {
		graficos.blitSprite(RenderPipelines.GUI_TEXTURED, FONDO, x, y, ancho, alto);
	}

	/**
	 * Texto centrado y SIN sombra.
	 *
	 * centeredText de vanilla no tiene variante sin sombra, asi que se centra a
	 * mano y se llama a text con el interruptor en false.
	 */
	public static void textoCentrado(final GuiGraphicsExtractor graficos, final Font fuente,
			final net.minecraft.network.chat.Component texto, final int centroX, final int y, final int color) {
		net.minecraft.util.FormattedCharSequence visual = texto.getVisualOrderText();
		graficos.text(fuente, visual, centroX - fuente.width(visual) / 2, y, color, false);
	}
}
