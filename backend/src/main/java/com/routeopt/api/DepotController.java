package com.routeopt.api;

import com.routeopt.api.Dtos.DepotResponse;
import com.routeopt.api.Dtos.SaveDepotRequest;
import com.routeopt.service.DepotService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/depots")
public class DepotController {

    private final DepotService depots;

    public DepotController(DepotService depots) {
        this.depots = depots;
    }

    /** Most recently used first, so the list matches what the dispatcher reaches for. */
    @GetMapping
    public List<DepotResponse> list() {
        return depots.findAll().stream().map(DepotResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepotResponse save(@Valid @RequestBody SaveDepotRequest request) {
        return DepotResponse.from(depots.save(request.name(), request.address()));
    }

    @PatchMapping("/{id}")
    public DepotResponse update(@PathVariable Long id, @RequestBody SaveDepotRequest request) {
        return DepotResponse.from(depots.update(id, request.name(), request.address()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        depots.delete(id);
    }
}
