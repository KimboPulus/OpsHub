package pl.fortaco.opshub.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.fortaco.opshub.service.OperationsEventService;

@RestController
public class OperationsEventsController {
    private final OperationsEventService events;

    public OperationsEventsController(OperationsEventService events) {
        this.events = events;
    }

    @GetMapping(value = "/api/events/operations", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter operations() {
        return events.subscribe();
    }
}
