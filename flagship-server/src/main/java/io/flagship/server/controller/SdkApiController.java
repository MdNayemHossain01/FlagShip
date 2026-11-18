package io.flagship.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.model.FlagDefinition;
import io.flagship.server.entity.ExposureEvent;
import io.flagship.server.entity.GoalEvent;
import io.flagship.server.repository.ExposureEventRepository;
import io.flagship.server.repository.GoalEventRepository;
import io.flagship.server.service.FlagService;
import io.flagship.server.sse.SseFanOutHub;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sdk")
@CrossOrigin(origins = "*")
public class SdkApiController {

    private final FlagService flagService;
    private final SseFanOutHub sseHub;
    private final ExposureEventRepository exposureRepo;
    private final GoalEventRepository goalRepo;
    private final ObjectMapper objectMapper;

    public SdkApiController(FlagService flagService, SseFanOutHub sseHub,
                            ExposureEventRepository exposureRepo, GoalEventRepository goalRepo,
                            ObjectMapper objectMapper) {
        this.flagService = flagService;
        this.sseHub = sseHub;
        this.exposureRepo = exposureRepo;
        this.goalRepo = goalRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/snapshot")
    public Map<String, Object> getSnapshot(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env) {
        Map<String, Object> snapshot = flagService.getSnapshot(project, env);

        // Record evaluation timestamp for hygiene tracking
        @SuppressWarnings("unchecked")
        Map<String, FlagDefinition> flags = (Map<String, FlagDefinition>) snapshot.get("flags");
        if (flags != null) {
            flags.keySet().forEach(flagService::recordEvaluation);
        }

        return snapshot;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFlags(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env) throws Exception {
        Map<String, Object> snapshot = flagService.getSnapshot(project, env);
        String initialJson = objectMapper.writeValueAsString(snapshot);
        return sseHub.subscribe(project, env, initialJson);
    }

    @PostMapping("/events")
    public Map<String, Object> ingestEvents(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestBody List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return Map.of("status", "ok", "ingested", 0);
        }

        int count = 0;
        for (Map<String, Object> event : events) {
            String type = (String) event.get("type");
            String flagKey = (String) event.get("flagKey");
            String userKey = (String) event.get("userKey");
            String variation = (String) event.get("variation");

            flagService.recordEvaluation(flagKey);

            if ("EXPOSURE".equalsIgnoreCase(type)) {
                exposureRepo.save(new ExposureEvent(project, env, flagKey, variation, userKey, Instant.now()));
                count++;
            } else if ("GOAL".equalsIgnoreCase(type)) {
                String goalName = (String) event.get("goalName");
                double val = event.containsKey("value") ? ((Number) event.get("value")).doubleValue() : 1.0;
                goalRepo.save(new GoalEvent(project, env, flagKey, goalName, variation, userKey, val, Instant.now()));
                count++;
            }
        }

        return Map.of("status", "ok", "ingested", count);
    }
}
