package io.github.cadiboo.nocubes.client;

import io.github.cadiboo.nocubes.NoCubes;
import io.github.cadiboo.nocubes.client.render.ExtendedLiquidChunkRenderer;
import io.github.cadiboo.nocubes.client.render.MeshRenderer;
import io.github.cadiboo.nocubes.config.ModConfig;
import io.github.cadiboo.nocubes.mesh.MeshGenerator;
import io.github.cadiboo.nocubes.util.CacheUtil;
import io.github.cadiboo.nocubes.util.ModProfiler;
import io.github.cadiboo.nocubes.util.ModUtil;
import io.github.cadiboo.nocubes.util.PooledDensityCache;
import io.github.cadiboo.nocubes.util.PooledFace;
import io.github.cadiboo.nocubes.util.Vec3.PooledVec3;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkBlockEvent;
import io.github.cadiboo.renderchunkrebuildchunkhooks.event.RebuildChunkPreEvent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockModelRenderer.AmbientOcclusionFace;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnknownConstructorException;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.github.cadiboo.renderchunkrebuildchunkhooks.hooks.RenderChunkRebuildChunkHooksHooks.renderChunk_preRenderBlocks;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static net.minecraft.util.BlockRenderLayer.CUTOUT;
import static net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED;
import static net.minecraft.util.EnumFacing.DOWN;
import static net.minecraft.util.EnumFacing.EAST;
import static net.minecraft.util.EnumFacing.NORTH;
import static net.minecraft.util.EnumFacing.SOUTH;
import static net.minecraft.util.EnumFacing.UP;
import static net.minecraft.util.EnumFacing.WEST;
import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.getPositionRandom;

/**
 * Util that is only used on the Physical Client i.e. Rendering code
 *
 * @author Cadiboo
 */
@SuppressWarnings("WeakerAccess")
@SideOnly(Side.CLIENT)
public final class ClientUtil {

	/**
	 * The order of {@link EnumFacing} and null used in {@link #getQuad(IBlockState, BlockPos, BlockRendererDispatcher)}
	 */
	public static final EnumFacing[] ENUMFACING_QUADS_ORDERED = {
			UP, null, DOWN, NORTH, EAST, SOUTH, WEST,
	};

	// add or subtract from the sprites UV location to remove transparent lines in between textures
	private static final float UV_CORRECT = 1 / 10000F;

//	private static final ThreadLocal<HashMap<BlockPos, HashMap<BlockPos, Object[]>>> RENDER_LIQUID_POSITIONS = ThreadLocal.withInitial(HashMap::new);

	private static final Constructor<AmbientOcclusionFace> ambientOcclusionFace;
	private static final boolean ambientOcclusionFaceNeedsBlockModelRenderer;
	static {
		Constructor<AmbientOcclusionFace> ambientOcclusionFaceConstructor = null;
		boolean needsBlockModelRenderer = false;
		try {
			try {
				//TODO: stop using ReflectionHelper
				ambientOcclusionFaceConstructor = ReflectionHelper.findConstructor(AmbientOcclusionFace.class);
			} catch (UnknownConstructorException e) {
				//TODO: stop using ReflectionHelper
				ambientOcclusionFaceConstructor = ReflectionHelper.findConstructor(AmbientOcclusionFace.class, BlockModelRenderer.class);
				needsBlockModelRenderer = true;
			}
		} catch (Exception e) {
			final CrashReport crashReport = new CrashReport("Unable to find constructor for BlockModelRenderer$AmbientOcclusionFace", e);
			crashReport.makeCategory("Finding Constructor");
			throw new ReportedException(crashReport);
		}
		ambientOcclusionFace = ambientOcclusionFaceConstructor;
		ambientOcclusionFaceNeedsBlockModelRenderer = needsBlockModelRenderer;
	}

	/**
	 * @param red   the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @param green the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @param blue  the red value of the color, between 0x00 (decimal 0) and 0xFF (decimal 255)
	 * @return the color in ARGB format
	 */
	public static int color(int red, int green, int blue) {

		red = clamp(red, 0x00, 0xFF);
		green = clamp(green, 0x00, 0xFF);
		blue = clamp(blue, 0x00, 0xFF);

		final int alpha = 0xFF;

		// 0x alpha red green blue
		// 0xaarrggbb

		// int colorRGBA = 0;
		// colorRGBA |= red << 16;
		// colorRGBA |= green << 8;
		// colorRGBA |= blue << 0;
		// colorRGBA |= alpha << 24;

		return blue | red << 16 | green << 8 | alpha << 24;

	}

	/**
	 * @param red   the red value of the color, 0F and 1F
	 * @param green the green value of the color, 0F and 1F
	 * @param blue  the blue value of the color, 0F and 1F
	 * @return the color in ARGB format
	 */
	public static int colorf(final float red, final float green, final float blue) {
		final int redInt = max(0, min(255, round(red * 255)));
		final int greenInt = max(0, min(255, round(green * 255)));
		final int blueInt = max(0, min(255, round(blue * 255)));
		return color(redInt, greenInt, blueInt);
	}

	public static int getLightmapSkyLightCoordsFromPackedLightmapCoords(int packedLightmapCoords) {
		return (packedLightmapCoords >> 16) & 0xFFFF; // get upper 4 bytes
	}

	public static int getLightmapBlockLightCoordsFromPackedLightmapCoords(int packedLightmapCoords) {
		return packedLightmapCoords & 0xFFFF; // get lower 4 bytes
	}

	/**
	 * Gets The first quad of a model for a pos & state or null if the model has no quads
	 *
	 * @param state                   the state
	 * @param pos                     the position used in {@link MathHelper#getPositionRandom(Vec3i)}
	 * @param blockRendererDispatcher the {@link BlockRendererDispatcher} to get the model from
	 * @return The first quad or null if the model has no quads
	 */
	@Nullable
	public static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final BlockRendererDispatcher blockRendererDispatcher) {
		final long posRand = getPositionRandom(pos);
		final IBakedModel model = blockRendererDispatcher.getModelForState(state);
		return getQuad(state, pos, posRand, model, ENUMFACING_QUADS_ORDERED);
	}

	/**
	 * Gets The first quad of a model for a pos & state or null if the model has no quads
	 *
	 * @param state                   the state
	 * @param pos                     the position used in {@link MathHelper#getPositionRandom(Vec3i)}
	 * @param blockRendererDispatcher the {@link BlockRendererDispatcher} to get the model from
	 * @param facing                  the {@link EnumFacing to check first}
	 * @return The first quad or null if the model has no quads
	 */
	@Nullable
	public static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final BlockRendererDispatcher blockRendererDispatcher, EnumFacing facing) {
		final long posRand = getPositionRandom(pos);
		final IBakedModel model = blockRendererDispatcher.getModelForState(state);
		final BakedQuad quad = getQuad(state, pos, posRand, model, facing);
		if (quad != null) {
			return quad;
		} else {
			return getQuad(state, pos, posRand, model, ENUMFACING_QUADS_ORDERED);
		}
	}

	/**
	 * helper method to actually get the quads
	 */
	@Nullable
	private static BakedQuad getQuad(final IBlockState state, final BlockPos pos, final long posRand, final IBakedModel model, final EnumFacing... facings) {
		for (EnumFacing facing : facings) {
			final List<BakedQuad> quads = model.getQuads(state, facing, posRand);
			if (!quads.isEmpty()) {
				return quads.get(0);
			}
		}
		return null;
	}

	/**
	 * Gets the color of a quad through a block at a pos
	 *
	 * @param quad  the quad
	 * @param state the state
	 * @param cache the cache
	 * @param pos   the pos
	 * @return the color
	 */
	public static int getColor(final BakedQuad quad, final IBlockState state, final IBlockAccess cache, final BlockPos pos) {
		final int red;
		final int green;
		final int blue;

		if (quad.hasTintIndex()) {
			final int colorMultiplier = Minecraft.getMinecraft().getBlockColors().colorMultiplier(state, cache, pos, 0);
			red = (colorMultiplier >> 16) & 255;
			green = (colorMultiplier >> 8) & 255;
			blue = colorMultiplier & 255;
		} else {
			red = 0xFF;
			green = 0xFF;
			blue = 0xFF;
		}
		return color(red, green, blue);
	}

//	public static void extendLiquids(final RebuildChunkBlockEvent event) {
//
//		final IBlockState state = event.getBlockState();
//		if (!ModUtil.shouldSmooth(state)) {
//			return;
//		}
//		final ChunkCache cache = event.getChunkCache();
//		final BlockPos pos = event.getBlockPos();
//
//		MutableBlockPos liquidPos = null;
//		IBlockState liquidState = null;
//
//		for (final MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
//			final IBlockState tempState = cache.getBlockState(mutablePos);
//			if (tempState.getBlock() instanceof BlockLiquid) {
//				//usually we would make it immutable, but since it wont be changed anymore we can just reference it without worrying about that
//				liquidPos = mutablePos;
//				liquidState = tempState;
//				break;
//			}
//		}
//
//		// set at same time so can skip
//		if (liquidPos == null /*|| liquidState == null*/) {
//			return;
//		}
//
//		event.getBlockRendererDispatcher().renderBlock(liquidState, pos, cache, event.getBufferBuilder());
//
//	}

	/**
	 * Gets the fixed minimum U coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed minimum U coordinate to use when rendering the sprite
	 */
	public static float getMinU(final TextureAtlasSprite sprite) {
		return sprite.getMinU() + UV_CORRECT;
	}

	/**
	 * Gets the fixed maximum U coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed maximum U coordinate to use when rendering the sprite
	 */
	public static float getMaxU(final TextureAtlasSprite sprite) {
		return sprite.getMaxU() - UV_CORRECT;
	}

	/**
	 * Gets the fixed minimum V coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed minimum V coordinate to use when rendering the sprite
	 */
	public static float getMinV(final TextureAtlasSprite sprite) {
		return sprite.getMinV() + UV_CORRECT;
	}

	/**
	 * Gets the fixed maximum V coordinate to use when rendering the sprite.
	 *
	 * @param sprite the sprite
	 * @return The fixed maximum V coordinate to use when rendering the sprite
	 */
	public static float getMaxV(final TextureAtlasSprite sprite) {
		return sprite.getMaxV() - UV_CORRECT;
	}

//	@Deprecated
//	//FIXME
//	public static void calculateExtendedLiquids(final RebuildChunkPreEvent event) {
//
//		Minecraft.getMinecraft().profiler.startSection("Rendering smooth world liquids in Pre");
//
//		final BlockPos renderChunkPosition = event.getRenderChunkPosition();
//		final ChunkCache cache = event.getChunkCache();
//
//		final HashMap<BlockPos, Object[]> map = new HashMap<>();
//		RENDER_LIQUID_POSITIONS.get().put(renderChunkPosition, map);
//
//		// 18 * 18 * 18 add 1 block on each side of chunk
//		final boolean[] isLiquid = new boolean[5832];
//
//		for (MutableBlockPos mutableBlockPos : BlockPos.getAllInBoxMutable(renderChunkPosition.add(-1, -1, -1), renderChunkPosition.add(16, 16, 16))) {
//			final BlockPos sub = mutableBlockPos.subtract(renderChunkPosition);
//			final int x = sub.getX() + 1;
//			final int y = sub.getY() + 1;
//			final int z = sub.getZ() + 1;
//			// Flat[x + WIDTH * (y + HEIGHT * z)] = Original[x, y, z]
//			isLiquid[x + 18 * (y + 18 * z)] = cache.getBlockState(mutableBlockPos).getBlock() instanceof BlockLiquid && !(cache.getBlockState(mutableBlockPos.up()).getBlock() instanceof BlockLiquid);
//		}
//
//		for (MutableBlockPos mutableBlockPos : BlockPos.getAllInBoxMutable(renderChunkPosition, renderChunkPosition.add(15, 15, 15))) {
//			IF:
//			if (ModUtil.shouldSmooth(cache.getBlockState(mutableBlockPos))) {
//				final BlockPos sub = mutableBlockPos.subtract(renderChunkPosition);
//				final int x = sub.getX() + 1;
//				final int y = sub.getY() + 1;
//				final int z = sub.getZ() + 1;
//				for (int xOff = -1; xOff <= 1; xOff++) {
//					for (int zOff = -1; zOff <= 1; zOff++) {
//						if (isLiquid[(x + xOff) + 18 * (y + 18 * (z + zOff))]) {
//
//							final BlockPos potentialLiquidPos = mutableBlockPos.add(xOff, 0, zOff);
//							final IBlockState liquidState = cache.getBlockState(potentialLiquidPos);
//
//							if (!(liquidState.getBlock() instanceof BlockLiquid)) {
//								continue;
//							}
//
//							// if not source block
//							if (liquidState.getValue(BlockLiquid.LEVEL) != 0) {
//								continue;
//							}
//							map.put(mutableBlockPos.toImmutable(), new Object[]{potentialLiquidPos.toImmutable(), liquidState});
//							break IF;
//						}
//					}
//				}
//			}
//		}
//
//		Minecraft.getMinecraft().profiler.endSection();
//
//	}
//
//	public static void handleExtendedLiquidRender(final RebuildChunkBlockEvent event) {
//
//		final BlockPos renderChunkPos = event.getRenderChunkPosition();
//		final HashMap<BlockPos, Object[]> map = RENDER_LIQUID_POSITIONS.get().get(renderChunkPos);
//		final BlockPos pos = event.getBlockPos();
//		final Object[] data = map.get(pos);
//
//		if (data == null) {
//			return;
//		}
//
//		Minecraft.getMinecraft().profiler.startSection("Rendering smooth world liquid in Block");
//
//		final BlockPos liquidPos = (BlockPos) data[0];
//		final IBlockState liquidState = (IBlockState) data[1];
//		final ChunkCache cache = event.getChunkCache();
//
//		final BlockRenderLayer blockRenderLayer = getRenderLayer(liquidState);
//		final BufferBuilder bufferBuilder = event.getGenerator().getRegionRenderCacheBuilder().getWorldRendererByLayer(blockRenderLayer);
//		final CompiledChunk compiledChunk = event.getCompiledChunk();
//		final RenderChunk renderChunk = event.getRenderChunk();
//
//		if (!compiledChunk.isLayerStarted(blockRenderLayer)) {
//			compiledChunk.setLayerStarted(blockRenderLayer);
//			event.getUsedBlockRenderLayers()[blockRenderLayer.ordinal()] = true;
//			renderChunk_preRenderBlocks(renderChunk, bufferBuilder, renderChunkPos);
//		}
//
//		OptifineCompatibility.pushShaderThing(liquidState, liquidPos, cache, bufferBuilder);
//
////		FluidInBlockRenderer.renderLiquidInBlock(liquidState, liquidPos, pos, cache, bufferBuilder);
//
//		OptifineCompatibility.popShaderThing(bufferBuilder);
//
//		map.remove(pos);
//
//		Minecraft.getMinecraft().profiler.endSection();
//
//	}
//
//	public static void cleanupExtendedLiquids(final RebuildChunkPostEvent event) {
//
//		Minecraft.getMinecraft().profiler.startSection("Rendering smooth world liquids in Post");
//
//		final MutableBlockPos renderChunkPos = event.getRenderChunkPosition();
//		RENDER_LIQUID_POSITIONS.get().remove(renderChunkPos);
//
//		Minecraft.getMinecraft().profiler.endSection();
//
//	}

//	public static BlockRenderData getBlockRenderData(final BlockPos pos, final IBlockAccess cache) {
//
//		final IBlockState state = cache.getBlockState(pos);
//		final BlockRendererDispatcher blockRendererDispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
//
//		final Object[] texturePosAndState = ClientUtil.getTexturePosAndState(cache, pos, state);
//		final BlockPos texturePos = (BlockPos) texturePosAndState[0];
//		final IBlockState textureState = (IBlockState) texturePosAndState[1];
//
//		BakedQuad quad = ClientUtil.getQuad(textureState, texturePos, blockRendererDispatcher);
//		if (quad == null) {
//			quad = blockRendererDispatcher.getBlockModelShapes().getModelManager().getMissingModel().getQuads(null, null, 0L).get(0);
//		}
//		final TextureAtlasSprite sprite = quad.getSprite();
//		final int color = ClientUtil.getColor(quad, textureState, cache, texturePos);
//		final int red = (color >> 16) & 255;
//		final int green = (color >> 8) & 255;
//		final int blue = color & 255;
//		final int alpha = 0xFF;
//
//		final float minU = ClientUtil.getMinU(sprite);
//		final float minV = ClientUtil.getMinV(sprite);
//		final float maxU = ClientUtil.getMaxU(sprite);
//		final float maxV = ClientUtil.getMaxV(sprite);
//
//		//real pos not texture pos
//		final LightmapInfo lightmapInfo = ClientUtil.getLightmapInfo(pos, cache);
//		final int lightmapSkyLight = lightmapInfo.getLightmapSkyLight();
//		final int lightmapBlockLight = lightmapInfo.getLightmapBlockLight();
//
//		final BlockRenderLayer blockRenderLayer = state.getBlock().getRenderLayer();
//
//		return new BlockRenderData(blockRenderLayer, red, green, blue, alpha, minU, maxU, minV, maxV, lightmapSkyLight, lightmapBlockLight);
//
//	}

	//TODO
	private static final int[][] OFFSETS_ORDERED = {
			// check 6 immediate neighbours
			{+0, -1, +0},
			{+0, +1, +0},
			{+1, +0, +0},
			{-1, +0, +0},
			{+0, +0, +1},
			{+0, +0, -1},
			// check 8 corner neighbours
			{+1, +1, +1},
			{+1, +1, -1},
			{-1, +1, +1},
			{-1, +1, -1},
			{+1, -1, +1},
			{+1, -1, -1},
			{-1, -1, +1},
			{-1, -1, -1},
//			// check 6 immediate neighbours
//			{0, -1, 0},
//			{0, +1, 0},
//			{-1, 0, 0},
//			{+1, 0, 0},
//			{0, 0, -1},
//			{0, 0, +1},
//			// check 8 corner neighbours
//			{-1, -1, -1},
//			{-1, -1, +1},
//			{+1, -1, -1},
//			{+1, -1, +1},
//			{-1, +1, -1},
//			{-1, +1, +1},
//			{+1, +1, -1},
//			{+1, +1, +1},
	};

	/**
	 * @param cache
	 * @param pos
	 * @param state
	 * @param pooledMutableBlockPos
	 * @return a state and a texture pos which is guaranteed to be immutable
	 */
	public static Object[] getTexturePosAndState(final IBlockAccess cache, final BlockPos pos, final IBlockState state, final Function<IBlockState, Boolean> isStateSmoothable, PooledMutableBlockPos pooledMutableBlockPos) {

		IBlockState textureState = state;
		BlockPos texturePos = pos;

		//check pos first
		if (isStateSmoothable.apply(cache.getBlockState(pos))) {
			return new Object[]{
					texturePos,
					textureState
			};
		}

		final int x = pos.getX();
		final int y = pos.getY();
		final int z = pos.getZ();

//			if (ModConfig.beautifyTexturesLevel == FANCY) {
//
//				for (int[] withOffset : OFFSETS_ORDERED) {
//					final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + withOffset[0], y + withOffset[1], z + withOffset[2]));
//					if (tempState.getBlock() == Blocks.SNOW_LAYER) {
//						textureState = tempState;
//						texturePos = pooledMutableBlockPos.toImmutable();
//						break;
//					}
//				}
//
//				for (int[] withOffset : OFFSETS_ORDERED) {
//					final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + withOffset[0], y + withOffset[1], z + withOffset[2]));
//					if (tempState.getBlock() == Blocks.GRASS) {
//						textureState = tempState;
//						texturePos = pooledMutableBlockPos.toImmutable();
//						break;
//					}
//				}
//			}

		for (int[] offset : OFFSETS_ORDERED) {
			final IBlockState tempState = cache.getBlockState(pooledMutableBlockPos.setPos(x + offset[0], y + offset[1], z + offset[2]));
			if (isStateSmoothable.apply(tempState)) {
				textureState = tempState;
				texturePos = pooledMutableBlockPos.toImmutable();
				break;
			}
		}

		return new Object[]{
				texturePos,
				textureState
		};

	}

	public static AmbientOcclusionFace makeAmbientOcclusionFace() {
		try {
			if (ambientOcclusionFaceNeedsBlockModelRenderer) {
				return ambientOcclusionFace.newInstance(Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelRenderer());
			} else {
				return ambientOcclusionFace.newInstance();
			}
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			CrashReport crashReport = new CrashReport("Instantiating BlockModelRenderer$AmbientOcclusionFace!", e);
			final CrashReportCategory crashReportCategory = crashReport.makeCategory("Reflectively Accessing BlockModelRenderer$AmbientOcclusionFace");
			crashReportCategory.addCrashSection("Needs BlockModelRenderer", ambientOcclusionFaceNeedsBlockModelRenderer);
			throw new ReportedException(crashReport);
		}
	}

	private static void renderFaces(final IBlockAccess cache, final BlockPos renderChunkPosition, final BlockRendererDispatcher blockRendererDispatcher, final RenderChunk renderChunk, final CompiledChunk compiledChunk, final ChunkCompileTaskGenerator generator, final Map<int[], ArrayList<PooledFace>> chunkData, final Function<IBlockState, Boolean> isStateSmoothable, final PooledMutableBlockPos pooledMutableBlockPos, final boolean renderOpposite) {

		final int renderChunkPositionX = renderChunkPosition.getX();
		final int renderChunkPositionY = renderChunkPosition.getY();
		final int renderChunkPositionZ = renderChunkPosition.getZ();

		chunkData.forEach((pos, faces) -> {

			if (faces.isEmpty()) return;

			pooledMutableBlockPos.setPos(
					renderChunkPositionX + pos[0],
					renderChunkPositionY + pos[1],
					renderChunkPositionZ + pos[2]
			);

			final IBlockState realState = cache.getBlockState(pooledMutableBlockPos);

			final Object[] texturePosAndState = getTexturePosAndState(cache, pooledMutableBlockPos.toImmutable(), realState, isStateSmoothable, pooledMutableBlockPos);
			final BlockPos texturePos = (BlockPos) texturePosAndState[0];
			final IBlockState textureState = (IBlockState) texturePosAndState[1];

			try {

				//TODO: use Event
				final BlockRenderLayer blockRenderLayer = getRenderLayer(textureState);

				final int blockRenderLayerOrdinal = blockRenderLayer.ordinal();

				final BufferBuilder bufferBuilder = generator.getRegionRenderCacheBuilder().getWorldRendererByLayerId(blockRenderLayerOrdinal);

				if (!compiledChunk.isLayerStarted(blockRenderLayer)) {
					compiledChunk.setLayerStarted(blockRenderLayer);
					USED_RENDER_LAYERS.get()[blockRenderLayerOrdinal] = true;
					renderChunk_preRenderBlocks(renderChunk, bufferBuilder, renderChunkPosition);
				}

				BakedQuad quad = ClientUtil.getQuad(textureState, texturePos, blockRendererDispatcher);
				if (quad == null) {
					quad = blockRendererDispatcher.getBlockModelShapes().getModelManager().getMissingModel().getQuads(null, null, 0L).get(0);
				}
				final TextureAtlasSprite sprite = quad.getSprite();
				final int color = ClientUtil.getColor(quad, textureState, cache, texturePos);
				final int red = (color >> 16) & 255;
				final int green = (color >> 8) & 255;
				final int blue = color & 255;
				final int alpha = 0xFF;

				final float minU = ClientUtil.getMinU(sprite);
				final float minV = ClientUtil.getMinV(sprite);
				final float maxU = ClientUtil.getMaxU(sprite);
				final float maxV = ClientUtil.getMaxV(sprite);

				for (int i = 0; i < faces.size(); i++) {
					final PooledFace face = faces.get(i);
					try {
						//south east when looking down onto up face
						final PooledVec3 v0 = face.getVertex0().addOffset(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ);
						//north east when looking down onto up face
						final PooledVec3 v1 = face.getVertex1().addOffset(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ);
						//north west when looking down onto up face
						final PooledVec3 v2 = face.getVertex2().addOffset(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ);
						//south west when looking down onto up face
						final PooledVec3 v3 = face.getVertex3().addOffset(renderChunkPositionX, renderChunkPositionY, renderChunkPositionZ);

						final int lightmapSkyLight0;
						final int lightmapSkyLight1;
						final int lightmapSkyLight2;
						final int lightmapSkyLight3;

						final int lightmapBlockLight0;
						final int lightmapBlockLight1;
						final int lightmapBlockLight2;
						final int lightmapBlockLight3;

						if (ModConfig.approximateLighting) {

							final double pos0X = v0.x + ((v0.x - pos[0] - renderChunkPositionX) * ModConfig.isosurfaceLevel);
							final double pos0Y = v0.y + ((v0.y - pos[1] - renderChunkPositionY) * ModConfig.isosurfaceLevel);
							final double pos0Z = v0.z + (v0.z - pos[2] - renderChunkPositionZ) * ModConfig.isosurfaceLevel;

							final int packedLight0 = cache.getBlockState(pooledMutableBlockPos.setPos(floor(pos0X), floor(pos0Y), floor(pos0Z))).getPackedLightmapCoords(cache, pooledMutableBlockPos);

							lightmapSkyLight0 = packedLight0 >> 16 & 0xFFFF;
							lightmapBlockLight0 = packedLight0 & 0xFFFF;

							final double pos1X = v1.x + ((v1.x - pos[0] - renderChunkPositionX) * ModConfig.isosurfaceLevel);
							final double pos1Y = v1.y + ((v1.y - pos[1] - renderChunkPositionY) * ModConfig.isosurfaceLevel);
							final double pos1Z = v1.z + (v1.z - pos[2] - renderChunkPositionZ) * ModConfig.isosurfaceLevel;

							final int packedLight1 = cache.getBlockState(pooledMutableBlockPos.setPos(floor(pos1X), floor(pos1Y), floor(pos1Z))).getPackedLightmapCoords(cache, pooledMutableBlockPos);

							lightmapSkyLight1 = packedLight1 >> 16 & 0xFFFF;
							lightmapBlockLight1 = packedLight1 & 0xFFFF;

							final double pos2X = v2.x + ((v2.x - pos[0] - renderChunkPositionX) * ModConfig.isosurfaceLevel);
							final double pos2Y = v2.y + ((v2.y - pos[1] - renderChunkPositionY) * ModConfig.isosurfaceLevel);
							final double pos2Z = v2.z + (v2.z - pos[2] - renderChunkPositionZ) * ModConfig.isosurfaceLevel;

							final int packedLight2 = cache.getBlockState(pooledMutableBlockPos.setPos(floor(pos2X), floor(pos2Y), floor(pos2Z))).getPackedLightmapCoords(cache, pooledMutableBlockPos);

							lightmapSkyLight2 = packedLight2 >> 16 & 0xFFFF;
							lightmapBlockLight2 = packedLight2 & 0xFFFF;

							final double pos3X = v3.x + ((v3.x - pos[0] - renderChunkPositionX) * ModConfig.isosurfaceLevel);
							final double pos3Y = v3.y + ((v3.y - pos[1] - renderChunkPositionY) * ModConfig.isosurfaceLevel);
							final double pos3Z = v3.z + (v3.z - pos[2] - renderChunkPositionZ) * ModConfig.isosurfaceLevel;

							final int packedLight3 = cache.getBlockState(pooledMutableBlockPos.setPos(floor(pos3X), floor(pos3Y), floor(pos3Z))).getPackedLightmapCoords(cache, pooledMutableBlockPos);

							lightmapSkyLight3 = packedLight3 >> 16 & 0xFFFF;
							lightmapBlockLight3 = packedLight3 & 0xFFFF;

//							//v0 =
//							//TODO: change to ModUtil#map
//							//<editor-fold desc="Snap to integer coords and light index">
//							final int v0X = clamp((round(v0.x) - pos[0] - renderChunkPositionX), 0, 1);
//							final int v0Y = clamp((round(v0.y) - pos[1] - renderChunkPositionY), 0, 1);
//							final int v0Z = clamp((round(v0.z) - pos[2] - renderChunkPositionZ), 0, 1);
//							final int v1X = clamp((round(v1.x) - pos[0] - renderChunkPositionX), 0, 1);
//							final int v1Y = clamp((round(v1.y) - pos[1] - renderChunkPositionY), 0, 1);
//							final int v1Z = clamp((round(v1.z) - pos[2] - renderChunkPositionZ), 0, 1);
//							final int v2X = clamp((round(v2.x) - pos[0] - renderChunkPositionX), 0, 1);
//							final int v2Y = clamp((round(v2.y) - pos[1] - renderChunkPositionY), 0, 1);
//							final int v2Z = clamp((round(v2.z) - pos[2] - renderChunkPositionZ), 0, 1);
//							final int v3X = clamp((round(v3.x) - pos[0] - renderChunkPositionX), 0, 1);
//							final int v3Y = clamp((round(v3.y) - pos[1] - renderChunkPositionY), 0, 1);
//							final int v3Z = clamp((round(v3.z) - pos[2] - renderChunkPositionZ), 0, 1);
//							//</editor-fold>
//
//							//<editor-fold desc="get and unpack lightmap coords">
//							lightmapSkyLight0 = packedLight[v0X][v0Y][v0Z] >> 16 & 0xFFFF;
//							lightmapBlockLight0 = packedLight[v0X][v0Y][v0Z] & 0xFFFF;
//							lightmapSkyLight1 = packedLight[v1X][v1Y][v1Z] >> 16 & 0xFFFF;
//							lightmapBlockLight1 = packedLight[v1X][v1Y][v1Z] & 0xFFFF;
//							lightmapSkyLight2 = packedLight[v2X][v2Y][v2Z] >> 16 & 0xFFFF;
//							lightmapBlockLight2 = packedLight[v2X][v2Y][v2Z] & 0xFFFF;
//							lightmapSkyLight3 = packedLight[v3X][v3Y][v3Z] >> 16 & 0xFFFF;
//							lightmapBlockLight3 = packedLight[v3X][v3Y][v3Z] & 0xFFFF;
//							//</editor-fold>

						} else {
							lightmapSkyLight0 = lightmapSkyLight1 = lightmapSkyLight2 = lightmapSkyLight3 = 240;
							lightmapBlockLight0 = lightmapBlockLight1 = lightmapBlockLight2 = lightmapBlockLight3 = 0;
						}

						try {
							bufferBuilder.pos(v0.x, v0.y, v0.z).color(red, green, blue, alpha).tex(minU, minV).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
							bufferBuilder.pos(v1.x, v1.y, v1.z).color(red, green, blue, alpha).tex(minU, maxV).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
							bufferBuilder.pos(v2.x, v2.y, v2.z).color(red, green, blue, alpha).tex(maxU, maxV).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
							bufferBuilder.pos(v3.x, v3.y, v3.z).color(red, green, blue, alpha).tex(maxU, minV).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
							if (renderOpposite) {
								bufferBuilder.pos(v3.x, v3.y, v3.z).color(red, green, blue, alpha).tex(maxU, minV).lightmap(lightmapSkyLight3, lightmapBlockLight3).endVertex();
								bufferBuilder.pos(v2.x, v2.y, v2.z).color(red, green, blue, alpha).tex(maxU, maxV).lightmap(lightmapSkyLight2, lightmapBlockLight2).endVertex();
								bufferBuilder.pos(v1.x, v1.y, v1.z).color(red, green, blue, alpha).tex(minU, maxV).lightmap(lightmapSkyLight1, lightmapBlockLight1).endVertex();
								bufferBuilder.pos(v0.x, v0.y, v0.z).color(red, green, blue, alpha).tex(minU, minV).lightmap(lightmapSkyLight0, lightmapBlockLight0).endVertex();
							}
						} finally {
							v0.release();
							v1.release();
							v2.release();
							v3.release();
						}
					} finally {
						face.release();
					}

				}
			} catch (Exception e) {
				final CrashReport crashReport = new CrashReport("Rendering smooth block in world", e);

				CrashReportCategory realBlockCrashReportCategory = crashReport.makeCategory("Block being rendered");
				final BlockPos blockPos = new BlockPos(renderChunkPositionX + pos[0], renderChunkPositionX + pos[0], renderChunkPositionX + pos[0]);
				CrashReportCategory.addBlockInfo(realBlockCrashReportCategory, blockPos, realState.getBlock(), realState.getBlock().getMetaFromState(realState));

				CrashReportCategory textureBlockCrashReportCategory = crashReport.makeCategory("TextureBlock of Block being rendered");
				CrashReportCategory.addBlockInfo(textureBlockCrashReportCategory, texturePos, textureState.getBlock(), textureState.getBlock().getMetaFromState(textureState));

				throw new ReportedException(crashReport);
			}

		});

	}

	public static BlockRenderLayer getRenderLayer(IBlockState state) {
		final BlockRenderLayer blockRenderLayer = state.getBlock().getRenderLayer();
		switch (blockRenderLayer) {
			default:
			case SOLID:
			case TRANSLUCENT:
				return blockRenderLayer;
			case CUTOUT_MIPPED:
				return Minecraft.getMinecraft().gameSettings.mipmapLevels == 0 ? CUTOUT : CUTOUT_MIPPED;
			case CUTOUT:
				return Minecraft.getMinecraft().gameSettings.mipmapLevels != 0 ? CUTOUT_MIPPED : CUTOUT;
		}
	}

	private static final ThreadLocal<boolean[]> USED_RENDER_LAYERS = ThreadLocal.withInitial(() -> new boolean[BlockRenderLayer.values().length]);

	public static void renderChunk(final RebuildChunkPreEvent event) {
		final ModProfiler profiler = NoCubes.getProfiler();

		final RenderChunk renderChunk = event.getRenderChunk();
		final BlockPos renderChunkPosition = event.getRenderChunkPosition();
		final CompiledChunk compiledChunk = event.getCompiledChunk();
		final ChunkCompileTaskGenerator generator = event.getGenerator();
		final IBlockAccess blockAccess = event.getIBlockAccess();

		final int meshSizeX;
		final int meshSizeY;
		final int meshSizeZ;
		if (ModConfig.getMeshGenerator() == MeshGenerator.SurfaceNets) {
			//yay, surface nets is special and needs an extra +1. why? no-one knows
			meshSizeX = 18;
			meshSizeY = 18;
			meshSizeZ = 18;
		} else {
			meshSizeX = 17;
			meshSizeY = 17;
			meshSizeZ = 17;
		}

		final PooledMutableBlockPos pooledMutableBlockPos = PooledMutableBlockPos.retain();
		try {
//			try (final PooledStateCache stateCache = CacheUtil.generateStateCache(cacheStartPosX, cacheStartPosY, cacheStartPosZ, cacheSizeX, cacheSizeY, cacheSizeZ, blockAccess, pooledMutableBlockPos)) {
//				public static PooledStateCache generateStateCache(final int startPosX, final int startPosY, final int startPosZ, final int cacheSizeX, final int cacheSizeY, final int cacheSizeZ, final IBlockAccess cache, PooledMutableBlockPos pooledMutableBlockPos) {
//				profiler.endSection();

			final boolean[] usedBlockRenderLayers = USED_RENDER_LAYERS.get();

			profiler.startSection("extendLiquids");
			try {
				ExtendedLiquidChunkRenderer.renderChunk();
			} catch (ReportedException e) {
				throw e;
			} catch (Exception e) {
				CrashReport crashReport = new CrashReport("Error extending liquids in Pre event!", e);
				crashReport.makeCategory("Extending liquids");
				throw new ReportedException(crashReport);
			}
			profiler.endSection();

			profiler.startSection("renderSmoothChunk");
			try {
				MeshRenderer.renderChunk2(
						renderChunkPosition,
						renderChunk,
						compiledChunk,
						generator,
						meshSizeX, meshSizeY, meshSizeZ,
						blockAccess,
						pooledMutableBlockPos,
						usedBlockRenderLayers
				);
			} catch (ReportedException e) {
				throw e;
			} catch (Exception e) {
				CrashReport crashReport = new CrashReport("Error rendering smooth chunk in Pre event!", e);
				crashReport.makeCategory("Rendering smooth chunk");
				throw new ReportedException(crashReport);
			}
			profiler.endSection();
//			}
		} finally {
			pooledMutableBlockPos.release();
		}
	}

//	public static void renderChunk(final RebuildChunkPreEvent event) {
//		final ModProfiler profiler = NoCubes.getProfiler();
//
//		final RenderChunk renderChunk = event.getRenderChunk();
//		final BlockPos renderChunkPosition = event.getRenderChunkPosition();
//		final CompiledChunk compiledChunk = event.getCompiledChunk();
//		final ChunkCompileTaskGenerator generator = event.getGenerator();
//		final IBlockAccess blockAccess = event.getIBlockAccess();
//
//		profiler.startSection("generateStateCache");
//
//		final int meshSizeX;
//		final int meshSizeY;
//		final int meshSizeZ;
//		if (ModConfig.getMeshGenerator() == MeshGenerator.SurfaceNets) {
//			//yay, surface nets is special and needs an extra +1. why? no-one knows
//			meshSizeX = 18;
//			meshSizeY = 18;
//			meshSizeZ = 18;
//		} else {
//			meshSizeX = 17;
//			meshSizeY = 17;
//			meshSizeZ = 17;
//		}
//
//		/* Also called startPosX */
//		final int renderChunkPositionX = renderChunkPosition.getX();
//		/* Also called startPosY */
//		final int renderChunkPositionY = renderChunkPosition.getY();
//		/* Also called startPosZ */
//		final int renderChunkPositionZ = renderChunkPosition.getZ();
//
//		final int endPosX = renderChunkPositionX + meshSizeX;
//		final int endPosY = renderChunkPositionY + meshSizeY;
//		final int endPosZ = renderChunkPositionZ + meshSizeZ;
//
//		// Density takes +1 block on every negative axis into account so we need to start at -1 block
//		final int cacheStartPosX = renderChunkPositionX - 1;
//		final int cacheStartPosY = renderChunkPositionY - 1;
//		final int cacheStartPosZ = renderChunkPositionZ - 1;
//
//		// Density takes +1 block on every negative axis into account so we need bigger caches
//		final int cacheSizeX = endPosX - cacheStartPosX + 1;
//		final int cacheSizeY = endPosY - cacheStartPosY + 1;
//		final int cacheSizeZ = endPosZ - cacheStartPosZ + 1;
//
//		final PooledMutableBlockPos pooledMutableBlockPos = PooledMutableBlockPos.retain();
//		try {
//			try (final PooledStateCache stateCache = CacheUtil.generateStateCache(cacheStartPosX, cacheStartPosY, cacheStartPosZ, cacheSizeX, cacheSizeY, cacheSizeZ, blockAccess, pooledMutableBlockPos)) {
////				public static PooledStateCache generateStateCache(final int startPosX, final int startPosY, final int startPosZ, final int cacheSizeX, final int cacheSizeY, final int cacheSizeZ, final IBlockAccess cache, PooledMutableBlockPos pooledMutableBlockPos) {
//				profiler.endSection();
//
//				final boolean[] usedBlockRenderLayers = USED_RENDER_LAYERS.get();
//
//				profiler.startSection("extendLiquids");
//				try {
//					ExtendedLiquidChunkRenderer.renderChunk();
//				} catch (ReportedException e) {
//					throw e;
//				} catch (Exception e) {
//					CrashReport crashReport = new CrashReport("Error extending liquids in Pre event!", e);
//					crashReport.makeCategory("Extending liquids");
//					throw new ReportedException(crashReport);
//				}
//				profiler.endSection();
//
//				profiler.startSection("renderSmoothChunk");
//				try {
//					MeshRenderer.renderChunk(
//							renderChunkPosition,
//							renderChunk,
//							compiledChunk,
//							generator,
//							stateCache,
//							cacheSizeX, cacheSizeZ, cacheSizeY,
//							meshSizeX, meshSizeY, meshSizeZ,
//							blockAccess,
//							pooledMutableBlockPos,
//							usedBlockRenderLayers
//					);
//				} catch (ReportedException e) {
//					throw e;
//				} catch (Exception e) {
//					CrashReport crashReport = new CrashReport("Error rendering smooth chunk in Pre event!", e);
//					crashReport.makeCategory("Rendering smooth chunk");
//					throw new ReportedException(crashReport);
//				}
//				profiler.endSection();
//			}
//		} finally {
//			pooledMutableBlockPos.release();
//		}
//	}

	public static void renderBlock(final RebuildChunkBlockEvent event) {
		final ModProfiler profiler = NoCubes.getProfiler();
		profiler.startSection("extendLiquids");
		try {

			final int ordinal = event.getBlockRenderLayer().ordinal();
			event.getUsedBlockRenderLayers()[ordinal] |= USED_RENDER_LAYERS.get()[ordinal];
			final IBlockState state = event.getBlockState();
			event.setCanceled(
					ModUtil.shouldSmooth(state) || ModUtil.shouldSmoothLeaves(state)
			);

		} catch (ReportedException e) {
			throw e;
		} catch (Exception e) {
			CrashReport crashReport = new CrashReport("Error rendering smooth chunk in Block event!", e);
			final CrashReportCategory crashReportCategory = crashReport.makeCategory("Rendering smooth chunk");
			CrashReportCategory.addBlockInfo(crashReportCategory, event.getBlockPos(), event.getBlockState());
			throw new ReportedException(crashReport);
		}
		profiler.endSection();
	}

}
