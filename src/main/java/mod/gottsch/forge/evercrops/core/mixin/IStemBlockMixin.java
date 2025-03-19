package mod.gottsch.forge.evercrops.core.mixin;

import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * @author by Mark Gottschling on 3/19/2025
 */
@Mixin(StemBlock.class)
public interface IStemBlockMixin {

    @Accessor
    StemGrownBlock getFruit();
}
