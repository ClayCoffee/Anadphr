package cn.claycoffee.anadphr.settings;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 包含所有矿物生成配置 {@link OreConfig} 的集合 (不可变)。
 * 通过构造函数传入具体的矿物配置列表。
 * 此类用于集中管理世界中需要生成的所有矿物类型及其参数。
 */
public final class OreSettings { // 标记为 final，表示不可继承
    /**
     * 此世界中配置用于生成的所有矿物列表。
     * 该列表在对象创建后是不可修改的，以保证线程安全和配置一致性。
     */
    @NotNull
    public final List<OreConfig> ores;

    /**
     * 创建一个新的矿物设置实例。
     * 构造函数会复制传入的列表，以确保内部列表的不可变性。
     *
     * @param ores 包含所有要生成的 {@link OreConfig} 实例的列表。不允许为 null。
     * @throws NullPointerException 如果 ores 列表为 null。
     */
    public OreSettings(@NotNull List<OreConfig> ores) {
        this.ores = List.copyOf(Objects.requireNonNull(ores, "Ores list cannot be null"));
    }


    @NotNull
    public static OreSettings ANADPHR = new OreSettings(Arrays.asList(
            OreConfig.COAL, OreConfig.IRON, OreConfig.COPPER, OreConfig.GOLD,
            OreConfig.REDSTONE, OreConfig.LAPIS, OreConfig.DIAMOND, OreConfig.EMERALD,
            OreConfig.ANDESITE, OreConfig.DIORITE, OreConfig.GRANITE,
            OreConfig.DIRT, OreConfig.GRAVEL, OreConfig.TUFF
    ));
}