package mod.gottsch.forge.evercrops.core.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

/**
 * CropRegistry is exists for all dimensions, so in order to avoid collisions,
 * a dimension is combined with a BlockPos.
 *
 * @author by Mark Gottschling on 3/17/2025
 */
public class DimensionalBlockPos implements Serializable {
    private ResourceLocation dimension;
    private BlockPos pos;

    public DimensionalBlockPos() {}

    public DimensionalBlockPos(ResourceLocation dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    public DimensionalBlockPos(String dimension, int x, int y, int z) {
        this.dimension = new ResourceLocation(dimension);
        this.pos = new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DimensionalBlockPos that = (DimensionalBlockPos) o;
        return Objects.equals(dimension, that.dimension) && Objects.equals(pos, that.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode() + dimension.hashCode();
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public void setDimension(ResourceLocation dimension) {
        this.dimension = dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public static class Serializer implements org.mapdb.Serializer<DimensionalBlockPos> {

        @Override
        public void serialize(DataOutput2 out, DimensionalBlockPos value) {
            try {
                out.writeUTF(value.dimension.toString());
                out.writeInt(value.pos.getX());
                out.writeInt(value.pos.getY());
                out.writeInt(value.pos.getZ());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public DimensionalBlockPos deserialize(DataInput2 input, int available) {
            try {
                String dimension = input.readUTF();
                int x = input.readInt();
                int y = input.readInt();
                int z = input.readInt();
                return new DimensionalBlockPos(dimension, x, y, z);
            } catch(IOException e) {
                throw new RuntimeException();
            }
        }

        @Override
        public boolean isTrusted() {
            return true;
        }
    }
}
