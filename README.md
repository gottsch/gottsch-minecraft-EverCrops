# EverCrops

EverCrops utilizes mixins to update the vanilla crop blocks (any block that extends CropBlock or StemBlock - wheat, carrots, potatoes, watermelon, pumpkins) enabling them to grow even when they are not currently loaded. 

So, it doesn't actually grow when the crop blocks are not loaded, instead it calculates the elapsed time since the block last randomly ticked and "catches-up" with the growth when the chunk is reloaded and the block randomly ticks again. If you went off adventuring for a while in a far off place, when you came back and close enough that the chunk the crops are in loads, the amount of time elapsed will be applied to the growth process, updating the block state. (Note - for single player games, there isn't any grow when you aren't playing).

EverCrops only uses **@Inject**, **@Accessor** and **@Invoker** mixins, and thus it should be compatible with any mod that also uses mixins. No new custom blocks nor block entities are introduced.

## Technicals

EverCrops was harder to implement than EverFurnace because the crop blocks don't use BlockEntities, and thus have limited BlockState properties. As well, since they are only Blocks, they only use a randomTick() call, which as the name suggests, ticks randomly. Because of this you may not see immediate growth results when you return, but when the specific crop randomly ticks again, it will be like Bonemeal(s) was applied (depending how long the blocks were unloaded).
