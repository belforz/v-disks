package com.v_disk.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v_disk.dto.vinyl.VinylCreateDTO;
import com.v_disk.dto.vinyl.VinylUpdateDTO;
import com.v_disk.model.Vinyl;
import com.v_disk.repository.VinylRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vinyls")
public class VinylController {
    private final VinylRepository repo;

    public VinylController(VinylRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Vinyl> list() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Vinyl get(@PathVariable String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Vinyl create(@RequestBody @Valid VinylCreateDTO dto) {
        Vinyl v = new Vinyl();
        v.setTitle(dto.title());
        v.setArtist(dto.artist());
        v.setStock(dto.stock());
        v.setPrice(dto.price());
        v.setCoverPath(dto.coverPath());
        v.setGallery(dto.gallery());
        return repo.save(v);
    }

    @PatchMapping("/{id}")
    public Vinyl update(@PathVariable String id, @RequestBody @Valid VinylUpdateDTO dto){
        Vinyl v = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found"));
        if(dto.title() !=null) {v.setTitle(dto.title());}
        if(dto.artist() !=null) {v.setArtist(dto.artist());}
        if(dto.stock() !=null) {v.setStock(dto.stock());}
        if(dto.price() !=null) {v.setPrice(dto.price());}
        if(dto.coverPath() !=null) {v.setCoverPath(dto.coverPath());}
        if(dto.gallery() !=null) {v.setGallery(dto.gallery());}
        v.setUpdatedAt(Instant.now());

        return repo.save(v);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found");
        }
        repo.deleteById(id);
    }

}
