package cn.claycoffee.anadphr.settings;

import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 生物群系生成参数配置 (不可变)。
 * 包含决定生物群系分布的各种噪声参数和核心选择逻辑。
 * 这个类的核心是 {@link #selectBiome(BiomeSelectionInputs, TerrainSettings)} 方法，
 * 它根据多种环境因素决定一个坐标点的最终生物群系。
 */
public final class BiomeSettings {

    /** 温度基础噪声频率。影响温度变化区域的大小。 (> 0) */
    public final double temperatureFrequency;
    /** 湿度基础噪声频率。影响湿度变化区域的大小。 (> 0) */
    public final double humidityFrequency;
    /** 海拔直减率。海拔每升高1格，温度降低的值 (>= 0)。 */
    public final double altitudeLapseRate;
    /** 生物群系边界过渡抖动噪声的频率。用于平滑边界。 (> 0) */
    public final double transitionDitherScale;
    /** 此世界允许生成的所有生物群系列表 (不可修改)。 */
    @NotNull
    public final List<Biome> allowedBiomes;
    /** 用于判断河流生物群系的最大海拔偏移量 (相对于海平面) (>= 0)。 */
    public final int riverCheckMaxAltitudeOffset;
    /** 用于判断河流生物群系的侵蚀度噪声上限 (负值通常表示更湿润/平缓)。 */
    public final double riverErosionThreshold;

    /**
     * 用于传递给 {@link #selectBiome(BiomeSelectionInputs, TerrainSettings)} 的所有输入参数的记录类 (不可变)。
     * 封装了选择生物群系所需的所有环境和噪声信息。
     *
     * @param baseTemperature    基础温度噪声值 [0, 1]。反映了该区域的基础热量水平。
     * @param baseHumidity       基础湿度噪声值 [0, 1]。反映了该区域的基础水分水平。
     * @param continentalness    大陆性/海洋性噪声值 [0, 1] (0=海洋中心, 1=大陆中心)。影响气候的极端程度。
     * @param erosion            侵蚀度噪声值 [-1, 1]。可能反映了地形的崎岖程度或排水情况。
     * @param peaksValleys       山峰/河谷噪声值 [-1, 1]。绝对值大表示地形陡峭，正负可能区分山脊/山谷。
     * @param weirdness          怪异度/罕见度噪声值 [-1, 1]。用于引入非标准、稀有的生物群系。
     * @param altitude           当前查询点的 Y 坐标。用于计算海拔对温度的影响。
     * @param terrainHeight      当前 (X, Z) 坐标的地表高度。用于海拔相关的判断。
     * @param seaLevel           当前世界的海平面高度。用于区分水下、海岸和陆地。
     * @param ditherNoise        用于边界过渡的抖动噪声值 [-1, 1]。用于模糊生物群系边界。
     */
    public record BiomeSelectionInputs(
            double baseTemperature, double baseHumidity, double continentalness,
            double erosion, double peaksValleys, double weirdness,
            int altitude, int terrainHeight, int seaLevel, double ditherNoise
    ) {}

    /**
     * 创建一个新的、不可变的生物群系设置实例。
     *
     * @param temperatureFrequency 温度噪声频率 (> 0)。
     * @param humidityFrequency 湿度噪声频率 (> 0)。
     * @param altitudeLapseRate 海拔直减率 (>= 0)。
     * @param transitionDitherScale 边界抖动噪声频率 (> 0)。
     * @param riverCheckMaxAltitudeOffset 检查河流的最大相对高度 (>= 0)。
     * @param riverErosionThreshold 河流区域的侵蚀度阈值。
     * @param allowedBiomes 允许生成的生物群系列表。如果为 null，则使用默认列表。该列表将被复制以确保不可变性。
     * @throws IllegalArgumentException 如果频率、直减率或海拔偏移参数无效。
     */
    public BiomeSettings(double temperatureFrequency, double humidityFrequency, double altitudeLapseRate,
                         double transitionDitherScale, int riverCheckMaxAltitudeOffset, double riverErosionThreshold,
                         @Nullable List<Biome> allowedBiomes) {
        // 参数验证
        if (temperatureFrequency <= 0 || humidityFrequency <= 0 || transitionDitherScale <= 0)
            throw new IllegalArgumentException("Frequencies must be positive");
        if (altitudeLapseRate < 0) throw new IllegalArgumentException("Lapse rate cannot be negative");
        if (riverCheckMaxAltitudeOffset < 0) throw new IllegalArgumentException("River check altitude offset cannot be negative");

        this.temperatureFrequency = temperatureFrequency;
        this.humidityFrequency = humidityFrequency;
        this.altitudeLapseRate = altitudeLapseRate;
        this.transitionDitherScale = transitionDitherScale;
        this.riverCheckMaxAltitudeOffset = riverCheckMaxAltitudeOffset;
        this.riverErosionThreshold = riverErosionThreshold;
        // 防御性复制，确保列表不可变
        this.allowedBiomes = allowedBiomes != null ? List.copyOf(allowedBiomes) : getDefaultAllowedBiomes();
    }

    /**
     * 获取一个包含所有默认允许生物群系的不可修改列表。
     * 该列表基于 Minecraft 1.21+ 的常见生物群系。
     * @return 包含默认生物群系的 {@link List}&lt;{@link Biome}&gt;。
     */
    @NotNull
    public static List<Biome> getDefaultAllowedBiomes() {
        // 使用 List.of 创建不可变列表
        return List.of(
                Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.FOREST, Biome.FLOWER_FOREST, Biome.BIRCH_FOREST, Biome.OLD_GROWTH_BIRCH_FOREST, Biome.DARK_FOREST,
                Biome.TAIGA, Biome.OLD_GROWTH_PINE_TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA, Biome.SNOWY_TAIGA,
                Biome.SAVANNA, Biome.SAVANNA_PLATEAU, Biome.WINDSWEPT_SAVANNA,
                Biome.JUNGLE, Biome.SPARSE_JUNGLE, Biome.BAMBOO_JUNGLE,
                Biome.DESERT,
                Biome.BADLANDS, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS,
                Biome.SNOWY_PLAINS, Biome.ICE_SPIKES,
                Biome.SWAMP, Biome.MANGROVE_SWAMP,
                Biome.BEACH, Biome.SNOWY_BEACH, Biome.STONY_SHORE,
                Biome.RIVER, Biome.FROZEN_RIVER,
                Biome.OCEAN, Biome.DEEP_OCEAN, Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN,
                Biome.LUKEWARM_OCEAN, Biome.DEEP_LUKEWARM_OCEAN, Biome.WARM_OCEAN,
                Biome.MUSHROOM_FIELDS,
                Biome.MEADOW, Biome.GROVE, Biome.SNOWY_SLOPES, Biome.JAGGED_PEAKS, Biome.FROZEN_PEAKS, Biome.STONY_PEAKS,
                Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_FOREST,
                Biome.LUSH_CAVES
        );
    }


    public static BiomeSettings ANADPHR = new BiomeSettings(
                0.005, // temperatureFrequency
                        0.006, // humidityFrequency
                        0.005, // altitudeLapseRate
                        0.07,  // transitionDitherScale
                        3,     // riverCheckMaxAltitudeOffset
                        -0.4,  // riverErosionThreshold
                        null   // 使用默认的 allowedBiomes 列表
    );

    // 辅助枚举: 气候带，用于简化逻辑
    private enum ClimateZone { FROZEN, COLD, TEMPERATE, HOT }

    /**
     * 这是一个内置的默认生物群系选择逻辑。
     * 根据输入的噪声和环境参数选择最合适的生物群系。
     * 这是生成逻辑的核心，它整合了温度、湿度、海拔、大陆性、侵蚀度、山峰/河谷度、怪异度和抖动噪声，
     * 通过一系列优先级判断和气候带划分来决定最终的生物群系。
     * 此方法设计为线程安全的，因为它不依赖任何可变共享状态，并且所有计算都基于传入的 final 参数。
     *
     * @param inputs 包含所有相关参数的 {@link BiomeSelectionInputs} 输入对象。不能为空。
     * @param terrainSettings 地形设置，主要用于访问山峰阈值和怪异度阈值。不能为空。
     * @return 计算得到的 {@link Biome}。永远不会返回 null。
     * @throws NullPointerException 如果 inputs 或 terrainSettings 为 null。
     */
    @NotNull
    public Biome selectBiome(@NotNull BiomeSelectionInputs inputs, @NotNull TerrainSettings terrainSettings) {
        Objects.requireNonNull(inputs, "BiomeSelectionInputs cannot be null");
        Objects.requireNonNull(terrainSettings, "TerrainSettings cannot be null");

        // --- 0. 计算生效参数 ---
        final double ditherAmount = 0.04;
        final double effectiveTemp = Math.max(0.0, Math.min(1.0, inputs.baseTemperature + inputs.ditherNoise * ditherAmount));
        final double effectiveHumidity = Math.max(0.0, Math.min(1.0, inputs.baseHumidity + inputs.ditherNoise * ditherAmount));
        // 海拔调整温度，并再次确保在 [0, 1] 范围内
        final double altitudeAdjustedTemp = Math.max(0.0, Math.min(1.0, effectiveTemp - Math.max(0, inputs.altitude - inputs.seaLevel) * this.altitudeLapseRate));

        // --- 1. 优先级判断 (特殊地形/水体优先) ---

        // 特殊: 怪异度 -> 蘑菇岛 (在合适的陆地海拔)
        if (inputs.weirdness > terrainSettings.weirdnessThreshold && inputs.altitude >= inputs.seaLevel && inputs.altitude < inputs.seaLevel + 40) {
            return Biome.MUSHROOM_FIELDS;
        }

        // 特殊: 极端海拔 -> 山峰 (基于海拔、陡峭度和温度)
        final int peakStartAltitude = inputs.seaLevel + 120; // 山峰判断起始高度
        if (inputs.altitude > peakStartAltitude) {
            final double peakValue = Math.abs(inputs.peaksValleys); // 陡峭程度
            if (peakValue > terrainSettings.peaksThreshold) { // 足够陡峭 -> 山峰
                if (altitudeAdjustedTemp < 0.05) return Biome.FROZEN_PEAKS; // 极寒 -> 冰封山峰
                if (altitudeAdjustedTemp < 0.25) return Biome.JAGGED_PEAKS; // 寒冷 -> 裸岩山峰
                return Biome.STONY_PEAKS; // 其他 -> 石峰
            } else { // 不够陡峭的高海拔区域
                if (altitudeAdjustedTemp < 0.1) return Biome.SNOWY_SLOPES; // 寒冷 -> 雪坡
                if (altitudeAdjustedTemp < 0.4) return Biome.GROVE;      // 温和 -> 林地 (针叶林为主)
                return Biome.MEADOW;     // 温暖 -> 草甸
            }
        }

        // 水体: 海洋 / 河流
        if (inputs.altitude <= inputs.seaLevel) {
            // 河流判断: 靠近海平面 + 低侵蚀度 + 非极端大陆性
            final boolean isPotentialRiver = inputs.altitude > inputs.seaLevel - 15 && // 不太深 (允许深河?)
                    inputs.erosion < this.riverErosionThreshold &&
                    inputs.continentalness > 0.08 && inputs.continentalness < 0.92;
            if (isPotentialRiver) {
                return (altitudeAdjustedTemp < 0.1) ? Biome.FROZEN_RIVER : Biome.RIVER; // 根据温度区分冻河
            } else { // 海洋判断
                final boolean isDeep = inputs.altitude < inputs.seaLevel - 25 || inputs.continentalness < 0.03; // 深海判断条件更严格
                if (altitudeAdjustedTemp < 0.05) return isDeep ? Biome.DEEP_FROZEN_OCEAN : Biome.FROZEN_OCEAN;
                if (altitudeAdjustedTemp < 0.2) return isDeep ? Biome.DEEP_COLD_OCEAN : Biome.COLD_OCEAN;
                if (altitudeAdjustedTemp < 0.65) return isDeep ? Biome.DEEP_OCEAN : Biome.OCEAN;
                if (altitudeAdjustedTemp < 0.85) return isDeep ? Biome.DEEP_LUKEWARM_OCEAN : Biome.LUKEWARM_OCEAN;
                // 暖海通常不深，这里简化处理
                return Biome.WARM_OCEAN;
            }
        }

        // 海岸线 / 河岸线 (略高于海平面)
        if (inputs.altitude <= inputs.seaLevel + this.riverCheckMaxAltitudeOffset) {
            // 这里可以根据是否靠近河流（低侵蚀度？）选择河岸特定生物群系，但目前简化
            if (altitudeAdjustedTemp < 0.1) return Biome.SNOWY_BEACH; // 寒冷 -> 雪沙滩
            // 石岸 (陡峭或高侵蚀)
            if (Math.abs(inputs.peaksValleys) > 0.45 || inputs.erosion > 0.55) return Biome.STONY_SHORE;
            return Biome.BEACH; // 默认普通沙滩
        }

        // --- 2. 主要陆地生物群系 (基于气候带和详细参数) ---
        // 确定气候带
        final ClimateZone zone;
        if      (altitudeAdjustedTemp < 0.12) zone = ClimateZone.FROZEN;
        else if (altitudeAdjustedTemp < 0.35) zone = ClimateZone.COLD;
        else if (altitudeAdjustedTemp < 0.75) zone = ClimateZone.TEMPERATE;
        else                                  zone = ClimateZone.HOT;

        return switch (zone) {
            case FROZEN -> { // 冻原带
                // 冰刺
                if (effectiveHumidity > 0.7 && Math.abs(inputs.peaksValleys) > 0.6) yield Biome.ICE_SPIKES;
                yield Biome.SNOWY_PLAINS; // 默认雪原
            }
            case COLD -> { // 冷温带
                // 高地/风袭地判断
                if (inputs.altitude > inputs.seaLevel + 70 && (Math.abs(inputs.peaksValleys) > 0.35 || inputs.erosion > 0.4)) {
                    yield Biome.WINDSWEPT_HILLS; // 使用更通用的风袭丘陵
                }
                // 基于湿度区分
                if (effectiveHumidity < 0.3) yield Biome.PLAINS; // 冷但非冻结的平原
                if (effectiveHumidity < 0.65) {
                    // 区分不同针叶林
                    yield (inputs.erosion < -0.2) ? Biome.OLD_GROWTH_SPRUCE_TAIGA : Biome.TAIGA; // 低侵蚀->云杉, 否则普通
                }
                yield Biome.OLD_GROWTH_PINE_TAIGA; // 湿冷 -> 松木林
            }
            case TEMPERATE -> { // 温带
                // 恶地判断 (干 + 高侵蚀)
                if (effectiveHumidity < 0.25 && inputs.erosion > 0.65) {
                    yield (Math.abs(inputs.peaksValleys) > 0.5) ? Biome.ERODED_BADLANDS : Biome.BADLANDS;
                }
                // 风袭地判断
                if (inputs.altitude > inputs.seaLevel + 65 && (Math.abs(inputs.peaksValleys) > 0.3 || inputs.erosion > 0.5)) {
                    yield (effectiveHumidity < 0.4) ? Biome.WINDSWEPT_GRAVELLY_HILLS : Biome.WINDSWEPT_FOREST;
                }
                // 基于湿度区分
                if (effectiveHumidity < 0.25) yield Biome.PLAINS; // 温带平原
                if (effectiveHumidity < 0.55) { // 中湿
                    yield (effectiveHumidity > 0.4 && inputs.erosion > 0.0) ? Biome.BIRCH_FOREST : Biome.FOREST; // 桦木林 vs 普通森林
                }
                if (effectiveHumidity < 0.8) { // 湿润
                    yield (inputs.erosion < -0.3) ? Biome.DARK_FOREST : Biome.FLOWER_FOREST; // 黑森林 vs 花林
                }
                yield Biome.SWAMP; // 极湿 -> 沼泽
            }
            case HOT -> { // 热带
                if (effectiveHumidity < 0.1) yield Biome.DESERT; // 极干 -> 沙漠
                if (effectiveHumidity < 0.4) { // 干热 -> 热带草原
                    if (inputs.altitude > inputs.seaLevel + 40 && Math.abs(inputs.peaksValleys) < 0.15) yield Biome.SAVANNA_PLATEAU; // 高原
                    if (inputs.altitude > inputs.seaLevel + 60 && (Math.abs(inputs.peaksValleys) > 0.4 || inputs.erosion > 0.5)) yield Biome.WINDSWEPT_SAVANNA; // 风袭
                    yield Biome.SAVANNA; // 普通
                }
                // 湿热 -> 丛林 / 红树林
                if (inputs.continentalness < 0.05 && effectiveHumidity > 0.85) yield Biome.MANGROVE_SWAMP; // 靠近海洋且极湿润 -> 红树林
                if (effectiveHumidity > 0.8 && inputs.erosion < -0.1) yield Biome.BAMBOO_JUNGLE; // 竹林
                yield (effectiveHumidity < 0.7) ? Biome.SPARSE_JUNGLE : Biome.JUNGLE; // 稀疏 vs 茂密丛林
            }
        };
    }
}
