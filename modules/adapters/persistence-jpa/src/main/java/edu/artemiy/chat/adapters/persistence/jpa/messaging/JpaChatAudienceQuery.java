package edu.artemiy.chat.adapters.persistence.jpa.messaging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import edu.artemiy.chat.messaging.api.ChatAudienceQuery;
import edu.artemiy.chat.messaging.api.ChatTargetRef;

@Component
class JpaChatAudienceQuery implements ChatAudienceQuery {

    private static final String ROOM_AUDIENCE_SQL = """
        select user_id
        from room_memberships
        where room_id = ?
        order by joined_at, user_id
        """;

    private static final String DIRECT_DIALOG_AUDIENCE_SQL = """
        select user_low_id, user_high_id
        from direct_dialogs
        where id = ?
        """;

    private final JdbcTemplate jdbcTemplate;

    JpaChatAudienceQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<UUID> listAudienceUserIds(ChatTargetRef chat) {
        return switch (chat.type()) {
            case ROOM -> new LinkedHashSet<>(jdbcTemplate.queryForList(ROOM_AUDIENCE_SQL, UUID.class, chat.id()));
            case DIRECT -> directDialogAudience(chat.id());
        };
    }

    private Set<UUID> directDialogAudience(UUID directDialogId) {
        return jdbcTemplate.query(DIRECT_DIALOG_AUDIENCE_SQL, rs -> {
            if (!rs.next()) {
                return Set.<UUID>of();
            }
            LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
            userIds.add((UUID) rs.getObject("user_low_id"));
            userIds.add((UUID) rs.getObject("user_high_id"));
            return Set.copyOf(userIds);
        }, directDialogId);
    }
}
