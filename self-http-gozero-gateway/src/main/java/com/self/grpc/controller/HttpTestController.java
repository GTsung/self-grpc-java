package com.self.grpc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@RestController
public class HttpTestController {

    @Resource
    private RestTemplate restTemplate;

    @GetMapping("get/{dictId}")
    public Object get(@PathVariable("dictId") String dictId) {
        return restTemplate.getForObject("http://localhost:8888/getDict/" + dictId, String.class);
    }
}
