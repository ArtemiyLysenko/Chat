package edu.artemiy.chat.adapters.persistence.jpa.federation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface XmppClientSessionJpaRepository extends JpaRepository<XmppClientSessionEntity, String> {

    List<XmppClientSessionEntity> findTop100ByOrderByConnectedAtDesc();
}
