package com.janne6565.stratabackend.services.engine.mongo;

import com.janne6565.stratabackend.model.core.ColumnInfo;
import com.janne6565.stratabackend.model.core.ConnectionDetails;
import com.janne6565.stratabackend.model.core.ObjectRef;
import com.janne6565.stratabackend.model.core.QueryMode;
import com.janne6565.stratabackend.model.core.QueryResult;
import com.janne6565.stratabackend.model.core.RowPage;
import com.janne6565.stratabackend.model.core.SchemaInfo;
import com.janne6565.stratabackend.model.core.TableInfo;
import com.janne6565.stratabackend.model.exception.EngineException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the MongoDB adapter against a real (Testcontainers) target database. */
@Testcontainers
class MongoEngineTest {

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    private final MongoEngine engine = new MongoEngine();

    private ConnectionDetails details() {
        return new ConnectionDetails(
                "mongodb", MONGO.getHost(), MONGO.getFirstMappedPort(), "testdb", null, null);
    }

    @BeforeAll
    static void seed() {
        try (MongoClient client = MongoClients.create(MONGO.getConnectionString())) {
            client.getDatabase("testdb")
                    .getCollection("customer")
                    .insertMany(
                            List.of(
                                    new Document("_id", 1).append("name", "Ada"),
                                    new Document("_id", 2).append("name", "Linus"),
                                    new Document("_id", 3).append("name", "Grace")));
        }
    }

    @Test
    void introspectListsCollectionWithInferredColumns() {
        SchemaInfo schema = engine.introspect(details());

        TableInfo customer =
                schema.tables().stream()
                        .filter(t -> t.name().equals("customer"))
                        .findFirst()
                        .orElseThrow();
        assertThat(customer.columns()).extracting(ColumnInfo::name).contains("_id", "name");
        ColumnInfo id =
                customer.columns().stream()
                        .filter(col -> col.name().equals("_id"))
                        .findFirst()
                        .orElseThrow();
        assertThat(id.primaryKey()).isTrue();
    }

    @Test
    void browseReturnsDocuments() {
        RowPage page = engine.browse(details(), new ObjectRef("testdb", "customer"), 0, 2);

        assertThat(page.columns()).contains("_id", "name");
        assertThat(page.rows()).hasSize(2);
    }

    @Test
    void readFindReturnsRows() {
        QueryResult result =
                engine.runQuery(
                        details(),
                        "{ \"find\": \"customer\", \"filter\": {}, \"sort\": { \"name\": 1 } }",
                        QueryMode.READ);

        assertThat(result.columns()).contains("_id", "name");
        assertThat(result.rows()).hasSize(3);
        assertThat(result.rows().get(0).get(1)).isEqualTo("Ada");
    }

    @Test
    void readModeRejectsWriteCommand() {
        assertThatThrownBy(
                        () ->
                                engine.runQuery(
                                        details(),
                                        "{ \"insert\": \"customer\", \"documents\": [{ \"name\": \"Mallory\" }] }",
                                        QueryMode.READ))
                .isInstanceOf(EngineException.class);
    }

    @Test
    void writeModeRunsWriteCommand() {
        QueryResult result =
                engine.runQuery(
                        details(),
                        "{ \"insert\": \"customer\", \"documents\": [{ \"_id\": 4, \"name\": \"Dennis\" }] }",
                        QueryMode.WRITE);

        assertThat(result.rows()).isNotEmpty();
        assertThat(result.columns()).contains("n");
    }
}
