package io.flagship.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flagship.core.model.FlagType;
import io.flagship.core.rules.Operator;
import io.flagship.core.rules.RuleNode;
import io.flagship.server.entity.*;
import io.flagship.server.repository.*;
import io.flagship.server.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserAccountRepository userRepo;
    private final ProjectRepository projectRepo;
    private final EnvironmentRepository envRepo;
    private final FeatureFlagRepository flagRepo;
    private final FlagEnvironmentConfigRepository configRepo;
    private final FlagVersionRepository versionRepo;
    private final ExposureEventRepository exposureRepo;
    private final GoalEventRepository goalRepo;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DataInitializer(
            UserAccountRepository userRepo,
            ProjectRepository projectRepo,
            EnvironmentRepository envRepo,
            FeatureFlagRepository flagRepo,
            FlagEnvironmentConfigRepository configRepo,
            FlagVersionRepository versionRepo,
            ExposureEventRepository exposureRepo,
            GoalEventRepository goalRepo,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.projectRepo = projectRepo;
        this.envRepo = envRepo;
        this.flagRepo = flagRepo;
        this.configRepo = configRepo;
        this.versionRepo = versionRepo;
        this.exposureRepo = exposureRepo;
        this.goalRepo = goalRepo;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepo.count() > 0) {
            log.info("Database already seeded. Skipping initial data generation.");
            return;
        }

        log.info("Seeding initial FlagShip platform data...");

        // 1. Seed Users (ADMIN, APPROVER, EDITOR, VIEWER)
        String adminUser = System.getenv("FLAGSHIP_ADMIN_USERNAME");
        if (adminUser == null || adminUser.isBlank()) adminUser = "admin";

        String adminPass = System.getenv("FLAGSHIP_ADMIN_PASSWORD");
        if (adminPass == null || adminPass.isBlank()) adminPass = "admin123";

        userRepo.save(new UserAccount(adminUser, passwordEncoder.encode(adminPass), "Lead Architect", "admin@flagship.io", Role.ADMIN));
        userRepo.save(new UserAccount("approver", passwordEncoder.encode("approver123"), "Release Approver", "approver@flagship.io", Role.APPROVER));
        userRepo.save(new UserAccount("editor", passwordEncoder.encode("editor123"), "Senior Developer", "editor@flagship.io", Role.EDITOR));
        userRepo.save(new UserAccount("viewer", passwordEncoder.encode("viewer123"), "Product Observer", "viewer@flagship.io", Role.VIEWER));

        // 2. Seed Default Project
        Project project = new Project("default", "E-Commerce Core", "Main e-commerce platform & checkout microservices");
        projectRepo.save(project);

        // 3. Seed Environments
        Environment devEnv = new Environment(project, "development", "Development", "fs_dev_default", false);
        Environment stgEnv = new Environment(project, "staging", "Staging", "fs_stg_default", false);
        Environment prodEnv = new Environment(project, "production", "Production", "fs_live_default", true);
        envRepo.saveAll(List.of(devEnv, stgEnv, prodEnv));

        // 4. Seed Feature Flag 1: bkash-one-click-checkout (Boolean, 20% rollout, targeting rules)
        FeatureFlag flagCheckout = new FeatureFlag(
            project, "bkash-one-click-checkout", "bKash One-Click Checkout",
            "Instant payment flow bypassing multi-step redirects for mobile users",
            FlagType.BOOLEAN, "team-payments"
        );
        flagCheckout.setLastEvaluatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        flagRepo.save(flagCheckout);

        RuleNode.LeafClause bdRule = new RuleNode.LeafClause("country", Operator.EQUALS, List.of("BD"));
        RuleNode.LeafClause semverRule = new RuleNode.LeafClause("appVersion", Operator.SEMVER_GREATER_THAN, List.of("2.0.0"));
        RuleNode.AndNode rootAnd = new RuleNode.AndNode(List.of(bdRule, semverRule));
        String rulesJson = objectMapper.writeValueAsString(List.of(
            Map.of("id", "rule-bd-v2", "name", "Bangladesh App v2+", "rootNode", rootAnd, "variation", "on")
        ));
        String targetsJson = objectMapper.writeValueAsString(Map.of("on", Set.of("nayem1541", "vip-user-1")));
        String booleanVariationsJson = objectMapper.writeValueAsString(Map.of("on", true, "off", false));

        // Configs for each env
        FlagEnvironmentConfig devCfg = new FlagEnvironmentConfig(flagCheckout, devEnv);
        devCfg.setEnabled(true);
        devCfg.setRolloutPercentage(100);
        devCfg.setDefaultVariation("on");
        devCfg.setVariationsJson(booleanVariationsJson);
        configRepo.save(devCfg);

        FlagEnvironmentConfig stgCfg = new FlagEnvironmentConfig(flagCheckout, stgEnv);
        stgCfg.setEnabled(true);
        stgCfg.setRolloutPercentage(50);
        stgCfg.setDefaultVariation("off");
        stgCfg.setVariationsJson(booleanVariationsJson);
        configRepo.save(stgCfg);

        FlagEnvironmentConfig prodCfg = new FlagEnvironmentConfig(flagCheckout, prodEnv);
        prodCfg.setEnabled(true);
        prodCfg.setRolloutPercentage(20);
        prodCfg.setDefaultVariation("off");
        prodCfg.setVariationsJson(booleanVariationsJson);
        prodCfg.setIndividualTargetsJson(targetsJson);
        prodCfg.setRulesJson(rulesJson);
        configRepo.save(prodCfg);

        versionRepo.save(new FlagVersion("default", "production", flagCheckout.getKey(), 1L,
            "admin", "Initial release at 20% rollout", prodCfg.getVariationsJson()));

        // 5. Seed Feature Flag 2: welcome-banner-text (String)
        FeatureFlag flagBanner = new FeatureFlag(
            project, "welcome-banner-text", "Welcome Banner Copy",
            "Dynamic header banner copy experimentation",
            FlagType.STRING, "growth-marketing"
        );
        flagBanner.setLastEvaluatedAt(Instant.now().minus(Duration.ofMinutes(15)));
        flagRepo.save(flagBanner);

        String bannerVariations = objectMapper.writeValueAsString(Map.of(
            "standard", "Welcome to FlagShip!",
            "flash_sale", "⚡ Flash 40% Off Weekend Sale!",
            "vip_exclusive", "👑 Exclusive Early Access for VIPs"
        ));
        FlagEnvironmentConfig prodBannerCfg = new FlagEnvironmentConfig(flagBanner, prodEnv);
        prodBannerCfg.setEnabled(true);
        prodBannerCfg.setDefaultVariation("standard");
        prodBannerCfg.setRolloutPercentage(50);
        prodBannerCfg.setVariationsJson(bannerVariations);
        configRepo.save(prodBannerCfg);

        // 6. Seed Feature Flag 3: checkout-discount-pct (Number)
        FeatureFlag flagDiscount = new FeatureFlag(
            project, "checkout-discount-pct", "Checkout Discount Percentage",
            "Dynamic customer loyalty discount rate",
            FlagType.NUMBER, "pricing-team"
        );
        flagDiscount.setLastEvaluatedAt(Instant.now().minus(Duration.ofMinutes(30)));
        flagRepo.save(flagDiscount);

        String discountVariations = objectMapper.writeValueAsString(Map.of(
            "zero", 0,
            "tier_1", 10,
            "tier_2", 20
        ));
        FlagEnvironmentConfig prodDiscountCfg = new FlagEnvironmentConfig(flagDiscount, prodEnv);
        prodDiscountCfg.setEnabled(true);
        prodDiscountCfg.setDefaultVariation("zero");
        prodDiscountCfg.setRolloutPercentage(10);
        prodDiscountCfg.setVariationsJson(discountVariations);
        configRepo.save(prodDiscountCfg);

        // 7. Seed Feature Flag 4: ui-layout-config (JSON)
        FeatureFlag flagLayout = new FeatureFlag(
            project, "ui-layout-config", "Storefront UI Layout Config",
            "Config-driven dashboard widget layout and density",
            FlagType.JSON, "design-systems"
        );
        flagLayout.setLastEvaluatedAt(Instant.now().minus(Duration.ofHours(1)));
        flagRepo.save(flagLayout);

        String layoutVariations = objectMapper.writeValueAsString(Map.of(
            "compact", Map.of("columns", 4, "density", "high", "darkMode", true),
            "standard", Map.of("columns", 3, "density", "normal", "darkMode", false)
        ));
        FlagEnvironmentConfig prodLayoutCfg = new FlagEnvironmentConfig(flagLayout, prodEnv);
        prodLayoutCfg.setEnabled(true);
        prodLayoutCfg.setDefaultVariation("compact");
        prodLayoutCfg.setRolloutPercentage(100);
        prodLayoutCfg.setVariationsJson(layoutVariations);
        configRepo.save(prodLayoutCfg);

        // 8. Seed Feature Flag 5: legacy-api-v1-support (Stale Flag Demonstration for Hygiene)
        FeatureFlag staleFlag = new FeatureFlag(
            project, "legacy-api-v1-support", "Legacy API v1 Backward Compatibility",
            "Deprecated bridge for v1 clients. 100% rolled out for > 20 days with zero traffic.",
            FlagType.BOOLEAN, "platform-infra"
        );
        staleFlag.setCreatedAt(Instant.now().minus(Duration.ofDays(45)));
        staleFlag.setLastEvaluatedAt(Instant.now().minus(Duration.ofDays(35))); // Stale > 30 days
        staleFlag.setFullyRolledOutAt(Instant.now().minus(Duration.ofDays(20))); // 100% > 14 days
        flagRepo.save(staleFlag);

        FlagEnvironmentConfig staleProdCfg = new FlagEnvironmentConfig(staleFlag, prodEnv);
        staleProdCfg.setEnabled(true);
        staleProdCfg.setRolloutPercentage(100);
        staleProdCfg.setDefaultVariation("on");
        staleProdCfg.setVariationsJson(booleanVariationsJson);
        configRepo.save(staleProdCfg);

        // 9. Seed Synthetic Exposure and Conversion Events for bkash-one-click-checkout
        log.info("Generating synthetic experiment data for statistical analysis...");
        Instant eventBase = Instant.now().minus(Duration.ofDays(2));

        // Baseline "off": 500 users, 50 conversions (10.0% conversion rate)
        for (int i = 1; i <= 500; i++) {
            String uid = "user-off-" + i;
            exposureRepo.save(new ExposureEvent("default", "production", "bkash-one-click-checkout", "off", uid, eventBase.plusSeconds(i * 10)));
            if (i <= 50) {
                goalRepo.save(new GoalEvent("default", "production", "bkash-one-click-checkout", "checkout_completed", "off", uid, 25.0, eventBase.plusSeconds(i * 10 + 5)));
            }
        }

        // Treatment "on": 500 users, 75 conversions (15.0% conversion rate) => statistically significant uplift!
        for (int i = 1; i <= 500; i++) {
            String uid = "user-on-" + i;
            exposureRepo.save(new ExposureEvent("default", "production", "bkash-one-click-checkout", "on", uid, eventBase.plusSeconds(i * 10)));
            if (i <= 75) {
                goalRepo.save(new GoalEvent("default", "production", "bkash-one-click-checkout", "checkout_completed", "on", uid, 30.0, eventBase.plusSeconds(i * 10 + 5)));
            }
        }

        log.info("FlagShip platform data initialization completed successfully.");
    }
}
