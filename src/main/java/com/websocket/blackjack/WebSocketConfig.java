package com.websocket.blackjack;

import com.websocket.blackjack.Handler.BlackJackHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private BlackJackHandler blackJackHandler;
    public WebSocketConfig(BlackJackHandler blackJackHandler){
        this.blackJackHandler = blackJackHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(blackJackHandler, "/api").setAllowedOrigins("*");
    }
}
