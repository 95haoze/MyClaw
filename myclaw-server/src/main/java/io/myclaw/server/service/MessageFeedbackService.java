package io.myclaw.server.service;

import io.myclaw.server.persistence.entity.MessageFeedbackEntity;
import io.myclaw.server.persistence.repository.ChatMessageRepository;
import io.myclaw.server.persistence.repository.ChatSessionRepository;
import io.myclaw.server.persistence.repository.MessageFeedbackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageFeedbackService {
    private final MessageFeedbackRepository feedbacks;
    private final ChatMessageRepository messages;
    private final ChatSessionRepository sessions;
    public MessageFeedbackService(MessageFeedbackRepository feedbacks, ChatMessageRepository messages,
                                  ChatSessionRepository sessions) {
        this.feedbacks = feedbacks; this.messages = messages; this.sessions = sessions;
    }
    @Transactional
    public void save(Long messageId, String value) {
        verifyAssistant(messageId);
        if (!"up".equals(value) && !"down".equals(value))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "反馈值必须是 up 或 down");
        var item = feedbacks.findByMessageIdAndOwnerEmail(messageId, owner())
                .orElseGet(() -> new MessageFeedbackEntity(messageId, owner(), value));
        item.setValue(value);
        feedbacks.save(item);
    }
    @Transactional
    public void delete(Long messageId) {
        verifyAssistant(messageId);
        feedbacks.findByMessageIdAndOwnerEmail(messageId, owner()).ifPresent(feedbacks::delete);
    }
    @Transactional(readOnly = true)
    public void deleteForMessages(java.util.List<Long> messageIds) {
        feedbacks.deleteByMessageIds(messageIds);
    }

    public String value(Long messageId) {
        return feedbacks.findByMessageIdAndOwnerEmail(messageId, owner()).map(MessageFeedbackEntity::getValue).orElse(null);
    }
    private void verifyAssistant(Long messageId) {
        var message = messages.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"assistant".equals(message.getRole()) || sessions.findByIdAndOwnerEmail(message.getSessionId(), owner()).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private static String owner() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}
