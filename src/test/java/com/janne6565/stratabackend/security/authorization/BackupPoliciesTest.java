package com.janne6565.stratabackend.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.model.core.Role;
import org.junit.jupiter.api.Test;

class BackupPoliciesTest {

    private final BackupPolicies policies = new BackupPolicies();

    @Test
    void ownerMayListAndDownload() {
        UserEntity owner = new UserEntity("owner", "hash", Role.OWNER);

        assertThat(policies.canListBackups(null, null, owner)).isTrue();
        assertThat(policies.canDownloadBackup(null, null, owner)).isTrue();
    }

    @Test
    void adminAndUserAreDenied() {
        UserEntity admin = new UserEntity("admin", "hash", Role.ADMIN);
        UserEntity user = new UserEntity("user", "hash", Role.USER);

        assertThat(policies.canListBackups(null, null, admin)).isFalse();
        assertThat(policies.canDownloadBackup(null, null, admin)).isFalse();
        assertThat(policies.canListBackups(null, null, user)).isFalse();
        assertThat(policies.canDownloadBackup(null, null, user)).isFalse();
    }
}
