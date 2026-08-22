package com.mikkeljck.chestshield.block;

import com.mikkeljck.chestshield.red.RedCofres;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Punto unico de apertura de cofres.
 *
 * Existe porque un cofre doble no puede abrirse desde el BlockEntity: hay que
 * combinar los dos inventarios en uno de 54 huecos. Y porque el permiso conviene
 * comprobarlo UNA vez, aqui, en vez de repartido por varios sitios.
 */
public final class AperturaCofre {

	private AperturaCofre() {
	}

	/**
	 * @param comoInvitado true cuando el jugador acaba de acertar la contrasena.
	 *                     El permiso dura solo esta apertura.
	 */
	public static void abrir(final Player jugador, final Level level, final BlockPos pos,
			final BlockState estado, final boolean comoInvitado) {
		if (!(jugador instanceof ServerPlayer servidorJugador)) {
			return;
		}
		if (!(estado.getBlock() instanceof CofrePersonalBlock bloque)) {
			return;
		}
		if (CofrePersonalBlock.parejaBloqueada(level, pos, estado)) {
			return;
		}

		MenuProvider proveedor = bloque.combinar(estado, level, pos, false)
				.apply(new DoubleBlockCombiner.Combiner<CofrePersonalBlockEntity, MenuProvider>() {
					@Override
					public MenuProvider acceptDouble(final CofrePersonalBlockEntity primero,
							final CofrePersonalBlockEntity segundo) {
						return proveedorDoble(primero, segundo);
					}

					@Override
					public MenuProvider acceptSingle(final CofrePersonalBlockEntity unico) {
						return proveedorSimple(unico);
					}

					@Override
					public MenuProvider acceptNone() {
						return null;
					}
				});

		if (proveedor == null) {
			return;
		}

		if (!comoInvitado && !tienePermiso(level, pos, estado, jugador)) {
			return;
		}

		// Si entro por un permiso pendiente, ahora si tenemos su UUID: se convierte
		// en un permiso normal y deja de depender del nombre.
		if (level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre) {
			CofrePersonalBlock.paraAmbasMitades(cofre,
					mitad -> mitad.getProteccion().resolverPendiente(jugador));
		}

		RedCofres.avisarCofreAbierto(servidorJugador, pos);
		servidorJugador.openMenu(proveedor);
	}

	/** Para un cofre doble hay que poder acceder a las DOS mitades. */
	private static boolean tienePermiso(final Level level, final BlockPos pos, final BlockState estado,
			final Player jugador) {
		if (!(level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre)) {
			return false;
		}
		if (!cofre.puedeAbrir(jugador)) {
			return false;
		}
		if (estado.getValue(CofrePersonalBlock.TYPE) == ChestType.SINGLE) {
			return true;
		}
		BlockPos posPareja = pos.relative(CofrePersonalBlock.direccionUnion(estado));
		return !(level.getBlockEntity(posPareja) instanceof CofrePersonalBlockEntity pareja)
				|| pareja.puedeAbrir(jugador);
	}

	private static MenuProvider proveedorSimple(final CofrePersonalBlockEntity cofre) {
		return new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return cofre.getDisplayName();
			}

			@Override
			public @Nullable AbstractContainerMenu createMenu(final int id, final Inventory inventario, final Player jugador) {
				return ChestMenu.threeRows(id, inventario, cofre.getInventario());
			}
		};
	}

	private static MenuProvider proveedorDoble(final CofrePersonalBlockEntity primero,
			final CofrePersonalBlockEntity segundo) {
		final Container combinado = new CompoundContainer(primero.getInventario(), segundo.getInventario());
		return new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return primero.getProteccion().getPropietario() == null
						? Component.translatable("container.chest_shield.large_shielded_chest")
						: Component.translatable("container.chest_shield.cofre_grande_de",
								primero.getNombrePropietario());
			}

			@Override
			public @Nullable AbstractContainerMenu createMenu(final int id, final Inventory inventario, final Player jugador) {
				return ChestMenu.sixRows(id, inventario, combinado);
			}
		};
	}
}
