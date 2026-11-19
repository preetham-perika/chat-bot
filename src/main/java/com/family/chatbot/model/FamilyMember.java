package com.family.chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a registered family member.
 * Only the Master Admin can create, modify, or delete family members.
 */
@Entity
@Table(name = "family_members", indexes = {
    @Index(name = "idx_member_phone", columnList = "phoneNumber", unique = true)
})
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, length = 100)
    private String folderName;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Comma-separated list of document services enabled for this user.
     * Default: "AADHAAR,PAN,DRIVING_LICENSE,PASSPORT,HEALTH,OTHER"
     * Admin can add or remove any service dynamically per user!
     */
    @Column(length = 500)
    private String allowedServices;

    public FamilyMember() {
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.allowedServices = "AADHAAR,PAN,DRIVING_LICENSE,PASSPORT,HEALTH,OTHER";
    }

    public FamilyMember(String name, String phoneNumber, UserRole role, String folderName) {
        this(name, phoneNumber, role, folderName, "AADHAAR,PAN,DRIVING_LICENSE,PASSPORT,HEALTH,OTHER");
    }

    public FamilyMember(String name, String phoneNumber, UserRole role, String folderName, String allowedServices) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.folderName = folderName != null && !folderName.isBlank() ? folderName : name.replaceAll("[^a-zA-Z0-9_-]", "_");
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.allowedServices = (allowedServices != null && !allowedServices.isBlank())
                ? allowedServices
                : "AADHAAR,PAN,DRIVING_LICENSE,PASSPORT,HEALTH,OTHER";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getAllowedServices() {
        return allowedServices;
    }

    public void setAllowedServices(String allowedServices) {
        this.allowedServices = allowedServices;
    }

    public java.util.Set<String> getAllowedServiceSet() {
        if (allowedServices == null || allowedServices.isBlank() || "*".equals(allowedServices.trim())) {
            return java.util.Set.of("AADHAAR", "PAN", "DRIVING_LICENSE", "PASSPORT", "HEALTH", "OTHER");
        }
        return java.util.Arrays.stream(allowedServices.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    public boolean hasService(String serviceKey) {
        if (role == UserRole.ADMIN) {
            return true;
        }
        if (serviceKey == null || serviceKey.isBlank()) {
            return true;
        }
        if (allowedServices == null || allowedServices.isBlank() || "*".equals(allowedServices.trim())) {
            return true;
        }
        return getAllowedServiceSet().contains(serviceKey.toUpperCase().trim());
    }
}
