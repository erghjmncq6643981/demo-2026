package com.chandler.fcc.server.call.infrastructure.persistence.data;

import java.time.LocalDateTime;
import lombok.Data;

/** 一个通话下的话道身份及可选的源结束事实，用于批量对账。 */
@Data
public class CallChannelFact {
    /** FCC 业务通话主键。 */
    private Long callId;
    /** FreeSWITCH 单个话道标识。 */
    private String channelUuid;
    /** 源结束时间，话道身份行可为空。 */
    private LocalDateTime endedAt;
    /** 源挂机原因，可为空。 */
    private String cause;
}
