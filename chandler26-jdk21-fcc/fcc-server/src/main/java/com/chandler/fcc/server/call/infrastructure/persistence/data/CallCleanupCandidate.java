package com.chandler.fcc.server.call.infrastructure.persistence.data;

import java.time.LocalDateTime;
import lombok.Data;

/** 数据库对账所需的通话摘要；不加载原始事件、客户信息或媒体载荷。 */
@Data
public class CallCleanupCandidate {
    /** FCC 业务通话主键。 */
    private Long id;
    /** 控制关联标识。 */
    private String ctrlId;
    /** 当前生命周期状态。 */
    private String status;
    /** 当前挂机结果码。 */
    private String result;
    /** UTC 开始时间。 */
    private LocalDateTime startedAt;
    /** UTC 结束时间，可为空。 */
    private LocalDateTime endedAt;
    /** 乐观并发版本。 */
    private Long version;
    /** 创建时固定的客户话道标识。 */
    private String guestChannelUuid;
    /** 创建时固定的坐席话道标识。 */
    private String agentChannelUuid;
}
