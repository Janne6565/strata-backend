package com.janne6565.stratabackend.services.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.OAuthStateEntity;
import com.janne6565.stratabackend.repository.OAuthStateRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthStateStoreTest {

    @Mock private OAuthStateRepository repository;

    private OAuthStateStore store;

    @BeforeEach
    void setUp() {
        store = new OAuthStateStore(repository);
    }

    @Test
    void issueState_savesAndReturnsUuid() {
        when(repository.save(any(OAuthStateEntity.class))).thenAnswer(i -> i.getArgument(0));

        String state = store.issueState();

        assertThat(state).isNotBlank().matches("[0-9a-f-]{36}");
        verify(repository).save(any(OAuthStateEntity.class));
    }

    @Test
    void consumeState_unknownState_returnsFalseAndDeletesNothing() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        assertThat(store.consumeState("unknown")).isFalse();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void consumeState_validRecentState_returnsTrueAndDeletes() {
        when(repository.findById("abc"))
                .thenReturn(Optional.of(new OAuthStateEntity("abc", Instant.now())));

        assertThat(store.consumeState("abc")).isTrue();
        verify(repository).deleteById("abc");
    }

    @Test
    void consumeState_expiredState_returnsFalseButStillDeletes() {
        when(repository.findById("old"))
                .thenReturn(
                        Optional.of(new OAuthStateEntity("old", Instant.now().minusSeconds(3600))));

        assertThat(store.consumeState("old")).isFalse();
        // Consumed regardless — single-use even when expired.
        verify(repository).deleteById("old");
    }

    @Test
    void evictExpired_deletesOldEntries() {
        store.evictExpired();
        verify(repository).deleteExpiredBefore(any(Instant.class));
    }
}
