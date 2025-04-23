package cn.claycoffee.anadphr.planet.anadphr.generation.settings;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        // 使用防御性复制创建不可变列表
        this.ores = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(ores, "Ores list cannot be null")));
    }

    /**
     * 获取一个包含所有预定义默认矿物配置的 {@link OreSettings} 实例。
     * 可用于快速创建具有标准矿物生成的配置。
     *
     * @return 一个包含默认矿物设置的 OreSettings 对象。该对象是不可变的。
     */
    @NotNull
    public static OreSettings getDefault() {
        // 返回包含所有预定义 OreConfig 的实例
        return new OreSettings(Arrays.asList(
                OreConfig.COAL, OreConfig.IRON, OreConfig.COPPER, OreConfig.GOLD,
                OreConfig.REDSTONE, OreConfig.LAPIS, OreConfig.DIAMOND, OreConfig.EMERALD,
                OreConfig.ANDESITE, OreConfig.DIORITE, OreConfig.GRANITE,
                OreConfig.DIRT, OreConfig.GRAVEL, OreConfig.TUFF
        ));
    }
}