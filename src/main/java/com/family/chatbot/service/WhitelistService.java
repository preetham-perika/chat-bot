package com.family.chatbot.service;

import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.model.FamilyMember;
import com.family.chatbot.model.UserRole;
import com.family.chatbot.repository.FamilyMemberRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to restrict access strictly to whitelisted family numbers.
 * Backed by persistent database records (FamilyMember entity) and in-code configuration.
 * Only the Master Admin has permissions to add, modify, or remove family members.
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    /**
     * =========================================================================
     * IN-CODE MASTER ADMIN PHONE NUMBER
     * Master Admin: +91 8790208445
     * =========================================================================
     */
    private static final String IN_CODE_ADMIN_NUMBER = "918790208445";

    /**
     * =========================================================================
     * IN-CODE FAMILY MEMBERS WHITELIST
     * Add default family members here.
     * =========================================================================
     */
    private static final Set<String> IN_CODE_FAMILY_MEMBERS = Set.of(
            // "919876543211", // Family Member 1
            // "919876543212"  // Family Member 2
    );

    private final WhatsAppProperties properties;
    private final FamilyMemberRepository memberRepository;
    private final com.family.chatbot.service.sync.DriveSyncService driveSyncService;

    public WhitelistService(WhatsAppProperties properties, FamilyMemberRepository memberRepository) {
        this(properties, memberRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WhitelistService(WhatsAppProperties properties,
                            FamilyMemberRepository memberRepository,
                            @org.springframework.beans.factory.annotation.Autowired(required = false) com.family.chatbot.service.sync.DriveSyncService driveSyncService) {
        this.properties = properties;
        this.memberRepository = memberRepository;
        this.driveSyncService = driveSyncService;
    }

    @PostConstruct
    @Transactional
    public void init() {
        // 1. Initialize Admin Number in DB
        String adminPhone = !IN_CODE_ADMIN_NUMBER.isBlank()
                ? normalize(IN_CODE_ADMIN_NUMBER)
                : normalize(properties.getAdminPhoneNumber());

        if (!adminPhone.isBlank()) {
            Optional<FamilyMember> existingAdmin = memberRepository.findAll().stream()
                    .filter(m -> m.getRole() == UserRole.ADMIN)
                    .findFirst();

            if (existingAdmin.isPresent()) {
                FamilyMember admin = existingAdmin.get();
                if (!admin.getPhoneNumber().equals(adminPhone)) {
                    admin.setPhoneNumber(adminPhone);
                    memberRepository.save(admin);
                    log.info("Updated existing Master Admin phone number in database to: +{}", adminPhone);
                }
            } else if (!memberRepository.existsByPhoneNumber(adminPhone)) {
                memberRepository.save(new FamilyMember("Master Admin", adminPhone, UserRole.ADMIN, "Admin"));
                log.info("Initialized Master Admin in database: +{}", adminPhone);
            }
        }

        // 2. Initialize in-code family members
        for (String num : IN_CODE_FAMILY_MEMBERS) {
            String norm = normalize(num);
            if (!norm.isBlank() && !memberRepository.existsByPhoneNumber(norm)) {
                memberRepository.save(new FamilyMember("Family Member", norm, UserRole.MEMBER, "Member_" + norm));
            }
        }

        // 3. Initialize members from application.yml
        if (properties.getWhitelist() != null) {
            for (String num : properties.getWhitelist()) {
                String norm = normalize(num);
                if (!norm.isBlank() && !memberRepository.existsByPhoneNumber(norm)) {
                    memberRepository.save(new FamilyMember("Family Member", norm, UserRole.MEMBER, "Member_" + norm));
                }
            }
        }

        List<FamilyMember> activeMembers = memberRepository.findByActiveTrue();
        log.info("WhitelistService loaded {} active family members from database", activeMembers.size());

        // Pre-create and ensure Google Drive and local folders exist for all active family members
        if (driveSyncService != null) {
            for (FamilyMember m : activeMembers) {
                driveSyncService.createMemberFolder(m.getFolderName());
            }
        }
    }

    /**
     * Validates and normalizes an Indian phone number (+91 followed by 10 digits).
     * Accepts formats like:
     * - "+91 8790208445"
     * - "+918790208445"
     * - "+91-8790208445"
     * - "918790208445"
     * - "8790208445"
     *
     * Returns the normalized 12-digit format "918790208445".
     * Throws IllegalArgumentException if invalid.
     */
    public static String validateAndNormalizeIndianNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be empty. Must be an Indian number (+91 followed by 10 digits).");
        }

        String clean = rawPhone.trim().toLowerCase();
        if (clean.startsWith("whatsapp:")) {
            clean = clean.substring("whatsapp:".length()).trim();
        }

        // Check for non-Indian foreign country codes (e.g. +1, +44, etc.)
        if (clean.startsWith("+")) {
            String withoutPlus = clean.substring(1).replaceAll("[^0-9]", "");
            if (!withoutPlus.startsWith("91")) {
                throw new IllegalArgumentException("Invalid phone number '" + rawPhone + "': Only +91 (India) phone numbers are allowed.");
            }
        }

        String digitsOnly = clean.replaceAll("[^0-9]", "");

        // If 10 digits, prefix 91
        if (digitsOnly.length() == 10) {
            digitsOnly = "91" + digitsOnly;
        }

        // Strict check: Must be 12 digits starting with 91 (country code 91 + 10-digit number)
        if (!digitsOnly.matches("^91[0-9]{10}$")) {
            throw new IllegalArgumentException("Invalid phone number '" + rawPhone + "'. Only +91 followed by 10 digits is allowed (e.g. +91 8790208445).");
        }

        return digitsOnly;
    }

    /**
     * Determine the role of a given sender phone number.
     */
    public UserRole getRole(String rawPhoneNumber) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            return UserRole.UNAUTHORIZED;
        }

        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);

        return opt.map(FamilyMember::getRole).orElse(UserRole.UNAUTHORIZED);
    }

    public boolean isAdmin(String rawPhoneNumber) {
        return getRole(rawPhoneNumber) == UserRole.ADMIN;
    }

    public boolean isAuthorized(String rawPhoneNumber) {
        return getRole(rawPhoneNumber) != UserRole.UNAUTHORIZED;
    }

    public Optional<FamilyMember> getMember(String rawPhoneNumber) {
        return findMember(normalize(rawPhoneNumber));
    }

    public String getUserDisplayName(String rawPhoneNumber) {
        return getMember(rawPhoneNumber)
                .map(FamilyMember::getName)
                .orElse("Family Member");
    }

    public String getUserFolderName(String rawPhoneNumber) {
        return getMember(rawPhoneNumber)
                .map(FamilyMember::getFolderName)
                .orElseGet(() -> "Member_" + normalize(rawPhoneNumber));
    }

    /**
     * Admin action: Register a new family member.
     * Enforces strict Indian phone number validation and duplicate check.
     */
    @Transactional
    public FamilyMember addFamilyMember(String name, String rawPhoneNumber, UserRole role) {
        String normalized = validateAndNormalizeIndianNumber(rawPhoneNumber);

        Optional<FamilyMember> existing = memberRepository.findByPhoneNumber(normalized);
        if (existing.isPresent()) {
            FamilyMember member = existing.get();
            if (member.isActive()) {
                throw new IllegalArgumentException(String.format(
                        "Duplicate phone number detected: +%s is already registered in the whitelist as '%s' (%s). Duplicate numbers are not allowed.",
                        normalized, member.getName(), member.getRole()));
            } else {
                // Reactivate previously deactivated member
                member.setName(name);
                member.setActive(true);
                member.setFolderName(name.replaceAll("[^a-zA-Z0-9_-]", "_"));
                member.setRole(role);
                FamilyMember saved = memberRepository.save(member);
                if (driveSyncService != null) {
                    driveSyncService.createMemberFolder(saved.getFolderName());
                }
                return saved;
            }
        }

        String safeFolderName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        FamilyMember member = new FamilyMember(name, normalized, role, safeFolderName);
        FamilyMember saved = memberRepository.save(member);
        if (driveSyncService != null) {
            driveSyncService.createMemberFolder(safeFolderName);
        }
        return saved;
    }

    /**
     * Admin action: Deactivate / remove a family member.
     */
    @Transactional
    public boolean removeFamilyMember(String rawPhoneNumber) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isPresent()) {
            FamilyMember member = opt.get();
            if (member.getRole() == UserRole.ADMIN) {
                throw new IllegalStateException("Cannot remove Master Admin account");
            }
            member.setActive(false);
            memberRepository.save(member);
            return true;
        }
        return false;
    }

    /**
     * Admin action: Rename / modify a family member.
     */
    @Transactional
    public boolean modifyFamilyMember(String rawPhoneNumber, String newName) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isPresent()) {
            FamilyMember member = opt.get();
            member.setName(newName);
            String safe = newName.replaceAll("[^a-zA-Z0-9_-]", "_");
            member.setFolderName(safe);
            memberRepository.save(member);
            if (driveSyncService != null) {
                driveSyncService.createMemberFolder(safe);
            }
            return true;
        }
        return false;
    }

    public List<FamilyMember> getAllActiveMembers() {
        return memberRepository.findByActiveTrue();
    }

    public Set<String> getMemberNumbers() {
        return memberRepository.findByActiveTrue().stream()
                .filter(m -> m.getRole() == UserRole.MEMBER)
                .map(FamilyMember::getPhoneNumber)
                .collect(Collectors.toSet());
    }

    public String getAdminNumber() {
        return memberRepository.findByActiveTrue().stream()
                .filter(m -> m.getRole() == UserRole.ADMIN)
                .map(FamilyMember::getPhoneNumber)
                .findFirst()
                .orElseGet(() -> normalize(properties.getAdminPhoneNumber()));
    }

    /**
     * Check if a user has access to a specific document service (e.g. AADHAAR, PAN, DRIVING_LICENSE).
     * Admin has access to all services automatically.
     */
    public boolean hasService(String rawPhoneNumber, String serviceKey) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isEmpty()) {
            return false;
        }
        return opt.get().hasService(serviceKey);
    }

    /**
     * Retrieve the set of allowed services for a user.
     */
    public Set<String> getUserServices(String rawPhoneNumber) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        return opt.map(FamilyMember::getAllowedServiceSet).orElse(Set.of());
    }

    /**
     * Admin action: Dynamically update allowed services for a user.
     */
    @Transactional
    public boolean updateUserServices(String rawPhoneNumber, Set<String> services) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isPresent()) {
            FamilyMember member = opt.get();
            String joined = (services != null && !services.isEmpty())
                    ? String.join(",", services)
                    : "";
            member.setAllowedServices(joined);
            memberRepository.save(member);
            log.info("Updated allowed services for +{}: {}", normalized, joined);
            return true;
        }
        return false;
    }

    /**
     * Admin action: Add a specific document service to a user.
     */
    @Transactional
    public boolean addUserService(String rawPhoneNumber, String serviceKey) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isPresent()) {
            FamilyMember member = opt.get();
            Set<String> set = new HashSet<>(member.getAllowedServiceSet());
            set.add(serviceKey.toUpperCase().trim());
            member.setAllowedServices(String.join(",", set));
            memberRepository.save(member);
            log.info("Added service '{}' for user +{}", serviceKey, normalized);
            return true;
        }
        return false;
    }

    /**
     * Admin action: Remove a specific document service from a user.
     */
    @Transactional
    public boolean removeUserService(String rawPhoneNumber, String serviceKey) {
        String normalized = normalize(rawPhoneNumber);
        Optional<FamilyMember> opt = findMember(normalized);
        if (opt.isPresent()) {
            FamilyMember member = opt.get();
            Set<String> set = new HashSet<>(member.getAllowedServiceSet());
            set.remove(serviceKey.toUpperCase().trim());
            member.setAllowedServices(String.join(",", set));
            memberRepository.save(member);
            log.info("Removed service '{}' from user +{}", serviceKey, normalized);
            return true;
        }
        return false;
    }

    private Optional<FamilyMember> findMember(String normalized) {
        if (normalized.isBlank()) return Optional.empty();

        // Exact match
        Optional<FamilyMember> direct = memberRepository.findByPhoneNumber(normalized);
        if (direct.isPresent() && direct.get().isActive()) {
            return direct;
        }

        // Suffix/prefix match for country code variations
        for (FamilyMember m : memberRepository.findByActiveTrue()) {
            String p = m.getPhoneNumber();
            if (normalized.endsWith(p) || p.endsWith(normalized)) {
                return Optional.of(m);
            }
        }

        return Optional.empty();
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return "";
        }
        String clean = phone.trim().toLowerCase();
        if (clean.startsWith("whatsapp:")) {
            clean = clean.substring("whatsapp:".length()).trim();
        }
        return clean.replaceAll("[^0-9]", "");
    }
}
