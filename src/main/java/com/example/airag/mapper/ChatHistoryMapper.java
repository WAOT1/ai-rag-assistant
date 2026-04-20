package com.example.airag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airag.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId} ORDER BY create_time LIMIT 20")
    List<ChatHistory> selectBySession(@Param("sessionId") String sessionId);
}
