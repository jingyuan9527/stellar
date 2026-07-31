package com.stellar;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.stellar.ai.mapper", "com.stellar.tts.mapper", "com.stellar.game.mapper", "com.stellar.system.mapper"})
public class StellarApplication {

    public static void main(String[] args) {
        SpringApplication.run(StellarApplication.class, args);
    }
}
