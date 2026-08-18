package com.routeopt.ai;

/**
 * Extracts structured delivery orders from free-form text.
 *
 * <p>This is the only part of the system that talks to a language model. Everything downstream —
 * geocoding, distance matrices, sequencing — is deterministic. The interface exists so tests can
 * run without network access or credentials.
 */
public interface OrderParser {

    ParsedOrders parse(String freeText);

    /** Whether the parser is actually usable right now (for example, credentials are present). */
    boolean isAvailable();
}
