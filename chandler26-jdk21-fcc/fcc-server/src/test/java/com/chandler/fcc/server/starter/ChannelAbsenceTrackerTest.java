package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.call.ChannelAbsenceTracker;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 恢复快照不能将查询失败解释为挂机。 */
class ChannelAbsenceTrackerTest {
    /** 任一话道存在或查询失败都会重新开始缺失计时。 */
    @Test void requiresContinuousCompleteAbsence() {
        var tracker=new ChannelAbsenceTracker();
        assertFalse(tracker.confirmed("call","agent","guest",Set.of(),0));
        assertFalse(tracker.confirmed("call","agent","guest",null,120000));
        assertFalse(tracker.confirmed("call","agent","guest",Set.of(),130000));
        assertFalse(tracker.confirmed("call","agent","guest",Set.of("agent"),250000));
        assertFalse(tracker.confirmed("call","agent","guest",Set.of(),260000));
        assertTrue(tracker.confirmed("call","agent","guest",Set.of(),380000));
        tracker.retain(Set.of());
        assertFalse(tracker.confirmed("call","agent","guest",Set.of(),500000));
    }
}
