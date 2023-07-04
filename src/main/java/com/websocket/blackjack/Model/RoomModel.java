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
public class RoomModel {
    private String roomId;
    private String roomState;
    private List<PlayerModel> playerList;
    private List<CardModel> deck;
}
