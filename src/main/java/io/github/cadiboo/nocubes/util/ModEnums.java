package io.github.cadiboo.nocubes.util;

import io.github.cadiboo.nocubes.client.render.MarchingCubes;
import io.github.cadiboo.nocubes.client.render.MarchingTetrahedra;
import io.github.cadiboo.nocubes.client.render.OldNoCubes;
import io.github.cadiboo.nocubes.client.render.SurfaceNets;
import io.github.cadiboo.nocubes.client.render.SurfaceNetsDev;
import io.github.cadiboo.nocubes.debug.client.render.DebugOldNoCubes;
import io.github.cadiboo.nocubes.debug.client.render.IDebugRenderAlgorithm;
import io.github.cadiboo.nocubes.debug.client.render.IDebugRenderAlgorithm.Face;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkBlockEvent;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkBlockRenderInLayerEvent;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkBlockRenderInTypeEvent;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkPostEvent;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkPreEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Holds all enums and enum-related stuff for this mod
 *
 * @author Cadiboo
 */
public final class ModEnums {

	public enum StableRenderAlgorithm implements IEnumNameFormattable {

		SURFACE_NETS(SurfaceNets::renderPre, SurfaceNets::renderLayer, SurfaceNets::renderType, SurfaceNets::renderBlock, SurfaceNets::renderPost, SurfaceNets::getPoints),

		MARCHING_CUBES(MarchingCubes::renderPre, MarchingCubes::renderLayer, MarchingCubes::renderType, MarchingCubes::renderBlock, MarchingCubes::renderPost, MarchingCubes::getPoints),

		OLD_NO_CUBES(OldNoCubes::renderPre, OldNoCubes::renderLayer, OldNoCubes::renderType, OldNoCubes::renderBlock, OldNoCubes::renderPost, OldNoCubes::getPoints),

		MARCHING_TETRAHEDRA(MarchingTetrahedra::renderPre, MarchingTetrahedra::renderLayer, MarchingTetrahedra::renderType, MarchingTetrahedra::renderBlock, MarchingTetrahedra::renderPost, MarchingTetrahedra::getPoints),

		SURFACE_NETS_DEV(SurfaceNetsDev::renderPre, SurfaceNetsDev::renderLayer, SurfaceNetsDev::renderType, SurfaceNetsDev::renderBlock, SurfaceNetsDev::renderPost, SurfaceNets::getPoints),

		NONE(event -> {
		}, event -> {
		}, event -> {
		}, event -> {
		}, event -> {
		}, (blockPos, world) -> null),

		;

		private final Consumer<RebuildChunkPreEvent> renderPre;

		private final Consumer<RebuildChunkBlockRenderInLayerEvent> renderLayer;

		private final Consumer<RebuildChunkBlockRenderInTypeEvent> renderType;

		private final Consumer<RebuildChunkBlockEvent> renderBlock;

		private final Consumer<RebuildChunkPostEvent> renderPost;

		private final BiFunction<BlockPos, World, Vec3[]> getPoints;

		StableRenderAlgorithm(final Consumer<RebuildChunkPreEvent> renderPre, final Consumer<RebuildChunkBlockRenderInLayerEvent> renderLayer, final Consumer<RebuildChunkBlockRenderInTypeEvent> renderType, final Consumer<RebuildChunkBlockEvent> renderBlock, final Consumer<RebuildChunkPostEvent> renderPost, final BiFunction<BlockPos, World, Vec3[]> getPoints) {
			this.renderPre = renderPre;
			this.renderLayer = renderLayer;
			this.renderType = renderType;
			this.renderBlock = renderBlock;
			this.renderPost = renderPost;
			this.getPoints = getPoints;
		}

		public void renderPre(final RebuildChunkPreEvent event) {
			renderPre.accept(event);
		}

		public void renderLayer(final RebuildChunkBlockRenderInLayerEvent event) {
			renderLayer.accept(event);
		}

		public void renderType(final RebuildChunkBlockRenderInTypeEvent event) {
			renderType.accept(event);
		}

		public void renderBlock(final RebuildChunkBlockEvent event) {
			this.renderBlock.accept(event);
		}

		public void renderPost(final RebuildChunkPostEvent event) {
			renderPost.accept(event);
		}

		public Vec3[] getPoints(final BlockPos blockPos, final World world) {
			return getPoints.apply(blockPos, world);
		}
	}

	public enum DebugRenderAlgorithm implements IEnumNameFormattable {

		OLD_NO_CUBES(new DebugOldNoCubes()),

		;

		private final IDebugRenderAlgorithm renderAlgorithm;

		DebugRenderAlgorithm(final IDebugRenderAlgorithm renderAlgorithm) {

			this.renderAlgorithm = renderAlgorithm;
		}

		@Nonnull
		public List<Vec3> getVertices(final BlockPos blockPos, final IBlockAccess world) {
			return this.renderAlgorithm.getVertices(blockPos, world);
		}

		@Nonnull
		public List<Face<Vec3>> getFaces(final BlockPos blockPos, final IBlockAccess world) {
			return this.renderAlgorithm.getFaces(blockPos, world);
		}

	}

	public static enum EffortLevel implements IEnumNameFormattable {
		OFF,
		FAST,
		FANCY,

		;
	}

	/**
	 * provides some default methods for formatting enum names
	 *
	 * @author Cadiboo
	 */
	public interface IEnumNameFormattable {

		/**
		 * Converts the name to lowercase as per {@link java.lang.String#toLowerCase() String.toLowerCase}.
		 */
		default String getNameLowercase() {
			return this.name().toLowerCase();
		}

		/**
		 * Converts the name to uppercase as per {@link java.lang.String#toUpperCase() String.toUpperCase}.
		 */
		default String getNameUppercase() {
			return this.getNameLowercase().toUpperCase();
		}

		/**
		 * Capitalizes the name of the material as per {@link org.apache.commons.lang3.StringUtils#capitalize(String) StringUtils.capitalize}.
		 */
		default String getNameFormatted() {
			return StringUtils.capitalize(this.getNameLowercase());
		}

		/* not exactly hacky, but this method is provided by enum */
		String name();

	}

}
