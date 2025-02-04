package io.github.cadiboo.nocubes.mesh;

import io.github.cadiboo.nocubes.util.Area;
import io.github.cadiboo.nocubes.util.Face;
import io.github.cadiboo.nocubes.util.ModUtil;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;

import java.util.function.Predicate;

public class StupidCubicMeshGenerator implements MeshGenerator {

	@Override
	public Vector3i getPositiveAreaExtension() {
		return ModUtil.VEC_ZERO;
	}

	@Override
	public Vector3i getNegativeAreaExtension() {
		return ModUtil.VEC_ZERO;
	}

	@Override
	public void generate(Area area, Predicate<BlockState> isSmoothable, VoxelAction voxelAction, FaceAction faceAction) {
		BlockPos size = area.size;
		int depth = size.getZ();
		int height = size.getY();
		int width = size.getX();

		final float min = 0F;
		final float max = 1F - min;

		BlockState[] blocks = area.getAndCacheBlocks();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		Face face = new Face();
		int index = 0;
		for (int z = 0; z < depth; ++z) {
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width; ++x, ++index) {
					boolean smoothable = isSmoothable.test(blocks[index]);
					if (!voxelAction.apply(pos.set(x, y, z), smoothable ? 1 : 0))
						return;
					if (!smoothable)
						// We aren't smoothable
						continue;

					// Up (pos y)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + max, y + max, z + max,
						x + max, y + max, z + min,
						x + min, y + max, z + min,
						x + min, y + max, z + max
					)))
						return;

					// Down (neg y)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + max, y, z + max,
						x + min, y, z + max,
						x + min, y, z + min,
						x + max, y, z + min
					)))
						return;

					// South (pos z)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + max, y + max, z + max,
						x + min, y + max, z + max,
						x + min, y + min, z + max,
						x + max, y + min, z + max
					)))
						return;

					// North (neg z)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + max, y + max, z + min,
						x + max, y + min, z + min,
						x + min, y + min, z + min,
						x + min, y + max, z + min
					)))
						return;

					// East (pos x)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + max, y + max, z + max,
						x + max, y + min, z + max,
						x + max, y + min, z + min,
						x + max, y + max, z + min
					)))
						return;

					// West (neg x)
					if (!faceAction.apply(pos.set(x, y, z), face.set(
						x + min, y + max, z + max,
						x + min, y + max, z + min,
						x + min, y + min, z + min,
						x + min, y + min, z + max
					)))
						return;
				}
			}
		}
	}

}
