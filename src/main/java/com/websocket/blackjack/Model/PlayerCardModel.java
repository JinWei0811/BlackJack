package com.websocket.blackjack.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlayerCardModel {
    private String method;
    private String name;
    private List<String> suits;
    private List<String> ranks;
    private String state;
    private Integer points;
}
