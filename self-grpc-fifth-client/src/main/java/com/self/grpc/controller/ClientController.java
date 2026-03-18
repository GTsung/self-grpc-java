package com.self.grpc.controller;

import com.self.grpc.api.GreeterGrpc;
import com.self.grpc.api.HelloReply;
import com.self.grpc.api.HelloRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
public class ClientController {

    @Resource
    private GreeterGrpc.GreeterBlockingStub greeterStub;

    @GetMapping("/hello")
    public String sayHello(@RequestParam(defaultValue = "World") String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response = greeterStub.sayHello(request);
        return response.getMessage();
    }

}
