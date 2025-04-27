package cn.claycoffee.anadphr;

import cn.claycoffee.anadphr.core.AbstractChunkGenerator;
import cn.claycoffee.anadphr.core.NoiseGeneratorCore;
import cn.claycoffee.anadphr.planet.anadphr.generation.AnadphrChunkGenerator;
import cn.claycoffee.anadphr.settings.BiomeSettings;
import cn.claycoffee.anadphr.settings.TerrainSettings;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 主插件类，负责注册自定义世界生成器 {@link AbstractChunkGenerator}。
 * 它使用线程安全的 {@link ConcurrentHashMap} 来缓存每个世界的 {@link NoiseGeneratorCore} 实例，
 * 以支持多世界配置和 Folia 的并发环境。
 * 可以通过配置文件（如果实现）或基于世界名/ID 加载不同的生成设置。
 */
public final class AnadphrCore extends JavaPlugin { // 标记为 final

    /** 插件的 Logger 实例。 */
    private static final Logger LOGGER = Logger.getLogger("CustomWorldGeneratorPlugin");
    /**
     * 线程安全的 Map，用于缓存每个世界名对应的 GeneratorCore 实例。
     * Key: 世界名称 (String)
     * Value: 该世界的 GeneratorCore 实例 (GeneratorCore)
     */
    private final Map<String, NoiseGeneratorCore> worldCores = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        LOGGER.info("Anadphr Core 加载中...");
        LOGGER.info("准备为世界提供自定义生成器...");
    }

    /**
     * 当插件禁用时调用。
     * 记录禁用信息，并清理资源（例如清空缓存）。
     */
    @Override
    public void onDisable() {
        LOGGER.info("Anadphr Core 正在禁用...");
        // 清理 GeneratorCore 缓存
        worldCores.clear();
        LOGGER.info("Anadphr Core 已禁用!");
    }

    /**
     * Bukkit 调用此方法为指定世界获取区块生成器实例。
     * 这是插件与 Bukkit 世界生成机制交互的主要入口点。
     * 此实现是线程安全的，它原子性地查找或创建与世界名关联的 {@link NoiseGeneratorCore}，
     * 并将其注入到新的 {@link AbstractChunkGenerator} 实例中。
     *
     * @param worldName 请求生成器的世界名称。不能为空。
     * @param id        可选的生成器 ID (例如来自命令 `-g PluginName:id`)。可以为 null。
     * 此 ID 可用于加载特定的世界生成配置。
     * @return 一个配置好的 {@link AbstractChunkGenerator} 实例。在极少数初始化失败的情况下可能返回 null。
     */
    @Override
    @Nullable // 返回值理论上不为 null，除非 Core 创建失败
    public ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        if(worldName.equals("world")) {
            LOGGER.info("请求为世界 '" + worldName + "' (ID: " + (id != null ? id : "无") + ") 提供生成器...");

            // 使用 ConcurrentHashMap.computeIfAbsent 来原子性地获取或创建 GeneratorCore
            // Lambda 表达式只在 Key 不存在时执行，保证了 Core 的单例性（每个世界一个）和线程安全
            NoiseGeneratorCore coreToUse = worldCores.computeIfAbsent(worldName, wn -> {
                LOGGER.info("缓存未命中，将为世界 '" + wn + "' 创建新的 GeneratorCore...");
                long seed = determineSeed(wn);
                WorldHeight limits = determineWorldHeight(wn);

                // TODO: 多个星球
                LOGGER.info("正在为世界 '" + wn + "' 加载生成设置 (当前使用默认)...");
                TerrainSettings terrainSettings = TerrainSettings.getAnadphrSettings(limits.minHeight, limits.maxHeight);
                BiomeSettings biomeSettings = BiomeSettings.ANADPHR;
                LOGGER.info("使用种子 " + seed + ", 高度 [" + limits.minHeight + ", " + limits.maxHeight + "], 和默认设置创建 Core。");

                // 创建并返回新的 GeneratorCore 实例
                // 这个实例会被放入 ConcurrentHashMap 中
                try {
                    return new NoiseGeneratorCore(seed, terrainSettings, biomeSettings);
                } catch (Exception e) {
                    // 如果 Core 创建失败 (例如参数验证失败)，记录严重错误并返回 null
                    LOGGER.log(Level.SEVERE, "为世界 '" + wn + "' 创建 GeneratorCore 时失败!", e);
                    return null; // 返回 null 会导致 Bukkit 使用默认生成器或报错
                }
            });

            // 如果 computeIfAbsent 返回了 null (因为 lambda 中返回了 null)，则无法创建生成器
            if (coreToUse == null) {
                LOGGER.severe("无法为世界 '" + worldName + "' 获取或创建 GeneratorCore！将无法使用自定义生成器。");
                return null;
            }

            // 返回注入了正确 GeneratorCore 的 MyChunkGenerator 实例
            LOGGER.info("为世界 '" + worldName + "' 提供了配置好的 MyChunkGenerator 实例。");
            return new AnadphrChunkGenerator(coreToUse);
        }
        return null;
    }

    /**
     * 辅助方法：确定指定世界的种子 (线程安全)。
     * 优先尝试从已加载的 World 对象获取，如果失败则基于世界名称生成确定性种子。
     * @param worldName 世界名称。不能为空。
     * @return 世界的种子 (long)。
     */
    private long determineSeed(@NotNull String worldName) {
        Objects.requireNonNull(worldName, "World name cannot be null for seed determination");
        Server server = getServer(); // 获取 Server 实例
        World world = server.getWorld(worldName); // Bukkit.getWorld 是线程安全的读操作
        if (world != null) {
            return world.getSeed(); // 从已加载的世界获取种子
        } else {
            // 世界未加载时的后备方案：基于名称的确定性伪随机种子
            LOGGER.warning("无法在请求生成器时获取世界 '" + worldName + "' 的种子。将基于世界名称生成确定性种子。这可能在世界首次创建时发生。");
            // 使用稍微复杂一点的哈希组合来提高种子的唯一性
            long nameHash = worldName.hashCode();
            long fixedSalt = 0xCAFEBABE12345678L; // 使用一个固定的、随机选择的 64 位盐值
            return (nameHash << 32) ^ (nameHash + fixedSalt); // 简单的位运算和加法组合
        }
    }

    /** 辅助记录类: 存储世界高度限制 */
    private record WorldHeight(int minHeight, int maxHeight) {}

    /**
     * 辅助方法：确定指定世界的高度限制 (线程安全)。
     * 尝试从已加载的 World 对象获取，如果失败则使用 1.18+ 的默认值。
     * @param worldName 世界名称。不能为空。
     * @return 包含最低和最高高度的 {@link WorldHeight} 记录。
     */
    @NotNull
    private WorldHeight determineWorldHeight(@NotNull String worldName) {
        Objects.requireNonNull(worldName, "World name cannot be null for height determination");
        // 设置 1.18+ 默认值作为后备
        int minHeight = -64;
        int maxHeight = 320;
        Server server = getServer();
        World world = server.getWorld(worldName); // 线程安全的读操作
        if (world != null) {
            try {
                // World 的 getMinHeight/getMaxHeight 通常是线程安全的读操作
                minHeight = world.getMinHeight();
                maxHeight = world.getMaxHeight();
            } catch (Exception e) {
                // 捕获潜在的异常 (虽然理论上不应发生)
                LOGGER.log(Level.WARNING, "获取已加载世界 '" + worldName + "' 高度时发生意外错误，将使用默认值。错误: " + e.getMessage());
            }
        } else {
            LOGGER.warning("无法在请求生成器时获取世界 '" + worldName + "'（可能未加载），将使用默认高度 [" + minHeight + ", " + maxHeight + "]。");
        }
        return new WorldHeight(minHeight, maxHeight);
    }
}