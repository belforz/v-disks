package com.v_disk.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.v_disk.model.Vinyl;

public interface VinylRepository  extends  MongoRepository<Vinyl, String>{
	java.util.List<Vinyl> findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(String title, String artist);
}
