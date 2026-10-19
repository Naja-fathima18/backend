package com.app.backend.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false, updatable = false)
    private Long bookId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private String memberId;

    @Column(name = "borrowed_at", nullable = false, updatable = false)
    private Instant borrowedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected Loan() {}

    private Loan(Long bookId, String memberId, Instant borrowedAt) {
        this.bookId = bookId;
        this.memberId = memberId;
        this.borrowedAt = borrowedAt;
    }

    public static Loan open(Long bookId, String memberId, Instant borrowedAt) {
        return new Loan(bookId, memberId, borrowedAt);
    }

    public void close(Instant returnedAt) {
        if (this.returnedAt != null) {
            throw new BookNotBorrowedException(bookId);
        }
        this.returnedAt = returnedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getMemberId() {
        return memberId;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
