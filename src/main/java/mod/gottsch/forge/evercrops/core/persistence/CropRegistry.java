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
package mod.gottsch.forge.evercrops.core.persistence;

import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.util.LoggerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.SaveFormat;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.logging.log4j.Level;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * this is the working registry used for the mod.
 *
 * @author by Mark Gottschling on 3/16/2025
 */
public class CropRegistry {
    /*
    MC 1.18.2: net/minecraft/server/MinecraftServer.storageSource
    Name: l => f_129744_ => storageSource
    Side: BOTH
    AT: public net.minecraft.server.MinecraftServer f_129744_ # storageSource
    Type: net/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess
     */
    private static final String SAVE_FORMAT_LEVEL_SAVE_SRG_NAME = "field_71310_m";

    /*
     * db for mod.
     */
    private static final String DB_FILE_NAME = "evercrops.db";
    private static DB db;
    private static HTreeMap<DimensionalBlockPos, CropState> map;

    public static void start(MinecraftServer server) {
        Optional<Path> worldSavePath = getWorldSaveFolder(server);
        if (worldSavePath.isPresent()) {
            Path dbPath = Paths.get(worldSavePath.get().toString()).toAbsolutePath();
            try {
                Files.createDirectories(dbPath);
            } catch (IOException e) {
//                throw new RuntimeException(e);
                LoggerUtil.formatLogMessage(Level.ERROR.toString(), "Unable to create path -> " + dbPath.toString());
            }

            db = DBMaker.fileDB(dbPath.resolve(DB_FILE_NAME).toString()).transactionEnable().make();
            map = db.hashMap("cropMap", new DimensionalBlockPos.Serializer(), new CropState.Serializer()).createOrOpen();
        } else {
//            throw new RuntimeException("unable to locate world save folder.");
            LoggerUtil.formatLogMessage(Level.ERROR.toString(), "Unable to locate world save folder.");
        }
    }

    public static void stop() {
        db.close();
    }

    public static boolean isStarted() {
        return db != null && !db.isClosed();
    }

    public static Optional<CropState> get(DimensionalBlockPos pos) {
        return Optional.ofNullable(map.get(pos));
    }

    public static Optional<CropState> put(DimensionalBlockPos pos, CropState data) {
        Optional<CropState> value = Optional.ofNullable(map.put(pos, data));
        db.commit();
        return value;
    }

    public static Optional<CropState> remove(DimensionalBlockPos pos) {
        Optional<CropState> value = Optional.ofNullable(map.remove(pos));
        db.commit();
        return value;
    }

    /**
     * @param server
     * @return
     */
    private static Optional<Path> getWorldSaveFolder(MinecraftServer server) {
        Object save = ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, SAVE_FORMAT_LEVEL_SAVE_SRG_NAME);
        if (save instanceof SaveFormat.LevelSave) {
            Path path = ((SaveFormat.LevelSave) save).getWorldDir().resolve(EverCrops.MOD_ID);
            return Optional.of(path);
        }
        return Optional.empty();
    }
}
