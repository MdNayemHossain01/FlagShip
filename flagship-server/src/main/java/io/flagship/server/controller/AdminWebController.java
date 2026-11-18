package io.flagship.server.controller;

import io.flagship.core.context.EvaluationContext;
import io.flagship.core.engine.FlagEvaluator;
import io.flagship.core.model.FlagDefinition;
import io.flagship.core.model.FlagType;
import io.flagship.server.analytics.ExperimentService;
import io.flagship.server.entity.*;
import io.flagship.server.hygiene.FlagHygieneScanner;
import io.flagship.server.repository.*;
import io.flagship.server.service.FlagService;
import io.flagship.server.service.ScheduledChangeService;
import io.flagship.server.sse.SseFanOutHub;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminWebController {

    private final FeatureFlagRepository flagRepo;
    private final FlagEnvironmentConfigRepository configRepo;
    private final ProjectRepository projectRepo;
    private final EnvironmentRepository envRepo;
    private final FlagVersionRepository versionRepo;
    private final ApprovalRequestRepository approvalRepo;
    private final UserAccountRepository userRepo;
    private final FlagService flagService;
    private final ExperimentService experimentService;
    private final FlagHygieneScanner hygieneScanner;
    private final SseFanOutHub sseHub;
    private final ScheduledChangeService scheduledService;
    private final ScheduledChangeRepository scheduledRepo;
    private final FlagEvaluator evaluator = new FlagEvaluator();

    public AdminWebController(
            FeatureFlagRepository flagRepo,
            FlagEnvironmentConfigRepository configRepo,
            ProjectRepository projectRepo,
            EnvironmentRepository envRepo,
            FlagVersionRepository versionRepo,
            ApprovalRequestRepository approvalRepo,
            UserAccountRepository userRepo,
            FlagService flagService,
            ExperimentService experimentService,
            FlagHygieneScanner hygieneScanner,
            SseFanOutHub sseHub,
            ScheduledChangeService scheduledService,
            ScheduledChangeRepository scheduledRepo) {
        this.flagRepo = flagRepo;
        this.configRepo = configRepo;
        this.projectRepo = projectRepo;
        this.envRepo = envRepo;
        this.versionRepo = versionRepo;
        this.approvalRepo = approvalRepo;
        this.userRepo = userRepo;
        this.flagService = flagService;
        this.experimentService = experimentService;
        this.hygieneScanner = hygieneScanner;
        this.sseHub = sseHub;
        this.scheduledService = scheduledService;
        this.scheduledRepo = scheduledRepo;
    }

    private void addCommonAttributes(Model model, String projectKey, String envKey, Authentication auth) {
        model.addAttribute("currentProject", projectKey);
        model.addAttribute("currentEnv", envKey);
        model.addAttribute("projects", projectRepo.findAll());
        model.addAttribute("environments", envRepo.findByProjectKey(projectKey));
        model.addAttribute("pendingApprovalsCount", approvalRepo.countByStatus(ApprovalStatus.PENDING));
        model.addAttribute("staleFlagsCount", hygieneScanner.scanForStaleFlags().size());
        model.addAttribute("activeSdkClients", sseHub.getActiveSubscriberCount());
        if (auth != null) {
            model.addAttribute("currentUser", auth.getName());
            model.addAttribute("currentRole", auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        }
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);

        List<FeatureFlag> flags = flagRepo.findByProjectKeyAndArchivedFalse(project);
        List<FlagEnvironmentConfig> configs = configRepo.findByEnvironmentProjectKeyAndEnvironmentKey(project, env);

        long activeCount = configs.stream().filter(FlagEnvironmentConfig::isEnabled).count();
        long disabledCount = configs.size() - activeCount;
        long rolloutCount = configs.stream().filter(c -> c.isEnabled() && c.getRolloutPercentage() > 0 && c.getRolloutPercentage() < 100).count();

        model.addAttribute("totalProjects", projectRepo.count());
        model.addAttribute("totalFlags", flags.size());
        model.addAttribute("activeFlags", activeCount);
        model.addAttribute("disabledFlags", disabledCount);
        model.addAttribute("percentageRollouts", rolloutCount);
        model.addAttribute("flags", flags);
        model.addAttribute("configs", configs);
        model.addAttribute("recentVersions", versionRepo.findTop50ByOrderByCreatedAtDesc());

        return "dashboard";
    }

    @GetMapping("/flags")
    public String flagsList(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("flags", flagRepo.findByProjectKeyAndArchivedFalse(project));
        model.addAttribute("configs", configRepo.findByEnvironmentProjectKeyAndEnvironmentKey(project, env));

        return "flags";
    }

    @GetMapping("/flags/{key}")
    public String flagDetail(
            @PathVariable String key,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);

        FeatureFlag flag = flagRepo.findByProjectKeyAndKey(project, key)
            .orElseThrow(() -> new IllegalArgumentException("Flag not found: " + key));

        FlagEnvironmentConfig config = configRepo.findByFlagKeyAndEnvironmentKey(key, env)
            .orElseGet(() -> {
                Environment targetEnv = envRepo.findByProjectKeyAndKey(project, env).orElseThrow();
                return configRepo.save(new FlagEnvironmentConfig(flag, targetEnv));
            });

        model.addAttribute("flag", flag);
        model.addAttribute("config", config);
        model.addAttribute("versions", versionRepo.findByProjectKeyAndEnvironmentKeyAndFlagKeyOrderByVersionNumberDesc(project, env, key));
        model.addAttribute("scheduledChanges", scheduledRepo.findByProjectKeyAndEnvironmentKeyOrderByScheduledTimeDesc(project, env));

        return "flag-detail";
    }

    @PostMapping("/flags/{key}/schedule-ui")
    public String scheduleChangeUi(
            @PathVariable String key,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam String scheduledTime,
            @RequestParam(required = false) Integer rolloutPercentage,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "Scheduled release") String reason,
            Authentication auth, RedirectAttributes ra) {
        try {
            String author = auth != null ? auth.getName() : "admin";
            Instant time = Instant.parse(scheduledTime);
            Map<String, Object> payload = new HashMap<>();
            if (rolloutPercentage != null) payload.put("rolloutPercentage", rolloutPercentage);
            if (enabled != null) payload.put("enabled", enabled);
            payload.put("reason", reason);

            scheduledService.scheduleChange(project, env, key, author, time, payload);
            ra.addFlashAttribute("successMessage", "Change successfully scheduled for " + scheduledTime + "!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to schedule change: " + e.getMessage());
        }
        return "redirect:/flags/" + key + "?project=" + project + "&env=" + env;
    }

    @PostMapping("/flags/{key}/toggle-ui")
    public String toggleKillSwitchUi(
            @PathVariable String key,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam(defaultValue = "UI Toggle") String reason,
            Authentication auth, RedirectAttributes ra) {

        try {
            String author = auth != null ? auth.getName() : "admin";
            boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean applied = flagService.toggleKillSwitch(project, env, key, enabled, author, reason, isAdmin);

            if (applied) {
                ra.addFlashAttribute("successMessage", "Flag '" + key + "' was successfully " + (enabled ? "ENABLED" : "KILL-SWITCHED") + "!");
            } else {
                ra.addFlashAttribute("warningMessage", "Production approval request submitted for '" + key + "'! Pending reviewer approval.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to update flag: " + e.getMessage());
        }

        return "redirect:/flags/" + key + "?project=" + project + "&env=" + env;
    }

    @PostMapping("/flags/{key}/update-targeting")
    public String updateTargetingUi(
            @PathVariable String key,
            @RequestParam int rolloutPercentage,
            @RequestParam String defaultVariation,
            @RequestParam String offVariation,
            @RequestParam(required = false) String individualTargetsJson,
            @RequestParam(required = false) String rulesJson,
            @RequestParam(defaultValue = "Targeting updated") String changeReason,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Authentication auth, RedirectAttributes ra) {

        try {
            String author = auth != null ? auth.getName() : "admin";
            boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            boolean applied = flagService.updateFlagConfig(
                project, env, key, rolloutPercentage, defaultVariation, offVariation,
                individualTargetsJson, rulesJson, author, changeReason, isAdmin
            );

            if (applied) {
                ra.addFlashAttribute("successMessage", "Targeting rules for '" + key + "' updated successfully!");
            } else {
                ra.addFlashAttribute("warningMessage", "Targeting change submitted for Production Approval! Awaiting reviewer confirmation.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Error updating targeting: " + e.getMessage());
        }

        return "redirect:/flags/" + key + "?project=" + project + "&env=" + env;
    }

    @PostMapping("/flags/create")
    public String createFlagUi(
            @RequestParam String key,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "BOOLEAN") String type,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Authentication auth, RedirectAttributes ra) {
        try {
            String owner = auth != null ? auth.getName() : "admin";
            FlagType flagType = FlagType.valueOf(type.toUpperCase());
            flagService.createFlag(project, key, name, description != null ? description : "", flagType, owner);
            ra.addFlashAttribute("successMessage", "Feature flag '" + key + "' created successfully!");
            return "redirect:/flags/" + key + "?project=" + project + "&env=" + env;
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to create flag: " + e.getMessage());
            return "redirect:/flags?project=" + project + "&env=" + env;
        }
    }

    @PostMapping("/flags/{key}/archive")
    public String archiveFlagUi(
            @PathVariable String key,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Authentication auth, RedirectAttributes ra) {
        try {
            String author = auth != null ? auth.getName() : "admin";
            flagService.archiveFlag(project, key, author);
            ra.addFlashAttribute("successMessage", "Flag '" + key + "' archived successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Failed to archive flag: " + e.getMessage());
        }
        return "redirect:/flags?project=" + project + "&env=" + env;
    }

    @GetMapping("/approvals")
    public String approvals(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("pendingApprovals", approvalRepo.findByStatusOrderByRequestedAtDesc(ApprovalStatus.PENDING));
        model.addAttribute("allApprovals", approvalRepo.findAll());

        return "approvals";
    }

    @GetMapping("/audit")
    public String auditLog(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("versions", versionRepo.findTop50ByOrderByCreatedAtDesc());

        return "audit";
    }

    @PostMapping("/audit/rollback")
    public String rollbackUi(
            @RequestParam String flagKey,
            @RequestParam long targetVersion,
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Authentication auth, RedirectAttributes ra) {

        try {
            String author = auth != null ? auth.getName() : "admin";
            flagService.rollbackToVersion(project, env, flagKey, targetVersion, author);
            ra.addFlashAttribute("successMessage", "Flag '" + flagKey + "' rolled back to version " + targetVersion + "!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Rollback failed: " + e.getMessage());
        }

        return "redirect:/audit?project=" + project + "&env=" + env;
    }

    @GetMapping("/experiments")
    public String experiments(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam(defaultValue = "bkash-one-click-checkout") String flag,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("selectedFlag", flag);
        model.addAttribute("report", experimentService.generateReport(project, env, flag));
        model.addAttribute("flags", flagRepo.findByProjectKeyAndArchivedFalse(project));

        return "experiments";
    }

    @GetMapping("/hygiene")
    public String hygiene(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("staleFlags", hygieneScanner.scanForStaleFlags());

        return "hygiene";
    }

    @GetMapping("/users")
    public String users(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("usersList", userRepo.findAll());

        return "users";
    }

    @GetMapping("/playground")
    public String playground(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "production") String env,
            @RequestParam(defaultValue = "bkash-one-click-checkout") String flagKey,
            @RequestParam(defaultValue = "nayem1541") String userKey,
            @RequestParam(defaultValue = "BD") String country,
            @RequestParam(defaultValue = "2.4.0") String appVersion,
            Model model, Authentication auth) {

        addCommonAttributes(model, project, env, auth);
        model.addAttribute("flagKey", flagKey);
        model.addAttribute("userKey", userKey);
        model.addAttribute("country", country);
        model.addAttribute("appVersion", appVersion);

        Map<String, Object> snapshot = flagService.getSnapshot(project, env);
        @SuppressWarnings("unchecked")
        Map<String, FlagDefinition> flagMap = (Map<String, FlagDefinition>) snapshot.get("flags");
        FlagDefinition def = flagMap != null ? flagMap.get(flagKey) : null;

        EvaluationContext ctx = EvaluationContext.builder(userKey)
            .attribute("country", country)
            .attribute("appVersion", appVersion)
            .build();

        FlagEvaluator.EvaluationResult<?> result = evaluator.evaluate(def, ctx, "FALLBACK");
        model.addAttribute("evaluationResult", result);
        model.addAttribute("availableFlags", flagRepo.findByProjectKeyAndArchivedFalse(project));

        return "playground";
    }
}
