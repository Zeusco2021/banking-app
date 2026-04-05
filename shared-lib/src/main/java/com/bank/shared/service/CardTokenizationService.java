package com.bank.shared.service;

import com.bank.shared.model.CardData;

/**
 * CardTokenizationService
 *
 * Defines the contract for tokenizing and detokenizing payment card data
 * using AWS Payment Cryptography.
 *
 * Satisfies Requirement 12.6: card data is tokenized before any persistence;
 * raw PANs are never stored in plain text.
 */
public interface CardTokenizationService {

    /**
     * Tokenizes raw card data into an opaque token.
     *
     * Preconditions:
     *   - cardData != null
     *   - cardData.pan() is a valid 13–19 digit PAN
     *
     * Postconditions:
     *   - Returns a non-null, non-empty token string
     *   - The token can be used to retrieve the original card data via detokenize()
     *   - Raw card data is NOT persisted or logged anywhere in this method
     *
     * @param cardData the raw card data to tokenize
     * @return an opaque token representing the card
     */
    String tokenize(CardData cardData);

    /**
     * Detokenizes a token back to the original card data.
     *
     * Preconditions:
     *   - token != null and non-empty
     *   - token was previously issued by tokenize()
     *
     * Postconditions:
     *   - Returns the original CardData associated with the token
     *   - Access is audited (caller must have appropriate IAM permissions)
     *
     * @param token the opaque token to detokenize
     * @return the original CardData
     * @throws IllegalArgumentException if the token is unknown or invalid
     */
    CardData detokenize(String token);
}
