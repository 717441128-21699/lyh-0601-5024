package com.housingfund.service;

import cn.hutool.json.JSONUtil;
import com.housingfund.entity.NotificationRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketPushService implements WebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    private static String buildSessionKey(Long userId, String userType) {
        return userType + ":" + userId;
    }

    @PostConstruct
    public void init() {
        log.info("WebSocket推送服务初始化完成");
    }

    @PreDestroy
    public void destroy() {
        log.info("WebSocket推送服务关闭，清理{}个连接", SESSIONS.size());
        SESSIONS.values().forEach(session -> {
            try {
                if (session.isOpen()) session.close();
            } catch (Exception ignored) {}
        });
        SESSIONS.clear();
    }

    public void pushToUser(Long userId, String userType, NotificationRecord record) {
        String key = buildSessionKey(userId, userType);
        WebSocketSession session = SESSIONS.get(key);
        if (session != null && session.isOpen()) {
            try {
                TextMessage message = new TextMessage(JSONUtil.toJsonStr(record));
                session.sendMessage(message);
                log.debug("WebSocket推送成功: user={}, type={}", key, record.getNotificationType());
            } catch (Exception e) {
                log.warn("WebSocket推送异常: user={}, error={}", key, e.getMessage());
            }
        }
    }

    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        SESSIONS.values().forEach(session -> {
            try {
                if (session.isOpen()) session.sendMessage(textMessage);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        Long userId = extractParam(query, "userId");
        String userType = extractParamStr(query, "userType", "employee");

        if (userId != null) {
            String key = buildSessionKey(userId, userType);
            SESSIONS.put(key, session);
            log.info("WebSocket连接建立: user={}, sessionId={}", key, session.getId());
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        log.debug("收到WebSocket消息: sessionId={}, content={}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket传输异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        removeSession(session);
        log.debug("WebSocket连接关闭: sessionId={}", session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void removeSession(WebSocketSession session) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }

    private Long extractParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && name.equals(kv[0])) {
                try { return Long.parseLong(kv[1]); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String extractParamStr(String query, String name, String defaultValue) {
        if (query == null) return defaultValue;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && name.equals(kv[0])) return kv[1];
        }
        return defaultValue;
    }
}
