package com.example.application;

import java.time.Duration;

import com.example.application.framework.PushEndpoint;

import reactor.core.publisher.Flux;

@PushEndpoint
public class MyPushEndpoint {
    public Flux<String> startCountdown(String name, int duration) {
        return Flux.interval(Duration.ofSeconds(1)).take(duration + 1).map(number -> {
            long remaining = duration - number.intValue();
            if (remaining == 0) {
                return "Hello, " + name;
            } else {
                return remaining + "...";
            }
        });
    }
}
