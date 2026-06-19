package com.janne6565.stratabackend.grant;

import com.janne6565.stratabackend.auth.User;
import com.janne6565.stratabackend.auth.UserRepository;
import com.janne6565.stratabackend.catalog.Datasource;
import com.janne6565.stratabackend.catalog.DatasourceRepository;
import com.janne6565.stratabackend.common.BadRequestException;
import com.janne6565.stratabackend.common.NotFoundException;
import com.janne6565.stratabackend.grant.dto.CreateGrantRequest;
import com.janne6565.stratabackend.grant.dto.GrantResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages access grants. Validates scope consistency (NAMESPACE ⇒ namespace only, DATABASE ⇒
 * datasource only) and the existence of the referenced user/datasource before persisting, so the
 * DB CHECK/FK constraints are never the first line of defence.
 */
@Service
public class GrantService {

    private final AccessGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final DatasourceRepository datasourceRepository;

    public GrantService(
            AccessGrantRepository grantRepository,
            UserRepository userRepository,
            DatasourceRepository datasourceRepository) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.datasourceRepository = datasourceRepository;
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> listForUser(UUID userId) {
        return grantRepository.findByUserId(userId).stream().map(GrantResponse::from).toList();
    }

    @Transactional
    public GrantResponse create(CreateGrantRequest request, User createdBy) {
        User user =
                userRepository
                        .findById(request.userId())
                        .orElseThrow(
                                () -> new NotFoundException("User not found: " + request.userId()));

        Datasource datasource = resolveScope(request);

        AccessGrant grant =
                new AccessGrant(
                        user,
                        request.scopeType(),
                        request.scopeType() == ScopeType.NAMESPACE ? request.namespace() : null,
                        datasource,
                        request.readOnly(),
                        createdBy);
        return GrantResponse.from(grantRepository.save(grant));
    }

    @Transactional
    public void revoke(UUID grantId) {
        if (!grantRepository.existsById(grantId)) {
            throw new NotFoundException("Grant not found: " + grantId);
        }
        grantRepository.deleteById(grantId);
    }

    /** Validates the scope fields and, for DATABASE scope, loads (and returns) the datasource. */
    private Datasource resolveScope(CreateGrantRequest request) {
        return switch (request.scopeType()) {
            case NAMESPACE -> {
                if (!StringUtils.hasText(request.namespace())) {
                    throw new BadRequestException("A NAMESPACE grant requires a namespace");
                }
                if (request.datasourceId() != null) {
                    throw new BadRequestException("A NAMESPACE grant must not target a datasource");
                }
                yield null;
            }
            case DATABASE -> {
                if (request.datasourceId() == null) {
                    throw new BadRequestException("A DATABASE grant requires a datasourceId");
                }
                yield datasourceRepository
                        .findById(request.datasourceId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Datasource not found: " + request.datasourceId()));
            }
        };
    }
}
