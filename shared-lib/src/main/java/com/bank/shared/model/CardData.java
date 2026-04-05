package com.bank.shared.model;

/**
 * CardData
 *
 * Represents raw payment card data used exclusively for tokenization.
 * This object must NEVER be persisted or logged — it is only passed to
 * CardTokenizationService.tokenize() and then discarded.
 *
 * Satisfies Requirement 12.6: no raw card data is stored in plain text.
 */
public record CardData(
        String pan,           // Primary Account Number (16-digit card number)
        String expiryMonth,   // MM format
        String expiryYear,    // YYYY format
        String cardholderName
) {

    /**
     * Ensures PAN is never exposed in logs or toString output.
     */
    @Override
    public String toString() {
        return "CardData[pan=****-****-****-" + (pan != null && pan.length() >= 4
                ? pan.substring(pan.length() - 4) : "????") + "]";
    }
}
