package edu.artemiy.chat.adapters.xmpp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import edu.artemiy.chat.contacts.api.ContactsException;
import edu.artemiy.chat.contacts.api.ContactsService;

@Component
class XmppSessionRegistry {

    private final ContactsService contactsService;
    private final Map<String, XmppConnectionSession> sessionsByFullJid = new ConcurrentHashMap<>();
    private final Map<UUID, Set<XmppConnectionSession>> sessionsByUserId = new ConcurrentHashMap<>();

    XmppSessionRegistry(ContactsService contactsService) {
        this.contactsService = contactsService;
    }

    void register(XmppConnectionSession session) {
        XmppConnectionSession replacedSession = sessionsByFullJid.put(session.fullJid(), session);
        if (replacedSession != null && replacedSession != session) {
            removeInternal(replacedSession);
            replacedSession.handler().closeSilently();
        }
        sessionsByUserId.computeIfAbsent(session.userId(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    void markAvailable(XmppConnectionSession session) {
        if (!session.markAvailable()) {
            return;
        }
        friendUserIds(session.userId()).forEach(friendUserId -> {
            sessions(friendUserId).forEach(friendSession ->
                friendSession.handler().sendPresence(session.fullJid(), friendSession.fullJid(), null)
            );
            sessions(friendUserId).stream()
                .filter(XmppConnectionSession::isAvailable)
                .forEach(friendSession -> session.handler().sendPresence(friendSession.fullJid(), session.fullJid(), null));
        });
    }

    void markUnavailable(XmppConnectionSession session) {
        if (!session.markUnavailable()) {
            return;
        }
        friendUserIds(session.userId()).forEach(friendUserId ->
            sessions(friendUserId).forEach(friendSession ->
                friendSession.handler().sendPresence(session.fullJid(), friendSession.fullJid(), "unavailable")
            )
        );
    }

    void remove(XmppConnectionSession session) {
        if (session == null) {
            return;
        }
        boolean wasAvailable = session.isAvailable();
        if (wasAvailable) {
            markUnavailable(session);
        }
        removeInternal(session);
    }

    void sendDirectMessage(UUID recipientUserId, String fromJid, UUID stanzaId, String bodyText) {
        List<XmppConnectionSession> recipientSessions = sessions(recipientUserId);
        recipientSessions.forEach(session ->
            session.handler().sendMessage(fromJid, session.fullJid(), stanzaId, bodyText)
        );
    }

    void disconnectAll() {
        sessionsByFullJid.values().forEach(session -> session.handler().closeSilently());
        sessionsByFullJid.clear();
        sessionsByUserId.clear();
    }

    private void removeInternal(XmppConnectionSession session) {
        sessionsByFullJid.remove(session.fullJid(), session);
        Set<XmppConnectionSession> sessions = sessionsByUserId.get(session.userId());
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(session.userId(), sessions);
        }
    }

    private List<XmppConnectionSession> sessions(UUID userId) {
        Set<XmppConnectionSession> sessions = sessionsByUserId.get(userId);
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    private List<UUID> friendUserIds(UUID userId) {
        try {
            return contactsService.listContacts(userId).friends().stream()
                .map(friend -> friend.user().id())
                .toList();
        }
        catch (ContactsException ignored) {
            return List.of();
        }
    }
}
