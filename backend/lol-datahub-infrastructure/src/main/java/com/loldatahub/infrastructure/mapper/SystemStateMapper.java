package com.loldatahub.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemStateMapper {
    @Select("SELECT data_version FROM system_state WHERE id = 1")
    long currentDataVersion();

    @Update("UPDATE system_state SET data_version = data_version + 1 WHERE id = 1")
    int incrementDataVersion();
}
