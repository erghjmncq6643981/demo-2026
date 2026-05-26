package com.chandler.motivation.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MotivationChildMapper extends BaseMapper<MotivationChild> {

    @Select("select avatar_data, avatar_content_type from motivation_child where id = #{id} limit 1")
    @Results({
            @Result(column = "avatar_data", property = "avatarData"),
            @Result(column = "avatar_content_type", property = "avatarContentType")
    })
    MotivationChild selectAvatarById(@Param("id") Long id);
}
