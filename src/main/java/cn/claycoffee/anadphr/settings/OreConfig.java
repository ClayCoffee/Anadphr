package cn.claycoffee.anadphr.settings;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


    /**
     * 定义单个矿物类型的生成配置 (不可变 Record)。
     * 用于指定矿物类型、生成高度、矿脉大小、稀有度及洞穴富集等。
     * <p>
     * 该记录类是不可变的，其实例一旦创建，其属性值不能被修改，保证了在多线程环境下的配置安全。
     * 所有参数在构造时进行验证。
     *
     * @param oreType             主要的矿物材质 (例如 Material.COAL_ORE)。不能为空。
     * @param deepslateOreType    对应的深层矿物材质 (例如 Material.DEEPSLATE_COAL_ORE)，如果没有则为 null。
     * @param minY                允许生成的最低 Y 层。
     * @param maxY                允许生成的最高 Y 层 (必须大于 minY)。
     * @param veinSize            每个矿脉大致包含的矿石方块数量 (必须大于 0)。
     * @param attemptsPerChunk    每个区块 (Chunk) 尝试生成该矿物的次数 (必须 >= 0)。
     * @param baseChance          每次尝试成功生成矿脉的基础几率 (0.0 到 1.0)。
     * @param caveEnrichmentFactor 在洞穴内生成时，基础几率的乘数因子 (必须 >= 1.0)。1.0 表示无富集。
     */
    public record OreConfig(
            @NotNull Material oreType,
            @Nullable Material deepslateOreType,
            int minY, int maxY, int veinSize, int attemptsPerChunk,
            double baseChance, double caveEnrichmentFactor
    ) {
        // Record 的紧凑构造函数，用于参数验证
        public OreConfig {
            Objects.requireNonNull(oreType, "oreType cannot be null");
            if (minY > maxY) throw new IllegalArgumentException("minY (" + minY + ") cannot be greater than maxY (" + maxY + ") for OreConfig: " + oreType.name());
            if (veinSize <= 0) throw new IllegalArgumentException("veinSize (" + veinSize + ") must be positive for OreConfig: " + oreType.name());
            if (attemptsPerChunk < 0) throw new IllegalArgumentException("attemptsPerChunk (" + attemptsPerChunk + ") cannot be negative for OreConfig: " + oreType.name());
            if (baseChance < 0.0 || baseChance > 1.0) throw new IllegalArgumentException("baseChance (" + baseChance + ") must be between 0.0 and 1.0 for OreConfig: " + oreType.name());
            if (caveEnrichmentFactor < 1.0) throw new IllegalArgumentException("caveEnrichmentFactor (" + caveEnrichmentFactor + ") must be >= 1.0 for OreConfig: " + oreType.name());
        }

        /** 煤矿配置实例 */
        public static final OreConfig COAL = new OreConfig(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, 0, 136, 17, 20, 0.9, 1.5);
        /** 铁矿配置实例 */
        public static final OreConfig IRON = new OreConfig(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, -24, 80, 9, 25, 0.7, 1.8);
        /** 铜矿配置实例 */
        public static final OreConfig COPPER = new OreConfig(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, 0, 96, 10, 16, 0.75, 1.6);
        /** 金矿配置实例 */
        public static final OreConfig GOLD = new OreConfig(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, -64, 32, 9, 6, 0.5, 2.0);
        /** 红石矿配置实例 */
        public static final OreConfig REDSTONE = new OreConfig(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, -64, 16, 8, 8, 0.6, 2.2);
        /** 青金石矿配置实例 */
        public static final OreConfig LAPIS = new OreConfig(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, -64, 32, 7, 3, 0.4, 2.5);
        /** 钻石矿配置实例 */
        public static final OreConfig DIAMOND = new OreConfig(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, -64, 16, 8, 2, 0.3, 3.0);
        /** 绿宝石矿配置实例 (通常只在山地生成，需要 BiomeProvider 配合检查) */
        public static final OreConfig EMERALD = new OreConfig(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, -16, 256, 1, 10, 0.1, 1.2);
        /** 安山岩配置实例 */
        public static final OreConfig ANDESITE = new OreConfig(Material.ANDESITE, Material.ANDESITE, 0, 112, 33, 10, 0.8, 1.0);
        /** 闪长岩配置实例 */
        public static final OreConfig DIORITE = new OreConfig(Material.DIORITE, Material.DIORITE, 0, 112, 33, 10, 0.8, 1.0);
        /** 花岗岩配置实例 */
        public static final OreConfig GRANITE = new OreConfig(Material.GRANITE, Material.GRANITE, 0, 112, 33, 10, 0.8, 1.0);
        /** 泥土配置实例 */
        public static final OreConfig DIRT = new OreConfig(Material.DIRT, Material.DIRT, -64, 160, 33, 20, 0.5, 1.0);
        /** 砂砾配置实例 */
        public static final OreConfig GRAVEL = new OreConfig(Material.GRAVEL, Material.GRAVEL, -64, 160, 33, 10, 0.5, 1.0);
        /** 凝灰岩配置实例 */
        public static final OreConfig TUFF = new OreConfig(Material.TUFF, Material.TUFF, -64, 32, 33, 5, 0.7, 1.0);
    }
