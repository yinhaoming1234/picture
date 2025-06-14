package com.yin.picture.model.enums;

import lombok.Getter;

@Getter
public enum LuaStatusEnum {  
    // 成功  
    SUCCESS(1L),  
    // 失败  
    FAIL(-1L),
    //图片不存在
    PICTURE_NOT_EXISTS(-2L),
    ;  
  
    private final long value;  
  
    LuaStatusEnum(long value) {  
        this.value = value;  
    }  
}
