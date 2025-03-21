package mod.gottsch.forge.evercrops.core.mixin;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author by Mark Gottschling on 3/20/2025
 */
@Mixin(CropBlock.class)
public interface ICropBlockMixin {

    @Invoker
    int callGetAge(BlockState state);
}
