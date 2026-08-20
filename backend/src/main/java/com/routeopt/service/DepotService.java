package com.routeopt.service;

import com.routeopt.domain.Depot;
import com.routeopt.domain.DepotRepository;
import com.routeopt.service.DepotResolver.ResolvedDepot;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved depots, so a dispatcher picks one instead of retyping an address every morning.
 *
 * <p>Saving geocodes once and stores the coordinates. Selecting a saved depot afterwards costs
 * nothing at all — which matters more than it sounds, because the geocoder is capped at one
 * request per second and every avoided call is a second off the dispatcher's morning.
 */
@Service
public class DepotService {

    private final DepotRepository repository;
    private final DepotResolver resolver;

    public DepotService(DepotRepository repository, DepotResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public List<Depot> findAll() {
        return repository.findAllMostRecentlyUsedFirst();
    }

    /** Geocodes the address and stores the result under {@code name}. */
    @Transactional
    public Depot save(String name, String address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A saved depot needs a name.");
        }
        String trimmed = name.trim();
        repository.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "A depot named \"%s\" already exists. Rename it or delete the old one."
                            .formatted(existing.getName()));
        });

        // Reuses the same ladder, cache and coordinate short-circuit the deliveries get. The label
        // is left null on purpose so the resolver hands back the geocoder's own display name - the
        // dispatcher's name for the depot is stored separately, and seeing where it actually landed
        // is the point of keeping both.
        ResolvedDepot resolved = resolver.resolve(address, null, null, null);
        return repository.save(new Depot(
                trimmed,
                address == null ? null : address.trim(),
                resolved.label(),
                resolved.coordinate().lat(),
                resolved.coordinate().lon()));
    }

    @Transactional
    public Depot update(Long id, String name, String address) {
        Depot depot = require(id);
        if (name != null && !name.isBlank()) {
            depot.setName(name.trim());
        }
        if (address != null && !address.equals(depot.getAddress())) {
            // A corrected address is the point of this endpoint, so re-resolve it.
            ResolvedDepot resolved = resolver.resolve(address, null, null, null);
            depot.setAddress(address.trim());
            depot.setNormalizedAddress(resolved.label());
            depot.setLat(resolved.coordinate().lat());
            depot.setLon(resolved.coordinate().lon());
        }
        return repository.save(depot);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
    }

    /**
     * Loads a saved depot for dispatch and records that it was used.
     *
     * <p>The timestamp is what keeps the list ordered by what the dispatcher actually reaches for,
     * rather than by whatever was typed first months ago.
     */
    @Transactional
    public ResolvedDepot useSaved(Long id) {
        Depot depot = require(id);
        depot.markUsed();
        repository.save(depot);
        return new ResolvedDepot(depot.coordinate(), depot.getName());
    }

    private Depot require(Long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No saved depot with id " + id));
    }
}
