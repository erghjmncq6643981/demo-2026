/*
 * chandler-instance-client
 * 2024/3/4 17:26
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.instance.client.example.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2024/3/4 17:26
 * @since 1.8
 */
@Data
@TableName("user")
@Schema(name = "用户", description = "用户")
public class User {
    @Schema(description = "ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    Long id;
    @Schema(description = "姓名")
    String name;
    @Schema(description = "年龄")
    Integer age;
    @Schema(description = "邮箱")
    String email;
}