package com.chandler.fcc.common.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * FCC 标识符 JSON 序列化模块。
 *
 * <p>Java {@link Long} 标识符超过 JavaScript 安全整数范围时会丢失精度。
 * 本模块仅把名为 {@code id} 或以 {@code Id} 结尾的 Long 属性序列化为字符串，
 * 时长、时间戳、字节数和统计值仍保持数值类型。</p>
 */
public final class FccIdentifierJacksonModule {

    private FccIdentifierJacksonModule() {
    }

    /**
     * 创建标识符序列化模块。
     *
     * @return 可注册到 Spring/Jackson 的模块
     */
    public static SimpleModule create() {
        SimpleModule module = new SimpleModule("fcc-identifier-as-string");
        module.setSerializerModifier(new IdentifierSerializerModifier());
        return module;
    }

    /** 对 Long 类型标识符属性应用字符串序列化器。 */
    private static final class IdentifierSerializerModifier extends BeanSerializerModifier {

        /**
         * 为当前 Bean 的标识符属性分配字符串序列化器。
         *
         * @param config 序列化配置
         * @param beanDesc Bean 描述
         * @param beanProperties 属性写入器
         * @return 调整后的属性写入器
         */
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                         BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter property : beanProperties) {
                Class<?> rawType = property.getType().getRawClass();
                String name = property.getName();
                boolean identifierName = "id".equals(name) || name.endsWith("Id");
                if (identifierName && (rawType == Long.class || rawType == long.class)) {
                    JsonSerializer<Object> serializer = ToStringSerializer.instance;
                    property.assignSerializer(serializer);
                }
            }
            return beanProperties;
        }
    }
}
