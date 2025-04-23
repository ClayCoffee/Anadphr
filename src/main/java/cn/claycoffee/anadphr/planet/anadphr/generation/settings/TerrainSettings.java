package cn.claycoffee.anadphr.planet.anadphr.generation.settings;

import org.jetbrains.annotations.NotNull;

/**
 * 地形生成参数配置 (不可变)。
 * 包含基础世界参数以及控制各种噪声函数的参数，这些噪声共同决定了地形的高度和宏观/微观形态。
 * 此类及其所有字段均为 final，确保实例在创建后不可修改，适用于多线程环境。
 */
public final class TerrainSettings { // 标记为 final

    // --- 基本世界参数 ---
    /** 世界海平面高度 Y 值。影响水体生成和部分生物群系判断。 */
    public final int seaLevel;
    /** 深板岩（Deepslate）开始替换石头的大致 Y 值。影响地下材质分布。 */
    public final int deepslateLevel;
    /** 当前世界允许的最低 Y 高度。通常由服务器或世界配置决定。 */
    public final int minHeight;
    /** 当前世界允许的最高 Y 高度。通常由服务器或世界配置决定。 */
    public final int maxHeight;

    // --- 基础地形噪声 (宏观形状) ---
    /** 基础地形噪声频率 (Frequency)。控制大陆、山脉等大型特征的大小。值越小，特征越大。 ( > 0) */
    public final double baseFreq;
    /** 基础地形噪声叠加层数 (Octaves)。增加复杂度，但也增加计算量。 ( > 0) */
    public final int baseOctaves;
    /** 基础地形噪声持续性 (Persistence)。影响高频噪声的幅度衰减。 (通常 0-1) */
    public final double basePersistence;
    /** 基础地形噪声空隙度 (Lacunarity)。影响高频噪声的频率增加。 (一般 > 1) */
    public final double baseLacunarity;
    /** 基础地形噪声对最终高度的影响幅度 (Amplitude/Scale)。决定了基础地形的起伏程度。 ( > 0) */
    public final double baseHeightScale;

    // --- 细节地形噪声 (小尺度起伏) ---
    /** 细节噪声频率。控制小山丘、地面细节等。应显著高于基础频率。 ( > 0) */
    public final double detailFreq;
    /** 细节噪声叠加层数。通常较少。 ( > 0) */
    public final int detailOctaves;
    /** 细节噪声持续性。 */
    public final double detailPersistence;
    /** 细节噪声空隙度。 */
    public final double detailLacunarity;
    /** 细节噪声对最终高度的影响幅度。决定了细节的起伏程度。 ( >= 0) */
    public final double detailHeightScale;

    // --- 大陆性/海洋性噪声 (影响生物群系) ---
    /** 大陆性噪声频率。用于区分沿海和内陆的大尺度区域。通常非常低。 ( > 0) */
    public final double continentalnessFreq;
    /** 大陆性噪声叠加层数。通常较少。 ( > 0) */
    public final int continentalnessOctaves;
    /** 大陆性噪声持续性。 */
    public final double continentalnessPersistence;
    /** 大陆性噪声空隙度。 */
    public final double continentalnessLacunarity;

    // --- 侵蚀度噪声 (影响生物群系) ---
    /** 侵蚀度噪声频率。模拟地形受流水/风力影响的程度。 ( > 0) */
    public final double erosionFreq;
    /** 侵蚀度噪声叠加层数。 ( > 0) */
    public final int erosionOctaves;
    /** 侵蚀度噪声持续性。 */
    public final double erosionPersistence;
    /** 侵蚀度噪声空隙度。 */
    public final double erosionLacunarity;

    // --- 山峰/河谷噪声 (影响地貌和生物群系) ---
    /** 山峰/河谷噪声频率。用于产生更陡峭或更平缓的区域。 ( > 0) */
    public final double peaksValleysFreq;
    /** 山峰/河谷噪声叠加层数。 ( > 0) */
    public final int peaksValleysOctaves;
    /** 山峰/河谷噪声持续性。 */
    public final double peaksValleysPersistence;
    /** 山峰/河谷噪声空隙度。 */
    public final double peaksValleysLacunarity;
    /** 用于判定尖锐山峰的噪声阈值 (基于噪声绝对值)。 (一般 0-1) */
    public final double peaksThreshold;

    // --- 怪异度/罕见度噪声 (用于特殊生物群系) ---
    /** 怪异度噪声频率。用于产生大片稀有生物群系区域。非常低。 ( > 0) */
    public final double weirdnessFreq;
    /** 怪异度噪声叠加层数。通常很少。 ( > 0) */
    public final int weirdnessOctaves;
    /** 怪异度噪声持续性。 */
    public final double weirdnessPersistence;
    /** 怪异度噪声空隙度。 */
    public final double weirdnessLacunarity;
    /** 用于判定特殊生物群系的噪声阈值。 (一般 0-1) */
    public final double weirdnessThreshold;

    /**
     * 创建一个新的、不可变的 {@link TerrainSettings} 实例。
     * 此构造函数接收所有地形生成所需的核心参数。
     *
     * @param seaLevel 海平面Y值。
     * @param deepslateLevel 深板岩开始生成的大致Y值。
     * @param minHeight 世界允许的最低Y值。
     * @param maxHeight 世界允许的最高Y值。
     * @param baseFreq 基础地形噪声频率。
     * @param baseOctaves 基础地形噪声叠加层数。
     * @param basePersistence 基础地形噪声持续性。
     * @param baseLacunarity 基础地形噪声空隙度。
     * @param baseHeightScale 基础地形噪声幅度。
     * @param detailFreq 细节地形噪声频率。
     * @param detailOctaves 细节地形噪声叠加层数。
     * @param detailPersistence 细节地形噪声持续性。
     * @param detailLacunarity 细节地形噪声空隙度。
     * @param detailHeightScale 细节地形噪声幅度。
     * @param continentalnessFreq 大陆性噪声频率。
     * @param continentalnessOctaves 大陆性噪声叠加层数。
     * @param continentalnessPersistence 大陆性噪声持续性。
     * @param continentalnessLacunarity 大陆性噪声空隙度。
     * @param erosionFreq 侵蚀度噪声频率。
     * @param erosionOctaves 侵蚀度噪声叠加层数。
     * @param erosionPersistence 侵蚀度噪声持续性。
     * @param erosionLacunarity 侵蚀度噪声空隙度。
     * @param peaksValleysFreq 山峰/河谷噪声频率。
     * @param peaksValleysOctaves 山峰/河谷噪声叠加层数。
     * @param peaksValleysPersistence 山峰/河谷噪声持续性。
     * @param peaksValleysLacunarity 山峰/河谷噪声空隙度。
     * @param peaksThreshold 用于判定尖锐山峰的噪声阈值。
     * @param weirdnessFreq 怪异度/罕见度噪声频率。
     * @param weirdnessOctaves 怪异度/罕见度噪声叠加层数。
     * @param weirdnessPersistence 怪异度/罕见度噪声持续性。
     * @param weirdnessLacunarity 怪异度/罕见度噪声空隙度。
     * @param weirdnessThreshold 用于判定特殊生物群系的噪声阈值。
     * @throws IllegalArgumentException 如果基本参数不合理（例如 minHeight >= maxHeight 或 seaLevel 超出范围）。
     */
    public TerrainSettings(int seaLevel, int deepslateLevel, int minHeight, int maxHeight,
                           double baseFreq, int baseOctaves, double basePersistence, double baseLacunarity, double baseHeightScale,
                           double detailFreq, int detailOctaves, double detailPersistence, double detailLacunarity, double detailHeightScale,
                           double continentalnessFreq, int continentalnessOctaves, double continentalnessPersistence, double continentalnessLacunarity,
                           double erosionFreq, int erosionOctaves, double erosionPersistence, double erosionLacunarity,
                           double peaksValleysFreq, int peaksValleysOctaves, double peaksValleysPersistence, double peaksValleysLacunarity, double peaksThreshold,
                           double weirdnessFreq, int weirdnessOctaves, double weirdnessPersistence, double weirdnessLacunarity, double weirdnessThreshold) {

        // 基本参数验证
        if (minHeight >= maxHeight) throw new IllegalArgumentException("minHeight (" + minHeight + ") must be less than maxHeight (" + maxHeight + ")");
        if (seaLevel < minHeight || seaLevel >= maxHeight) throw new IllegalArgumentException("seaLevel (" + seaLevel + ") must be between minHeight [" + minHeight + "] and maxHeight [" + maxHeight + ")");
        // 可选: 添加对频率、Octaves等参数的更严格验证 (例如 > 0)

        // 字段赋值
        this.seaLevel = seaLevel; this.deepslateLevel = deepslateLevel; this.minHeight = minHeight; this.maxHeight = maxHeight;
        this.baseFreq = baseFreq; this.baseOctaves = baseOctaves; this.basePersistence = basePersistence; this.baseLacunarity = baseLacunarity; this.baseHeightScale = baseHeightScale;
        this.detailFreq = detailFreq; this.detailOctaves = detailOctaves; this.detailPersistence = detailPersistence; this.detailLacunarity = detailLacunarity; this.detailHeightScale = detailHeightScale;
        this.continentalnessFreq = continentalnessFreq; this.continentalnessOctaves = continentalnessOctaves; this.continentalnessPersistence = continentalnessPersistence; this.continentalnessLacunarity = continentalnessLacunarity;
        this.erosionFreq = erosionFreq; this.erosionOctaves = erosionOctaves; this.erosionPersistence = erosionPersistence; this.erosionLacunarity = erosionLacunarity;
        this.peaksValleysFreq = peaksValleysFreq; this.peaksValleysOctaves = peaksValleysOctaves; this.peaksValleysPersistence = peaksValleysPersistence; this.peaksValleysLacunarity = peaksValleysLacunarity; this.peaksThreshold = peaksThreshold;
        this.weirdnessFreq = weirdnessFreq; this.weirdnessOctaves = weirdnessOctaves; this.weirdnessPersistence = weirdnessPersistence; this.weirdnessLacunarity = weirdnessLacunarity; this.weirdnessThreshold = weirdnessThreshold;
    }

    /**
     * 获取具有推荐默认参数的地形设置实例。
     * 这些默认值旨在产生一个相对平衡和有趣的地形，但可能需要根据具体需求进行调整。
     *
     * @param minWorldHeight 当前世界允许的最低 Y 值。
     * @param maxWorldHeight 当前世界允许的最高 Y 值。
     * @return 一个配置好的、不可变的 {@link TerrainSettings} 实例。
     */
    @NotNull
    public static TerrainSettings getDefault(int minWorldHeight, int maxWorldHeight) {
        // 使用精心调整的默认值
        return new TerrainSettings(
                63, 8, minWorldHeight, maxWorldHeight, // 基本参数
                0.0035, 8, 0.5, 2.0, 80.0,  // Base Noise (低频, 高影响)
                0.025, 4, 0.45, 2.1, 10.0, // Detail Noise (高频, 低影响)
                0.0015, 3, 0.5, 2.0,      // Continentalness Noise (极低频)
                0.018, 6, 0.5, 2.1,      // Erosion Noise (中频)
                0.022, 5, 0.55, 2.2, 0.5, // Peaks/Valleys Noise + Threshold (中高频, 用于判断山峰)
                0.0006, 3, 0.45, 2.0, 0.65 // Weirdness Noise + Threshold (极低频, 用于稀有生物群系)
        );
    }
}
