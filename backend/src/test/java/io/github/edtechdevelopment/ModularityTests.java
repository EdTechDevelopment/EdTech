package io.github.edtechdevelopment;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(EdTechApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void detectsExpectedBusinessModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder(
                        "identity",
                        "notifications",
                        "scheduling",
                        "tutoring",
                        "workflows"
                );
    }
}
