package com.mikkeljck.chestshield.block;

import java.util.List;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.proteccion.ContenedorBlindado;
import com.mikkeljck.chestshield.proteccion.Proteccion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Entidad del Cofre Personal.
 *
 * IMPORTANTE: esta clase NO implementa Container a proposito. Las tolvas y las
 * tuberias de otros mods buscan un Container en el BlockEntity; al no exponerlo,
 * el cofre queda sellado para cualquier automatizacion. El inventario real vive
 * en un SimpleContainer interno que solo se entrega al menu del jugador.
 *
 * Toda la logica de permisos vive en {@link Proteccion}; aqui solo queda lo que
 * es propio de un cofre: el inventario, la tapa animada y el sonido.
 */
public class CofrePersonalBlockEntity extends BlockEntity implements MenuProvider, LidBlockEntity, ContenedorBlindado {

	public static final int TAMANO = 27;
	public static final int LONGITUD_MAXIMA_CLAVE = Proteccion.LONGITUD_MAXIMA_CLAVE;

	private final Proteccion proteccion = new Proteccion(this::alCambiarProteccion);

	private final SimpleContainer inventario = new SimpleContainer(TAMANO) {
		// El menu llama a estos dos al abrirse y cerrarse. Son nuestra unica
		// senal de cuando animar la tapa, porque el BlockEntity no es Container.
		@Override
		public void startOpen(final ContainerUser usuario) {
			CofrePersonalBlockEntity.this.alAbrirMenu(usuario);
		}

		@Override
		public void stopOpen(final ContainerUser usuario) {
			CofrePersonalBlockEntity.this.alCerrarMenu(usuario);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			CofrePersonalBlockEntity.this.setChanged();
		}

		@Override
		public boolean stillValid(final Player player) {
			// El permiso se comprueba al ABRIR, no aqui. Si lo comprobaramos en
			// cada tick, el invitado que acerto la clave veria el menu cerrarse
			// al instante.
			return !CofrePersonalBlockEntity.this.isRemoved()
					&& player.distanceToSqr(
							CofrePersonalBlockEntity.this.worldPosition.getX() + 0.5,
							CofrePersonalBlockEntity.this.worldPosition.getY() + 0.5,
							CofrePersonalBlockEntity.this.worldPosition.getZ() + 0.5) <= 64.0;
		}
	};

	/** Animacion de la tapa: el controlador vive en el cliente, el contador en el servidor. */
	private final ChestLidController controladorTapa = new ChestLidController();

	private final ContainerOpenersCounter contadorAbiertos = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(final Level level, final BlockPos pos, final BlockState estado) {
			reproducir(level, pos, estado, SoundEvents.CHEST_OPEN);
		}

		@Override
		protected void onClose(final Level level, final BlockPos pos, final BlockState estado) {
			reproducir(level, pos, estado, SoundEvents.CHEST_CLOSE);
		}

		@Override
		protected void openerCountChanged(final Level level, final BlockPos pos, final BlockState estado,
				final int anterior, final int actual) {
			// Evento de bloque 1 = "hay N jugadores mirando". Es lo que hace que
			// la tapa se anime en TODOS los clientes, no solo en el que abrio.
			level.blockEvent(pos, estado.getBlock(), 1, actual);
		}

		@Override
		public boolean isOwnContainer(final Player player) {
			if (!(player.containerMenu instanceof ChestMenu menu)) {
				return false;
			}
			Container contenedor = menu.getContainer();
			// En un cofre doble el menu apunta al contenedor combinado, no al nuestro.
			return contenedor == CofrePersonalBlockEntity.this.inventario
					|| contenedor instanceof CompoundContainer combinado
							&& combinado.contains(CofrePersonalBlockEntity.this.inventario);
		}
	};

	/**
	 * En un cofre doble las DOS mitades reciben el aviso de apertura, porque el
	 * CompoundContainer se lo reenvia a ambas. Si cada una tocara su sonido se
	 * oiria doble. Solucion de vanilla, que copiamos: la mitad izquierda se calla
	 * y la derecha suena desplazada al punto medio entre los dos bloques, para
	 * que el sonido salga del centro del mueble.
	 */
	private static void reproducir(final Level level, final BlockPos pos, final BlockState estado,
			final SoundEvent sonido) {
		ChestType tipo = estado.hasProperty(CofrePersonalBlock.TYPE)
				? estado.getValue(CofrePersonalBlock.TYPE)
				: ChestType.SINGLE;

		if (tipo == ChestType.LEFT) {
			return;
		}

		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		if (tipo == ChestType.RIGHT) {
			Direction union = CofrePersonalBlock.direccionUnion(estado);
			x += union.getStepX() * 0.5;
			z += union.getStepZ() * 0.5;
		}

		level.playSound(null, x, y, z, sonido, SoundSource.BLOCKS, 0.5F,
				level.getRandom().nextFloat() * 0.1F + 0.9F);
	}

	public CofrePersonalBlockEntity(final BlockPos pos, final BlockState state) {
		super(CofresPersonales.COFRE_PERSONAL_BE, pos, state);
	}

	// ---------- Proteccion ----------

	@Override
	public Proteccion getProteccion() {
		return this.proteccion;
	}

	/** Lo llama la Proteccion cada vez que cambia algo que hay que persistir y sincronizar. */
	private void alCambiarProteccion() {
		this.setChanged();
		this.sincronizarConClientes();
	}

	private void sincronizarConClientes() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Atajo para el codigo de cliente, que no tiene la Proteccion a mano. */
	public static boolean sostieneLlaveMaestra(final Player player) {
		return Proteccion.sostieneLlaveMaestra(player);
	}

	// El tiempo de juego solo lo conoce el BlockEntity, por eso estos dos no son
	// defaults de la interfaz.

	public boolean enEspera(final Player player) {
		return this.level != null && this.proteccion.enEspera(player, this.level.getGameTime());
	}

	public void registrarFallo(final Player player) {
		if (this.level != null) {
			this.proteccion.registrarFallo(player, this.level.getGameTime());
		}
	}

	public boolean puedeEmparejarseCon(final CofrePersonalBlockEntity otro) {
		return this.proteccion.esCompatibleCon(otro.proteccion);
	}

	public void copiarAjustesDe(final CofrePersonalBlockEntity otra) {
		this.proteccion.copiarAjustesDe(otra.proteccion);
	}

	/**
	 * Abre el cofre para alguien que acerto la contrasena. El permiso dura
	 * exactamente esta apertura: la proxima vez tendra que teclearla de nuevo.
	 */
	public void abrirParaInvitado(final ServerPlayer player) {
		this.proteccion.setInvitadoTemporal(player.getUUID());
		try {
			AperturaCofre.abrir(player, this.level, this.worldPosition, this.getBlockState(), true);
		} finally {
			this.proteccion.setInvitadoTemporal(null);
		}
	}

	// ---------- Inventario ----------

	public SimpleContainer getInventario() {
		return this.inventario;
	}

	// ---------- Guardado ----------

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.inventario.getItems());
		this.proteccion.guardar(output);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.inventario.getItems().replaceAll(stack -> ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.inventario.getItems());
		this.proteccion.cargar(input);
	}

	// ---------- Sincronizacion con el cliente ----------

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		this.proteccion.escribirUpdateTag(tag);
		return tag;
	}

	// ---------- Menu ----------

	/**
	 * El titulo del contenedor dice de quien es el cofre. Se calcula en el
	 * servidor, que es quien conoce al dueno, y llega al cliente dentro del
	 * paquete de apertura del menu.
	 */
	@Override
	public Component getDisplayName() {
		return this.proteccion.getPropietario() == null
				? Component.translatable("block.chest_shield.shielded_chest")
				: Component.translatable("container.chest_shield.cofre_de", this.proteccion.getNombrePropietario());
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		if (!this.proteccion.esInvitado(player) && !this.puedeAcceder(player)) {
			return null;
		}
		return ChestMenu.threeRows(containerId, inventory, this.inventario);
	}

	// ---------- Animacion de la tapa ----------

	void alAbrirMenu(final ContainerUser usuario) {
		if (!this.isRemoved() && !usuario.getLivingEntity().isSpectator()) {
			this.contadorAbiertos.incrementOpeners(usuario.getLivingEntity(), this.getLevel(),
					this.getBlockPos(), this.getBlockState(), usuario.getContainerInteractionRange());
		}
	}

	void alCerrarMenu(final ContainerUser usuario) {
		if (!this.isRemoved() && !usuario.getLivingEntity().isSpectator()) {
			this.contadorAbiertos.decrementOpeners(usuario.getLivingEntity(), this.getLevel(),
					this.getBlockPos(), this.getBlockState());
		}
	}

	@Override
	public boolean triggerEvent(final int id, final int valor) {
		if (id == 1) {
			this.controladorTapa.shouldBeOpen(valor > 0);
			return true;
		}
		return super.triggerEvent(id, valor);
	}

	@Override
	public float getOpenNess(final float parcial) {
		return this.controladorTapa.getOpenness(parcial);
	}

	/** Ticker de cliente: avanza la animacion de la tapa. */
	public static void animarTapa(final Level level, final BlockPos pos, final BlockState estado,
			final CofrePersonalBlockEntity cofre) {
		cofre.controladorTapa.tickLid();
	}

	/** Ticker de servidor: red de seguridad si alguien se desconecta con el cofre abierto. */
	public static void revisarAbiertos(final Level level, final BlockPos pos, final BlockState estado,
			final CofrePersonalBlockEntity cofre) {
		if (!cofre.isRemoved() && level.getGameTime() % 10L == 0L) {
			cofre.contadorAbiertos.recheckOpeners(level, pos, estado);
		}
	}

	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return this.contadorAbiertos.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
	}
}
