package mod.gottsch.forge.evercrops.core.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropsBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author by Mark Gottschling on 3/20/2025
 */
@Mixin(CropsBlock.class)
public interface ICropBlockMixin {

    @Invoker
    int callGetAge(BlockState state);
}
