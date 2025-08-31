package com.v_disk.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.v_disk.dto.vinyl.VinylCreateDTO;
import com.v_disk.dto.vinyl.VinylUpdateDTO;
import com.v_disk.model.Vinyl;
import com.v_disk.repository.VinylRepository;
import com.v_disk.utils.ResponseJSON;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vinyls")
public class VinylController {
    private final VinylRepository repo;

    public VinylController(VinylRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<ResponseJSON<List<Vinyl>>> list() {
        List<Vinyl> all = repo.findAll();
        return ResponseEntity.ok(new ResponseJSON<>("Listed successfully", all));
    }

    @GetMapping("/search")
    public ResponseEntity<ResponseJSON<List<Vinyl>>> search(@RequestParam("term") String term) {
        List<Vinyl> result = repo.findByTitleContainingIgnoreCaseOrArtistContainingIgnoreCase(term, term);
        return ResponseEntity.ok(new ResponseJSON<>("Search results", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseJSON<Vinyl>> get(@PathVariable String id) {
        Vinyl v = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found"));
        return ResponseEntity.ok(new ResponseJSON<>("Listed one successfully", v));
    }

    @GetMapping("/principal")
    public ResponseEntity<ResponseJSON<Vinyl>> getPrincipal(@RequestParam String vinylId,
            @RequestParam boolean isPrincipal) {
        if (isPrincipal) {
            Vinyl v = repo.findById(vinylId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found"));
            return ResponseEntity.ok(new ResponseJSON<>("Principal vinyl found", v));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vinyl is not principal");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ResponseJSON<Vinyl>> create(@RequestBody @Valid VinylCreateDTO dto) {
        Vinyl v = new Vinyl();
        v.setTitle(dto.title());
        v.setArtist(dto.artist());
        v.setStock(dto.stock());
        v.setPrice(dto.price());
        v.setCoverPath(dto.coverPath());
        v.setGallery(dto.gallery());
        v.setIsPrincipal(Boolean.FALSE);
        Vinyl saved = repo.save(v);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ResponseJSON<>("Created Successfully", saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ResponseJSON<Vinyl>> update(@PathVariable String id, @RequestBody @Valid VinylUpdateDTO dto) {
        Vinyl v = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found"));
        if (dto.title() != null) {
            v.setTitle(dto.title());
        }
        if (dto.artist() != null) {
            v.setArtist(dto.artist());
        }
        if (dto.stock() != null) {
            v.setStock(dto.stock());
        }
        if (dto.price() != null) {
            v.setPrice(dto.price());
        }
        if (dto.coverPath() != null) {
            v.setCoverPath(dto.coverPath());
        }
        if (dto.gallery() != null) {
            v.setGallery(dto.gallery());
        }
        if (dto.isPrincipal() != null) {
            v.setIsPrincipal(dto.isPrincipal());
        }

        v.setUpdatedAt(Instant.now());

        Vinyl saved = repo.save(v);
        return ResponseEntity.ok(new ResponseJSON<>("Edited Successfully", saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseJSON<String>> delete(@PathVariable String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinyl not found");
        }
        repo.deleteById(id);
        return ResponseEntity.ok(new ResponseJSON<>("Deleted Successfully", id));
    }

}
