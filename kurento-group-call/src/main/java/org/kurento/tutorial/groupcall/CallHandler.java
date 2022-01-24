/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kurento.tutorial.groupcall;

import java.io.IOException;

import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);

  private static final Gson gson = new GsonBuilder().create();

  @Autowired
  private RoomManager roomManager;

  @Autowired
  private UserRegistry registry;//현재 연결된 user의 session을 user의 name을 key로하는 map을 가지고 있다.

  //client 에서 받아온 메세지를 다루는 메소드 ->TextWebSocketHandler를 상속받았기 때문에 handleTextMessage를 구현
  //TextWebSocketHandler이외에 BinaryWebSocketHandler도 지원한다.
  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);//message에 담긴 payload를 jsonObject형태로 저장한다.

    final UserSession user = registry.getBySession(session);//session에 연결된 user를 registry에서 꺼내온다.

    if (user != null) {//session이 registry에 존재한다면
      log.debug("Incoming message from user '{}': {}", user.getName(), jsonMessage);
    } else {//session이 registry에 존재하지 않는다면
      log.debug("Incoming message from new user: {}", jsonMessage);
    }

    //message로 받아온 동작에 따라 분기
    switch (jsonMessage.get("id").getAsString()) {
      case "joinRoom"://채팅방에 입장하는 메세지
        joinRoom(jsonMessage, session);
        break;
      case "receiveVideoFrom"://비디오영상을 받는 메세지??
        final String senderName = jsonMessage.get("sender").getAsString();//메세지를 보낸사람
        final UserSession sender = registry.getByName(senderName);//보낸사람의 session을 가져온다.
        final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();//sdpOffer : 어떤 방식으로 연결될지 공유하기 위해 필요한 것
        user.receiveVideoFrom(sender, sdpOffer);
        break;
      case "leaveRoom"://채팅방 퇴장 메세지
        leaveRoom(user);
        break;
      case "onIceCandidate"://????
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        if (user != null) {//유저 세션이 존재한다면
          IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
              candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand, jsonMessage.get("name").getAsString());//유저세션에 접속가능한 주소 추가
        }
        break;
      default:
        break;
    }
  }
  //웹소켓 클라이언트와 연결이 끊겼을때 호출이된다.
  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    UserSession user = registry.removeBySession(session);//끊으려는 session을 registry에서 지운 후 userSession을 가져온다
    roomManager.getRoom(user.getRoomName()).leave(user);// 유저를 방에서 내보낸다.
  }

  private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
    //message에 담겨있는 userName과 roomName을 받아온다.
    final String roomName = params.get("room").getAsString();
    final String name = params.get("name").getAsString();
    log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

    Room room = roomManager.getRoom(roomName);//roomName으로 생성되어있는 room을 가져온다.
    final UserSession user = room.join(name, session);//name과 session을 room의 pipeline과 연결시켜 UserSession을 생성한다.
    registry.register(user);//생성된 UserSession을 등록한다.
  }

  private void leaveRoom(UserSession user) throws IOException {
    final Room room = roomManager.getRoom(user.getRoomName());//유저가 접속해있는 방을 가져온다.
    room.leave(user);//유저를 방에서 내보낸다.
    if (room.getParticipants().isEmpty()) {//방에 남아있는 참가자가 없다면
      roomManager.removeRoom(room);//방을 삭제한다.
    }
  }
}
