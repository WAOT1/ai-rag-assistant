package com.example.airag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.airag.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT * FROM document_chunk WHERE document_id = #{docId} ORDER BY chunk_index")
    List<DocumentChunk> selectByDocumentId(@Param("docId") Long docId);

    @Select("SELECT * FROM document_chunk")
    List<DocumentChunk> selectAllChunks();
}
