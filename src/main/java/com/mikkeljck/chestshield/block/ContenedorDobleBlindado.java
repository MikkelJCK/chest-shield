package com.mikkeljck.chestshield.block;

import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Las dos mitades de un cofre doble vistas como un solo contenedor de 54 huecos,
 * para las tolvas y las tuberias.
 *
 * Hace falta porque cada mitad es un BlockEntity distinto con su propio
 * inventario de 27. Sin esto, una tolva puesta bajo una mitad solo alcanza los
 * huecos de esa mitad, y como el menu del jugador llena primero una de las dos,
 * daba la impresion de que solo funcionaba "el lado izquierdo".
 *
 * CompoundContainer ya resuelve el reparto de huecos entre las dos mitades; aqui
 * solo se le anaden los tres metodos de WorldlyContainer, que son los que
 * deciden si las tolvas pueden tocar el cofre.
 */
public class ContenedorDobleBlindado extends CompoundContainer implements WorldlyContainer {

	/** Para el caso imposible de que el bloque no tenga BlockEntity. */
	public static final WorldlyContainer VACIO = new ContenedorVacio();

	private final WorldlyContainer primero;
	private final WorldlyContainer segundo;
	private final int tamanoPrimero;

	public ContenedorDobleBlindado(final WorldlyContainer primero, final WorldlyContainer segundo) {
		super(primero, segundo);
		this.primero = primero;
		this.segundo = segundo;
		this.tamanoPrimero = primero.getContainerSize();
	}

	/**
	 * Los huecos de la primera mitad tal cual, mas los de la segunda desplazados.
	 * Si las dos mitades tienen las tolvas apagadas, las dos devuelven un array
	 * vacio y esta union tambien lo es.
	 */
	@Override
	public int[] getSlotsForFace(final Direction cara) {
		int[] huecosPrimero = this.primero.getSlotsForFace(cara);
		int[] huecosSegundo = this.segundo.getSlotsForFace(cara);
		int[] union = new int[huecosPrimero.length + huecosSegundo.length];
		System.arraycopy(huecosPrimero, 0, union, 0, huecosPrimero.length);
		for (int i = 0; i < huecosSegundo.length; i++) {
			union[huecosPrimero.length + i] = huecosSegundo[i] + this.tamanoPrimero;
		}
		return union;
	}

	@Override
	public boolean canPlaceItemThroughFace(final int hueco, final ItemStack pila, final @Nullable Direction cara) {
		return hueco >= this.tamanoPrimero
				? this.segundo.canPlaceItemThroughFace(hueco - this.tamanoPrimero, pila, cara)
				: this.primero.canPlaceItemThroughFace(hueco, pila, cara);
	}

	@Override
	public boolean canTakeItemThroughFace(final int hueco, final ItemStack pila, final Direction cara) {
		return hueco >= this.tamanoPrimero
				? this.segundo.canTakeItemThroughFace(hueco - this.tamanoPrimero, pila, cara)
				: this.primero.canTakeItemThroughFace(hueco, pila, cara);
	}

	/** Un contenedor de cero huecos que no deja hacer nada. */
	private static final class ContenedorVacio extends SimpleContainer implements WorldlyContainer {

		private static final int[] SIN_HUECOS = new int[0];

		private ContenedorVacio() {
			super(0);
		}

		@Override
		public int[] getSlotsForFace(final Direction cara) {
			return SIN_HUECOS;
		}

		@Override
		public boolean canPlaceItemThroughFace(final int hueco, final ItemStack pila, final @Nullable Direction cara) {
			return false;
		}

		@Override
		public boolean canTakeItemThroughFace(final int hueco, final ItemStack pila, final Direction cara) {
			return false;
		}
	}
}
