package com.echocyan.codenest.framework.jackson;

import com.echocyan.codenest.common.util.DateTimes;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * JSON 约定：Long 输出为字符串（避免前端精度丢失），时间输出为带偏移的 ISO-8601。
 * 枚举默认按名称（大写）输出，null 字段默认保留。
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    public SimpleModule codeNestJacksonModule() {
        SimpleModule module = new SimpleModule("code-nest");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, new OffsetLocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new OffsetLocalDateTimeDeserializer());
        return module;
    }

    static class OffsetLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.atZone(DateTimes.ZONE).toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    static class OffsetLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(text).atZoneSameInstant(DateTimes.ZONE).toLocalDateTime();
            } catch (DateTimeParseException e) {
                // 不带偏移时按全站时区理解
                return LocalDateTime.parse(text);
            }
        }
    }
}
