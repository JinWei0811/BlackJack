package com.websocket.blackjack.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class GameResultModel {
    private String roomId;
    private String name;
    private String result;
    private String method;
}
