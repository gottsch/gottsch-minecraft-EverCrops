/*
 * This file is part of EverCrops.
 * Copyright (c) 2025 Mark Gottschling (gottsch)
 *
 * EverCrops is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EverCrops is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with EverCrops.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.evercrops.core.persistence.data;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.persistence.BlockPosSerializer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * this is a data-gathering pre-mod registry
 * @author by Mark Gottschling on 3/13/2025
 */
public class CropDataRegistry {

    /*
     * db for gathering of crop growth data. this db will not be active in the actual mod.
     */
    private static final String DATA_DB_FILE_NAME = "evercrops_data.db";
    private static DB dataDb;
    public static HTreeMap<BlockPos, CropGrowthData> dataMap;

    private CropDataRegistry() {}

    public static void start() {
        // the data db can go into the config path as it is not required to be per world.
        Path dbPath = Paths.get(FMLPaths.CONFIGDIR.get().toString(), EverCrops.MOD_ID).toAbsolutePath();
        try {
            Files.createDirectories(dbPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        dataDb = DBMaker.fileDB(dbPath.resolve(DATA_DB_FILE_NAME).toString()).transactionEnable().make();
        dataMap = dataDb.hashMap("dataCropMap", new BlockPosSerializer(), new CropGrowthData.Serializer()).createOrOpen();
    }

    public static void stop() {
        dataDb.close();
    }

    public static Optional<CropGrowthData> get(BlockPos pos) {
        return Optional.ofNullable(dataMap.get(pos));
    }

    public static Optional<CropGrowthData> put(BlockPos pos, CropGrowthData data) {
        return Optional.ofNullable(dataMap.put(pos, data));
    }

    public static void commit() {
        dataDb.commit();
    }
}
