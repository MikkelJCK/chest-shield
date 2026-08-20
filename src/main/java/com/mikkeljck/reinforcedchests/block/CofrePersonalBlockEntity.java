package com.mikkeljck.reinforcedchests.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mikkeljck.reinforcedchests.CofresPersonales;
import com.mikkeljck.reinforcedchests.util.HashClave;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
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
 */
public class CofrePersonalBlockEntity extends BlockEntity implements MenuProvider, LidBlockEntity {

	public static final int TAMANO = 27;
	public static final int LONGITUD_MAXIMA_CLAVE = 32;

	/** Ticks de espera tras un intento fallido, para frenar la fuerza bruta. */
	private static final long ENFRIAMIENTO_TICKS = 40L;

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

	private @Nullable UUID propietario;
	private String nombrePropietario = "";

	/** Se sincroniza al cliente. El hash y el salt JAMAS salen del servidor. */
	private boolean tieneClave;
	private String hashClave = "";
	private String saltClave = "";

	/** Estado volatil, no se guarda en disco. */
	private @Nullable UUID invitadoTemporal;
	private final Map<UUID, Long> esperaHasta = new HashMap<>();

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

	// ---------- Propiedad ----------

	public void asignarPropietario(final Player player) {
		this.propietario = player.getUUID();
		this.nombrePropietario = player.getName().getString();
		this.setChanged();
		this.sincronizarConClientes();
	}

	private void sincronizarConClientes() {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
		}
	}

	public String getNombrePropietario() {
		return this.nombrePropietario.isEmpty() ? "desconocido" : this.nombrePropietario;
	}

	public boolean esPropietario(final Player player) {
		return this.propietario == null || this.propietario.equals(player.getUUID());
	}

	/** Solo mira el item en las manos; sirve igual en cliente y en servidor. */
	public static boolean sostieneLlaveMaestra(final Player player) {
		return player.getMainHandItem().is(CofresPersonales.LLAVE_MAESTRA)
				|| player.getOffhandItem().is(CofresPersonales.LLAVE_MAESTRA);
	}

	/**
	 * Acceso sin contrasena: el dueno, o un admin con la Llave Maestra.
	 *
	 * El nivel de op solo se puede comprobar en el servidor. En el cliente somos
	 * optimistas para que la interfaz responda bien; el servidor tiene siempre la
	 * ultima palabra.
	 */
	public boolean puedeAcceder(final Player player) {
		if (this.esPropietario(player)) {
			return true;
		}
		if (!sostieneLlaveMaestra(player)) {
			return false;
		}
		return !(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public void avisarPropiedad(final Player player) {
		this.avisar(player, Component.translatable("message.reinforced_chests.protegido", this.getNombrePropietario())
				.withStyle(ChatFormatting.RED));
	}

	public void avisar(final Player player, final Component mensaje) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(mensaje);
		}
	}

	// ---------- Contrasena ----------

	public boolean tieneClave() {
		return this.tieneClave;
	}

	/**
	 * Decide si estos dos cofres pueden formar un cofre doble.
	 *
	 * OJO CON EL ORDEN: al colocar un bloque, los vecinos reciben la
	 * actualizacion ANTES de que setPlacedBy le asigne dueno y clave al recien
	 * puesto. Por eso un cofre sin estrenar (dueno nulo, sin clave) cuenta como
	 * compatible: su dueno y su clave se deciden un instante despues. La
	 * seguridad no se pierde porque getStateForPlacement ya comprobo que quien lo
	 * coloco era el dueno del vecino; un cofre ajeno nunca llega a marcarse como
	 * mitad, y sin eso el vecino jamas intenta unirse.
	 */
	public boolean puedeEmparejarseCon(final CofrePersonalBlockEntity otro) {
		if (this.propietario != null && otro.propietario != null
				&& !this.propietario.equals(otro.propietario)) {
			return false;
		}
		// Claves: o alguna esta vacia (esa heredara la de la otra), o son identicas.
		if (!this.tieneClave || !otro.tieneClave) {
			return true;
		}
		return this.hashClave.equals(otro.hashClave) && this.saltClave.equals(otro.saltClave);
	}

	/**
	 * Copia tal cual la clave de la otra mitad, incluido el caso de "sin clave".
	 *
	 * Un cofre doble es un solo mueble para el jugador, asi que las dos mitades
	 * deben compartir siempre la misma clave. Se copian hash y salt en vez de
	 * volver a calcularlos, para que ambas queden identicas y sigan contando como
	 * compatibles.
	 */
	public void copiarClaveDe(final CofrePersonalBlockEntity otra) {
		this.tieneClave = otra.tieneClave;
		this.hashClave = otra.hashClave;
		this.saltClave = otra.saltClave;
		this.esperaHasta.clear();
		this.setChanged();
		this.sincronizarConClientes();
	}

	/** Cadena vacia = quitar la contrasena. Solo debe llamarse en el servidor. */
	public void establecerClave(final String clave) {
		if (clave.isBlank()) {
			this.tieneClave = false;
			this.hashClave = "";
			this.saltClave = "";
		} else {
			this.saltClave = HashClave.nuevoSalt();
			this.hashClave = HashClave.calcular(clave, this.saltClave);
			this.tieneClave = true;
		}
		this.esperaHasta.clear();
		this.setChanged();
		this.sincronizarConClientes();
	}

	public boolean verificarClave(final String clave) {
		return HashClave.coincide(clave, this.saltClave, this.hashClave);
	}

	public boolean enEspera(final Player player) {
		if (this.level == null) {
			return false;
		}
		Long hasta = this.esperaHasta.get(player.getUUID());
		return hasta != null && this.level.getGameTime() < hasta;
	}

	public void registrarFallo(final Player player) {
		if (this.level != null) {
			this.esperaHasta.put(player.getUUID(), this.level.getGameTime() + ENFRIAMIENTO_TICKS);
		}
	}

	/**
	 * Abre el cofre para alguien que acerto la contrasena. El permiso dura
	 * exactamente esta apertura: la proxima vez tendra que teclearla de nuevo.
	 */
	public void abrirParaInvitado(final ServerPlayer player) {
		this.invitadoTemporal = player.getUUID();
		try {
			AperturaCofre.abrir(player, this.level, this.worldPosition, this.getBlockState(), true);
		} finally {
			this.invitadoTemporal = null;
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
		if (this.propietario != null) {
			output.store("Propietario", UUIDUtil.CODEC, this.propietario);
			output.putString("NombrePropietario", this.nombrePropietario);
		}
		output.putBoolean("TieneClave", this.tieneClave);
		if (this.tieneClave) {
			output.putString("HashClave", this.hashClave);
			output.putString("SaltClave", this.saltClave);
		}
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.inventario.getItems().replaceAll(stack -> ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.inventario.getItems());
		this.propietario = input.read("Propietario", UUIDUtil.CODEC).orElse(null);
		this.nombrePropietario = input.getStringOr("NombrePropietario", "");
		this.tieneClave = input.getBooleanOr("TieneClave", false);
		this.hashClave = input.getStringOr("HashClave", "");
		this.saltClave = input.getStringOr("SaltClave", "");
	}

	// ---------- Sincronizacion con el cliente ----------
	// Solo viajan el duenno y si el cofre tiene clave o no. El inventario, el
	// hash y el salt JAMAS se envian: el cliente no debe poder leer ni el
	// contenido de un cofre ajeno ni nada que sirva para romper la clave.

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		if (this.propietario != null) {
			tag.store("Propietario", UUIDUtil.CODEC, this.propietario);
			tag.putString("NombrePropietario", this.nombrePropietario);
		}
		tag.putBoolean("TieneClave", this.tieneClave);
		return tag;
	}

	// ---------- Menu ----------

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.reinforced_chests.reinforced_chest");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		boolean invitado = player.getUUID().equals(this.invitadoTemporal);
		if (!invitado && !this.puedeAcceder(player)) {
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
