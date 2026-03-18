package com.self.grpc;

import io.etcd.jetcd.*;
import io.etcd.jetcd.election.CampaignResponse;
import io.etcd.jetcd.election.LeaderKey;
import io.etcd.jetcd.election.LeaderResponse;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lease.LeaseRevokeResponse;
import io.etcd.jetcd.lease.LeaseTimeToLiveResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.LeaseOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Unit test for simple App.
 */
@Slf4j
@SpringBootTest
public class AppTest {

    private Client client;

    @Before
    public void before() {
        this.client = Client.builder().endpoints("http://127.0.0.1:2379").build();
    }

    @After
    public void after() {
        if (this.client != null) {
            this.client.close();
        }
    }

    @Test
    public void testPut() throws Exception {
        ByteSequence key = ByteSequence.from("services/user-service", StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from("13.12.11.23:9011", StandardCharsets.UTF_8);
        CompletableFuture<PutResponse> put = client.getKVClient().put(key, value);
        log.info("put result: {}", put.get());

        ByteSequence key2 = ByteSequence.from("services/order-service", StandardCharsets.UTF_8);
        ByteSequence value2 = ByteSequence.from("11.12.21.31:8900", StandardCharsets.UTF_8);
        CompletableFuture<PutResponse> put2 = client.getKVClient().put(key2, value2);
        log.info("put2 result: {}", put2.get());
    }

    @Test
    public void testGet() throws Exception {
        KV kvClient = client.getKVClient();
        ByteSequence key = ByteSequence.from("services/user-service", StandardCharsets.UTF_8);
        CompletableFuture<GetResponse> getResponseCompletableFuture = kvClient.get(key);
        GetResponse getResponse = getResponseCompletableFuture.get();
        if (getResponse.getCount() > 0) {
            log.info("get result: {}", getResponse.getKvs().get(0).getValue());
        }

        key = ByteSequence.from("services/", StandardCharsets.UTF_8);
        getResponseCompletableFuture = kvClient.get(key,
                GetOption.builder().isPrefix(true).build());
        getResponse = getResponseCompletableFuture.get();
        for (KeyValue kv : getResponse.getKvs()) {
            log.info("get result: key:{}, value:{}", kv.getKey(), kv.getValue());
        }
    }

    @Test
    public void testDelete() throws Exception {
        KV kvClient = client.getKVClient();
        ByteSequence key = ByteSequence.from("services/user-service", StandardCharsets.UTF_8);
        CompletableFuture<DeleteResponse> delete = kvClient.delete(key);
        DeleteResponse deleteResponse = delete.get();
        log.info("delete result: {}", deleteResponse);
    }

    @Test
    public void watch() throws Exception {
        Watch watch = client.getWatchClient();
        watch.watch(ByteSequence.from("services/", StandardCharsets.UTF_8),
                WatchOption.builder().isPrefix(true).build(),
                new Watch.Listener() {
                    @Override
                    public void onNext(WatchResponse response) {
                        List<WatchEvent> events = response.getEvents();
                        for (WatchEvent watchEvent : events) {
                            log.info("eventType={},value={}", watchEvent.getEventType(),
                                    watchEvent.getKeyValue().getValue());
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("发生异常:{}", throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        log.info("complete");
                    }
                });

        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

    @Test
    public void lease() throws Exception {
        Lease lease = client.getLeaseClient();

        // 创建租约
        LeaseGrantResponse leaseGrantResponse = lease.grant(10).get();
        long leaseId = leaseGrantResponse.getID();

        // 租约与键值数据绑定
        ByteSequence key = ByteSequence.from("lease-key", StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from("lease-value", StandardCharsets.UTF_8);
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
        client.getKVClient().put(key, value, putOption).get();

        Thread.sleep(1000);

        // 查看租约剩余时间
        LeaseOption leaseOption = LeaseOption.builder().withAttachedKeys().build();
        LeaseTimeToLiveResponse leaseTimeToLiveResponse = lease.timeToLive(leaseId, leaseOption).get();
        log.info("leaseTimeToLiveResponse={}", leaseTimeToLiveResponse);

        // 使租约一直有效
        lease.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(LeaseKeepAliveResponse leaseKeepAliveResponse) {
                log.info("Lease keep-alive response:{}", leaseGrantResponse.getTTL());
            }

            @Override
            public void onError(Throwable throwable) {
                log.info("发生异常:{}", throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("Complete");
            }
        });

        Thread.sleep(1000 * 30);

        // 撤销租约
        LeaseRevokeResponse leaseRevokeResponse = lease.revoke(leaseId).get();
        log.info("leaseRevokeResponse={}", leaseRevokeResponse);
    }

    @Test
    public void lock() throws Exception {
        ByteSequence lockName = ByteSequence.from("my-lock", StandardCharsets.UTF_8);
        for (int i = 1; i <= 3; i++) {
            new Thread(() -> {
                try {
                    LeaseGrantResponse leaseGrantResponse = client.getLeaseClient().grant(10).get();
                    long leaseId = leaseGrantResponse.getID();

                    Lock lock = client.getLockClient();
                    // 阻塞获取锁
                    LockResponse lockResponse = lock.lock(lockName, leaseId).get();
                    ByteSequence lockKey = lockResponse.getKey();
                    log.info("{} 获得锁 {}", Thread.currentThread().getName(), lockResponse.getKey());
                    Thread.sleep(3000);
                    // 释放锁，租约撤销或到期也会释放锁
                    lock.unlock(lockKey).get();
                } catch (Exception e) {
                    log.error("", e);
                }
            }).start();
        }
        Thread.sleep(1000 * 20);
    }

    @Test
    public void election() throws Exception {
        ByteSequence electionName = ByteSequence.from("electionName", StandardCharsets.UTF_8);
        ByteSequence proposal1 = ByteSequence.from("proposal1", StandardCharsets.UTF_8);
        ByteSequence proposal2 = ByteSequence.from("proposal2", StandardCharsets.UTF_8);
        ByteSequence proposal3 = ByteSequence.from("proposal3", StandardCharsets.UTF_8);
        ByteSequence[] proposals = new ByteSequence[]{proposal1, proposal2, proposal3};

        for (ByteSequence proposal : proposals) {
            new Thread(() -> {
                try {
                    Election election = client.getElectionClient();
                    // 监听选举事件(可选)
                    election.observe(electionName, new Election.Listener() {
                        @Override
                        public void onNext(LeaderResponse leaderResponse) {
                            log.info("proposal={},key={},value={}", proposal,
                                    leaderResponse.getKv().getKey(), leaderResponse.getKv().getValue());
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("发生异常:{}", throwable.getMessage());
                        }

                        @Override
                        public void onCompleted() {
                            log.info("complete");
                        }
                    });

                    LeaseGrantResponse leaseGrantResponse = client.getLeaseClient().grant(5).get();
                    long leaseId = leaseGrantResponse.getID();
                    client.getLeaseClient().keepAlive(leaseId, null);

                    // 获得领导权限或租约到期退出等待
                    CampaignResponse campaignResponse = election.campaign(electionName, leaseId, proposal).get();
                    LeaderKey leaderKey = campaignResponse.getLeader();
                    log.info("{},获得领导权,{}", proposal, leaderKey.getKey());
                    // 获取领导者，如果是租约到期则改行代码会抛出异常NoLeaderException
                    LeaderResponse leaderResponse = election.leader(electionName).get();
                    log.info("领导者:{}", leaderResponse.getKv().getValue());
                    // TODO:业务处理
                    Thread.sleep(1000 * 6);
                    // 释放领导权
                    election.resign(leaderKey).get();
                    client.getLeaseClient().revoke(leaseId);
                } catch (Exception e) {
                    log.error("", e);
                }
            }).start();
        }

        Thread.sleep(1000 * 30);
    }

}
