package com.family.chatbot;

import com.family.chatbot.config.WhatsAppProperties;
import com.family.chatbot.model.FamilyMember;
import com.family.chatbot.model.UserRole;
import com.family.chatbot.repository.FamilyMemberRepository;
import com.family.chatbot.service.WhitelistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhitelistServiceTest {

    private WhitelistService whitelistService;
    private WhatsAppProperties properties;
    private FamilyMemberRepository memberRepository;

    private static final String ADMIN_PHONE = "918790208445";
    private static final String MEMBER_1 = "919876543211";
    private static final String MEMBER_2 = "919876543212";

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(FamilyMemberRepository.class);
        List<FamilyMember> list = new ArrayList<>();

        Mockito.when(memberRepository.save(ArgumentMatchers.any(FamilyMember.class)))
                .thenAnswer(i -> {
                    FamilyMember m = i.getArgument(0);
                    list.removeIf(x -> x.getPhoneNumber().equals(m.getPhoneNumber()));
                    list.add(m);
                    return m;
                });
        Mockito.when(memberRepository.findByActiveTrue()).thenAnswer(i -> new ArrayList<>(list));
        Mockito.when(memberRepository.findAll()).thenAnswer(i -> new ArrayList<>(list));
        Mockito.when(memberRepository.findByPhoneNumber(ArgumentMatchers.anyString()))
                .thenAnswer(i -> {
                    String p = i.getArgument(0);
                    return list.stream().filter(x -> x.getPhoneNumber().equals(p)).findFirst();
                });
        Mockito.when(memberRepository.existsByPhoneNumber(ArgumentMatchers.anyString()))
                .thenAnswer(i -> {
                    String p = i.getArgument(0);
                    return list.stream().anyMatch(x -> x.getPhoneNumber().equals(p));
                });

        properties = new WhatsAppProperties();
        properties.setAdminPhoneNumber(ADMIN_PHONE);
        properties.setWhitelist(List.of(MEMBER_1, MEMBER_2));

        whitelistService = new WhitelistService(properties, memberRepository);
        whitelistService.init();
    }

    @Test
    void testAdminRoleDetection() {
        // Direct number
        assertEquals(UserRole.ADMIN, whitelistService.getRole(ADMIN_PHONE));
        // With plus sign
        assertEquals(UserRole.ADMIN, whitelistService.getRole("+" + ADMIN_PHONE));
        // With whatsapp: prefix
        assertEquals(UserRole.ADMIN, whitelistService.getRole("whatsapp:+" + ADMIN_PHONE));
        // With spaces and hyphens
        assertEquals(UserRole.ADMIN, whitelistService.getRole("+91 87902-08445"));

        assertTrue(whitelistService.isAdmin(ADMIN_PHONE));
        assertTrue(whitelistService.isAuthorized(ADMIN_PHONE));
    }

    @Test
    void testMemberRoleDetection() {
        assertEquals(UserRole.MEMBER, whitelistService.getRole(MEMBER_1));
        assertEquals(UserRole.MEMBER, whitelistService.getRole("+" + MEMBER_2));
        assertEquals(UserRole.MEMBER, whitelistService.getRole("whatsapp:+" + MEMBER_1));

        assertFalse(whitelistService.isAdmin(MEMBER_1));
        assertTrue(whitelistService.isAuthorized(MEMBER_1));
    }

    @Test
    void testUnauthorizedNumberRejected() {
        assertEquals(UserRole.UNAUTHORIZED, whitelistService.getRole("919999999999"));
        assertFalse(whitelistService.isAuthorized("919999999999"));
        assertFalse(whitelistService.isAdmin("919999999999"));

        assertEquals(UserRole.UNAUTHORIZED, whitelistService.getRole(""));
        assertEquals(UserRole.UNAUTHORIZED, whitelistService.getRole(null));
    }

    @Test
    void testPhoneNormalization() {
        assertEquals("918790208445", WhitelistService.normalize("+91 87902 08445"));
        assertEquals("918790208445", WhitelistService.normalize("whatsapp:+91-87902-08445"));
        assertEquals("918790208445", WhitelistService.normalize(" (91) 8790208445 "));
    }

    @Test
    void testIndianPhoneNumberValidation_ValidFormats() {
        assertEquals("918790208445", WhitelistService.validateAndNormalizeIndianNumber("+ 91 8790208445"));
        assertEquals("918790208445", WhitelistService.validateAndNormalizeIndianNumber("+918790208445"));
        assertEquals("918790208445", WhitelistService.validateAndNormalizeIndianNumber("+91-8790208445"));
        assertEquals("918790208445", WhitelistService.validateAndNormalizeIndianNumber("918790208445"));
        assertEquals("918790208445", WhitelistService.validateAndNormalizeIndianNumber("8790208445"));
    }

    @Test
    void testIndianPhoneNumberValidation_RejectsForeignOrInvalidNumbers() {
        // Foreign country code +1 (US)
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                WhitelistService.validateAndNormalizeIndianNumber("+1 555 123 4567"));
        assertTrue(ex1.getMessage().contains("Only +91 (India) phone numbers are allowed"));

        // Foreign country code +44 (UK)
        assertThrows(IllegalArgumentException.class, () ->
                WhitelistService.validateAndNormalizeIndianNumber("+44 7911 123456"));

        // Invalid length (too short)
        assertThrows(IllegalArgumentException.class, () ->
                WhitelistService.validateAndNormalizeIndianNumber("+91 12345"));

        // Invalid length (too long)
        assertThrows(IllegalArgumentException.class, () ->
                WhitelistService.validateAndNormalizeIndianNumber("+91 8790208445123"));

        // Null or empty
        assertThrows(IllegalArgumentException.class, () ->
                WhitelistService.validateAndNormalizeIndianNumber(""));
    }

    @Test
    void testDuplicateWhitelistedNumberPrevention() {
        // Adding a new unique valid member succeeds
        FamilyMember sister = whitelistService.addFamilyMember("Sister", "+919876543299", UserRole.MEMBER);
        assertNotNull(sister);
        assertEquals("919876543299", sister.getPhoneNumber());

        // Attempting to add the SAME number again must be blocked with Duplicate Exception!
        IllegalArgumentException dupEx = assertThrows(IllegalArgumentException.class, () ->
                whitelistService.addFamilyMember("Duplicate Sister", "+91 98765-43299", UserRole.MEMBER));

        assertTrue(dupEx.getMessage().contains("Duplicate phone number detected"),
                "Expected duplicate warning but got: " + dupEx.getMessage());

        // Attempting to add existing admin number as member must be blocked as duplicate
        IllegalArgumentException adminDup = assertThrows(IllegalArgumentException.class, () ->
                whitelistService.addFamilyMember("Fake Admin", "+91 8790208445", UserRole.MEMBER));
        assertTrue(adminDup.getMessage().contains("Duplicate phone number detected"));
    }
}
