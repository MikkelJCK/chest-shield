package com.mikkeljck.chestshield.client.render;

import java.util.Map;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.block.CofrePersonalBlock;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Dibuja el Cofre Personal con el modelo de cofre vanilla y su tapa animada.
 *
 * No se puede reutilizar ChestRenderer: ese esta atado a un enum cerrado de
 * materiales vanilla y a ChestBlock. Pero si podemos reutilizar los modelos
 * (ModelLayers.CHEST y las dos mitades) y colgar nuestras texturas en el mismo
 * atlas de cofres.
 *
 * En un cofre doble cada mitad se dibuja por separado, cada una con su propio
 * modelo y su propia textura. Por eso hay tres de cada.
 */
public class CofrePersonalRenderer implements BlockEntityRenderer<CofrePersonalBlockEntity, CofrePersonalRenderState> {

	private static final SpriteId SPRITE_SIMPLE = Sheets.CHEST_MAPPER.apply(CofresPersonales.id("shielded_chest"));
	private static final SpriteId SPRITE_IZQUIERDA = Sheets.CHEST_MAPPER.apply(CofresPersonales.id("shielded_chest_left"));
	private static final SpriteId SPRITE_DERECHA = Sheets.CHEST_MAPPER.apply(CofresPersonales.id("shielded_chest_right"));

	private static final Map<Direction, Transformation> GIROS = Util.makeEnumMap(
			Direction.class,
			cara -> new Transformation(new Matrix4f().rotationAround(
					Axis.YP.rotationDegrees(-cara.toYRot()), 0.5F, 0.0F, 0.5F)));

	private final ChestModel modeloSimple;
	private final ChestModel modeloIzquierda;
	private final ChestModel modeloDerecha;
	private final SpriteGetter sprites;

	public CofrePersonalRenderer(final BlockEntityRendererProvider.Context contexto) {
		this.modeloSimple = new ChestModel(contexto.bakeLayer(ModelLayers.CHEST));
		this.modeloIzquierda = new ChestModel(contexto.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT));
		this.modeloDerecha = new ChestModel(contexto.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT));
		this.sprites = contexto.sprites();
	}

	@Override
	public CofrePersonalRenderState createRenderState() {
		return new CofrePersonalRenderState();
	}

	@Override
	public void extractRenderState(
			final CofrePersonalBlockEntity cofre,
			final CofrePersonalRenderState estado,
			final float parcial,
			final Vec3 posicionCamara,
			final ModelFeatureRenderer.@Nullable CrumblingOverlay progresoRotura) {
		BlockEntityRenderer.super.extractRenderState(cofre, estado, parcial, posicionCamara, progresoRotura);

		BlockState estadoBloque = cofre.getBlockState();
		estado.orientacion = estadoBloque.hasProperty(CofrePersonalBlock.FACING)
				? estadoBloque.getValue(CofrePersonalBlock.FACING)
				: Direction.SOUTH;
		estado.tipo = estadoBloque.hasProperty(CofrePersonalBlock.TYPE)
				? estadoBloque.getValue(CofrePersonalBlock.TYPE)
				: ChestType.SINGLE;
		estado.apertura = aperturaCombinada(cofre, estadoBloque, parcial);
		estado.lightCoords = luzCombinada(cofre, estadoBloque, estado.lightCoords);
	}

	/**
	 * Las dos mitades de un cofre doble tienen que iluminarse igual.
	 *
	 * Cada mitad es un BlockEntity distinto y el juego le calcula la luz en SU
	 * bloque, asi que con una antorcha a un lado una mitad salia clara y la otra
	 * oscura, con una costura muy visible en el medio. Vanilla resuelve esto
	 * tomando la luz mayor de las dos posiciones; aqui se hace lo mismo.
	 */
	private static int luzCombinada(final CofrePersonalBlockEntity cofre, final BlockState estado,
			final int propia) {
		Level level = cofre.getLevel();
		if (level == null || !(estado.getBlock() instanceof CofrePersonalBlock bloque)) {
			return propia;
		}
		// BrightnessCombiner es la misma pieza que usa el cofre de vanilla: en un
		// cofre doble devuelve la luz mayor de las dos mitades, y en uno simple
		// deja pasar la que ya venia. Se apoya en el mismo combinador de mitades
		// que ya usamos para la tapa y el sonido.
		return bloque.combinar(estado, level, cofre.getBlockPos(), true)
				.apply(new BrightnessCombiner<CofrePersonalBlockEntity>())
				.applyAsInt(propia);
	}

	/**
	 * Las dos tapas de un cofre doble deben moverse juntas. Tomamos la mayor de
	 * las dos aperturas, igual que vanilla.
	 */
	private static float aperturaCombinada(final CofrePersonalBlockEntity cofre, final BlockState estado,
			final float parcial) {
		float propia = cofre.getOpenNess(parcial);
		Level level = cofre.getLevel();
		if (level == null || !estado.hasProperty(CofrePersonalBlock.TYPE)
				|| estado.getValue(CofrePersonalBlock.TYPE) == ChestType.SINGLE) {
			return propia;
		}
		return level.getBlockEntity(cofre.getBlockPos().relative(CofrePersonalBlock.direccionUnion(estado)))
				instanceof CofrePersonalBlockEntity pareja
						? Math.max(propia, pareja.getOpenNess(parcial))
						: propia;
	}

	@Override
	public void submit(
			final CofrePersonalRenderState estado,
			final PoseStack pila,
			final SubmitNodeCollector coleccion,
			final CameraRenderState camara) {
		pila.pushPose();
		pila.mulPose(GIROS.get(estado.orientacion));

		// Misma curva que el cofre vanilla: la tapa arranca rapido y frena al final.
		float abierto = 1.0F - estado.apertura;
		abierto = 1.0F - abierto * abierto * abierto;

		ChestModel modelo = switch (estado.tipo) {
			case SINGLE -> this.modeloSimple;
			case LEFT -> this.modeloIzquierda;
			case RIGHT -> this.modeloDerecha;
		};
		SpriteId sprite = switch (estado.tipo) {
			case SINGLE -> SPRITE_SIMPLE;
			case LEFT -> SPRITE_IZQUIERDA;
			case RIGHT -> SPRITE_DERECHA;
		};

		coleccion.submitModel(modelo, abierto, pila, estado.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, sprite, this.sprites, 0, estado.breakProgress);

		pila.popPose();
	}
}
