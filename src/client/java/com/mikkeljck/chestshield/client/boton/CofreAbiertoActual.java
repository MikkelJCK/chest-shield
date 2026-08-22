package com.mikkeljck.chestshield.client.boton;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Recuerda que cofre blindado tiene abierto este cliente.
 *
 * Lo pone el paquete que manda el servidor al abrir, y se limpia al cerrar la
 * pantalla. Sin esto no habria manera fiable de saber a que bloque pertenece la
 * pantalla de cofre que hay delante.
 */
public final class CofreAbiertoActual {

	private static @Nullable BlockPos posicion;

	private CofreAbiertoActual() {
	}

	public static void establecer(final BlockPos pos) {
		posicion = pos;
	}

	public static @Nullable BlockPos obtener() {
		return posicion;
	}

	public static void limpiar() {
		posicion = null;
	}
}
