package com.chatin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class ChatInApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatInApplication.class, args);
        System.out.println("🌐 ChatIn Backend API (Java) is running!");
    }
}
