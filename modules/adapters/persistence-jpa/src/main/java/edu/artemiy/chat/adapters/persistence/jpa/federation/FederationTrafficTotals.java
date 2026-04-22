package edu.artemiy.chat.adapters.persistence.jpa.federation;

interface FederationTrafficTotals {

    long getInboundMessages();

    long getOutboundMessages();

    long getInboundStanzas();

    long getOutboundStanzas();

    long getErrorCount();
}
