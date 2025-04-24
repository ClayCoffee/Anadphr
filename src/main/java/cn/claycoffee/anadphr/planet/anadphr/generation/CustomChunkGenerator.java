package cn.claycoffee.anadphr.planet.anadphr.generation;

import cn.claycoffee.anadphr.core.NoiseGeneratorCore;
import cn.claycoffee.anadphr.populators.CavePopulator;
import cn.claycoffee.anadphr.populators.OrePopulator;
import cn.claycoffee.anadphr.populators.RiverPopulator;
import cn.claycoffee.anadphr.settings.CaveSettings;
import cn.claycoffee.anadphr.settings.OreSettings;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * CustomChunkGenerator (自定义区块生成器) 是一个专门的区块生成器，
 * 旨在提供自定义的世界生成逻辑，包括自定义的地形特征、结构和装饰物。
 * 它利用了灵活且模块化的设计，允许注入预先配置的噪声生成核心和自定义的填充算法。
 * <p>
 * 该类扩展了 {@link AnadphrChunkGenerator} 并覆写了多种方法，
 * 以定义特定的生成行为，例如在世界生成过程中启用或禁用某些层或结构。
 */
public class CustomChunkGenerator extends AnadphrChunkGenerator {
    @Nullable
    volatile RiverPopulator riverPopulatorInstance;
    @Nullable
    volatile OrePopulator orePopulatorInstance;
    @Nullable
    volatile CavePopulator cavePopulatorInstance;


    /**
     * 创建一个 ChunkGenerator 实例，并可选择性地注入一个预配置的 GeneratorCore。
     * 这允许为不同的世界使用不同的生成参数。
     *
     * @param core 一个配置好的 {@link NoiseGeneratorCore} 实例。如果为 null，将在首次需要时使用默认设置创建。
     *             传入的 core 必须是完整初始化的。
     */
    public CustomChunkGenerator(@Nullable NoiseGeneratorCore core) {
        super(core);
    }

    /**
     * 获取用于填充区块特性的 BlockPopulator 列表。
     * 列表顺序建议了 Populator 的执行顺序（河流优先）。
     * 使用双重检查锁定模式安全地获取或创建缓存的实例。
     * @param worldInfo 世界信息。
     * @return 包含自定义 Populator (RiverPopulator, OrePopulator) 的列表。如果 Core 初始化失败则为空列表。
     */
    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World worldInfo) {
        CavePopulator cavePop = this.cavePopulatorInstance;
        if (cavePop == null) {
            synchronized (coreInitLock) {
                cavePop = this.cavePopulatorInstance;
                if (cavePop == null) {
                    cavePop = new CavePopulator(this, CaveSettings.ANADPHR);
                    this.cavePopulatorInstance = cavePop;
                }
            }
        }

        RiverPopulator riverPop = this.riverPopulatorInstance; // 先读 volatile 变量
        if (riverPop == null) {
            synchronized (coreInitLock) {
                riverPop = this.riverPopulatorInstance; // 再次读取
                if (riverPop == null) {
                    riverPop = new RiverPopulator(this);
                    this.riverPopulatorInstance = riverPop; // 写 volatile 变量
                }
            }
        }

        OrePopulator orePop = this.orePopulatorInstance; // 先读 volatile 变量
        if (orePop == null) {
            synchronized (coreInitLock) {
                orePop = this.orePopulatorInstance; // 再次读取
                if (orePop == null) {
                    orePop = new OrePopulator(this, OreSettings.ANADPHR, cavePopulatorInstance);
                    this.orePopulatorInstance = orePop; // 写 volatile 变量
                }
            }
        }

        return Arrays.asList(riverPop, cavePop, orePop);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return true;
    }
}
