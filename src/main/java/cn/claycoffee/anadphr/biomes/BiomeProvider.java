package cn.claycoffee.anadphr.biomes;

import cn.claycoffee.anadphr.core.NoiseGeneratorCore;
import cn.claycoffee.anadphr.settings.BiomeSettings;
import cn.claycoffee.anadphr.settings.TerrainSettings;
import org.bukkit.block.Biome;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * 自定义的生物群系提供器 (线程安全)。
 * 它通过注入的 {@link NoiseGeneratorCore} 实例获取必要的噪声数据和配置，
 * 然后调用 {@link BiomeSettings#selectBiome(BiomeSettings.BiomeSelectionInputs, TerrainSettings)}
 * 来为给定的世界坐标选择合适的生物群系。
 */
public final class BiomeProvider extends org.bukkit.generator.BiomeProvider { // 标记为 final

    /** 持有的核心生成器引用 (final, 线程安全)。 */
    @NotNull
    private final NoiseGeneratorCore core;

    /**
     * 创建一个新的 BiomeProvider 实例。
     * @param core 注入的 {@link NoiseGeneratorCore} 实例，提供所有必需的噪声和配置。不能为空。
     * @throws NullPointerException 如果 core 为 null。
     */
    public BiomeProvider(@NotNull NoiseGeneratorCore core) {
        // 确保注入的 Core 不为 null
        this.core = Objects.requireNonNull(core, "GeneratorCore cannot be null for BiomeProvider");
    }

    /**
     * 获取指定世界坐标的生物群系。
     * 这是生物群系生成的核心方法，会被 Bukkit 多次（可能并发地）调用。
     * 此实现是线程安全的，因为它不依赖可变共享状态，并且调用的 Core 方法也是线程安全的。
     *
     * @param worldInfo 提供关于当前世界的信息，如种子、高度限制等。
     * @param x         查询点的世界 X 坐标。
     * @param y         查询点的世界 Y 坐标 (注意: 这通常是 Bukkit 内部查询的高度，不一定是实际地表高度)。
     * @param z         查询点的世界 Z 坐标。
     * @return 计算得到的 {@link Biome}。永远不会返回 null。
     */
    @Override
    @NotNull
    public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        // --- 线程安全注意 ---
        // 1. core 是 final 的，其内部方法被设计为线程安全的。
        // 2. noiseParams 是局部变量。
        // 3. terrainHeight 是局部计算结果。
        // 4. inputs 是局部记录对象。
        // 5. selectBiome 方法被设计为线程安全的。

        // 1. 从 Core 获取所有基础噪声参数
        final NoiseGeneratorCore.BiomeParameterBundle noiseParams = core.getBiomeParameters(x, z);

        // 2. 计算当前 (X, Z) 的地表高度，这对海拔判断至关重要
        // 即使传入了 y，我们也需要地表高度来正确应用海拔影响
        final int terrainHeight = core.calculateTerrainHeight(x, z);

        // 3. 构建传递给 selectBiome 的输入对象
        // 使用传入的 y 作为 'altitude'，因为它可能是 Bukkit 关心的查询高度
        final BiomeSettings.BiomeSelectionInputs inputs = new BiomeSettings.BiomeSelectionInputs(
                noiseParams.temperature(), noiseParams.humidity(), noiseParams.continentalness(),
                noiseParams.erosion(), noiseParams.peaksValleys(), noiseParams.weirdness(),
                y,               // 当前查询点的 Y 坐标
                terrainHeight,   // 该XZ坐标的实际地表高度
                core.terrainSettings.seaLevel, // 从 Core 获取海平面
                noiseParams.dither()           // 抖动噪声
        );

        // 4. 调用 BiomeSettings 中的复杂逻辑来选择生物群系
        return core.biomeSettings.selectBiome(inputs, core.terrainSettings);
    }

    /**
     * 返回此生成器可能生成的所有生物群系的列表。
     * 用于世界预生成或信息查询。
     * @param worldInfo 提供世界信息。
     * @return 配置中定义的允许生物群系的不可修改列表。
     */
    @Override
    @NotNull
    public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        // 直接返回 Core 中存储的不可修改列表
        return core.biomeSettings.allowedBiomes;
    }
}