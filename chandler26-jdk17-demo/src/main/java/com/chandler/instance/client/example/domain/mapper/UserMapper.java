/*
 * chandler-instance-client
 * 2024/3/4 17:29
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.instance.client.example.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.instance.client.example.domain.dataobject.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2024/3/4 17:29
 * @since 1.8
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
