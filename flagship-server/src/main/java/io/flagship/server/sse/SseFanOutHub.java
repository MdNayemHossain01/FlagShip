package io.flagship.server.sse;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SseFanOutHub {
    private final Map<String, Set<SseEmitter>> channels = new ConcurrentHashMap<>();
    private final ExecutorService virtualFanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public SseEmitter subscribe(String projectKey, String env, String initialPayloadJson) {
        return registerEmitter(projectKey, env, new SseEmitter(0L), initialPayloadJson);
    }

    public SseEmitter registerEmitter(String projectKey, String env, SseEmitter emitter, String initialPayloadJson) {
        String key = projectKey + ":" + env;
        Set<SseEmitter> emitters = channels.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        if (initialPayloadJson != null) {
            try {
                emitter.send(SseEmitter.event().name("snapshot").data(initialPayloadJson));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
        return emitter;
    }

    public void broadcastSnapshot(String projectKey, String env, String snapshotJson) {
        Set<SseEmitter> emitters = channels.getOrDefault(projectKey + ":" + env, Set.of());
        for (SseEmitter emitter : emitters) {
            virtualFanOutExecutor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event().name("snapshot").data(snapshotJson));
                } catch (Exception ex) {
                    emitter.complete();
                    emitters.remove(emitter);
                }
            });
        }
    }

    public int getActiveSubscriberCount() {
        return channels.values().stream().mapToInt(Set::size).sum();
    }

    public int getActiveSubscriberCount(String projectKey, String env) {
        Set<SseEmitter> set = channels.get(projectKey + ":" + env);
        return set != null ? set.size() : 0;
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        channels.values().forEach(emitters -> {
            for (SseEmitter emitter : emitters) {
                virtualFanOutExecutor.submit(() -> {
                    try {
                        emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                    } catch (Exception ex) {
                        emitter.complete();
                        emitters.remove(emitter);
                    }
                });
            }
        });
    }
}
