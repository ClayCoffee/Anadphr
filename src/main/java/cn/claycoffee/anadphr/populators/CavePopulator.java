package cn.claycoffee.anadphr.populators;

import cn.claycoffee.anadphr.core.NoiseGeneratorCore;
import cn.claycoffee.anadphr.planet.anadphr.generation.AnadphrChunkGenerator;
import cn.claycoffee.anadphr.settings.CaveSettings;
import cn.claycoffee.anadphr.settings.TerrainSettings;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

/**
 * BlockPopulator 实现，用于在基础地形生成后雕刻出洞穴。
 * 它使用 {@link NoiseGeneratorCore} 提供的 3D 噪声函数来决定哪些方块应被移除。
 * 设计为线程安全的。
 */
public final class CavePopulator extends BlockPopulator {

    @NotNull
    private final NoiseGeneratorCore core;

    @NotNull
    private final AnadphrChunkGenerator generator;

    @NotNull
    private final CaveSettings settings;

    @NotNull
    private final SimplexNoiseGenerator noise;

    /**
     * 创建一个新的 CavePopulator 实例。
     * @param generator 注入的 {@link AnadphrChunkGenerator} 实例。不能为空。
     * @throws NullPointerException 如果 core 为 null。
     */
    public CavePopulator(@NotNull AnadphrChunkGenerator generator, CaveSettings settings) {
        this.generator = Objects.requireNonNull(generator, "GeneratorCore cannot be null for CavePopulator");
        this.core = generator.getCore();
        this.settings = Objects.requireNonNull(settings, "CaveSettings cannot be null for CavePopulator");
        this.noise = new SimplexNoiseGenerator(new Random(core.getSeed() + 3));
    }

    /**
     * 在指定的区块及其邻近区域填充洞穴特征（即挖空方块）。
     * 此方法会在基础地形（generateNoise）生成后、其他 Populator（如河流、矿物）运行之前被调用。
     * 实现是线程安全的。
     *
     * @param worldInfo 世界信息，用于获取高度限制和海平面。
     * @param random 用于此次填充操作的 Random 实例 (由 Bukkit 提供，在此上下文中线程安全)。当前实现未使用。
     * @param chunkX 当前填充区块的 X 坐标。
     * @param chunkZ 当前填充区块的 Z 坐标。
     * @param limitedRegion 提供对区块及邻近区域方块的安全、线程安全的访问接口。
     */
    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        // 获取配置和世界基本信息
        final TerrainSettings terrainSettings = core.terrainSettings; // 获取地形配置（需要海平面）
        final int seaLevel = terrainSettings.seaLevel;
        final int minHeight = worldInfo.getMinHeight();
        final int maxHeight = worldInfo.getMaxHeight();
        final int chunkMinX = chunkX << 4;
        final int chunkMinZ = chunkZ << 4;

        // 优化: 仅遍历当前区块 (0-15) 还是整个 LimitedRegion？
        // 遍历整个 LimitedRegion 可以确保洞穴在区块边界处平滑连接，但计算量稍大。
        // 这里选择遍历当前区块，依赖邻近区块的 Populator 处理连接。
        // 如果需要更完美的边界连接，应遍历整个 LimitedRegion 范围。

        for (int x = 0; x < 16; x++) {
            final int worldX = chunkMinX + x;
            for (int z = 0; z < 16; z++) {
                final int worldZ = chunkMinZ + z;

                // 遍历 Y 轴 (从最低到最高)
                // 优化: 可以从地表向下遍历到最低，或者只检查石头/深板岩层？
                // 从下往上遍历，直到接近地表或世界顶部
                for (int y = minHeight; y < maxHeight; y++) {

                    // 检查坐标是否真的在 LimitedRegion 内 (保险起见)
                    // 虽然我们理论上只处理当前区块，但 LimitedRegion 可能有特殊边界
                    if (!limitedRegion.isInRegion(worldX, y, worldZ)) {
                        continue;
                    }

                    // --- 洞穴判断 ---
                    // 调用 GeneratorCore 的 isCave 方法判断当前坐标是否应为洞穴
                    if (isCave(worldX, y, worldZ)) {
                        // --- 洞穴处理 ---
                        // 获取当前位置的方块类型
                        Material currentMaterial = limitedRegion.getType(worldX, y, worldZ);

                        // 只替换固体方块 (避免挖掉空气或已有的水)
                        // 同时也避免替换基岩
                        if (currentMaterial.isSolid() && currentMaterial != Material.BEDROCK) {
                            // --- 水下洞穴处理 ---
                            // 如果洞穴位置在海平面或以下
                            if (y <= seaLevel) {
                                // 检查下方方块，如果下方是空气，则这里也保持空气（形成竖井）
                                // 否则填充水
                                if (y > minHeight && limitedRegion.getType(worldX, y - 1, worldZ) == Material.AIR) {
                                    limitedRegion.setType(worldX, y, worldZ, Material.AIR);
                                } else {
                                    limitedRegion.setType(worldX, y, worldZ, Material.WATER);
                                }
                            } else {
                                // 陆地上的洞穴，直接设置为空气
                                limitedRegion.setType(worldX, y, worldZ, Material.AIR);
                            }
                        }
                    } // 结束 isCave 判断
                } // 结束 y 循环
            } // 结束 z 循环
        } // 结束 x 循环
    }

    /**
     * 判断指定 (X, Y, Z) 坐标是否应为洞穴。
     * 基于 3D Simplex 噪声值与 {@link CaveSettings#threshold} 的比较。
     * 设计为线程安全的。
     * @param x 世界 X 坐标。
     * @param y 世界 Y 坐标。
     * @param z 世界 Z 坐标。
     * @return 如果该点的噪声值超过阈值，则返回 true，表示是洞穴。
     */
    public boolean isCave(int x, int y, int z) {
        // 计算 3D 噪声值
        final double noiseValue = noise.noise(
                x * settings.frequency,
                y * settings.frequency,
                z * settings.frequency);
        // 比较阈值
        return noiseValue > settings.threshold;
    }

    public @NotNull AnadphrChunkGenerator getGenerator() {
        return generator;
    }
}
