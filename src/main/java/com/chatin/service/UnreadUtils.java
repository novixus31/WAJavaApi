package com.chatin.service;

import com.chatin.model.Chat;
import com.chatin.repository.ChatRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Unread count utilities - Java port of lib/unreadUtils.ts
 */
@Service
public class UnreadUtils {

    private final ChatRepository chatRepository;

    public UnreadUtils(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Get total unread count for an account for a specific user
     */
    public int getAccountUnreadCount(String accountId, String userId) {
        List<Chat> chats = chatRepository.findByAccountId(accountId);
        int total = 0;
        for (Chat chat : chats) {
            Map<String, Integer> unreadCounts = chat.getUnreadCounts();
            if (unreadCounts != null && unreadCounts.containsKey(userId)) {
                total += unreadCounts.get(userId);
            }
        }
        return total;
    }
}
