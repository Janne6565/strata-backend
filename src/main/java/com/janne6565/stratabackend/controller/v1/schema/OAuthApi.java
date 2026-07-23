package com.janne6565.stratabackend.controller.v1.schema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Federated OIDC login contract ("Login with Authentik"). Both endpoints drive top-level browser
 * navigation and answer with 302 redirects — never JSON — so they are invoked by full navigation,
 * not the Orval/axios client.
 */
@Tag(name = "OAuth", description = "Federated OIDC login (Authentik)")
@RequestMapping("/v1/auth/oauth")
public interface OAuthApi {

    @Operation(
            summary = "Start the OIDC authorization-code flow",
            description = "Redirects the browser to the provider's authorization page.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "302",
                description = "Redirect to the provider authorization URL")
    })
    @GetMapping("/{provider}/authorize")
    ResponseEntity<Void> authorize(@PathVariable String provider);

    @Operation(
            summary = "Handle the OIDC callback",
            description =
                    "Validates state, exchanges the code, resolves or creates the local user, sets"
                            + " the refresh cookie, and redirects to the frontend (home on success,"
                            + " /login?oauthError=noAccess|true on failure).")
    @ApiResponses({@ApiResponse(responseCode = "302", description = "Redirect to the frontend")})
    @GetMapping("/{provider}/callback")
    ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state);
}
