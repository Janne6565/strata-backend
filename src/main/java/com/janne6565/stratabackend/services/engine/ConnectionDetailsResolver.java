package com.janne6565.stratabackend.services.engine;

import com.janne6565.stratabackend.entity.DatasourceEntity;
import com.janne6565.stratabackend.model.core.ConnectionDetails;

/**
 * Resolves the live {@link ConnectionDetails} for a datasource (cluster-DNS host + on-demand
 * credentials). The seam between the browse/query service and the Kubernetes credential machinery
 * ({@code CredentialReader}) — letting the engine path be tested against a real database without
 * the cluster. Returned details are in-memory only, never persisted or logged (AUTH.md).
 */
public interface ConnectionDetailsResolver {

    ConnectionDetails resolve(DatasourceEntity datasource);
}
