package com.chatin.controller;

import com.chatin.model.Account;
import com.chatin.model.Chat;
import com.chatin.model.Message;
import com.chatin.model.User;
import com.chatin.model.UserAccount;
import com.chatin.repository.*;
import com.chatin.service.WhatsAppManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final ReactionRepository reactionRepository;
    private final WhatsAppManager whatsAppManager;

    public ChatController(ChatRepository chatRepository, MessageRepository messageRepository,
                          AccountRepository accountRepository, UserAccountRepository userAccountRepository,
                          ReactionRepository reactionRepository, WhatsAppManager whatsAppManager) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.accountRepository = accountRepository;
        this.userAccountRepository = userAccountRepository;
        this.reactionRepository = reactionRepository;
        this.whatsAppManager = whatsAppManager;
    }

    /**
     * GET /api/chats/all - Get all chats for accessible accounts
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllChats(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String userId = (String) request.getAttribute("userId");
        String companyId = (String) request.getAttribute("userCompanyId");

        List<Chat> allChats = new ArrayList<>();
        
        if ("superadmin".equals(role)) {
            allChats = chatRepository.findAll(Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));
        } else if ("admin".equals(role) || "manager".equals(role)) {
            List<Account> companyAccounts = accountRepository.findByCompanyId(companyId);
            for (Account acc : companyAccounts) {
                allChats.addAll(chatRepository.findByAccountId(acc.getId(), Sort.by(Sort.Direction.DESC, "lastMessageTimestamp")));
            }
        } else {
            List<UserAccount> userAccounts = userAccountRepository.findByUserId(userId);
            for (UserAccount ua : userAccounts) {
                allChats.addAll(chatRepository.findByAccountId(ua.getAccountId(), Sort.by(Sort.Direction.DESC, "lastMessageTimestamp")));
            }
        }
        
        allChats.sort((c1, c2) -> {
            if (c1.getLastMessageTimestamp() == null) return 1;
            if (c2.getLastMessageTimestamp() == null) return -1;
            return c2.getLastMessageTimestamp().compareTo(c1.getLastMessageTimestamp());
        });

        return ResponseEntity.ok(allChats);
    }

    /**
     * GET /api/chats/:accountId - Get chats for an account
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getChats(@PathVariable String accountId, HttpServletRequest request) {
        // Verify access
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized to access this account"));
        }

        List<Chat> chats = chatRepository.findByAccountId(accountId,
                Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));

        return ResponseEntity.ok(chats);
    }

    /**
     * GET /api/chats/:accountId/:remoteJid/messages - Get messages for a chat
     */
    @GetMapping("/{accountId}/{remoteJid}/messages")
    public ResponseEntity<?> getMessages(@PathVariable String accountId, @PathVariable String remoteJid,
                                         @RequestParam(defaultValue = "50") int limit,
                                         @RequestParam(required = false) String before,
                                         HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        List<Message> messages;
        PageRequest pageRequest = PageRequest.of(0, limit);

        if (before != null) {
            Instant beforeInstant = Instant.parse(before);
            messages = messageRepository.findByAccountIdAndRemoteJidAndMessageTimestampBeforeOrderByMessageTimestampDesc(
                    accountId, remoteJid, beforeInstant, pageRequest);
        } else {
            messages = messageRepository.findByAccountIdAndRemoteJidOrderByMessageTimestampDesc(
                    accountId, remoteJid, pageRequest);
        }

        // Reverse to get chronological order
        Collections.reverse(messages);

        return ResponseEntity.ok(messages);
    }

    /**
     * PUT /api/chats/:accountId/:remoteJid/title - Update chat custom title
     */
    @PutMapping("/{accountId}/{remoteJid}/title")
    public ResponseEntity<?> updateTitle(@PathVariable String accountId, @PathVariable String remoteJid,
                                         @RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Chat> chatOpt = chatRepository.findByAccountIdAndRemoteJid(accountId, remoteJid);
        if (chatOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Chat not found"));
        }

        Chat chat = chatOpt.get();
        chat.setCustomTitle(body.get("customTitle"));
        chatRepository.save(chat);

        return ResponseEntity.ok(chat);
    }

    /**
     * DELETE /api/chats/:accountId/:remoteJid/title - Delete chat custom title
     */
    @DeleteMapping("/{accountId}/{remoteJid}/title")
    public ResponseEntity<?> deleteTitle(@PathVariable String accountId, @PathVariable String remoteJid, HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Chat> chatOpt = chatRepository.findByAccountIdAndRemoteJid(accountId, remoteJid);
        if (chatOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Chat not found"));
        }

        Chat chat = chatOpt.get();
        chat.setCustomTitle(null);
        chatRepository.save(chat);

        return ResponseEntity.ok(Map.of("message", "Custom title removed"));
    }

    /**
     * POST /api/chats/:accountId/:remoteJid/read - Mark chat as read
     */
    @PostMapping("/{accountId}/{remoteJid}/read")
    public ResponseEntity<?> markAsRead(@PathVariable String accountId, @PathVariable String remoteJid,
                                        HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");

        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Chat> chatOpt = chatRepository.findByAccountIdAndRemoteJid(accountId, remoteJid);
        if (chatOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Chat not found"));
        }

        Chat chat = chatOpt.get();
        Map<String, Integer> unreadCounts = chat.getUnreadCounts();
        if (unreadCounts == null) {
            unreadCounts = new HashMap<>();
        }
        unreadCounts.put(userId, 0);
        chat.setUnreadCounts(unreadCounts);
        chatRepository.save(chat);

        // Also mark as read on WhatsApp
        whatsAppManager.markChatRead(accountId, remoteJid);

        return ResponseEntity.ok(Map.of("message", "Chat marked as read"));
    }

    /**
     * POST /api/chats/:accountId/:remoteJid/send - Send a text message
     */
    @PostMapping("/{accountId}/{remoteJid}/send")
    public ResponseEntity<?> sendMessage(@PathVariable String accountId, @PathVariable String remoteJid,
                                         @RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String text = (String) body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message text is required"));
        }

        try {
            // Get quoted message info if replying
            @SuppressWarnings("unchecked")
            Map<String, String> quotedMsg = (Map<String, String>) body.get("quotedMessage");

            whatsAppManager.sendTextMessage(accountId, remoteJid, text, quotedMsg);
            return ResponseEntity.ok(Map.of("message", "Message sent"));
        } catch (Exception e) {
            logger.error("Failed to send message: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send message: " + e.getMessage()));
        }
    }

    /**
     * POST /api/chats/:accountId/send-media - Send a media message
     */
    @PostMapping("/{accountId}/send-media")
    public ResponseEntity<?> sendMedia(@PathVariable String accountId,
                                       @RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String remoteJid = (String) body.get("remoteJid");
        if (remoteJid == null || remoteJid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "remoteJid is required"));
        }

        String mediaType = (String) body.get("type"); // image, video, audio, document
        if (mediaType == null) mediaType = (String) body.get("mediaType"); // fallback
        String mediaUrl = (String) body.get("url");
        if (mediaUrl == null) mediaUrl = (String) body.get("mediaData"); // fallback
        String caption = (String) body.get("caption");
        String fileName = (String) body.get("fileName");
        String mimetype = (String) body.get("mimetype");

        try {
            whatsAppManager.sendMediaMessage(accountId, remoteJid, mediaType, mediaUrl, caption, fileName, mimetype);
            return ResponseEntity.ok(Map.of("message", "Media message sent"));
        } catch (Exception e) {
            logger.error("Failed to send media: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send media: " + e.getMessage()));
        }
    }

    /**
     * POST /api/chats/:accountId/send-reaction - Send a reaction
     */
    @PostMapping("/{accountId}/send-reaction")
    public ResponseEntity<?> sendReaction(@PathVariable String accountId,
                                          @RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!hasAccountAccess(accountId, request)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String remoteJid = body.get("remoteJid");
        if (remoteJid == null || remoteJid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "remoteJid is required"));
        }

        String messageId = body.get("messageId");
        String emoji = body.get("emoji");

        try {
            whatsAppManager.sendReaction(accountId, remoteJid, messageId, emoji);
            return ResponseEntity.ok(Map.of("message", "Reaction sent"));
        } catch (Exception e) {
            logger.error("Failed to send reaction: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Failed to send reaction: " + e.getMessage()));
        }
    }

    /**
     * Check if the requesting user has access to the given account
     */
    private boolean hasAccountAccess(String accountId, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String userId = (String) request.getAttribute("userId");
        String companyId = (String) request.getAttribute("userCompanyId");

        if ("superadmin".equals(role)) return true;

        var accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) return false;

        var account = accountOpt.get();

        if ("admin".equals(role) || "manager".equals(role)) {
            return account.getCompanyId() != null && account.getCompanyId().equals(companyId);
        }

        // Regular user - check user-account assignment
        return userAccountRepository.findByUserIdAndAccountId(userId, accountId).isPresent();
    }
}
