package com.self.grpc.config;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.options.PutOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class EtcdRegistry implements ApplicationRunner, DisposableBean {

    @Resource
    private Client etcdClient;

    @Value("${server.port}")
    private int grpcPort;

    @Value("${spring.application.name}")
    private String serviceName;

    private long leaseId;

    private final ScheduledExecutorService scheduler = Executors
            .newScheduledThreadPool(1);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        register();
    }

    private void register() throws Exception {
        // 获取本机IP
        String host = InetAddress.getLocalHost().getHostAddress();
        String key = String.format("services/%s/%s:%d", serviceName, host, grpcPort);
        String value = String.format("{\"host\":\"%s\",\"port\":%d}", host, grpcPort);

        // 1. 创建租约 (TTL 10秒)
        Lease leaseClient = etcdClient.getLeaseClient();
        leaseId = leaseClient.grant(10).get().getID();

        // 2. 写入带租约的key
        PutOption putOption = PutOption.builder()
                .withLeaseId(leaseId)
                .build();
        etcdClient.getKVClient().put(
                ByteSequence.from(key, StandardCharsets.UTF_8),
                ByteSequence.from(value, StandardCharsets.UTF_8),
                putOption
        ).get();
        log.info("Service registered to etcd: key={}, leaseId={}", key, leaseId);
        // 3. 定期续租 (每5秒)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                etcdClient.getLeaseClient().keepAliveOnce(leaseId).get();
                log.debug("Lease renewed: {}", leaseId);
            } catch (Exception e) {
                log.error("Failed to renew lease", e);
                // 续租失败尝试重新注册
                try {
                    register();
                } catch (Exception ex) {
                    log.error("Re-register failed", ex);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        log.info("deRegister service...");
        scheduler.shutdown();
        if (leaseId != 0) {
            try {
                etcdClient.getLeaseClient().revoke(leaseId).get();
            } catch (Exception e) {
                log.error("Failed to revoke lease", e);
            }
        }
    }

}
