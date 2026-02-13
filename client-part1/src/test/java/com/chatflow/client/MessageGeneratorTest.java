package com.chatflow.client;

import com.chatflow.client.generator.MessageGenerator;
import com.chatflow.client.model.ChatMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class MessageGeneratorTest {

    @Test
    void generatesCorrectNumberOfMessages() throws InterruptedException {
        int total = 1000;
        LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(total);
        MessageGenerator gen = new MessageGenerator(queue, total);

        Thread t = new Thread(gen);
        t.start();
        t.join(10_000); // 10s timeout

        assertEquals(total, queue.size());
    }

    @Test
    void generatedMessagesHaveValidFields() throws InterruptedException {
        int total = 100;
        LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(total);
        MessageGenerator gen = new MessageGenerator(queue, total);

        Thread t = new Thread(gen);
        t.start();
        t.join(5000);

        for (ChatMessage msg : queue) {
            String json = msg.toJson();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // userId: 1-100000
            int userId = Integer.parseInt(obj.get("userId").getAsString());
            assertTrue(userId >= 1 && userId <= 100000, "userId out of range: " + userId);

            // username: matches "user{userId}"
            assertEquals("user" + userId, obj.get("username").getAsString());

            // message: non-empty
            assertFalse(obj.get("message").getAsString().isEmpty());

            // messageType: one of TEXT, JOIN, LEAVE
            String type = obj.get("messageType").getAsString();
            assertTrue(type.equals("TEXT") || type.equals("JOIN") || type.equals("LEAVE"),
                    "Invalid messageType: " + type);

            // roomId: 1-20
            int roomId = msg.getRoomId();
            assertTrue(roomId >= 1 && roomId <= 20, "roomId out of range: " + roomId);
        }
    }

    @Test
    void messageTypeDistribution_approximately90_5_5() throws InterruptedException {
        int total = 50_000;
        LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(total);
        MessageGenerator gen = new MessageGenerator(queue, total);

        Thread t = new Thread(gen);
        t.start();
        t.join(15_000);

        Map<String, Integer> counts = new HashMap<>();
        for (ChatMessage msg : queue) {
            counts.merge(msg.getMessageType(), 1, Integer::sum);
        }

        int text = counts.getOrDefault("TEXT", 0);
        int join = counts.getOrDefault("JOIN", 0);
        int leave = counts.getOrDefault("LEAVE", 0);

        // Allow 2% tolerance
        double textPct = (double) text / total * 100;
        double joinPct = (double) join / total * 100;
        double leavePct = (double) leave / total * 100;

        assertTrue(textPct > 87 && textPct < 93,
                "TEXT should be ~90%, got " + String.format("%.1f%%", textPct));
        assertTrue(joinPct > 3 && joinPct < 7,
                "JOIN should be ~5%, got " + String.format("%.1f%%", joinPct));
        assertTrue(leavePct > 3 && leavePct < 7,
                "LEAVE should be ~5%, got " + String.format("%.1f%%", leavePct));
    }

    @Test
    void roomIdDistribution_coversAllRooms() throws InterruptedException {
        int total = 10_000;
        LinkedBlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>(total);
        MessageGenerator gen = new MessageGenerator(queue, total);

        Thread t = new Thread(gen);
        t.start();
        t.join(10_000);

        Map<Integer, Integer> roomCounts = new HashMap<>();
        for (ChatMessage msg : queue) {
            roomCounts.merge(msg.getRoomId(), 1, Integer::sum);
        }

        // All 20 rooms should be represented
        assertEquals(20, roomCounts.size(), "Should cover all 20 rooms");
        for (int roomId = 1; roomId <= 20; roomId++) {
            assertTrue(roomCounts.containsKey(roomId), "Missing room " + roomId);
        }
    }
}
