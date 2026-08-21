package com.mikkeljck.chestshield.block;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CofrePersonalBlock extends BaseEntityBlock {

	public static final MapCodec<CofrePersonalBlock> CODEC = simpleCodec(CofrePersonalBlock::new);

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;

	/** Mismas cajas de colision que el cofre vanilla. */
	private static final VoxelShape FORMA = Block.column(14.0, 0.0, 14.0);
	private static final Map<Direction, VoxelShape> FORMAS_MITAD = Shapes.rotateHorizontal(Block.boxZ(14.0, 0.0, 14.0, 0.0, 15.0));

	public CofrePersonalBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(FACING, Direction.NORTH)
				.setValue(TYPE, ChestType.SINGLE));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TYPE);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new CofrePersonalBlockEntity(pos, state);
	}

	// ---------- Cofres dobles ----------

	public static DoubleBlockCombiner.BlockType tipoCombinacion(final BlockState estado) {
		return switch (estado.getValue(TYPE)) {
			case SINGLE -> DoubleBlockCombiner.BlockType.SINGLE;
			case RIGHT -> DoubleBlockCombiner.BlockType.FIRST;
			case LEFT -> DoubleBlockCombiner.BlockType.SECOND;
		};
	}

	public static Direction direccionUnion(final BlockState estado) {
		Direction cara = estado.getValue(FACING);
		return estado.getValue(TYPE) == ChestType.LEFT ? cara.getClockWise() : cara.getCounterClockWise();
	}

	/**
	 * Combina este cofre con su pareja, si la tiene.
	 *
	 * @param ignorarBloqueo true para operaciones que no son "abrir" (por ejemplo
	 *                       calcular el nombre), donde un bloque encima no importa.
	 */
	public DoubleBlockCombiner.NeighborCombineResult<CofrePersonalBlockEntity> combinar(
			final BlockState estado, final Level level, final BlockPos pos, final boolean ignorarBloqueo) {
		BiPredicate<LevelAccessor, BlockPos> bloqueado = ignorarBloqueo
				? (acceso, posicion) -> false
				: CofrePersonalBlock::estaBloqueado;

		return DoubleBlockCombiner.combineWithNeigbour(
				CofresPersonales.COFRE_PERSONAL_BE,
				CofrePersonalBlock::tipoCombinacion,
				CofrePersonalBlock::direccionUnion,
				FACING, estado, level, pos, bloqueado);
	}

	/**
	 * Dos cofres solo se unen si son del MISMO DUENO y sus contrasenas son
	 * compatibles. Sin esta comprobacion, cualquiera podria pegar su cofre al tuyo
	 * y quedarse con acceso a tu inventario.
	 */
	private static boolean puedenUnirse(final BlockGetter level, final BlockPos posA, final BlockPos posB) {
		if (!(level.getBlockEntity(posA) instanceof CofrePersonalBlockEntity a)
				|| !(level.getBlockEntity(posB) instanceof CofrePersonalBlockEntity b)) {
			return false;
		}
		return a.puedeEmparejarseCon(b);
	}

	/** Version para el momento de colocar: el cofre nuevo aun no tiene BlockEntity. */
	private static boolean vecinoAceptaA(final BlockGetter level, final BlockPos posVecino, final @Nullable Player colocador) {
		if (!(level.getBlockEntity(posVecino) instanceof CofrePersonalBlockEntity vecino)) {
			return false;
		}
		return colocador != null && vecino.esPropietario(colocador);
	}

	private @Nullable Direction caraDeParejaCandidata(final Level level, final BlockPos pos,
			final Direction hacia, final @Nullable Player colocador) {
		BlockPos posVecino = pos.relative(hacia);
		BlockState vecino = level.getBlockState(posVecino);
		if (!vecino.is(this) || vecino.getValue(TYPE) != ChestType.SINGLE) {
			return null;
		}
		if (!vecinoAceptaA(level, posVecino, colocador)) {
			return null;
		}
		return vecino.getValue(FACING);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext contexto) {
		ChestType tipo = ChestType.SINGLE;
		Direction cara = contexto.getHorizontalDirection().getOpposite();
		Player colocador = contexto.getPlayer();
		boolean agachado = contexto.isSecondaryUseActive();
		Direction caraClickeada = contexto.getClickedFace();

		if (caraClickeada.getAxis().isHorizontal() && agachado) {
			Direction caraVecino = this.caraDeParejaCandidata(
					contexto.getLevel(), contexto.getClickedPos(), caraClickeada.getOpposite(), colocador);
			if (caraVecino != null && caraVecino.getAxis() != caraClickeada.getAxis()) {
				cara = caraVecino;
				tipo = cara.getCounterClockWise() == caraClickeada.getOpposite() ? ChestType.RIGHT : ChestType.LEFT;
			}
		}

		if (tipo == ChestType.SINGLE && !agachado) {
			Level level = contexto.getLevel();
			BlockPos pos = contexto.getClickedPos();
			if (cara == this.caraDeParejaCandidata(level, pos, cara.getClockWise(), colocador)) {
				tipo = ChestType.LEFT;
			} else if (cara == this.caraDeParejaCandidata(level, pos, cara.getCounterClockWise(), colocador)) {
				tipo = ChestType.RIGHT;
			}
		}

		return this.defaultBlockState().setValue(FACING, cara).setValue(TYPE, tipo);
	}

	@Override
	protected BlockState updateShape(final BlockState estado, final LevelReader level, final ScheduledTickAccess ticks,
			final BlockPos pos, final Direction haciaVecino, final BlockPos posVecino, final BlockState vecino,
			final RandomSource random) {
		if (vecino.is(this) && haciaVecino.getAxis().isHorizontal()) {
			ChestType tipoVecino = vecino.getValue(TYPE);
			if (estado.getValue(TYPE) == ChestType.SINGLE
					&& tipoVecino != ChestType.SINGLE
					&& estado.getValue(FACING) == vecino.getValue(FACING)
					&& direccionUnion(vecino) == haciaVecino.getOpposite()
					&& puedenUnirse(level, pos, posVecino)) {
				return estado.setValue(TYPE, tipoVecino.getOpposite());
			}
		} else if (direccionUnion(estado) == haciaVecino) {
			return estado.setValue(TYPE, ChestType.SINGLE);
		}
		return super.updateShape(estado, level, ticks, pos, haciaVecino, posVecino, vecino, random);
	}

	@Override
	protected VoxelShape getShape(final BlockState estado, final BlockGetter level, final BlockPos pos,
			final CollisionContext contexto) {
		return estado.getValue(TYPE) == ChestType.SINGLE
				? FORMA
				: FORMAS_MITAD.get(direccionUnion(estado));
	}

	// ---------- Bloqueo por arriba ----------

	/**
	 * Igual que el cofre vanilla: no se abre si tiene un bloque solido encima o
	 * un gato sentado arriba.
	 */
	public static boolean estaBloqueado(final LevelAccessor level, final BlockPos pos) {
		BlockPos arriba = pos.above();
		if (level.getBlockState(arriba).isRedstoneConductor(level, arriba)) {
			return true;
		}
		List<Cat> gatos = level.getEntitiesOfClass(Cat.class, new AABB(
				pos.getX(), pos.getY() + 1, pos.getZ(),
				pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1));
		for (Cat gato : gatos) {
			if (gato.isInSittingPose()) {
				return true;
			}
		}
		return false;
	}

	/** Comprueba las dos mitades: basta con que una este tapada. */
	public static boolean parejaBloqueada(final Level level, final BlockPos pos, final BlockState estado) {
		if (estaBloqueado(level, pos)) {
			return true;
		}
		if (estado.getValue(TYPE) == ChestType.SINGLE) {
			return false;
		}
		return estaBloqueado(level, pos.relative(direccionUnion(estado)));
	}

	// ---------- Interaccion ----------

	@Override
	public void setPlacedBy(final Level level, final BlockPos pos, final BlockState estado,
			final @Nullable LivingEntity colocador, final ItemStack pila) {
		super.setPlacedBy(level, pos, estado, colocador, pila);
		if (level.isClientSide() || !(colocador instanceof Player jugador)) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre)) {
			return;
		}
		cofre.asignarPropietario(jugador);

		// Si nacio siendo mitad de un cofre doble, hereda la clave de su pareja.
		if (estado.getValue(TYPE) != ChestType.SINGLE
				&& level.getBlockEntity(pos.relative(direccionUnion(estado))) instanceof CofrePersonalBlockEntity pareja) {
			cofre.copiarAjustesDe(pareja);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState estado, final Level level, final BlockPos pos,
			final Player jugador, final BlockHitResult golpe) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (parejaBloqueada(level, pos, estado)) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre) {
			// Agachado + dueno significa "configurar contrasena", nunca "abrir".
			if (jugador.isShiftKeyDown() && cofre.esPropietario(jugador)) {
				return InteractionResult.SUCCESS;
			}
			if (cofre.puedeAcceder(jugador)) {
				AperturaCofre.abrir(jugador, level, pos, estado, false);
			} else if (!cofre.tieneClave()) {
				cofre.avisarPropiedad(jugador);
			}
			// Con clave no hacemos nada: el cliente ya abrio la pantalla.
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected float getDestroyProgress(final BlockState estado, final Player jugador, final BlockGetter level,
			final BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre && !cofre.puedeAcceder(jugador)) {
			return 0.0F;
		}
		return super.getDestroyProgress(estado, jugador, level, pos);
	}

	@Override
	protected void attack(final BlockState estado, final Level level, final BlockPos pos, final Player jugador) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre
				&& !cofre.puedeAcceder(jugador)) {
			cofre.avisarPropiedad(jugador);
		}
		super.attack(estado, level, pos, jugador);
	}

	/** Al romperlo el dueno (o un admin con llave), suelta solo SU mitad. */
	@Override
	public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState estado,
			final Player jugador) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre
				&& cofre.puedeAcceder(jugador)) {
			Containers.dropContents(level, pos, cofre.getInventario());
		}
		return super.playerWillDestroy(level, pos, estado, jugador);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState estado,
			final BlockEntityType<T> tipo) {
		return createTickerHelper(tipo, CofresPersonales.COFRE_PERSONAL_BE,
				level.isClientSide()
						? CofrePersonalBlockEntity::animarTapa
						: CofrePersonalBlockEntity::revisarAbiertos);
	}
}
