package com.routeopt.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    /**
     * Most recently used first, then never-used by newest.
     *
     * <p>Written as JPQL because the derived-name grammar has no way to express NULLS LAST, and
     * that part is load-bearing: a depot never dispatched from should not outrank one used this
     * morning just because its {@code lastUsedAt} is null.
     */
    @Query("select d from Depot d order by d.lastUsedAt desc nulls last, d.createdAt desc")
    List<Depot> findAllMostRecentlyUsedFirst();

    Optional<Depot> findByNameIgnoreCase(String name);
}
