package cn.claycoffee.anadphr.planet.anadphr.generation.settings;

import org.jetbrains.annotations.NotNull;

/**
 * 洞穴生成参数配置 (不可变)。
 * 控制使用 3D Simplex 噪声生成的洞穴的形态。这些参数直接影响洞穴的频率、大小和连通性。
 */
public final class CaveSettings { // 标记为 final
    /**
     * 3D Simplex 洞穴噪声的频率 (Frequency)。
     * 这个值决定了噪声函数变化的速度。较高的频率意味着噪声变化更快，从而产生更小、更密集的洞穴特征（如细小通道或小型洞室）。较低的频率则产生更大、更平缓的洞穴结构。
     * 典型值范围: 0.04 - 0.1。必须为正数。
     */
    public final double frequency;

    /**
     * 洞穴噪声阈值 (Threshold)。
     * 3D 噪声函数在每个点生成一个值（通常在-1到1之间）。如果某点的噪声值大于此阈值，该点将被视为空气（即洞穴的一部分）。
     * 阈值越高，需要越强的噪声信号才能形成洞穴，因此洞穴会更少、更稀疏。阈值越低，则更容易形成洞穴，洞穴会更多、更可能相互连接。
     * 典型值范围: 0.5 - 0.8。
     */
    public final double threshold;

    /**
     * 创建一个新的、不可变的洞穴设置实例。
     * @param frequency 洞穴噪声频率 (例如 0.06)。必须是正数。
     * @param threshold 洞穴噪声阈值 (例如 0.65)。
     * @throws IllegalArgumentException 如果 frequency 不是正数。
     */
    public CaveSettings(double frequency, double threshold) {
        if (frequency <= 0) throw new IllegalArgumentException("Cave frequency must be positive.");
        this.frequency = frequency;
        this.threshold = threshold;
    }

    /**
     * 获取一个具有推荐默认值的洞穴设置实例。
     * 可用于快速创建具有标准洞穴形态的配置。
     *
     * @return 一个包含默认洞穴设置的 {@link CaveSettings} 实例。该对象是不可变的。
     */
    @NotNull
    public static final CaveSettings DEFAULT = new CaveSettings(0.06, 0.65); // 使用 final 静态实例确保唯一性
}
