package com.self.grpc.config;

import io.etcd.jetcd.Client;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

@Slf4j
public class EtcdNameResolverProvider extends NameResolverProvider {

    private final Client etcdClient;

    public EtcdNameResolverProvider(Client etcdClient) {
        this.etcdClient = etcdClient;
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 6;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!"etcd".equals(targetUri.getScheme())) {
            return null;
        }
        // 這裡將GrpcClientConfig類中的target屬性傳遞過來
        String serviceName = targetUri.getPath().replace("/", "");
        log.info("Creating etcd name resolver for service: {}", serviceName);
        return new EtcdNameResolver(serviceName, etcdClient);
    }

    @Override
    public String getDefaultScheme() {
        return "etcd";
    }
}
