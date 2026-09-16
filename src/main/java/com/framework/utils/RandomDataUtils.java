package com.framework.utils;

import java.security.SecureRandom;

/**
 * Generates random, non-sensitive test data — primarily Indian mobile numbers for flows that need
 * a phone number guaranteed not to already exist in the target environment (e.g. new-user
 * signup), so tests don't repeatedly hammer OTP delivery for one fixed test account and risk
 * rate-limiting it.
 */
public final class RandomDataUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] VALID_FIRST_DIGITS = {'6', '7', '8', '9'};

    private RandomDataUtils() {
    }

    /** A random syntactically-valid 10-digit Indian mobile number (starts with 6-9). */
    public static String randomIndianMobileNumber() {
        StringBuilder sb = new StringBuilder(10);
        sb.append(VALID_FIRST_DIGITS[RANDOM.nextInt(VALID_FIRST_DIGITS.length)]);
        for (int i = 0; i < 9; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** A random-looking but non-existent email address for new-account signup flows. */
    public static String randomEmail() {
        return "furlenco.qa." + System.currentTimeMillis() + "." + RANDOM.nextInt(10000) + "@example.com";
    }

    /** A random display name for new-account signup flows. */
    public static String randomName() {
        String[] names = {"Asha", "Rohan", "Kavya", "Vikram", "Meera", "Arjun", "Priya", "Karan"};
        return names[RANDOM.nextInt(names.length)] + " QA" + RANDOM.nextInt(1000);
    }
}
