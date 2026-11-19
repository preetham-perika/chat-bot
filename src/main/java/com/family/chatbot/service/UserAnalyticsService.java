package com.family.chatbot.service;

import com.family.chatbot.model.FamilyMember;
import com.family.chatbot.repository.AuditLogRepository;
import com.family.chatbot.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service to calculate user analytics, activity rankings, and usage graphs.
 * Helps Master Admin understand:
 * 1. Which users login how many times.
 * 2. What services are used most (Aadhaar, PAN, Driving License, Passport, Health, Downloads).
 * 3. Who is using the chatbot mostly.
 */
@Service
public class UserAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(UserAnalyticsService.class);

    private final AuditLogRepository auditRepository;
    private final DocumentRepository documentRepository;
    private final WhitelistService whitelistService;

    public UserAnalyticsService(AuditLogRepository auditRepository,
                                DocumentRepository documentRepository,
                                WhitelistService whitelistService) {
        this.auditRepository = auditRepository;
        this.documentRepository = documentRepository;
        this.whitelistService = whitelistService;
    }

    /**
     * Generate visual WhatsApp analytics message with ASCII/Unicode graphs.
     */
    public String generateWhatsAppAnalyticsGraph() {
        AnalyticsReport report = getAnalyticsReport();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *USER ACTIVITY ANALYSIS & USAGE GRAPH* 📊\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // 1. User Logins Graph
        sb.append("👤 *User Login Counts (Sessions Initiated):*\n");
        if (report.userLogins().isEmpty()) {
            sb.append("   _No user logins recorded yet._\n\n");
        } else {
            for (UserActivityStat u : report.userLogins()) {
                sb.append(String.format("• *%s* (+%s)\n  %s  *%d logins* (%.0f%%)\n",
                        u.displayName(), mask(u.phoneNumber()), u.bar(), u.count(), u.percentage()));
            }
            sb.append("\n");
        }

        // 2. Services Used Most
        sb.append("📈 *Services Used Most (Document Vault Services):*\n");
        if (report.serviceUsage().isEmpty()) {
            sb.append("   _No service interactions recorded yet._\n\n");
        } else {
            for (ServiceUsageStat s : report.serviceUsage()) {
                sb.append(String.format("• %s: *%d* (%.0f%%)\n  %s\n",
                        s.serviceName(), s.count(), s.percentage(), s.bar()));
            }
            sb.append("\n");
        }

        // 3. Overall Bot Usage Ranking
        sb.append("🏆 *Most Active Users (Overall Bot Usage):*\n");
        if (report.userRankings().isEmpty()) {
            sb.append("   _No activity recorded yet._\n\n");
        } else {
            int rank = 1;
            for (UserActivityStat r : report.userRankings()) {
                String medal = rank == 1 ? "🥇" : (rank == 2 ? "🥈" : (rank == 3 ? "🥉" : "•"));
                sb.append(String.format("%s *%s*: %d interactions  %s\n",
                        medal, r.displayName(), r.count(), r.bar()));
                rank++;
            }
            sb.append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("💡 *Summary Highlights:*\n");
        sb.append("• 👑 *Most Active Member:* ").append(report.mostActiveUser()).append("\n");
        sb.append("• 🔥 *Top Used Service:* ").append(report.mostUsedService()).append("\n");
        sb.append("• 🚪 *Total Sessions / Logins:* ").append(report.totalLogins()).append("\n");
        sb.append("• ⚡ *Total Bot Interactions:* ").append(report.totalInteractions()).append("\n");

        return sb.toString();
    }

    /**
     * Build comprehensive structured report for REST API and Web Dashboard.
     */
    public AnalyticsReport getAnalyticsReport() {
        // 1. User logins
        List<Object[]> loginRows = auditRepository.countLoginsBySender();
        long totalLogins = 0;
        long maxLogins = 1;
        Map<String, Long> loginMap = new LinkedHashMap<>();
        for (Object[] row : loginRows) {
            String phone = (String) row[0];
            long count = ((Number) row[1]).longValue();
            loginMap.put(phone, count);
            totalLogins += count;
            if (count > maxLogins) maxLogins = count;
        }

        List<UserActivityStat> loginStats = new ArrayList<>();
        for (Map.Entry<String, Long> entry : loginMap.entrySet()) {
            String phone = entry.getKey();
            long count = entry.getValue();
            double pct = totalLogins > 0 ? (count * 100.0 / totalLogins) : 0;
            String name = whitelistService.getUserDisplayName(phone);
            String bar = renderBar(count, maxLogins, 10);
            loginStats.add(new UserActivityStat(phone, name, count, pct, bar));
        }

        // 2. Service usage
        List<Object[]> serviceRows = auditRepository.countServiceUsage();
        Map<String, Long> serviceMap = new LinkedHashMap<>();
        long totalServices = 0;
        long maxService = 1;

        for (Object[] row : serviceRows) {
            String rawAction = (String) row[0];
            long count = ((Number) row[1]).longValue();
            String prettyName = formatServiceName(rawAction);
            serviceMap.put(prettyName, serviceMap.getOrDefault(prettyName, 0L) + count);
            totalServices += count;
        }

        // Sort descending
        List<Map.Entry<String, Long>> sortedServices = new ArrayList<>(serviceMap.entrySet());
        sortedServices.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (!sortedServices.isEmpty()) {
            maxService = Math.max(1, sortedServices.get(0).getValue());
        }

        List<ServiceUsageStat> serviceStats = new ArrayList<>();
        for (Map.Entry<String, Long> entry : sortedServices) {
            long count = entry.getValue();
            double pct = totalServices > 0 ? (count * 100.0 / totalServices) : 0;
            String bar = renderBar(count, maxService, 12);
            serviceStats.add(new ServiceUsageStat(entry.getKey(), count, pct, bar));
        }

        // 3. User activity ranking (Total actions)
        List<Object[]> activityRows = auditRepository.countTotalActivityBySender();
        long totalInteractions = 0;
        long maxActivity = 1;
        List<UserActivityStat> userRankings = new ArrayList<>();

        for (Object[] row : activityRows) {
            String phone = (String) row[0];
            long count = ((Number) row[1]).longValue();
            totalInteractions += count;
            if (count > maxActivity) maxActivity = count;
        }

        for (Object[] row : activityRows) {
            String phone = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double pct = totalInteractions > 0 ? (count * 100.0 / totalInteractions) : 0;
            String name = whitelistService.getUserDisplayName(phone);
            String bar = renderBar(count, maxActivity, 10);
            userRankings.add(new UserActivityStat(phone, name, count, pct, bar));
        }

        String mostActive = !userRankings.isEmpty() ? userRankings.get(0).displayName() : "None yet";
        String topService = !serviceStats.isEmpty() ? serviceStats.get(0).serviceName() : "None yet";

        return new AnalyticsReport(
                totalLogins,
                totalInteractions,
                mostActive,
                topService,
                loginStats,
                serviceStats,
                userRankings
        );
    }

    private String renderBar(long count, long max, int maxBars) {
        if (max <= 0 || count <= 0) return "░";
        int filled = (int) Math.round(((double) count / max) * maxBars);
        if (filled < 1) filled = 1;
        return "█".repeat(filled);
    }

    private String formatServiceName(String action) {
        if (action == null) return "Other";
        return switch (action.toUpperCase()) {
            case "SERVICE_AADHAAR", "AADHAAR" -> "🪪 Aadhaar Card";
            case "SERVICE_PAN", "PAN" -> "💳 PAN Card";
            case "SERVICE_DRIVING_LICENSE", "DRIVING_LICENSE", "LICENSE" -> "🚗 Driving License";
            case "SERVICE_PASSPORT", "PASSPORT" -> "🛂 Passport";
            case "SERVICE_HEALTH", "HEALTH" -> "🏥 Health & Insurance";
            case "SERVICE_OTHER", "OTHER" -> "📁 Other Documents";
            case "DOWNLOAD_DOCUMENT" -> "📥 File Downloads";
            case "STORE_FILE", "FILE_UPLOAD" -> "📤 Document Uploads";
            case "SAVE_NOTE" -> "📝 Quick Notes";
            case "SEARCH_DOCUMENTS" -> "🔍 Document Searches";
            case "GET_DOCUMENT" -> "👁️ Document View";
            default -> action.replace("_", " ");
        };
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 6) return "******";
        int len = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(len - 3);
    }

    public record AnalyticsReport(
            long totalLogins,
            long totalInteractions,
            String mostActiveUser,
            String mostUsedService,
            List<UserActivityStat> userLogins,
            List<ServiceUsageStat> serviceUsage,
            List<UserActivityStat> userRankings
    ) {}

    public record UserActivityStat(
            String phoneNumber,
            String displayName,
            long count,
            double percentage,
            String bar
    ) {}

    public record ServiceUsageStat(
            String serviceName,
            long count,
            double percentage,
            String bar
    ) {}
}
