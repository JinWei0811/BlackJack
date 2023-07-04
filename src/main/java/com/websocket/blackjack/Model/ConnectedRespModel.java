package com.websocket.blackjack.Model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ConnectedRespModel {
    private String roomId;
    private List<String> playerList;
    private List<String> playerStateList;
    private String content;
    private String method;
}
