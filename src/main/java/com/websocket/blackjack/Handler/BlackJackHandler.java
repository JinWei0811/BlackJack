package com.websocket.blackjack.Handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.websocket.blackjack.Model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

import java.io.IOException;
import java.util.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class BlackJackHandler implements WebSocketHandler {

    private final static Logger logger = LoggerFactory.getLogger(BlackJackHandler.class);
    private Map<String, Thread> multipleThread = new HashMap<>();
    private List<RoomModel> roomList = new ArrayList<>();

    @Value("${roomId.max.length}")
    private String roomIdLength;

    @Value("${allowed.char}")
    private String allowedChar;

    /**
     * Room State
     */
    @Value("${roomState.waiting}")
    private String waiting;
    @Value("${roomState.playing}")
    private String playing;

    /**
     * Player State
     */
    @Value("${playerState.create}")
    private String create;
    @Value("${playerState.join}")
    private String join;
    @Value("${playerState.leave}")
    private String leave;
    @Value("${playerState.ready}")
    private String ready;
    @Value("${playerState.not_ready}")
    private String not_ready;
    @Value("${playerState.hit}")
    private String hit;
    @Value("${playerState.continue}")
    private String continu;
    @Value("${playerState.skip}")
    private String skip;
    @Value("${playerState.win}")
    private String win;
    @Value("${playerState.lose}")
    private String lose;
    @Value("${model.result}")
    private String result;
    @Value("${model.playerCard}")
    private String playerCard;
    @Value("${model.response}")
    private String response;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.debug(String.format("%s Room Connection Established", session.getId()));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        ConnectedModel connected = convertJsonToModel((String) message.getPayload());
        if (connected != null) {
            // create room
            if (connected.getMethod().equals(create)) {
                var response = createRoom(session, connected);
                if (response != null) {
                    TextMessage responseMessage = new TextMessage(convertModelToJsonString(response));
                    session.sendMessage(responseMessage);
                }
            }

            // join room
            if (connected.getMethod().equals(join) && !connected.getRoomId().isEmpty()) {
                var response = joinRoom(session, connected);
                if (response != null) {
                    brodcastToPlayers(convertModelToJsonString(response), findRoomByRoomId(connected.getRoomId()));
                }
            }

            // leave room
            if (connected.getMethod().equals(leave) && !connected.getRoomId().isEmpty()) {
                var response = leaveRoom(session, connected);
                if (response != null) {
                    brodcastToPlayers(convertModelToJsonString(response), findRoomByRoomId(connected.getRoomId()));
                }
            }

            if (connected.getMethod().equals(ready)) {
                var response = playerReadyOrNotReady(session, connected, ready);
                if (response != null) {
                    brodcastToPlayers(convertModelToJsonString(response), findRoomByRoomId(connected.getRoomId()));
                    Thread thread = new Thread(() -> {
                        try {
                            checkPlayersReady(connected);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    thread.start();
                }
            }

            if (connected.getMethod().equals(not_ready)) {
                var response = playerReadyOrNotReady(session, connected, not_ready);
                if (response != null) {
                    brodcastToPlayers(convertModelToJsonString(response), findRoomByRoomId(connected.getRoomId()));
                }
            }

            if (connected.getMethod().equals(hit)) {
                Thread thread = new Thread(() -> {
                    try {
                        playerHit(session, connected);
                        allStateCheck(findRoomByRoomId(connected.getRoomId()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                thread.start();
            }

            if (connected.getMethod().equals(skip)) {
                Thread thread = new Thread(() -> {
                    try {
                        playerSkip(session, connected);
                        allStateCheck(findRoomByRoomId(connected.getRoomId()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                thread.start();
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private ConnectedRespModel createRoom(WebSocketSession session, ConnectedModel connectedModel) {
        var newRoomId = createRoomId();
        while (findRoomByRoomId(newRoomId) != null) {
            newRoomId = createRoomId();
        }
        var player = createPlayer(session, connectedModel.getName(), not_ready);
        var botPlayer = createPlayer(null, "bot", ready);
        List<PlayerModel> playerModelList = new ArrayList<>();
        playerModelList.add(botPlayer);
        playerModelList.add(player);

        RoomModel roomModel = createRoomModel(newRoomId, waiting, playerModelList);
        roomList.add(roomModel);
        return createConnectedResp(roomModel, "創建房間成功");
    }

    private ConnectedRespModel joinRoom(WebSocketSession session, ConnectedModel connected) {
        RoomModel currentRoom = findRoomByRoomId(connected.getRoomId());
        if (currentRoom == null) {
            return createConnectedResp(null, "查無此房間");
        }
        if (currentRoom.getPlayerList().size() > 4) {
            return createConnectedResp(null, "人數已滿，無法加入房間");
        }
        if (currentRoom.getRoomState().equals(playing)) {
            return createConnectedResp(null, "遊戲進行中，無法加入房間");
        }

        PlayerModel newPlayer = createPlayer(session, connected.getName(), not_ready);
        List<PlayerModel> playerList = currentRoom.getPlayerList();
        playerList.add(newPlayer);
        currentRoom.setPlayerList(playerList);
        String content = connected.getName() + " 加入房間";
        return createConnectedResp(currentRoom, content);
    }

    private ConnectedRespModel leaveRoom(WebSocketSession session, ConnectedModel connected) {
        RoomModel currentRoom = findRoomByRoomId(connected.getRoomId());
        if (currentRoom == null) {
            return null;
        }
        PlayerModel playerInfo = findPlayerInfoBySessionId(currentRoom.getPlayerList(), session.getId());
        if (playerInfo == null) {
            return null;
        }
        List<PlayerModel> newPlayerList = currentRoom.getPlayerList();
        newPlayerList.remove(playerInfo);
        if (newPlayerList.size() == 0) {
            roomList.remove(currentRoom);
            String content = connected.getName() + " 離開房間";
            return createConnectedResp(null, content);
        }
        currentRoom.setPlayerList(newPlayerList);
        String content = connected.getName() + " 離開房間";
        return createConnectedResp(currentRoom, content);
    }

    private ConnectedRespModel playerReadyOrNotReady(WebSocketSession session, ConnectedModel connected, String state) {
        RoomModel currentRoom = findRoomByRoomId(connected.getRoomId());
        List<PlayerModel> playerList = currentRoom.getPlayerList();
        PlayerModel newPlayerInfo = findPlayerInfoBySessionId(playerList, session.getId());
        String content = connected.getName();
        if (state == ready) {
            newPlayerInfo.setState(ready);
            content += "已準備就緒";
        }
        if (state == not_ready) {
            newPlayerInfo.setState(not_ready);
            content += "已取消準備";
        }
        return createConnectedResp(currentRoom, content);
    }

    private void playerHit(WebSocketSession session, ConnectedModel connected) throws Exception {
        RoomModel room = findRoomByRoomId(connected.getRoomId());
        PlayerModel playerInfo = findPlayerInfoBySessionId(room.getPlayerList(), session.getId());
        CardModel card = room.getDeck().remove(0);
        List<CardModel> playerHand = playerInfo.getHand();
        playerHand.add(card);
        playerInfo.setHand(playerHand);
        playerInfo.setPoint(calculateHandPoints(playerInfo.getHand()));
        playerInfo.setState(calculateHandBrodcast(playerInfo, room));
    }

    private void playerSkip(WebSocketSession session, ConnectedModel connected) throws Exception {
        RoomModel room = findRoomByRoomId(connected.getRoomId());
        PlayerModel newPlayerInfo = findPlayerInfoBySessionId(room.getPlayerList(), session.getId());
        newPlayerInfo.setState(skip);
        brodcastToPlayers(convertModelToJsonString(getPlayerCard(newPlayerInfo, skip)), room);
    }

    private void checkPlayersReady(ConnectedModel connected) throws Exception {
        RoomModel currentRoom = findRoomByRoomId(connected.getRoomId());
        if (currentRoom != null) {
            boolean isAllReady1 = checkPlayersReady(currentRoom.getPlayerList());
            try {
                Thread.sleep(3000);
            } catch (InterruptedException exc) {
                throw new RuntimeException(exc);
            }
            boolean isAllReady2 = checkPlayersReady(currentRoom.getPlayerList());
            if (isAllReady1 && isAllReady2) {
                currentRoom.getPlayerList().stream().forEach(v -> v.setState(continu));
                ConnectedRespModel connectedResp = createConnectedResp(currentRoom, "遊戲開始");
                brodcastToPlayers(convertModelToJsonString(connectedResp), currentRoom);
                gameStart(currentRoom);
            }
        }
    }

    private void gameStart(RoomModel room) throws Exception {
        List<CardModel> deck = createDeck();
        Collections.shuffle(deck);
        room.setDeck(deck);

        for (PlayerModel player : room.getPlayerList()) {
            List<CardModel> hand = new ArrayList<>();
            CardModel card = room.getDeck().remove(0);
            hand.add(card);
            player.setHand(hand);
            player.setPoint(calculateHandPoints(player.getHand()));
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, continu)), room);
        }

        for (PlayerModel player : room.getPlayerList()) {
            CardModel card = room.getDeck().remove(0);
            List<CardModel> playerHand = player.getHand();
            playerHand.add(card);
            player.setHand(playerHand);
            player.setPoint(calculateHandPoints(player.getHand()));
            calculateHandBrodcast(player, room);
        }
    }

    private String createRoomId() {
        StringBuilder stringBuilder = new StringBuilder(roomIdLength);
        Random random = new Random();
        for (int i = 0; i < Integer.parseInt(roomIdLength); i++) {
            int randomIndex = random.nextInt(allowedChar.length());
            char randomChar = allowedChar.charAt(randomIndex);
            stringBuilder.append(randomChar);
        }
        return stringBuilder.toString();
    }

    private PlayerModel createPlayer(WebSocketSession session, String name, String state) {
        PlayerModel playerModel = PlayerModel.builder()
                .sessionId(name == "bot" ? "bot" : session.getId())
                .session(session)
                .name(name)
                .state(state)
                .build();
        return playerModel;
    }

    private RoomModel createRoomModel(String roomId, String state, List<PlayerModel> playerList) {
        RoomModel roomModel = RoomModel.builder()
                .roomId(roomId)
                .roomState(state)
                .playerList(playerList)
                .build();
        return roomModel;
    }

    private ConnectedRespModel createConnectedResp(RoomModel roomModel, String content) {
        ConnectedRespModel.ConnectedRespModelBuilder connectedRespModel = ConnectedRespModel.builder();
        if (roomModel == null) {
            connectedRespModel.content(content);
        } else {
            List<String> playerList = roomModel.getPlayerList().stream().map(v -> v.getName()).toList();
            List<String> playerStateList = roomModel.getPlayerList().stream().map(v -> v.getState()).toList();
            connectedRespModel
                    .method(response)
                    .roomId(roomModel.getRoomId())
                    .playerList(playerList)
                    .playerStateList(playerStateList)
                    .content(content);
        }
        return connectedRespModel.build();
    }

    private ConnectedModel convertJsonToModel(String jsonPayload) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonPayload, ConnectedModel.class);
        } catch (IOException exc) {
            return null;
        }
    }

    private <T> String convertModelToJsonString(T inputModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        String jsonString = objectMapper.writeValueAsString(inputModel);
        return jsonString;
    }

    private void brodcastToPlayers(String text, RoomModel roomModel) throws Exception {
        TextMessage textMessage = new TextMessage(text);
        if (roomModel != null) {
            for (var player : roomModel.getPlayerList()) {
                if (player.getSessionId() != "bot") {
                    player.getSession().sendMessage(textMessage);
                }
            }
        }
    }

    private RoomModel findRoomByRoomId(String roomId) {
        return roomList.stream().filter(v -> v.getRoomId().equals(roomId)).findFirst().orElse(null);
    }

    private PlayerModel findPlayerInfoBySessionId(List<PlayerModel> playerList, String sessionId) {
        return playerList.stream().filter(v -> v.getSessionId().equals(sessionId)).findFirst().orElse(null);
    }

    private Boolean checkPlayersReady(List<PlayerModel> playerList) {
        return playerList.stream().filter(v -> v.getState().equals(not_ready)).findFirst().orElse(null) == null ? true : false;
    }

    private List<CardModel> createDeck() {
        List<CardModel> deck = new ArrayList<>();
        String[] suits = {"Spades", "Hearts", "Diamonds", "Clubs"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        for (String suit : suits) {
            for (String rank : ranks) {
                CardModel card = new CardModel(suit, rank);
                deck.add(card);
            }
        }
        return deck;
    }

    private Integer calculateHandPoints(List<CardModel> hand) {
        int totalPoints = 0;
        int numOfAce = 0;

        for (var card : hand) {
            if (card.getRank().equals("A")) {
                totalPoints += 1;
                numOfAce++;
            } else if (card.getRank().equals("J") || card.getRank().equals("Q") || card.getRank().equals("K")) {
                totalPoints += 10;
            } else {
                totalPoints += Integer.parseInt(card.getRank());
            }
        }

        // 處理A的點數
        for (int i = 0; i < numOfAce; i++) {
            if (totalPoints <= 11) {
                totalPoints += 10;
            }
        }

        return totalPoints;
    }

    private PlayerCardModel getPlayerCard(PlayerModel player, String state) {
        List<String> suits = new ArrayList<>();
        List<String> ranks = new ArrayList<>();
        for (var card : player.getHand()) {
            suits.add(card.getSuits());
            ranks.add(card.getRank());
        }

        return PlayerCardModel.builder()
                .method(playerCard)
                .name(player.getName())
                .suits(suits)
                .ranks(ranks)
                .state(state)
                .points(calculateHandPoints(player.getHand()))
                .build();
    }

    private String calculateHandBrodcast(PlayerModel player, RoomModel room) throws Exception {
        String state = "";
        if (player.getPoint() == 21) {
            state = skip;
            if (!player.getSessionId().equals("bot")) {
                allStateCheck(room);
            }
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), room);
        } else if (player.getPoint() > 21) {
            state = lose;
            if (!player.getSessionId().equals("bot")) {
                allStateCheck(room);
            }
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), room);
        } else {
            state = continu;
            brodcastToPlayers(convertModelToJsonString(getPlayerCard(player, state)), room);
        }
        return state;
    }

    private void allStateCheck(RoomModel room) throws Exception {
        Boolean isPlayersSkip = room.getPlayerList().stream().filter(v -> !v.getName().equals("bot") && v.getState().equals(continu)).findFirst().orElse(null) == null ? true : false;
        if (isPlayersSkip) {
            PlayerModel botPlayer = room.getPlayerList().stream().filter(v -> v.getSessionId().equals("bot")).findFirst().orElse(null);
            if (botPlayer != null) {
                while (botPlayer.getPoint() <= 16) {
                    CardModel card = room.getDeck().remove(0);
                    List<CardModel> botHand = botPlayer.getHand();
                    botHand.add(card);
                    botPlayer.setHand(botHand);
                    botPlayer.setPoint(calculateHandPoints(botPlayer.getHand()));
                    botPlayer.setState(calculateHandBrodcast(botPlayer, room));
                }

                if (botPlayer.getState().equals(continu) && botPlayer.getPoint() > 16) {
                    botPlayer.setState(skip);
                    List<GameResultModel> gameResults = createGameResult(room, botPlayer);
                    brodcastToPlayers(convertModelToJsonString(gameResults), room);
//                    brodcastToPlayers(convertModelToJsonString(getPlayerCard(botPlayer, skip)), room);
                }

                if (botPlayer.getState().equals(skip)) {
                    List<GameResultModel> gameResults = createGameResult(room, botPlayer);
                    brodcastToPlayers(convertModelToJsonString(gameResults), room);
                }

                if (botPlayer.getState().equals(lose)) {
                    List<GameResultModel> gameResults = createGameResult(room, botPlayer);
                    brodcastToPlayers(convertModelToJsonString(gameResults), room);
                }
            }
        }
    }

    private List<GameResultModel> createGameResult(RoomModel room, PlayerModel botPlayer) {
        List<GameResultModel> gameResults = new ArrayList<>();
        for (PlayerModel player : room.getPlayerList()) {
            if (player.getName().equals("bot")) {
                continue;
            }
            if (botPlayer.getState().equals(lose)) {
                if (!player.getState().equals(lose)) {
                    gameResults.add(generateGameResult(player, room.getRoomId(), win));
                }
            } else {
                if (player.getPoint() <= botPlayer.getPoint() || player.getState().equals(lose)) {
                    gameResults.add(generateGameResult(player, room.getRoomId(), lose));
                }
                if (player.getPoint() > botPlayer.getPoint() && player.getPoint() <= 21) {
                    gameResults.add(generateGameResult(player, room.getRoomId(), win));
                }
            }
        }
        return gameResults;
    }

    private GameResultModel generateGameResult(PlayerModel player, String roomId, String state) {
        return GameResultModel.builder()
                .roomId(roomId)
                .name(player.getName())
                .result(state)
                .method(result)
                .build();
    }

}
