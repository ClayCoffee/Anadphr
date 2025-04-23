package cn.claycoffee.anadphr.planet.anadphr.generation.populators;

import cn.claycoffee.anadphr.planet.anadphr.generation.GeneratorCore;
import org.bukkit.Material;
// import org.bukkit.World; // 避免直接使用 World，依赖 LimitedRegion
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo; // 引入 WorldInfo
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*; // 引入 util.*

/**
 * BlockPopulator 实现，用于在基础地形生成后雕刻河流并填充水体。
 * 通过模拟水流从高处向低处流动来确定河流路径。
 * 设计为线程安全，仅使用传递给 populate 方法的 Random 实例和 LimitedRegion。
 * 依赖注入的 {@link GeneratorCore} 提供配置信息。
 * 修正：使用 WorldInfo 获取世界高度限制。
 */
public final class RiverPopulator extends BlockPopulator { // 标记为 final

    @NotNull
    private final GeneratorCore core;
    // --- 河流生成参数 (硬编码或移入配置类) ---
    private static final int RIVER_SEARCH_BUFFER = 5;
    private static final int SOURCE_MIN_ALTITUDE_OFFSET = 20;
    private static final double SOURCE_CHANCE_PER_COLUMN = 0.005;
    private static final int MAX_PATH_LENGTH_PER_TRACE = 250;
    private static final int RIVER_CARVE_DEPTH = 5;
    private static final int RIVER_CARVE_WIDTH = 3;
    private static final int MAX_UPHILL_STEP = 0;

    /**
     * 创建一个新的 RiverPopulator 实例。
     * @param core 注入的 {@link GeneratorCore} 实例，用于访问配置。不能为空。
     * @throws NullPointerException 如果 core 为 null。
     */
    public RiverPopulator(@NotNull GeneratorCore core) {
        this.core = Objects.requireNonNull(core, "GeneratorCore cannot be null");
    }

    /**
     * 在指定的区块及其邻近区域填充河流特征。
     * 此方法会在基础地形（包括洞穴）生成后被 Bukkit 调用。
     * 实现是线程安全的。
     *
     * @param worldInfo 世界信息，用于获取高度限制等。**从此对象获取 min/max Height**。
     * @param random 用于此次填充操作的 Random 实例 (由 Bukkit 提供，在此上下文中线程安全)。
     * @param chunkX 当前填充区块的 X 坐标。
     * @param chunkZ 当前填充区块的 Z 坐标。
     * @param limitedRegion 提供对区块及邻近区域方块的安全访问接口。
     */
    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {

        final int seaLevel = core.terrainSettings.seaLevel;
        // **修正**: 从 worldInfo 获取高度限制
        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();
        final int startX = chunkX << 4;
        final int startZ = chunkZ << 4;

        Set<BlockVector> riverBlocks = new LinkedHashSet<>();
        // 传入 worldInfo 以便内部方法使用其高度限制
        Set<BlockVector> potentialSources = findPotentialSources(worldInfo, random, startX, startZ, limitedRegion, seaLevel);

        for (BlockVector source : potentialSources) {
            // 传入 worldInfo
            traceRiverPath(source, worldInfo, limitedRegion, random, riverBlocks, seaLevel);
        }

        if (!riverBlocks.isEmpty()) {
            // 传入 worldInfo
            carveAndFillRivers(worldInfo, limitedRegion, random, riverBlocks, seaLevel);
        }
    }

    /**
     * 在区块及缓冲区内寻找潜在的河流源头。
     * 此方法是线程安全的。
     *
     * @param worldInfo 提供世界高度限制。
     * @param random 用于随机判断的 Random 实例。
     * @param chunkStartX 区块的世界 X 坐标起点。
     * @param chunkStartZ 区块的世界 Z 坐标起点。
     * @param region 安全访问区域。
     * @param seaLevel 海平面高度。
     * @return 一组潜在源头的 {@link BlockVector} 坐标集合。
     */
    @NotNull
    private Set<BlockVector> findPotentialSources(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkStartX, int chunkStartZ, @NotNull LimitedRegion region, int seaLevel) {
        Set<BlockVector> sources = new HashSet<>();
        final int minSourceAltitude = seaLevel + SOURCE_MIN_ALTITUDE_OFFSET;
        // **修正**: 从 worldInfo 获取最低高度
        final int minWorldY = worldInfo.getMinHeight();

        for (int x = -RIVER_SEARCH_BUFFER; x < 16 + RIVER_SEARCH_BUFFER; x++) {
            for (int z = -RIVER_SEARCH_BUFFER; z < 16 + RIVER_SEARCH_BUFFER; z++) {
                final int worldX = chunkStartX + x;
                final int worldZ = chunkStartZ + z;

                // 优化: 检查 XZ 是否在 LimitedRegion 内 (Y 用 minWorldY 检查)
                if (!region.isInRegion(worldX, minWorldY, worldZ)) continue;

                final int surfaceY = region.getHighestBlockYAt(worldX, worldZ) - 1;

                // **修正**: 检查 surfaceY 是否在世界范围内
                if (surfaceY < minWorldY) continue;

                if (surfaceY >= minSourceAltitude &&
                        region.getType(worldX, surfaceY, worldZ) != Material.WATER &&
                        region.getType(worldX, surfaceY, worldZ) != Material.ICE &&
                        random.nextDouble() < SOURCE_CHANCE_PER_COLUMN)
                {
                    sources.add(new BlockVector(worldX, surfaceY, worldZ));
                }
            }
        }
        return sources;
    }


    /**
     * 从指定源头开始，向下追踪河流路径。
     * 此方法是线程安全的。
     *
     * @param startNode 河流源头的精确坐标。
     * @param worldInfo 提供世界高度限制。
     * @param region 安全访问区域。
     * @param random 用于洗牌方向的 Random 实例。
     * @param riverBlocks 用于存储河流路径点的集合 (会被修改)。
     * @param seaLevel 海平面高度。
     */
    private void traceRiverPath(@NotNull BlockVector startNode, @NotNull WorldInfo worldInfo, @NotNull LimitedRegion region, @NotNull Random random, @NotNull Set<BlockVector> riverBlocks, int seaLevel) {
        Queue<BlockVector> queue = new LinkedList<>();
        Set<BlockVector> visitedInTrace = new HashSet<>();

        if (!riverBlocks.contains(startNode)) {
            queue.add(startNode);
            visitedInTrace.add(startNode);
        }

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_PATH_LENGTH_PER_TRACE) {
            BlockVector current = queue.poll();
            steps++;

            if (riverBlocks.contains(current)) continue;
            riverBlocks.add(current);

            if (current.getBlockY() <= seaLevel) continue;

            // 传入 worldInfo
            BlockVector lowestNeighbor = findLowestNeighbor(current, worldInfo, region, visitedInTrace, seaLevel, random);

            if (lowestNeighbor != null) {
                // visitedInTrace.add(lowestNeighbor); // 已在 findLowestNeighbor 中处理
                queue.add(lowestNeighbor);
                visitedInTrace.add(lowestNeighbor); // 确保加入 visited
            }
        }
    }

    /**
     * 查找当前点周围海拔最低且满足条件的邻居点。
     * 此方法是线程安全的。
     *
     * @param current 当前点。
     * @param worldInfo 提供世界高度限制。
     * @param region 安全访问区域。
     * @param visitedInTrace 本次追踪已访问的点集合。
     * @param seaLevel 海平面。
     * @param random 用于洗牌方向的 Random 实例。
     * @return 最低的有效邻居点，如果找不到则返回 null。
     */
    @Nullable
    private BlockVector findLowestNeighbor(@NotNull BlockVector current, @NotNull WorldInfo worldInfo, @NotNull LimitedRegion region, @NotNull Set<BlockVector> visitedInTrace, int seaLevel, @NotNull Random random) {
        BlockVector bestNeighbor = null;
        int minHeightFound = current.getBlockY() + MAX_UPHILL_STEP;
        // **修正**: 从 worldInfo 获取最低高度
        final int minWorldY = worldInfo.getMinHeight();

        List<int[]> directions = Arrays.asList(new int[]{-1,0}, new int[]{1,0}, new int[]{0,-1}, new int[]{0,1}, new int[]{-1,-1}, new int[]{-1,1}, new int[]{1,-1}, new int[]{1,1});
        Collections.shuffle(directions, random);

        for (int[] dir : directions) {
            final int nx = current.getBlockX() + dir[0];
            final int nz = current.getBlockZ() + dir[1];

            // **修正**: 使用 minWorldY 检查 Y 边界
            if (!region.isInRegion(nx, minWorldY, nz)) continue;

            final int neighborSurfaceY = region.getHighestBlockYAt(nx, nz) - 1;

            // **修正**: 检查 neighborSurfaceY 是否有效
            if (neighborSurfaceY < minWorldY) continue;

            final BlockVector neighborActual = new BlockVector(nx, neighborSurfaceY, nz);

            if (visitedInTrace.contains(neighborActual)) continue;

            // 条件: 严格更低，或者在允许的上坡范围内
            if (neighborSurfaceY < minHeightFound) {
                minHeightFound = neighborSurfaceY;
                bestNeighbor = neighborActual;
            }
        }
        return bestNeighbor;
    }

    /**
     * 根据 {@code riverBlocks} 集合中的坐标，在 {@link LimitedRegion} 中雕刻河道并填充水。
     * 此方法是线程安全的。
     *
     * @param worldInfo 提供世界高度限制。
     * @param region 安全访问区域。
     * @param random 用于随机决定河床材质的 Random 实例。
     * @param riverBlocks 包含所有河流路径点的集合。
     * @param seaLevel 海平面高度。
     */
    private void carveAndFillRivers(@NotNull WorldInfo worldInfo, @NotNull LimitedRegion region, @NotNull Random random, @NotNull Set<BlockVector> riverBlocks, int seaLevel) {
        // **修正**: 从 worldInfo 获取高度限制
        final int minWorldY = worldInfo.getMinHeight();
        final int maxWorldY = worldInfo.getMaxHeight();

        for (BlockVector riverPos : riverBlocks) {
            final int rx = riverPos.getBlockX();
            final int rz = riverPos.getBlockZ();
            final int originalSurfaceY = riverPos.getBlockY();

            // **修正**: 确保 Y 在世界范围内
            if (originalSurfaceY < minWorldY || originalSurfaceY >= maxWorldY) continue;

            final int waterLevel = Math.min(seaLevel, originalSurfaceY - 1);
            // **修正**: 确保 riverBedY 不低于 minWorldY
            final int riverBedY = Math.max(minWorldY, waterLevel - RIVER_CARVE_DEPTH);

            if (waterLevel < riverBedY) continue;

            for (int dx = -RIVER_CARVE_WIDTH; dx <= RIVER_CARVE_WIDTH; dx++) {
                for (int dz = -RIVER_CARVE_WIDTH; dz <= RIVER_CARVE_WIDTH; dz++) {
                    final int cx = rx + dx;
                    final int cz = rz + dz;

                    // **修正**: 检查 XZ 边界 (Y 使用原始地表检查上边界)
                    if (!region.isInRegion(cx, originalSurfaceY, cz)) continue;

                    // 挖掘
                    for (int y = originalSurfaceY; y >= riverBedY; y--) {
                        // **修正**: 检查 Y 边界
                        if (y < minWorldY || y >= maxWorldY || !region.isInRegion(cx, y, cz)) continue;

                        Material currentMat = region.getType(cx, y, cz);
                        if (currentMat.isSolid() && currentMat != Material.BEDROCK) {
                            // **修正**: 使用传入的 random
                            region.setType(cx, y, cz, (y == riverBedY && random.nextDouble() < 0.6) ? Material.GRAVEL : Material.AIR);
                        }
                    }
                    // 填充水
                    for (int y = riverBedY; y <= waterLevel; y++) {
                        // **修正**: 检查 Y 边界
                        if (y < minWorldY || y >= maxWorldY || !region.isInRegion(cx, y, cz)) continue;
                        if (region.getType(cx, y, cz) == Material.AIR) {
                            region.setType(cx, y, cz, Material.WATER);
                        }
                    }
                }
            }
        }
    }
}