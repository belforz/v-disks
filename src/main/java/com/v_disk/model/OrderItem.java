package com.v_disk.model;

import java.math.BigDecimal;

public class OrderItem {
    private String vinylId;
    private Integer quantity = 1;
    private String title;
    private String artist;
    private BigDecimal price;
    private String coverPath;

    public String getVinylId() {
        return vinylId;
    }

    public void setVinylId(String vinylId) {
        this.vinylId = vinylId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity != null && quantity > 0 ? quantity : 1;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCoverPath() {
        return coverPath;
    }

    public void setCoverPath(String coverPath) {
        this.coverPath = coverPath;
    }
}
