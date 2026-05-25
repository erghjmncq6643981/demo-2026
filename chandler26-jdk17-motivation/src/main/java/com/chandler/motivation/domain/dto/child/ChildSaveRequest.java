package com.chandler.motivation.domain.dto.child;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ChildSaveRequest {
    private String nickname;
    private String avatarUrl;
    private LocalDate birthday;
    private String gender;
    private String remark;
    private Boolean createChildAccount;
    private String childUsername;
    private String childPassword;
}
