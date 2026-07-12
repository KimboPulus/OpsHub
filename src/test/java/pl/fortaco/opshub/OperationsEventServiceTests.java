package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import pl.fortaco.opshub.service.OperationsEventService;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationsEventServiceTests {
    @Test
    void subscribeRegistersLiveClient() {
        OperationsEventService events = new OperationsEventService();

        events.subscribe();

        assertEquals(1, events.activeSubscribers());
    }

    @Test
    void publishWithoutSubscribersIsSafe() {
        OperationsEventService events = new OperationsEventService();

        events.publish("issue.created", "Issue created.");

        assertEquals(0, events.activeSubscribers());
    }
}
