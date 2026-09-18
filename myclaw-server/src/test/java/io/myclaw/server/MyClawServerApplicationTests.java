package io.myclaw.server;

import io.myclaw.server.service.AgentSessionManager;
import io.myclaw.spring.factory.AgentFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class MyClawServerApplicationTests {

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private AgentSessionManager agentSessionManager;

    @Test
    void createsAgentInfrastructure() {
        assertNotNull(agentFactory);
        assertNotNull(agentSessionManager);
    }
}
