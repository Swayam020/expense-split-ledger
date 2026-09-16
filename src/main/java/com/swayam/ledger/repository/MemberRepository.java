package com.swayam.ledger.repository;

import com.swayam.ledger.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    List<Member> findByGroupId(UUID groupId);
}
