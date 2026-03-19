package com.self.grpc.controller;

import dict.DictOuterClass;
import dict.DictServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
public class GoClientController {

    @Resource
    private DictServiceGrpc.DictServiceBlockingStub dictServiceStub;

    @GetMapping("list")
    public Object list() {
        // 引入了 jackson-datatype-protobuf
        // 才能直接jackson序列化/反序列化protobuf
        return dictServiceStub
                .listDict(DictOuterClass.ListDictReq.newBuilder().build());
    }

}
