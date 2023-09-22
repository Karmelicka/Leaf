package net.minecraft.world.level.chunk.storage;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.util.ExceptionCollector;
import net.minecraft.world.level.ChunkPos;

public class RegionFileStorage implements AutoCloseable {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger(); // LinearPurpur
    public static final String ANVIL_EXTENSION = ".mca";
    private static final int MAX_CACHE_SIZE = 256;
    public final Long2ObjectLinkedOpenHashMap<org.purpurmc.purpur.region.AbstractRegionFile> regionCache = new Long2ObjectLinkedOpenHashMap(); // LinearPurpur
    private final Path folder;
    private final boolean sync;
    // LinearPurpur start - Per world chunk format
    public final org.purpurmc.purpur.region.RegionFileFormat format;
    public final int linearCompression;
    public final boolean linearCrashOnBrokenSymlink;
    // LinearPurpur end
    private final boolean isChunkData; // Paper

    // Paper start - cache regionfile does not exist state
    static final int MAX_NON_EXISTING_CACHE = 1024 * 64;
    private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet nonExistingRegionFiles = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
    private synchronized boolean doesRegionFilePossiblyExist(long position) {
        if (this.nonExistingRegionFiles.contains(position)) {
            this.nonExistingRegionFiles.addAndMoveToFirst(position);
            return false;
        }
        return true;
    }

    private synchronized void createRegionFile(long position) {
        this.nonExistingRegionFiles.remove(position);
    }

    private synchronized void markNonExisting(long position) {
        if (this.nonExistingRegionFiles.addAndMoveToFirst(position)) {
            while (this.nonExistingRegionFiles.size() >= MAX_NON_EXISTING_CACHE) {
                this.nonExistingRegionFiles.removeLastLong();
            }
        }
    }

    public synchronized boolean doesRegionFileNotExistNoIO(ChunkPos pos) {
        long key = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
        return !this.doesRegionFilePossiblyExist(key);
    }
    // Paper end - cache regionfile does not exist state

    protected RegionFileStorage(org.purpurmc.purpur.region.RegionFileFormat format, int linearCompression, boolean linearCrashOnBrokenSymlink, Path directory, boolean dsync) { // Paper - protected constructor // LinearPurpur
        // Paper start - add isChunkData param
        this(format, linearCompression, linearCrashOnBrokenSymlink, directory, dsync, false);
    }
    RegionFileStorage(org.purpurmc.purpur.region.RegionFileFormat format, int linearCompression, boolean linearCrashOnBrokenSymlink, Path directory, boolean dsync, boolean isChunkData) { // LinearPurpur
        // LinearPurpur start
        this.format = format;
        this.linearCompression = linearCompression;
        this.linearCrashOnBrokenSymlink = linearCrashOnBrokenSymlink;
        // LinearPurpur end
        this.isChunkData = isChunkData;
        // Paper end - add isChunkData param
        this.folder = directory;
        this.sync = dsync;
    }

    // Paper start
    @Nullable
    public static ChunkPos getRegionFileCoordinates(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca") || !fileName.endsWith(".linear")) { // LinearPurpur
            return null;
        }

        String[] split = fileName.split("\\.");

        if (split.length != 4) {
            return null;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);

            return new ChunkPos(x << 5, z << 5);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    
    public synchronized org.purpurmc.purpur.region.AbstractRegionFile getRegionFileIfLoaded(ChunkPos chunkcoordintpair) { // LinearPurpur
        return this.regionCache.getAndMoveToFirst(ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ()));
    }

    public synchronized boolean chunkExists(ChunkPos pos) throws IOException {
        org.purpurmc.purpur.region.AbstractRegionFile regionfile = getRegionFile(pos, true); // LinearPurpur

        return regionfile != null ? regionfile.hasChunk(pos) : false;
    }

    // LinearPurpur start
    private void guardAgainstBrokenSymlinks(Path path) throws IOException {
        if (!linearCrashOnBrokenSymlink) return;
        if (!this.format.equals("LINEAR")) return;
        if (!java.nio.file.Files.isSymbolicLink(path)) return;
        Path link = java.nio.file.Files.readSymbolicLink(path);
        if (!java.nio.file.Files.exists(link) || !java.nio.file.Files.isReadable(link)) {
            LOGGER.error("Linear region file {} is a broken symbolic link, crashing to prevent data loss", path);
            net.minecraft.server.MinecraftServer.getServer().halt(false);
            throw new IOException("Linear region file " + path + " is a broken symbolic link, crashing to prevent data loss");
        }
    }
    // LinearPurpur end

    public synchronized org.purpurmc.purpur.region.AbstractRegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly) throws IOException { // CraftBukkit // LinearPurpur
        return this.getRegionFile(chunkcoordintpair, existingOnly, false);
    }
    public synchronized org.purpurmc.purpur.region.AbstractRegionFile getRegionFile(ChunkPos chunkcoordintpair, boolean existingOnly, boolean lock) throws IOException { // LinearPurpur
        // Paper end
        long i = ChunkPos.asLong(chunkcoordintpair.getRegionX(), chunkcoordintpair.getRegionZ()); final long regionPos = i; // Paper - OBFHELPER
        org.purpurmc.purpur.region.AbstractRegionFile regionfile = this.regionCache.getAndMoveToFirst(i); // LinearPurpur

        if (regionfile != null) {
            // Paper start
            if (lock) {
                // must be in this synchronized block
                regionfile.getFileLock().lock(); // LinearPurpur
            }
            // Paper end
            return regionfile;
        } else {
            // Paper start - cache regionfile does not exist state
            if (existingOnly && !this.doesRegionFilePossiblyExist(regionPos)) {
                return null;
            }
            // Paper end - cache regionfile does not exist state
            if (this.regionCache.size() >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.regionFileCacheSize) { // Paper - Sanitise RegionFileCache and make configurable
                this.regionCache.removeLast().close(); // LinearPurpur
            }

            // Paper - only create directory if not existing only - moved down
            Path path = this.folder;
            int j = chunkcoordintpair.getRegionX();
            // LinearPurpur start - Polyglot
            Path path1;
            if (existingOnly) {
                Path anvil = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + ".mca");
                Path linear = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + ".linear");
                guardAgainstBrokenSymlinks(linear);
                if (java.nio.file.Files.exists(anvil)) path1 = anvil;
                else if (java.nio.file.Files.exists(linear)) path1 = linear;
                else {
                    this.markNonExisting(regionPos);
                    return null;
                }
            // LinearPurpur end
            } else {
                // LinearPurpur start - Polyglot
                String extension = switch (this.format) {
                    case LINEAR -> "linear";
                    default -> "mca";
                };
                path1 = path.resolve("r." + j + "." + chunkcoordintpair.getRegionZ() + "." + extension);
                // LinearPurpur end
                guardAgainstBrokenSymlinks(path1); // LinearPurpur - Crash on broken symlink
                this.createRegionFile(regionPos);
            }
            // Paper end - cache regionfile does not exist state
            FileUtil.createDirectoriesSafe(this.folder); // Paper - only create directory if not existing only - moved from above

            org.purpurmc.purpur.region.AbstractRegionFile regionfile1 = org.purpurmc.purpur.region.AbstractRegionFileFactory.getAbstractRegionFile(this.linearCompression, path1, this.folder, this.sync, this.isChunkData); // Paper - allow for chunk regionfiles to regen header // LinearPurpur
            this.regionCache.putAndMoveToFirst(i, regionfile1);
            // Paper start
            if (lock) {
                // must be in this synchronized block
                regionfile1.getFileLock().lock(); // LinearPurpur
            }
            // Paper end
            return regionfile1;
        }
    }

    // Paper start
    private static void printOversizedLog(String msg, Path file, int x, int z) {
        org.apache.logging.log4j.LogManager.getLogger().fatal(msg + " (" + file.toString().replaceAll(".+[\\\\/]", "") + " - " + x + "," + z + ") Go clean it up to remove this message. /minecraft:tp " + (x<<4)+" 128 "+(z<<4) + " - DO NOT REPORT THIS TO PAPER - You may ask for help on Discord, but do not file an issue. These error messages can not be removed.");
    }

    private static CompoundTag readOversizedChunk(org.purpurmc.purpur.region.AbstractRegionFile regionfile, ChunkPos chunkCoordinate) throws IOException { // LinearPurpur
        synchronized (regionfile) {
            try (DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkCoordinate)) {
                CompoundTag oversizedData = regionfile.getOversizedData(chunkCoordinate.x, chunkCoordinate.z);
                CompoundTag chunk = NbtIo.read((DataInput) datainputstream);
                if (oversizedData == null) {
                    return chunk;
                }
                CompoundTag oversizedLevel = oversizedData.getCompound("Level");

                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "Entities", "Entities");
                mergeChunkList(chunk.getCompound("Level"), oversizedLevel, "TileEntities", "TileEntities");

                return chunk;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                throw throwable;
            }
        }
    }

    private static void mergeChunkList(CompoundTag level, CompoundTag oversizedLevel, String key, String oversizedKey) {
        net.minecraft.nbt.ListTag levelList = level.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag oversizedList = oversizedLevel.getList(oversizedKey, net.minecraft.nbt.Tag.TAG_COMPOUND);

        if (!oversizedList.isEmpty()) {
            levelList.addAll(oversizedList);
            level.put(key, levelList);
        }
    }
    // Paper end

    @Nullable
    public CompoundTag read(ChunkPos pos) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        org.purpurmc.purpur.region.AbstractRegionFile regionfile = this.getRegionFile(pos, true, true); // Paper // LinearPurpur
        if (regionfile == null) {
            return null;
        }
        // Paper start - Add regionfile parameter
        return this.read(pos, regionfile);
    }
    public CompoundTag read(ChunkPos pos, org.purpurmc.purpur.region.AbstractRegionFile regionfile) throws IOException { // LinearPurpur
        // We add the regionfile parameter to avoid the potential deadlock (on fileLock) if we went back to obtain a regionfile
        // if we decide to re-read
        // Paper end
        // CraftBukkit end
        try { // Paper
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(pos);

        // Paper start
        if (regionfile.isOversized(pos.x, pos.z)) {
            printOversizedLog("Loading Oversized Chunk!", regionfile.getRegionFile(), pos.x, pos.z); // LinearPurpur
            return readOversizedChunk(regionfile, pos);
        }
        // Paper end
        CompoundTag nbttagcompound;
        label43:
        {
            try {
                if (datainputstream != null) {
                    nbttagcompound = NbtIo.read((DataInput) datainputstream);
                    // Paper start - recover from corrupt regionfile header
                    if (this.isChunkData) {
                        ChunkPos chunkPos = ChunkSerializer.getChunkCoordinate(nbttagcompound);
                        if (!chunkPos.equals(pos)) {
                            net.minecraft.server.MinecraftServer.LOGGER.error("Attempting to read chunk data at " + pos + " but got chunk data for " + chunkPos + " instead! Attempting regionfile recalculation for regionfile " + regionfile.getRegionFile().toAbsolutePath()); // LinearPurpur
                            if (regionfile.recalculateHeader()) {
                                regionfile.getFileLock().lock(); // otherwise we will unlock twice and only lock once. // LinearPurpur
                                return this.read(pos, regionfile);
                            }
                            net.minecraft.server.MinecraftServer.LOGGER.error("Can't recalculate regionfile header, regenerating chunk " + pos + " for " + regionfile.getRegionFile().toAbsolutePath()); // LinearPurpur
                            return null;
                        }
                    }
                    // Paper end - recover from corrupt regionfile header
                    break label43;
                }

                nbttagcompound = null;
            } catch (Throwable throwable) {
                if (datainputstream != null) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }

            if (datainputstream != null) {
                datainputstream.close();
            }

            return nbttagcompound;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

        return nbttagcompound;
        } finally { // Paper start
            regionfile.getFileLock().unlock(); // LinearPurpur
        } // Paper end
    }

    public void scanChunk(ChunkPos chunkPos, StreamTagVisitor scanner) throws IOException {
        // CraftBukkit start - SPIGOT-5680: There's no good reason to preemptively create files on read, save that for writing
        org.purpurmc.purpur.region.AbstractRegionFile regionfile = this.getRegionFile(chunkPos, true); // LinearPurpur
        if (regionfile == null) {
            return;
        }
        // CraftBukkit end
        DataInputStream datainputstream = regionfile.getChunkDataInputStream(chunkPos);

        try {
            if (datainputstream != null) {
                NbtIo.parse(datainputstream, scanner, NbtAccounter.unlimitedHeap());
            }
        } catch (Throwable throwable) {
            if (datainputstream != null) {
                try {
                    datainputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

    }

    protected void write(ChunkPos pos, @Nullable CompoundTag nbt) throws IOException {
        // Paper start - rewrite chunk system
        org.purpurmc.purpur.region.AbstractRegionFile regionfile = this.getRegionFile(pos, nbt == null, true); // CraftBukkit // Paper // Paper start - rewrite chunk system // LinearPurpur
        if (nbt == null && regionfile == null) {
            return;
        }
        try { // Try finally to unlock the region file
        // Paper end - rewrite chunk system
        // Paper start - Chunk save reattempt
        int attempts = 0;
        Exception lastException = null;
        while (attempts++ < 5) { try {
        // Paper end - Chunk save reattempt

        if (nbt == null) {
            regionfile.clear(pos);
        } else {
            DataOutputStream dataoutputstream = regionfile.getChunkDataOutputStream(pos);

            try {
                NbtIo.write(nbt, (DataOutput) dataoutputstream);
                regionfile.setStatus(pos.x, pos.z, ChunkSerializer.getStatus(nbt)); // Paper - Cache chunk status
                regionfile.setOversized(pos.x, pos.z, false); // Paper - We don't do this anymore, mojang stores differently, but clear old meta flag if it exists to get rid of our own meta file once last oversized is gone
                // Paper start - don't write garbage data to disk if writing serialization fails
                dataoutputstream.close(); // Only write if successful
            } catch (final RegionFileSizeException e) {
                attempts = 5; // Don't retry
                regionfile.clear(pos);
                throw e;
                // Paper end - don't write garbage data to disk if writing serialization fails
            } catch (Throwable throwable) {
                if (dataoutputstream != null) {
                    try {
                        //dataoutputstream.close(); // Paper - don't write garbage data to disk if writing serialization fails
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }
            // Paper - don't write garbage data to disk if writing serialization fails; move into try block to only write if successfully serialized
        }
        // Paper start - Chunk save reattempt
                return;
            } catch (Exception ex)  {
                lastException = ex;
            }
        }

        if (lastException != null) {
            com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(lastException);
            net.minecraft.server.MinecraftServer.LOGGER.error("Failed to save chunk {}", pos, lastException);
        }
        // Paper end - Chunk save reattempt
        // Paper start - rewrite chunk system
        } finally {
            regionfile.getFileLock().unlock(); // LinearPurpur
        }
        // Paper end - rewrite chunk system
    }

    public synchronized void close() throws IOException { // Paper -> synchronized
        ExceptionCollector<IOException> exceptionsuppressor = new ExceptionCollector<>();
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            org.purpurmc.purpur.region.AbstractRegionFile regionfile = (org.purpurmc.purpur.region.AbstractRegionFile) objectiterator.next(); // LinearPurpur

            try {
                regionfile.close();
            } catch (IOException ioexception) {
                exceptionsuppressor.add(ioexception);
            }
        }

        exceptionsuppressor.throwIfPresent();
    }

    public synchronized void flush() throws IOException { // Paper - synchronize
        ObjectIterator objectiterator = this.regionCache.values().iterator();

        while (objectiterator.hasNext()) {
            org.purpurmc.purpur.region.AbstractRegionFile regionfile = (org.purpurmc.purpur.region.AbstractRegionFile) objectiterator.next(); // LinearPurpur

            regionfile.flush();
        }

    }

    // Paper start - don't write garbage data to disk if writing serialization fails
    public static final class RegionFileSizeException extends RuntimeException {

        public RegionFileSizeException(String message) {
            super(message);
        }
    }
    // Paper end - don't write garbage data to disk if writing serialization fails
}
