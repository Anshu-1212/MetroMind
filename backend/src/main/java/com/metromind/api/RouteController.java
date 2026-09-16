package com.metromind.api;

import com.metromind.api.dto.RouteRequest;
import com.metromind.api.dto.RouteResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The routing endpoint of the MetroMind backend.
 *
 * <p>This controller stays thin: it only accepts the request and delegates to
 * {@link RouteService}, which validates, selects the algorithm, executes the real
 * router, and converts the result. No routing logic lives here.</p>
 */
@RestController
@RequestMapping("/api/routes")
public final class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    /**
     * Computes a route between two stations using one of the existing Java
     * algorithms.
     *
     * @param request the route request
     * @return the route result (HTTP 200), or a {@code 400}/ {@code 404}
     *         {@code ApiError} body for invalid requests
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public RouteResponse route(@RequestBody RouteRequest request) {
        return routeService.route(request);
    }
}