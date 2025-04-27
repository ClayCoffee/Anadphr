package cn.claycoffee.anadphr.core;

import cn.claycoffee.anadphr.biomes.BiomeProvider;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 自定义区块生成器的抽象实现 (线程安全设计)。
 * 负责调用 {@link NoiseGeneratorCore} 来执行实际的地形生成，
 * 并协调地表生成和 BlockPopulator 的执行。
 * 使用双重检查锁定模式安全地延迟初始化 GeneratorCore。
 */
public abstract class AbstractChunkGenerator extends ChunkGenerator { // 标记为 final

    static final Logger LOGGER = Logger.getLogger("MyChunkGenerator");

    // GeneratorCore 实例，可能延迟初始化
    @Nullable
    private volatile NoiseGeneratorCore core; // 使用 volatile 保证多线程可见性

    // 缓存 Provider 和 Populator 实例 (volatile 确保可见性)
    @Nullable
    private volatile BiomeProvider biomeProviderInstance;

    // 用于 core 延迟初始化的锁对象 (final)
    protected final Object coreInitLock = new Object();


    /**
     * 创建一个 ChunkGenerator 实例，并可选择性地注入一个预配置的 GeneratorCore。
     * 这允许为不同的世界使用不同的生成参数。
     *
     * @param core 一个配置好的 {@link NoiseGeneratorCore} 实例。如果为 null，将在首次需要时使用默认设置创建。
     * 传入的 core 必须是完整初始化的。
     */
    public AbstractChunkGenerator(@Nullable NoiseGeneratorCore core) {
        // 直接赋值，初始化将在需要时进行检查
        this.core = core;
    }

    /**
     * 默认构造函数。将在首次生成区块时使用默认设置初始化 GeneratorCore。
     * 通常用于通过 plugin.yml 直接指定生成器名称的情况。
     */
    public AbstractChunkGenerator() {
        this(null);
    }

    /**
     * 确保 GeneratorCore 实例已初始化 (线程安全)。
     * 使用双重检查锁定 (Double-Checked Locking) 模式。
     *
     * @param worldInfo 当前世界信息，用于获取种子和高度限制 (仅在需要初始化时使用)。
     */
    private void initializeCoreIfNeeded(@NotNull WorldInfo worldInfo) {
        // 第一次检查 (无锁，性能优化)
        if (this.core == null) {
            // 如果为 null，进入同步块
            synchronized (coreInitLock) {
                // 第二次检查 (有锁，保证只有一个线程初始化)
                if (this.core == null) {
                    LOGGER.info("[WorldGen] GeneratorCore 延迟初始化 (世界: " + worldInfo.getName() + ")...");
                    long seed = worldInfo.getSeed();
                    try {
                        this.core = createCore(worldInfo, seed);
                        LOGGER.info("[WorldGen] 默认 GeneratorCore 初始化完成 (种子: " + seed + ")");
                    } catch (Exception e) {
                        // 记录初始化错误，防止后续 NPE
                        LOGGER.severe("[WorldGen] GeneratorCore 初始化失败! 原因: " + e.getMessage());
                        // 可以在这里抛出运行时异常或者让 core 保持 null，并在 getCore 中处理
                        throw new IllegalStateException("Failed to initialize GeneratorCore", e);
                    }
                }
            }
        }
    }

    /**
     * 基于指定的种子和世界信息创建并返回一个新的 {@link NoiseGeneratorCore} 实例。
     * 此方法旨在由子类实现，以定义用于地形生成的自定义生成器核心初始化逻辑。
     *
     * @param info 世界信息。
     * @param seed 用于确定性噪声生成的种子。
     * @return 一个使用所提供参数配置并已初始化的 {@link NoiseGeneratorCore} 实例。
     */
    protected abstract NoiseGeneratorCore createCore(WorldInfo info, long seed);

    /**
     * 获取已初始化的 GeneratorCore 实例 (线程安全)。
     * 如果 Core 尚未初始化，将触发初始化。
     * @param worldInfo 当前世界信息。
     * @return 返回已初始化的 GeneratorCore 实例。
     * @throws IllegalStateException 如果 GeneratorCore 初始化失败。
     */
    @NotNull NoiseGeneratorCore getCore(@NotNull WorldInfo worldInfo) {
        // 确保初始化
        initializeCoreIfNeeded(worldInfo);
        // 此时 core 不应为 null，如果为 null 则初始化已失败
        // 使用 Objects.requireNonNull 提供更清晰的错误信息
        return Objects.requireNonNull(this.core, "GeneratorCore instance is null after initialization check!");
    }

    /**
     * 获取已初始化的 GeneratorCore 实例 (线程安全)。
     *
     * @return 返回已初始化的 GeneratorCore 实例。
     */
    public @NotNull NoiseGeneratorCore getCore() {
        return Objects.requireNonNull(this.core, "GeneratorCore instance is null after initialization check!");
    }


    /**
     * 生成区块的基础地形噪声。
     * 调用 {@link NoiseGeneratorCore} 中的方法执行计算。
     *
     * @param worldInfo 世界信息。
     * @param random    Bukkit 提供的用于此区块生成的 Random 实例 (未使用，因为 Core 内使用确定性随机)。
     * @param chunkX    区块 X 坐标。
     * @param chunkZ    区块 Z 坐标。
     * @param chunkData 用于写入方块数据的对象。
     */
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        final NoiseGeneratorCore currentCore = getCore(worldInfo); // 获取 Core (会触发初始化如果需要)
        final int seaLevel = currentCore.terrainSettings.seaLevel;
        final int minHeight = worldInfo.getMinHeight();
        final int maxHeight = worldInfo.getMaxHeight();
        final int worldStartX = chunkX << 4;
        final int worldStartZ = chunkZ << 4;

        // --- 线程安全: 所有操作基于 final 的 Core 和局部变量 ---
        for (int x = 0; x < 16; x++) {
            final int worldX = worldStartX + x;
            for (int z = 0; z < 16; z++) {
                final int worldZ = worldStartZ + z;
                final int terrainHeight = currentCore.calculateTerrainHeight(worldX, worldZ);

                for (int y = minHeight + 1; y < terrainHeight; y++) {
                    if (y >= maxHeight) break; // 超出高度则停止此列
                    // 使用世界坐标进行确定性随机获取材质
                    chunkData.setBlock(x, y, z, currentCore.getBaseMaterial(y, terrainHeight, worldX, worldZ));
                }
                // 填充水体 (海洋/湖泊)
                if (terrainHeight <= seaLevel) {
                    for (int y = terrainHeight; y <= seaLevel; y++) {
                        if (y < minHeight || y >= maxHeight) continue;
                        Material currentMat = chunkData.getType(x, y, z);
                        // 只填充非空气、非基岩
                        if (currentMat != Material.AIR && currentMat != Material.BEDROCK && currentMat != Material.WATER) {
                            chunkData.setBlock(x, y, z, Material.WATER);
                        }
                    }
                }
            }
        }
    }

    /**
     * 生成区块的地表覆盖 (线程安全)。
     * 依赖 {@link BiomeProvider} 获取生物群系。
     *
     * @param worldInfo 世界信息。
     * @param random    Bukkit 提供的 Random 实例 (未使用)。
     * @param chunkX    区块 X 坐标。
     * @param chunkZ    区块 Z 坐标。
     * @param chunkData 用于写入方块数据的对象。
     */
    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        final NoiseGeneratorCore currentCore = getCore(worldInfo);
        final BiomeProvider currentBiomeProvider = getCachedBiomeProvider(worldInfo);
        // 如果 BiomeProvider 获取失败，则无法进行地表生成
        if (currentBiomeProvider == null) {
            LOGGER.warning("[WorldGen] BiomeProvider is null, skipping surface generation for chunk " + chunkX + "," + chunkZ);
            return;
        }

        final int seaLevel = currentCore.terrainSettings.seaLevel;
        final int minHeight = worldInfo.getMinHeight();
        final int maxHeight = worldInfo.getMaxHeight();
        final int worldStartX = chunkX << 4;
        final int worldStartZ = chunkZ << 4;

        // --- 线程安全: 所有操作基于 final 的 Core, Provider 和局部变量 ---
        for (int x = 0; x < 16; x++) {
            final int worldX = worldStartX + x;
            for (int z = 0; z < 16; z++) {
                final int worldZ = worldStartZ + z;

                // 寻找地表 Y (优化查找)
                int surfaceY = findSurfaceY(chunkData, x, z, minHeight, maxHeight);
                if (surfaceY < minHeight) continue; // 未找到有效表面

                final Material blockAtSurface = chunkData.getType(x, surfaceY, z);
                final Biome biome = currentBiomeProvider.getBiome(worldInfo, worldX, surfaceY, worldZ);

                Material surfaceBlock;
                Material belowSurfaceBlock;
                int belowDepth = 3;

                // 只在可替换的方块上应用 (石头、泥土、沙子等基础方块)
                if (isSurfaceReplaceable(blockAtSurface)) {

                    // --- 使用 if-else if 结构根据生物群系选择表面配置 ---
                    if (biome == Biome.DESERT) {
                        surfaceBlock = Material.SAND;
                        belowSurfaceBlock = Material.SANDSTONE;
                        belowDepth = 5;
                    } else if (biome == Biome.BEACH) {
                        surfaceBlock = Material.SAND;
                        belowSurfaceBlock = Material.SANDSTONE;
                    } else if (biome == Biome.SNOWY_BEACH) {
                        surfaceBlock = Material.SNOW;
                        belowSurfaceBlock = Material.SAND;
                    } else if (biome == Biome.STONY_SHORE) {
                        surfaceBlock = Material.GRAVEL;
                        belowSurfaceBlock = Material.STONE;
                    } else if (biome == Biome.BADLANDS || biome == Biome.ERODED_BADLANDS || biome == Biome.WOODED_BADLANDS) {
                        surfaceBlock = Material.RED_SAND;
                        belowSurfaceBlock = Material.TERRACOTTA;
                        belowDepth = 8;
                    } else if (biome == Biome.MUSHROOM_FIELDS) {
                        surfaceBlock = Material.MYCELIUM;
                        belowSurfaceBlock = Material.DIRT;
                    } else if (biome == Biome.SWAMP) {
                        surfaceBlock = Material.GRASS_BLOCK;
                        belowSurfaceBlock = Material.DIRT;
                    } else if (biome == Biome.MANGROVE_SWAMP) {
                        surfaceBlock = Material.MUD;
                        belowSurfaceBlock = Material.MUD;
                        belowDepth = 2;
                    } else if (biome == Biome.SNOWY_PLAINS || biome == Biome.SNOWY_TAIGA || biome == Biome.SNOWY_SLOPES || biome == Biome.GROVE || biome == Biome.FROZEN_PEAKS || biome == Biome.ICE_SPIKES) {
                        surfaceBlock = Material.SNOW_BLOCK;
                        belowSurfaceBlock = Material.DIRT;
                    } else if (biome == Biome.JAGGED_PEAKS || biome == Biome.STONY_PEAKS) {
                        surfaceBlock = Material.STONE;
                        belowSurfaceBlock = Material.STONE;
                    } else if (biome == Biome.RIVER || biome == Biome.FROZEN_RIVER) {
                        // 河流的特殊处理
                        if (blockAtSurface != Material.WATER) { // 河岸
                            surfaceBlock = Material.GRASS_BLOCK;
                            belowSurfaceBlock = Material.DIRT;
                        } else { // 水下河床
                            surfaceBlock = Material.WATER; // 保持水
                            belowSurfaceBlock = Material.GRAVEL; // 河床用砂砾
                            belowDepth = 2;
                        }
                    }
                    // TODO More biomes
                    else {
                        // fallback
                        surfaceBlock = Material.GRASS_BLOCK;
                        belowSurfaceBlock = Material.DIRT;
                    }

                    // --- 湖底处理 ---
                    if (blockAtSurface != Material.WATER && surfaceY < seaLevel && biome != Biome.RIVER && !isOceanBiome(biome)) {
                        if (surfaceY + 1 < maxHeight) { // 检查上方边界
                            Material blockAbove = chunkData.getType(x, surfaceY + 1, z);
                            if (blockAbove == Material.WATER) {
                                surfaceBlock = Material.DIRT; // 强制设为泥土
                                belowSurfaceBlock = Material.DIRT;
                                belowDepth = 3;
                            }
                        }
                    }

                    if (surfaceY >= minHeight && surfaceY < maxHeight && surfaceBlock != Material.WATER) {
                        chunkData.setBlock(x, surfaceY, z, surfaceBlock);
                    }
                    for (int d = 1; d <= belowDepth; d++) {
                        final int currentY = surfaceY - d;
                        if (currentY < minHeight) break;
                        final Material currentMat = chunkData.getType(x, currentY, z);
                        if (isSurfaceReplaceable(currentMat)) {
                            chunkData.setBlock(x, currentY, z, belowSurfaceBlock);
                        } else if (currentMat.isSolid()){
                            break;
                        }
                    }

                }
            }
        }
    }

    /**
     * 辅助方法：判断一个生物群系是否属于海洋类型。
     * 用于湖底逻辑判断，排除海洋生物群系。
     * @param biome 要检查的生物群系。
     * @return 如果是海洋类型则返回 true。
     */
    private boolean isOceanBiome(@NotNull Biome biome) {
        // 直接比较枚举实例，比比较名称更高效、安全
        return biome == Biome.OCEAN || biome == Biome.DEEP_OCEAN ||
                biome == Biome.COLD_OCEAN || biome == Biome.DEEP_COLD_OCEAN ||
                biome == Biome.FROZEN_OCEAN || biome == Biome.DEEP_FROZEN_OCEAN ||
                biome == Biome.LUKEWARM_OCEAN || biome == Biome.DEEP_LUKEWARM_OCEAN ||
                biome == Biome.WARM_OCEAN;
    }

    /** 辅助方法: 查找地表 Y 坐标 (优化)。 */
    private int findSurfaceY(ChunkData chunkData, int x, int z, int minHeight, int maxHeight) {
        int surfaceY = maxHeight - 1;
        Material block = chunkData.getType(x, surfaceY, z);
        // 从上往下找第一个非空气/水
        while (surfaceY > minHeight && (block == Material.AIR || block == Material.WATER)) {
            surfaceY--;
            block = chunkData.getType(x, surfaceY, z);
        }
        // 处理找到的是水的情况 (找水下的固体)
        if (block == Material.WATER && surfaceY > minHeight) {
            int belowY = surfaceY - 1;
            Material blockBelow = chunkData.getType(x, belowY, z);
            if (blockBelow != Material.AIR && blockBelow != Material.WATER) {
                return belowY; // 返回水底 Y
            }
        }
        // 返回找到的固体 Y 或 minHeight (如果整列都是空气/水)
        return surfaceY;
    }

    /** 辅助方法: 判断方块是否是可被地表覆盖替换的基础类型。 */
    private boolean isSurfaceReplaceable(Material mat) {
        // 包含石头、深板岩、凝灰岩、泥土、草、沙子、砂砾、砂岩、红沙、陶瓦、泥巴
        return mat == Material.STONE || mat == Material.DEEPSLATE || mat == Material.TUFF ||
                mat == Material.DIRT || mat == Material.GRASS_BLOCK ||
                mat == Material.SAND || mat == Material.GRAVEL ||
                mat == Material.SANDSTONE || mat == Material.RED_SAND ||
                mat == Material.TERRACOTTA || mat == Material.MUD ||
                mat == Material.MYCELIUM; // 菌丝也可被替换
    }


    /**
     * 生成基岩层 (线程安全)。
     * @param random Bukkit 提供的 Random 实例。
     */
    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // 确保 Core 初始化 (虽然这里不用 Core，但保持一致性)
        initializeCoreIfNeeded(worldInfo);
        final int minY = worldInfo.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 优化: 仅在需要时设置最低层基岩
                if (chunkData.getType(x, minY, z) != Material.BEDROCK) {
                    chunkData.setBlock(x, minY, z, Material.BEDROCK);
                }
                // 使用传入的 random
                for (int y = minY + 1; y < minY + 5; y++) {
                    if (random.nextInt(5 - (y - (minY+1))) == 0) { // 递减概率
                        Material currentMat = chunkData.getType(x, y, z);
                        if (currentMat == Material.STONE || currentMat == Material.DEEPSLATE || currentMat == Material.TUFF) {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }
                }
            }
        }
    }

    // --- 提供 Provider 和 Populator (线程安全获取) ---

    /**
     * 获取此生成器使用的 BiomeProvider。
     * 使用双重检查锁定模式安全地获取或创建缓存的实例。
     * @param worldInfo 世界信息。
     * @return 自定义的 {@link BiomeProvider} 实例，如果初始化失败则返回 null。
     */
    @Override
    @Nullable
    public org.bukkit.generator.BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return getCachedBiomeProvider(worldInfo); // 使用辅助方法获取
    }

    /**
     * 获取用于填充区块特性的 BlockPopulator 列表。
     * 列表顺序建议了 Populator 的执行顺序（河流优先）。
     * 使用双重检查锁定模式安全地获取或创建缓存的实例。
     * @param worldInfo 世界信息。
     * @return 包含自定义 Populator (RiverPopulator, OrePopulator) 的列表。如果 Core 初始化失败则为空列表。
     */
    @Override
    public @NotNull abstract List<BlockPopulator> getDefaultPopulators(@NotNull World worldInfo);

    /**
     * 辅助方法: 获取缓存的 BiomeProvider (线程安全)。
     * @param worldInfo 世界信息。
     * @return BiomeProvider 实例，或 null 如果初始化失败。
     */
    @Nullable
    private BiomeProvider getCachedBiomeProvider(@NotNull WorldInfo worldInfo) {
        final NoiseGeneratorCore currentCore;
        try {
            currentCore = getCore(worldInfo); // 获取 Core
        } catch (IllegalStateException e) {
            LOGGER.log(Level.SEVERE, "无法获取 GeneratorCore，无法提供 BiomeProvider！", e);
            return null;
        }

        BiomeProvider provider = this.biomeProviderInstance; // 读 volatile
        if (provider == null) {
            synchronized(coreInitLock) {
                provider = this.biomeProviderInstance; // 再次读
                if (provider == null) {
                    provider = createBiomeProvider(core);
                    this.biomeProviderInstance = provider; // 写 volatile
                }
            }
        }
        return provider;
    }

    /**
     * 创建并返回一个使用给定 NoiseGeneratorCore 配置的新的 BiomeProvider 实例。
     * 此方法旨在供子类实现它们特定的生物群系（Biome）生成逻辑。
     *
     * @param core 预先配置的 {@link NoiseGeneratorCore} 实例，它为生物群系生成提供基于噪声的参数。
     * 不得为 null 且应被完全初始化。
     * @return 一个使用提供的 core 配置的新的 {@link BiomeProvider} 实例。
     */
    @NotNull
    protected abstract BiomeProvider createBiomeProvider(NoiseGeneratorCore core);


    // --- 控制生成阶段 (返回硬编码的 true/false，线程安全) ---
    /** 表示使用自定义噪声生成。 */
    @Override public abstract boolean shouldGenerateNoise();
    /** 表示使用自定义地表生成。 */
    @Override public abstract boolean shouldGenerateSurface();
    /** 表示使用自定义基岩生成。 */
    @Override public abstract boolean shouldGenerateBedrock();
    /** 禁用默认洞穴，因为使用自定义噪声生成。 */
    @Override public abstract boolean shouldGenerateCaves();
    /** 允许运行自定义 Populators 和默认装饰物（树木、花草等）。 */
    @Override public abstract boolean shouldGenerateDecorations();
    /** 允许默认生物生成。 */
    @Override public abstract boolean shouldGenerateMobs();
    /** 允许默认结构（村庄、神殿等）生成。 */
    @Override public abstract boolean shouldGenerateStructures();
}