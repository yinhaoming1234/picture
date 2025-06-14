package com.yin.picture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
public class RedisStringTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisStringOperations() {
        // 获取操作对象
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        // Key 和 Value
        String key = "testKey";
        String value = "testValue";

        // 1. 测试新增或更新操作
        valueOps.set(key, value);
        String storedValue = valueOps.get(key);
        assert value.equals(storedValue):"存储值与预期不符";


        // 2. 测试修改操作
        String updatedValue = "updatedValue";
        valueOps.set(key, updatedValue);
        storedValue = valueOps.get(key);
        assert updatedValue.equals(storedValue):"存储值与预期不符";

        // 3. 测试查询操作
        storedValue = valueOps.get(key);
        assert storedValue != null:"查询的值为 null";

        // 4. 测试删除操作
        stringRedisTemplate.delete(key);
        storedValue = valueOps.get(key);
        assert storedValue == null:"删除的的值不为 null";
    }
}
