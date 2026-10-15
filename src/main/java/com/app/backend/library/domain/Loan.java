package com.app.backend.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "member_name", nullable = false, length = 100)
    private String memberName;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Loan() {}

    public Loan(Book book, String memberName, Instant borrowedAt) {
        this.book = book;
        this.memberName = memberName;
        this.borrowedAt = borrowedAt;
    }

    public void close(Instant at) {
        this.returnedAt = at;
    }

    public boolean isOpen() {
        return returnedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return book.getId();
    }

    public String getMemberName() {
        return memberName;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
