/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.carried.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Partitions: the copy lost nothing, some, all, or grew; the cell holds more than the loss,
 * exactly it, less, nothing; bad arguments. */
final class LoanTest {
    @Test void theStorePaysWhatTheCopyLost() {
        assertEquals(0, Loan.debit(16, 16, 16), "nothing lost (a lookup the weapon never used)");
        assertEquals(1, Loan.debit(16, 15, 16), "one shot");
        assertEquals(16, Loan.debit(16, 0, 16), "the whole copy");
        assertEquals(0, Loan.debit(3, 5, 16), "a copy that grew owes nothing");
    }
    @Test void neverMoreThanTheCellHolds() {
        assertEquals(2, Loan.debit(16, 14, 2), "exactly what is left");
        assertEquals(1, Loan.debit(16, 10, 1), "less than the loss");
        assertEquals(0, Loan.debit(16, 10, 0), "the cell was emptied meanwhile");
    }
    @Test void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Loan.debit(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Loan.debit(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Loan.debit(0, 0, -1));
    }
}
