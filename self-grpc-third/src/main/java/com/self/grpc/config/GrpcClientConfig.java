package com.self.grpc.config;

import io.etcd.jetcd.Client;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    @Resource
    private Client etcdClient;

    @Value("${grpc.client.target}")
    private String target;

    private ManagedChannel channel;

//    @Bean
//    public GreeterGrpc.GreeterBlockingStub greeterStub() {
//
//        NameResolverRegistry.getDefaultRegistry()
//                .deregister(new EtcdNameResolverProvider(etcdClient));
//        // 注册自定义Resolver
//        channel = ManagedChannelBuilder
//                .forTarget(target)
//                .defaultLoadBalancingPolicy("round_robin") // 轮询负载均衡
//                .usePlaintext()
//                .build();
//        return GreeterGrpc.newBlockingStub(channel);
//    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

}
