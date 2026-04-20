# ADR 0004: Mandatory XMPP Federation Scope

## Status
Accepted

## Context
The brief includes advanced requirements for Jabber/XMPP client support, inter-server federation, and Jabber-specific admin screens.
The earlier repository baseline treated this as deferred or stretch work.
Product direction has now changed: these requirements are mandatory acceptance scope rather than optional follow-on work.

## Decision
For this repository:
- Jabber/XMPP client connectivity is required delivery scope.
- Federation between at least two independently configured server instances is required delivery scope.
- The implementation must use a Jabber/XMPP library compatible with the governed Java and Spring Boot stack.
- The web UI must include Jabber-specific administration screens for connection status and federation traffic statistics.
- Final acceptance must include two-server federation evidence with at least 50 clients connected to server A, at least 50 clients connected to server B, and bidirectional messaging between A and B.

## Why
- The user explicitly directed the project to treat the advanced requirements as mandatory.
- The brief text makes Jabber support, federation, and the related admin visibility part of the strongest possible implementation target.
- Treating these requirements as optional would misstate acceptance scope and distort architecture, testing, and delivery sequencing.

## Consequences
- Architecture, contracts, persistence planning, and delivery sequencing must all include XMPP and federation work.
- The local orchestration model must eventually support both single-server development and a two-server federation validation scenario.
- Admin observability for Jabber connections and federation traffic is required product surface, not internal-only tooling.
- Open technical choices such as the exact XMPP library, federation topology, and metric collection approach remain active bets until closed by evidence.
