package com.self.grpc.config;

import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 增強jackson ->
 * 引入 jackson-datatype-protobuf 依賴,使得jackson能直接序列化/反序列化grpc
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ProtobufModule protobufModule() {
        return new ProtobufModule();
    }

}
