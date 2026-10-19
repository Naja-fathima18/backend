package com.app.backend.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(name = "published_year", nullable = false)
    private int publishedYear;

    @Column(nullable = false)
    private boolean borrowed;

    @Version
    @Column(nullable = false)
    private long version;

    protected Book() {}

    public Book(String title, String author, String isbn, int publishedYear) {
        updateDetails(title, author, isbn, publishedYear);
    }

    public void updateDetails(String title, String author, String isbn, int publishedYear) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.publishedYear = publishedYear;
    }

    public boolean isAvailable() {
        return !borrowed;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public int getPublishedYear() {
        return publishedYear;
    }

    public long getVersion() {
        return version;
    }
}
