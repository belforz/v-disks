package com.v_disk.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "vinyls")
public class Vinyl {
    @Id
    private String id;
    private String title;
    private String artist;
    private BigDecimal price;
    private Integer stock;
    private String coverPath;
    private List<String> gallery;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }
    public String setId(String id) {
        this.id = id;
        return id;
    }
    public String getTitle(){
        return title;
    }
    public String setTitle(String title) {
        this.title = title;
        return title;
    }
    public String getArtist(){
        return artist;
    }
    public String setArtist(String artist){
        this.artist = artist;
        return artist;
    }

    public BigDecimal getPrice(){
        return price;
    }
    public BigDecimal setPrice(BigDecimal price) {
        this.price = price;
        return price;
    }

    public Integer getStock() {
        return stock;
    }
    public Integer setStock(Integer stock) {
        this.stock = stock;
        return stock;
    }
    public String getCoverPath() {
        return coverPath;
    }
    public String setCoverPath(String coverPath) {
        this.coverPath = coverPath;
        return coverPath;
    }

    public List<String> getGallery() {
        return gallery;
    }
    public List<String> setGallery(List<String> gallery) {
        this.gallery = gallery;
        return gallery;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public Instant setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return updatedAt;
    }

}
