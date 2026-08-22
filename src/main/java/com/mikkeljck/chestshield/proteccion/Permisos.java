package com.mikkeljck.chestshield.proteccion;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

/**
 * Quien puede administrar cofres ajenos.
 *
 * Existe para tener la decision en UN solo sitio: la usan la Llave Maestra y el
 * bypass de administrador del comando. Hoy es simplemente el nivel de op, pero
 * si algun dia se cambia, se cambia aqui y en ningun sitio mas.
 *
 * NOTA sobre gestores de permisos (probado y descartado el 2026-08-22):
 * se intento con la API de permisos de Fabric (fabric-permission-api-v1), con un
 * nodo chest_shield:master_key y op 2 de respaldo. LuckPerms tenia el nodo
 * concedido y resolvia true al comprobarlo, pero el mod seguia sin verlo: esa
 * API es demasiado nueva y LuckPerms todavia no la puentea. Se quito para no
 * arrastrar codigo que no hace nada ni prometerlo en la ficha del mod.
 *
 * Si se retoma, el camino es la libreria de permisos de lucko
 * (me.lucko:fabric-permissions-api-v0), que es la que LuckPerms si lee en
 * Fabric. Se puede empaquetar dentro del jar para no anadir una dependencia que
 * el jugador tenga que instalar.
 */
public final class Permisos {

	private Permisos() {
	}

	/**
	 * Si este jugador puede abrir y administrar cofres ajenos.
	 *
	 * Solo se puede resolver en el servidor. En el cliente somos optimistas para
	 * que la interfaz responda bien; el servidor tiene siempre la ultima palabra.
	 */
	public static boolean esAdministrador(final Player jugador) {
		return !(jugador instanceof ServerPlayer servidorJugador)
				|| esAdministrador(servidorJugador.createCommandSourceStack());
	}

	public static boolean esAdministrador(final CommandSourceStack fuente) {
		return fuente.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
