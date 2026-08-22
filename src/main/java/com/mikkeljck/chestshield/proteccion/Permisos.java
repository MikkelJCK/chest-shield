package com.mikkeljck.chestshield.proteccion;

import com.mikkeljck.chestshield.CofresPersonales;

import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Los permisos de administracion del mod.
 *
 * Hasta ahora la Llave Maestra solo miraba el nivel de op, y eso deja fuera a
 * cualquier servidor que reparta permisos por rangos. Con un nodo propio, un
 * gestor de permisos puede dar la llave a un rango de staff sin tener que darle
 * op, que es justo lo que se quiere evitar.
 *
 * El nivel de op sigue funcionando como respaldo: si nadie responde al nodo, se
 * exige op 2. Asi un servidor sin gestor de permisos se comporta igual que antes
 * y no hay que configurar nada.
 *
 * Nodo: chest_shield:master_key
 */
public final class Permisos {

	public static final PermissionNode<Boolean> LLAVE_MAESTRA =
			PermissionNode.of(CofresPersonales.MOD_ID, "master_key");

	/** Sin gestor de permisos, hace falta op 2. */
	private static final PermissionLevel RESPALDO = PermissionLevel.GAMEMASTERS;

	private Permisos() {
	}

	/**
	 * Si este jugador puede abrir cofres ajenos con la Llave Maestra.
	 *
	 * Solo se puede resolver en el servidor. En el cliente somos optimistas para
	 * que la interfaz responda bien; el servidor tiene siempre la ultima palabra.
	 */
	public static boolean puedeUsarLlaveMaestra(final Player jugador) {
		if (!(jugador instanceof ServerPlayer servidorJugador)) {
			return true;
		}
		return servidorJugador.createCommandSourceStack()
				.checkPermission(LLAVE_MAESTRA.key(), RESPALDO);
	}
}
