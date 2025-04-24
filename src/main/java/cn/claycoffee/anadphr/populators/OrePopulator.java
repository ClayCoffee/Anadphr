package cn.claycoffee.anadphr.populators;

import cn.claycoffee.anadphr.core.NoiseGeneratorCore;
import cn.claycoffee.anadphr.planet.anadphr.generation.AnadphrChunkGenerator;
import cn.claycoffee.anadphr.settings.OreConfig;
import cn.claycoffee.anadphr.settings.OreSettings;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * BlockPopulator 实现，用于在区块生成后填充矿物 (线程安全)。
 * 依赖 {@link NoiseGeneratorCore} 提供矿物配置和洞穴判断。
 * 仅使用传递给 populate 方法的 Random 实例和 LimitedRegion。
 * 修正：使用 WorldInfo 获取世界高度限制。
 */
public final class OrePopulator extends BlockPopulator { // 标记为 final

    @NotNull
    private final NoiseGeneratorCore core;

    @NotNull
    private final AnadphrChunkGenerator generator;

    @NotNull
    private final OreSettings settings;

    @Nullable
    private final CavePopulator cavePopulator;

    // 缓存山地生物群系列表 (静态 final，线程安全)
    private static final List<Biome> MOUNTAIN_BIOMES = List.of(/* ... 同上 ... */
            Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_FOREST,
            Biome.GROVE, Biome.SNOWY_SLOPES, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.STONY_PEAKS, Biome.TAIGA
    );

    /**
     * 创建一个新的 OrePopulator 实例。
     * @param generator 注入的 {@link AnadphrChunkGenerator}。不能为空。
     * @throws NullPointerException 如果 core 为 null。
     */
    public OrePopulator(@NotNull AnadphrChunkGenerator generator, OreSettings settings, @Nullable CavePopulator cavePopulator) {
        this.generator = Objects.requireNonNull(generator, "GeneratorCore cannot be null for CavePopulator");
        this.core = generator.getCore();
        this.settings = Objects.requireNonNull(settings, "OreSettings cannot be null");
        this.cavePopulator = cavePopulator;
    }

    /**
     * 在指定的区块及其邻近区域填充矿物。
     * 实现是线程安全的。
     *
     * @param worldInfo 世界信息，用于获取高度限制等。**从此对象获取 min/max Height**。
     * @param random 用于此次填充操作的 Random 实例 (由 Bukkit 提供，在此上下文中线程安全)。
     * @param chunkX 当前填充区块的 X 坐标。
     * @param chunkZ 当前填充区块的 Z 坐标。
     * @param limitedRegion 提供对区块及邻近区域方块的安全、线程安全的访问接口。
     */
    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        // **修正**: 从 worldInfo 获取高度限制
        final int minHeight = worldInfo.getMinHeight();
        final int maxHeight = worldInfo.getMaxHeight();
        final int deepslateLevel = core.terrainSettings.deepslateLevel;
        final int centerX = chunkX * 16 + 8;
        final int centerZ = chunkZ * 16 + 8;
        // **修正**: 确保 biomeCheckY 在世界高度内
        final int biomeCheckY = Math.max(minHeight, Math.min(maxHeight - 1, core.terrainSettings.seaLevel + 10));
        final Biome centerBiome = limitedRegion.getBiome(centerX, biomeCheckY, centerZ);

        for (OreConfig oreConfig : settings.ores) {
            if (oreConfig.oreType() == Material.EMERALD_ORE && !MOUNTAIN_BIOMES.contains(centerBiome)) {
                continue;
            }

            final Material oreType = oreConfig.oreType();
            final Material deepslateOreType = oreConfig.deepslateOreType();
            final int attempts = oreConfig.attemptsPerChunk();
            // **修正**: minY/maxY 使用 worldInfo 确定的边界
            final int minY = Math.max(minHeight, oreConfig.minY());
            final int maxY = Math.min(maxHeight, oreConfig.maxY());
            final int veinSize = oreConfig.veinSize();
            final double baseChance = oreConfig.baseChance();
            final double enrichmentFactor = oreConfig.caveEnrichmentFactor();

            if (minY >= maxY) continue;

            for (int i = 0; i < attempts; i++) {
                // 使用传入的 random
                final int x = random.nextInt(16);
                final int z = random.nextInt(16);
                final int y = random.nextInt(maxY - minY) + minY; // [minY, maxY-1]

                // **修正**: 检查 y 是否在世界范围内 (理论上 random.nextInt 已经保证)
                if (y < minHeight || y >= maxHeight) continue;


                final int worldX = chunkX * 16 + x;
                final int worldZ = chunkZ * 16 + z;


                final double chanceMultiplier;
                if(cavePopulator != null) chanceMultiplier = cavePopulator.isCave(worldX, y, worldZ) ? enrichmentFactor : 1.0;
                else chanceMultiplier = 1.0;
                final double finalChance = baseChance * chanceMultiplier;

                // 使用传入的 random
                if (random.nextDouble() < finalChance) {
                    final Material finalOreType = (y < deepslateLevel && deepslateOreType != null) ? deepslateOreType : oreType;
                    // 传入 worldInfo 以便 generateVein 检查边界
                    generateVein(worldInfo, limitedRegion, random, finalOreType, worldX, y, worldZ, veinSize);
                }
            }
        }
    }

    /**
     * 生成一个矿脉（一簇矿石方块）(线程安全)。
     * @param worldInfo 提供世界高度限制。
     * @param region 安全访问区域。
     * @param random 用于随机放置矿石的 Random 实例。
     * @param oreType 要生成的矿石材质。
     * @param centerX 矿脉中心的世界 X 坐标。
     * @param centerY 矿脉中心的世界 Y 坐标。
     * @param centerZ 矿脉中心的世界 Z 坐标。
     * @param maxSize 矿脉大致包含的方块数量。
     */
    private void generateVein(@NotNull WorldInfo worldInfo, @NotNull LimitedRegion region, @NotNull Random random, @NotNull Material oreType, int centerX, int centerY, int centerZ, int maxSize) {
        if (maxSize <= 0) return;

        int count = 0;
        final double radius = Math.pow(maxSize, 1.0/3.0) * 0.8 + 1.0;
        final int searchRadius = (int) Math.ceil(radius);
        // **修正**: 从 worldInfo 获取高度限制
        final int startY = Math.max(worldInfo.getMinHeight(), centerY - searchRadius);
        final int endY = Math.min(worldInfo.getMaxHeight() - 1, centerY + searchRadius);

        for (int dy = startY - centerY; dy <= endY - centerY && count < maxSize; dy++) {
            final int currentY = centerY + dy;
            // **修正**: 确保 currentY 在有效范围内
            if (currentY < worldInfo.getMinHeight() || currentY >= worldInfo.getMaxHeight()) continue;

            final double yDistFactor = 1.0 - (double)(dy * dy) / (searchRadius * searchRadius);
            if (yDistFactor <= 0) continue;
            final int xzSearchRadius = (int) Math.floor(searchRadius * Math.sqrt(yDistFactor));

            for (int dx = -xzSearchRadius; dx <= xzSearchRadius && count < maxSize; dx++) {
                for (int dz = -xzSearchRadius; dz <= xzSearchRadius && count < maxSize; dz++) {
                    final int currentX = centerX + dx;
                    final int currentZ = centerZ + dz;

                    // 使用传入的 random
                    final double distanceSq = dx*dx + dy*dy + dz*dz;
                    if (distanceSq > radius * radius * (random.nextDouble() * 0.6 + 0.7)) continue;

                    // **修正**: 检查 Y 坐标边界
                    if (!region.isInRegion(currentX, currentY, currentZ)) continue;

                    final Material existingMaterial = region.getType(currentX, currentY, currentZ);

                    if (existingMaterial == Material.STONE || existingMaterial == Material.DEEPSLATE || existingMaterial == Material.TUFF) {
                        region.setType(currentX, currentY, currentZ, oreType);
                        count++;
                    }
                }
            }
        }
    }

    /** 辅助方法：判断是否为山地生物群系。 */
    private boolean isMountainBiome(@NotNull Biome biome) {
        return MOUNTAIN_BIOMES.contains(biome);
    }

    public @NotNull AnadphrChunkGenerator getGenerator() {
        return generator;
    }
}