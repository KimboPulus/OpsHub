package pl.fortaco.opshub.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.fortaco.opshub.model.ProductionIssue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OperationsEventService {
    private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        send(emitter, new OperationsEvent("stream.connected", null, "Connected to OpsHub live operations stream.", Instant.now()));
        return emitter;
    }

    public void publishIssueEvent(String type, ProductionIssue issue) {
        publish(new OperationsEvent(
            type,
            issue.getId(),
            issue.getTitle(),
            Instant.now()));
    }

    public void publish(String type, String message) {
        publish(new OperationsEvent(type, null, message, Instant.now()));
    }

    public int activeSubscribers() {
        return subscribers.size();
    }

    private void publish(OperationsEvent event) {
        subscribers.forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, OperationsEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .name("operations")
                .data(Map.of(
                    "type", event.type(),
                    "issueId", event.issueId() == null ? "" : event.issueId(),
                    "message", event.message(),
                    "occurredAt", event.occurredAt().toString()
                )));
        } catch (IOException | IllegalStateException ex) {
            subscribers.remove(emitter);
        }
    }

    public record OperationsEvent(String type, Integer issueId, String message, Instant occurredAt) {
    }
}
