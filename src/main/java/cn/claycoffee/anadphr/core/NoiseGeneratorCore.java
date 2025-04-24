package cn.claycoffee.anadphr.core;

import cn.claycoffee.anadphr.settings.BiomeSettings;
import cn.claycoffee.anadphr.settings.TerrainSettings;
import org.bukkit.Material;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

/**
 * 封装世界生成的核心逻辑、配置和噪声生成器。
 * 设计为线程安全：不持有可变状态（除final字段），不使用共享 Random 实例进行生成。
 * 通过构造函数接收所有配置对象。
 */
public final class NoiseGeneratorCore { // 标记为 final

    // --- 配置实例 (final, 保证线程安全) ---
    @NotNull public final TerrainSettings terrainSettings;
    @NotNull public final BiomeSettings biomeSettings;

    // --- 噪声生成器 (final, 线程安全) ---
    private final SimplexOctaveGenerator baseTerrainNoise;
    private final SimplexOctaveGenerator detailTerrainNoise;
    private final SimplexNoiseGenerator temperatureNoise;
    private final SimplexNoiseGenerator humidityNoise;
    private final SimplexOctaveGenerator continentalnessNoise;
    private final SimplexNoiseGenerator biomeDitherNoise;
    private final SimplexOctaveGenerator erosionNoise;
    private final SimplexOctaveGenerator peaksValleysNoise;
    private final SimplexOctaveGenerator weirdnessNoise;

    private final long seed;

    // 用于确定性随机的种子 (从主种子派生)
    private final long deterministicRandSeed;

    /**
     * 创建 GeneratorCore 实例。
     * 此构造函数负责初始化所有基于种子和配置的噪声生成器。
     *
     * @param seed            世界种子，用于初始化所有噪声生成器以确保确定性。
     * @param terrainSettings 地形生成配置。不能为空。
     * @param biomeSettings   生物群系生成配置。不能为空。
     * @throws NullPointerException 如果任何 Settings 对象为 null。
     */
    public NoiseGeneratorCore(long seed,
                              @NotNull TerrainSettings terrainSettings, @NotNull BiomeSettings biomeSettings) {

        // 校验并存储传入的配置
        this.terrainSettings = Objects.requireNonNull(terrainSettings, "TerrainSettings cannot be null");
        this.biomeSettings = Objects.requireNonNull(biomeSettings, "BiomeSettings cannot be null");

        this.seed = seed;
        // 从主种子派生用于确定性随机的种子
        this.deterministicRandSeed = seed * 31L + 11L; // 简单的派生方式

        // --- 初始化所有噪声生成器 ---
        // 使用不同的种子偏移量确保每个噪声函数的独立性
        this.baseTerrainNoise = createOctaveGenerator(seed + 1, this.terrainSettings.baseOctaves, this.terrainSettings.baseFreq);
        this.detailTerrainNoise = createOctaveGenerator(seed + 2, this.terrainSettings.detailOctaves, this.terrainSettings.detailFreq);
        this.temperatureNoise = new SimplexNoiseGenerator(new Random(seed + 4));
        this.humidityNoise = new SimplexNoiseGenerator(new Random(seed + 5));
        this.continentalnessNoise = createOctaveGenerator(seed + 6, this.terrainSettings.continentalnessOctaves, this.terrainSettings.continentalnessFreq);
        this.biomeDitherNoise = new SimplexNoiseGenerator(new Random(seed + 7));
        this.erosionNoise = createOctaveGenerator(seed + 8, this.terrainSettings.erosionOctaves, this.terrainSettings.erosionFreq);
        this.peaksValleysNoise = createOctaveGenerator(seed + 9, this.terrainSettings.peaksValleysOctaves, this.terrainSettings.peaksValleysFreq);
        this.weirdnessNoise = createOctaveGenerator(seed + 10, this.terrainSettings.weirdnessOctaves, this.terrainSettings.weirdnessFreq);
    }

    public int getMinWorldHeight() {
        return terrainSettings.minHeight;
    }

    public int getMaxWorldHeight() {
        return terrainSettings.maxHeight;
    }

    /**
     * 辅助方法：创建并配置 SimplexOctaveGenerator。
     * 这是为了代码复用和简洁性。
     * @param seed 噪声种子。
     * @param octaves 叠加层数。
     * @param frequency 频率 (用于计算 scale)。
     * @return 配置好的 SimplexOctaveGenerator 实例。
     */
    private SimplexOctaveGenerator createOctaveGenerator(long seed, int octaves, double frequency) {
        SimplexOctaveGenerator gen = new SimplexOctaveGenerator(new Random(seed), octaves);
        // 避免因 frequency 为 0 或极小导致 scale 无效
        if (Math.abs(frequency) > 1e-9) {
            gen.setScale(1.0 / frequency);
        } else {
            gen.setScale(1.0); // 设置一个默认 scale
        }
        return gen;
    }

    /**
     * 计算指定 (X, Z) 坐标的地表高度。
     * 此方法结合了基础地形噪声和细节地形噪声，并应用了相应的幅度和偏移量。
     * 设计为线程安全的。
     * @param x 世界 X 坐标。
     * @param z 世界 Z 坐标。
     * @return 计算得到的地表高度 Y 值，该值被限制在世界的高度范围内。
     */
    public int calculateTerrainHeight(int x, int z) {
        final double baseNoise = baseTerrainNoise.noise(x, z, terrainSettings.basePersistence, terrainSettings.baseLacunarity, true);
        final double detailNoise = detailTerrainNoise.noise(x, z, terrainSettings.detailPersistence, terrainSettings.detailLacunarity, true);
        // 组合噪声并计算高度
        final double combinedNoise = baseNoise * terrainSettings.baseHeightScale + detailNoise * terrainSettings.detailHeightScale;
        // 基础高度偏移量，使平均地表在海平面以上
        final int baseHeightOffset = terrainSettings.seaLevel + 5;
        final int finalHeight = (int) (baseHeightOffset + combinedNoise);
        // 使用 Math.clamp (如果 Java 版本支持) 或 Math.max/min 限制高度
        // return Math.clamp(finalHeight, terrainSettings.minHeight + 1, terrainSettings.maxHeight - 1);
        return Math.max(terrainSettings.minHeight + 1, Math.min(finalHeight, terrainSettings.maxHeight - 1));
    }

    /**
     * 根据 Y 坐标和地表高度获取基础地质材料（石头、深板岩、凝灰岩）。
     * 使用基于坐标和种子的确定性伪随机数来处理过渡带，保证线程安全和结果一致性。
     *
     * @param y 当前 Y 坐标。
     * @param terrainHeight 当前 (X, Z) 的地表高度。
     * @param worldX 世界 X 坐标，用于确定性随机。
     * @param worldZ 世界 Z 坐标，用于确定性随机。
     * @return 计算得到的基础地质材料 {@link Material}。
     */
    @NotNull
    public Material getBaseMaterial(int y, int terrainHeight, int worldX, int worldZ) {
        // --- 确定性伪随机数生成 ---
        // 使用简单的哈希组合坐标和种子
        long hash = worldX * 31L + y * 17L + worldZ * 13L + this.deterministicRandSeed;
        // 从哈希生成一个 [0.0, 1.0) 的伪随机 double 值
        double pseudoRandom = ((double) (hash & 0xFFFFFFL)) / (double) 0x1000000L; // 使用长整型避免溢出

        // --- 材质选择逻辑 ---
        // 可选：凝灰岩层 (在深板岩层上方少量出现)
        if (y < terrainSettings.deepslateLevel + 5 && y > terrainSettings.deepslateLevel - 15 && pseudoRandom < 0.15) { // 调整概率和范围
            return Material.TUFF;
        }

        // 深板岩过渡带 (使用伪随机数)
        if (y < terrainSettings.deepslateLevel + 8) { // 检查是否在过渡带内或以下
            final double transitionStart = terrainSettings.deepslateLevel - 8.0;
            final double transitionRange = 16.0; // 过渡带宽度
            // 使用平滑插值因子
            final double factor = Math.max(0.0, Math.min(1.0, (y - transitionStart) / transitionRange));
            // 使用伪随机数决定是否替换为深板岩 (越深概率越高)
            if (pseudoRandom > factor * factor) { // factor^2 使过渡在底部更快完成
                return Material.DEEPSLATE;
            }
            // 如果在过渡带内但随机数判定为否，则仍然是石头
            return Material.STONE;
        }

        // 默认返回石头
        return Material.STONE;
    }

    /**
     * 获取用于生物群系决策的所有相关噪声参数。
     * 此方法一次性计算指定坐标的所有生物群系相关噪声，避免重复计算。
     * 设计为线程安全的。
     * @param x 世界 X 坐标。
     * @param z 世界 Z 坐标。
     * @return 包含所有必需噪声值的 {@link BiomeParameterBundle}。
     */
    @NotNull
    public BiomeParameterBundle getBiomeParameters(int x, int z) {
        // 频率参数 (来自对应 Settings 对象)
        final double tempFreq = biomeSettings.temperatureFrequency;
        final double humFreq = biomeSettings.humidityFrequency;
        final double contFreq = terrainSettings.continentalnessFreq;
        final double erosFreq = terrainSettings.erosionFreq;
        final double peakFreq = terrainSettings.peaksValleysFreq;
        final double weirdFreq = terrainSettings.weirdnessFreq;
        final double ditherScale = biomeSettings.transitionDitherScale;

        // 计算所有噪声值 (噪声生成器本身是线程安全的)
        final double temperature = (temperatureNoise.noise(x * tempFreq, z * tempFreq) + 1.0) * 0.5; // 归一化到 [0, 1]
        final double humidity = (humidityNoise.noise(x * humFreq, z * humFreq) + 1.0) * 0.5;     // 归一化到 [0, 1]
        final double continentalness = (continentalnessNoise.noise(x, z, terrainSettings.continentalnessPersistence, terrainSettings.continentalnessLacunarity, true) + 1.0) * 0.5; // 归一化到 [0, 1]
        final double erosion = erosionNoise.noise(x, z, terrainSettings.erosionPersistence, terrainSettings.erosionLacunarity, true);           // [-1, 1]
        final double peaksValleys = peaksValleysNoise.noise(x, z, terrainSettings.peaksValleysPersistence, terrainSettings.peaksValleysLacunarity, true); // [-1, 1]
        final double weirdness = weirdnessNoise.noise(x, z, terrainSettings.weirdnessPersistence, terrainSettings.weirdnessLacunarity, true);     // [-1, 1]
        final double dither = biomeDitherNoise.noise(x * ditherScale, z * ditherScale);                                                 // [-1, 1]

        // 返回包含所有值的 Record
        return new BiomeParameterBundle(temperature, humidity, continentalness, erosion, peaksValleys, weirdness, dither);
    }

    /**
     * 内部记录类，用于捆绑生物群系相关噪声参数 (不可变)。
     * @param temperature    温度噪声 [0, 1]
     * @param humidity       湿度噪声 [0, 1]
     * @param continentalness 大陆性噪声 [0, 1]
     * @param erosion        侵蚀度噪声 [-1, 1]
     * @param peaksValleys   山峰/河谷噪声 [-1, 1]
     * @param weirdness      怪异度噪声 [-1, 1]
     * @param dither         抖动噪声 [-1, 1]
     */
    public record BiomeParameterBundle(
            double temperature, double humidity, double continentalness,
            double erosion, double peaksValleys, double weirdness, double dither
    ) {}

    public long getSeed() {
        return seed;
    }

    public long getDeterministicRandSeed() {
        return deterministicRandSeed;
    }
}
