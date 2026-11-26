package org.symphonykernel.steps;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DatabaseQueryProgressTest {
    private static final String QUERY = "SELECT id\nFROM inventory";
    private final DatabaseStep step = new DatabaseStep();
    private final List<String> messages = new ArrayList<>();

    @Test
    void hidesSqlByDefault() {
        step.reportExecutingQuery(QUERY, messages::add);
        assertThat(messages).containsExactly("Executing database query...");
    }

    @Test
    void includesSqlWhenEnabled() {
        ReflectionTestUtils.setField(step, "streamQuery", true);
        step.reportExecutingQuery(QUERY, messages::add);
        assertThat(messages).containsExactly("Executing database query:\n" + QUERY);
    }

    @Test
    void doesNotEmitRejectedSql() {
        ReflectionTestUtils.setField(step, "streamQuery", true);
        assertThatThrownBy(() -> step.reportExecutingQuery("DELETE FROM inventory", messages::add))
                .isInstanceOf(SecurityException.class);
        assertThat(messages).isEmpty();
    }
}