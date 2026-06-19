package com.janne6565.stratabackend.services.core;

import com.janne6565.stratabackend.entity.AccessGrantEntity;
import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.action.CreateGrantRequest;
import com.janne6565.stratabackend.model.core.GrantResponse;
import com.janne6565.stratabackend.model.core.ScopeType;
import com.janne6565.stratabackend.model.exception.BadRequestException;
import com.janne6565.stratabackend.model.exception.NotFoundException;
import com.janne6565.stratabackend.repository.AccessGrantRepository;
import com.janne6565.stratabackend.repository.DatasourceRepository;
import com.janne6565.stratabackend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages access grants. Validates scope consistency (NAMESPACE ⇒ namespace only, DATABASE ⇒
 * datasource only) and the existence of the referenced user/datasource before persisting, so the DB
 * CHECK/FK constraints are never the first line of defence.
 */
@Service
@RequiredArgsConstructor
public class GrantService {

    private final AccessGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final DatasourceRepository datasourceRepository;

    @Transactional(readOnly = true)
    public List<GrantResponse> listForUser(UUID userId) {
        return grantRepository.findByUserId(userId).stream().map(GrantResponse::from).toList();
    }

    @Transactional
    public GrantResponse create(CreateGrantRequest request, UserEntity createdBy) {
        UserEntity user =
                userRepository
                        .findById(request.userId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "UserEntity not found: " + request.userId()));

        DatasourceEntity datasource = resolveScope(request);

        AccessGrantEntity grant =
                new AccessGrantEntity(
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
    private DatasourceEntity resolveScope(CreateGrantRequest request) {
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
                                                "DatasourceEntity not found: "
                                                        + request.datasourceId()));
            }
        };
    }
}
