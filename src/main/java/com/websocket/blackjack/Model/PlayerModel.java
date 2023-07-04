package com.websocket.blackjack.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class PlayerModel {
    private String sessionId;
    private String name;
    private WebSocketSession session;
    private String state;
    private Integer chip;
    private List<CardModel> hand;
    private Integer point;
}
