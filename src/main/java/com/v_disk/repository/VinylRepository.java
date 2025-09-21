package com.v_disk.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

import com.v_disk.model.Vinyl;

public interface VinylRepository  extends  MongoRepository<Vinyl, String>{
	List<Vinyl> findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(String title, String artist);

	@Query("{ 'isPrincipal': ?0 }")
	List<Vinyl> findByIsPrincipalTrue(boolean isPrincipal);
}
