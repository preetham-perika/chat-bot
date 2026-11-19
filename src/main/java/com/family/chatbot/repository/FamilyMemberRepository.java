package com.family.chatbot.repository;

import com.family.chatbot.model.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    Optional<FamilyMember> findByPhoneNumber(String phoneNumber);

    List<FamilyMember> findByActiveTrue();

    boolean existsByPhoneNumber(String phoneNumber);
}
