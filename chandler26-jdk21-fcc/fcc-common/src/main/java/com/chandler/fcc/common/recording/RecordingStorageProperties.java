package com.chandler.fcc.common.recording;

/**
 * 通话录音共享存储配置契约
 * <p>
 * 录音文件由 FreeSWITCH 直接写入 {@link #baseDir}，控制面 (fcc-server) 在录音指令中声明该目录下的
 * 绝对路径，管理端 (fcc-admin) 再从同一绝对路径读取文件对外复播/下载。因此
 * <b>FreeSWITCH 容器与 FCC 服务必须把 {@link #baseDir} 以完全相同的绝对路径挂载</b>，
 * 它通常是一块共享 NAS 或同一宿主机目录。
 * </p>
 *
 * <p>本类刻意保持为无框架依赖的纯配置契约对象：</p>
 * <ul>
 *   <li>fcc-server 侧用于生成录音落盘路径并下发指令；</li>
 *   <li>fcc-admin 侧用于校验数据库中的录音路径未越出共享目录。</li>
 * </ul>
 * <p>两个模块各自通过 {@code @ConfigurationProperties(prefix = "fcc.recording")} 绑定同一份 YAML 配置。</p>
 *
 * @author Chandler
 */
public class RecordingStorageProperties {

    /**
     * 未显式配置时使用的共享录音根目录
     * <p>
     * 取当前运行用户的 home 目录下 {@code fcc-records}，本地演示开箱可用；
     * 生产环境请通过 {@code fcc.recording.base-dir} 或环境变量 {@code FCC_RECORDING_BASE_DIR}
     * 指向 FreeSWITCH 与 FCC 服务共同挂载的共享目录。
     * </p>
     */
    public static final String DEFAULT_BASE_DIR =
            System.getProperty("user.home", "/tmp") + "/fcc-records";

    /**
     * 共享录音根目录 (FreeSWITCH 与 FCC 服务同路径可见)
     */
    private String baseDir = DEFAULT_BASE_DIR;

    /**
     * 录音子目录分层格式 (基于录音开始日期)，用于避免单目录文件过多
     */
    private String subDirPattern = "yyyy/MM/dd";

    /**
     * 默认录音封装格式
     */
    private String fileFormat = "wav";

    /**
     * 是否允许读取共享根目录之外的文件路径
     * <p>
     * 默认 false。数据库中的录音路径若解析后落在 {@link #baseDir} 之外一律拒绝提供，
     * 防止库内脏数据或人为篡改导致任意文件读取。
     * </p>
     */
    private boolean allowOutsideBaseDir = false;

    /**
     * 前端复播时按 HTTP Range 分块读取的缓冲区大小 (字节)
     */
    private int streamBufferBytes = 8192;

    /**
     * 未指定节点时，下发录音指令默认使用的 Sidecar 节点标识
     */
    private String defaultNodeId;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getSubDirPattern() {
        return subDirPattern;
    }

    public void setSubDirPattern(String subDirPattern) {
        this.subDirPattern = subDirPattern;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public boolean isAllowOutsideBaseDir() {
        return allowOutsideBaseDir;
    }

    public void setAllowOutsideBaseDir(boolean allowOutsideBaseDir) {
        this.allowOutsideBaseDir = allowOutsideBaseDir;
    }

    public int getStreamBufferBytes() {
        return streamBufferBytes;
    }

    public void setStreamBufferBytes(int streamBufferBytes) {
        this.streamBufferBytes = streamBufferBytes;
    }

    @Override
    public String toString() {
        return "RecordingStorageProperties{baseDir='" + baseDir
                + "', subDirPattern='" + subDirPattern
                + "', fileFormat='" + fileFormat
                + "', allowOutsideBaseDir=" + allowOutsideBaseDir + '}';
    }
}
