package com.chandler.learning.agent.system.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 产品内系统日志来源。
 */
@Getter
public enum SystemLogSource {

    SERVER(SystemLogConstants.SOURCE_SERVER, "服务端"),
    CLIENT(SystemLogConstants.SOURCE_CLIENT, "前端");

    private final String code;
    private final String label;

    SystemLogSource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static SystemLogSource of(String code) {
        String normalized = StrUtil.blankToDefault(code, CLIENT.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(source -> source.code.equals(normalized))
                .findFirst()
                .orElse(CLIENT);
    }
}
