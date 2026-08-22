package com.mikkeljck.chestshield.proteccion;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Lo implementa cualquier BlockEntity que quiera estar protegido.
 *
 * Solo obliga a exponer la {@link Proteccion}; el resto son atajos que se
 * heredan gratis. Asi el cofre de hoy y el barril de manana comparten la misma
 * logica de permisos sin duplicar una linea.
 */
public interface ContenedorBlindado {

	Proteccion getProteccion();

	default void asignarPropietario(final Player player) {
		this.getProteccion().asignarPropietario(player);
	}

	default String getNombrePropietario() {
		return this.getProteccion().getNombrePropietario();
	}

	default boolean esPropietario(final Player player) {
		return this.getProteccion().esPropietario(player);
	}

	default boolean puedeAbrir(final Player player) {
		return this.getProteccion().puedeAbrir(player);
	}

	default boolean puedeGestionar(final Player player) {
		return this.getProteccion().puedeGestionar(player);
	}

	default boolean estaProtegido() {
		return this.getProteccion().estaProtegido();
	}

	default boolean tieneClave() {
		return this.getProteccion().tieneClave();
	}

	default void establecerClave(final String clave) {
		this.getProteccion().establecerClave(clave);
	}

	default boolean verificarClave(final String clave) {
		return this.getProteccion().verificarClave(clave);
	}

	default void avisarPropiedad(final Player player) {
		this.getProteccion().avisarPropiedad(player);
	}

	default void avisar(final Player player, final Component mensaje) {
		Proteccion.avisar(player, mensaje);
	}

	default boolean tienePermiso(final Player player) {
		return this.getProteccion().tienePermiso(player.getUUID());
	}

	default void agregarPermiso(final UUID jugador, final String nombre) {
		this.getProteccion().agregarPermiso(jugador, nombre);
	}

	default boolean quitarPermiso(final UUID jugador) {
		return this.getProteccion().quitarPermiso(jugador);
	}

	default List<Ajustes.Permiso> getPermisos() {
		return this.getProteccion().getPermisos();
	}
}
