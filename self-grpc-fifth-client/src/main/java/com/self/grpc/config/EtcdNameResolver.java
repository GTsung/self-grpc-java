package com.self.grpc.config;

import com.self.grpc.util.JsonUtil;
import com.self.grpc.util.StreamUtil;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
public class EtcdNameResolver extends NameResolver {

    private final String serviceName;
    private final Client etcdClient;
    private Listener2 listener;
    private Watch.Watcher watcher;
    private final String watchKeyPrefix;

    public EtcdNameResolver(String serviceName, Client etcdClient) {
        this.serviceName = serviceName;
        this.etcdClient = etcdClient;
        this.watchKeyPrefix = "services/" + serviceName + "/";
    }

    @Override
    public String getServiceAuthority() {
        return serviceName;
    }

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        resolve();
        watch();
    }

    private void resolve() {
        try {
            // 查询所有服务节点
            GetResponse getResponse = etcdClient.getKVClient()
                    .get(ByteSequence.from(watchKeyPrefix, StandardCharsets.UTF_8),
                            GetOption.builder().isPrefix(true).build())
                    .get();

            List<EquivalentAddressGroup> addresses =
                    StreamUtil.map(getResponse.getKvs(), kv -> {
                        String value = kv.getValue().toString(StandardCharsets.UTF_8);
                        // 假设存储格式为: {"host":"192.168.1.1","port":9090}
                        Map<String, Object> hostPortMap = JsonUtil.fromJson(value);
                        String host = hostPortMap.get("host").toString();
                        int port = Integer.parseInt(hostPortMap.get("port").toString());
                        return new EquivalentAddressGroup(new InetSocketAddress(host, port));
                    });

            log.info("Resolved {} addresses for service {}", addresses.size(), serviceName);
            listener.onResult(ResolutionResult.newBuilder()
                    .setAddresses(addresses)
                    .setAttributes(Attributes.EMPTY)
                    .build());

        } catch (Exception e) {
            log.error("Failed to resolve from etcd", e);
            listener.onError(Status.fromThrowable(e));
        }
    }

    private void watch() {
        // 监听服务节点变化
        Watch.Listener watcherListener = new Watch.Listener() {
            @Override
            public void onNext(WatchResponse response) {
                response.getEvents().forEach(event -> {
                    log.info("Watch event: {}", event.getEventType());
                    // 重新解析地址
                    resolve();
                });
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };

        watcher = etcdClient.getWatchClient().watch(
                ByteSequence.from(watchKeyPrefix, StandardCharsets.UTF_8),
                WatchOption.builder().isPrefix(true).build(),
                watcherListener
        );
    }

    @Override
    public void shutdown() {
        if (watcher != null) {
            watcher.close();
        }
    }
}
