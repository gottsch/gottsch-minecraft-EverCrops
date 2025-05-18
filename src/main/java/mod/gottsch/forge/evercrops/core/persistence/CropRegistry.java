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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import mod.gottsch.forge.evercrops.core.EverCrops;
import mod.gottsch.forge.evercrops.core.util.LoggerUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.Level;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

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
    private static final String SAVE_FORMAT_LEVEL_SAVE_SRG_NAME = "f_129744_";

    private static ObjectMapper mapper = new ObjectMapper();

    /*
     * db for mod.
     */
    private static final String DB_FILE_NAME = "evercrops.rocksdb";

    private static RocksDB db;

    public static void start(MinecraftServer server) {
        EverCrops.LOGGER.info("starting server...");

        RocksDB.loadLibrary();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        Optional<Path> worldSavePath = getWorldSaveFolder(server);
        worldSavePath.ifPresentOrElse(path -> {
            Path dbPath = Paths.get(path.toString()).toAbsolutePath();

            try {
                Files.createDirectories(dbPath);
            } catch (IOException e) {
                LoggerUtil.formatLogMessage(Level.ERROR.toString(), "unable to create path -> " + dbPath.toString());
            }

            try {
                Options options = new Options().setCreateIfMissing(true);
                db = RocksDB.open(options, dbPath.resolve(DB_FILE_NAME).toString());
            } catch(RocksDBException e) {
                LoggerUtil.formatLogMessage(Level.ERROR.toString(), "unable to instantiate rocks db.");
            }
        }, () -> {
            LoggerUtil.formatLogMessage(Level.ERROR.toString(), "unable to locate world save folder.");
        });
    }

    public static void stop() {
        db.close();
        db = null;
    }

    public static boolean isStarted() {
        return db != null;
    }

    public static synchronized Optional<CropState> get(DimensionalBlockPos pos) {
        EverCrops.LOGGER.debug("get() @ pos -> {}", pos );
        CropState cropState = null;
        try {
            byte[] stateBytes = db.get(mapper.writeValueAsBytes(pos));
            if (stateBytes == null) return Optional.empty();
            cropState = mapper.readValue(stateBytes, CropState.class);
        } catch (RocksDBException | IOException e) {
            EverCrops.LOGGER.error("error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", pos, e.getCause(), e.getMessage());
        }
        return Optional.ofNullable(cropState);
    }

    // NOTE RocksDb does NOT follow a Map interface, and therefor the put
    // statement does not return the previous value at key position.
    public static synchronized void put(DimensionalBlockPos pos, CropState state) {
        EverCrops.LOGGER.debug("put() @ pos ->{}, state -> {}", pos, state);
        try {
            byte[] key = mapper.writeValueAsBytes(pos);
            byte[] data = mapper.writeValueAsBytes(state);
            try {
                db.put(key, data);
            } catch (RocksDBException e) {
                EverCrops.LOGGER.error("error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
            }
        } catch(IOException e) {
            EverCrops.LOGGER.error("error saving entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    public static void remove(DimensionalBlockPos pos) {
        try {
            db.delete(mapper.writeValueAsBytes(pos));
        } catch (RocksDBException | IOException e) {
            EverCrops.LOGGER.error("error deleting entry in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
        }
    }

    /**
     * @param server
     * @return
     */
    private static Optional<Path> getWorldSaveFolder(MinecraftServer server) {
        Object save = ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, SAVE_FORMAT_LEVEL_SAVE_SRG_NAME);
        if (save instanceof LevelStorageSource.LevelStorageAccess) {
            Path path = ((LevelStorageSource.LevelStorageAccess) save)
                    .getWorldDir().resolve(((LevelStorageSource.LevelStorageAccess) save).getLevelId())
                    .resolve(EverCrops.MOD_ID);
            return Optional.of(path);
        }
        return Optional.empty();
    }
}
