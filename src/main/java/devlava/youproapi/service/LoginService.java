package devlava.youproapi.service;

import devlava.youproapi.domain.TbLmsMember;
import devlava.youproapi.domain.TbStmsRole;
import devlava.youproapi.dto.LoginResponse;
import devlava.youproapi.exception.UserNotFoundException;
import devlava.youproapi.repository.TbLmsMemberRepository;
import devlava.youproapi.repository.TbStmsRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final TbLmsMemberRepository memberRepository;
    private final TbStmsRoleRepository roleRepository;

    /**
     * 사용자 로그인 (skid로 사용자 정보 조회)
     */
    public LoginResponse login(String skid) {
        TbLmsMember member = memberRepository.findById(skid)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + skid));

        return convertToLoginResponse(member, skid);
    }

    /**
     * TbLmsMember -> LoginResponse 변환
     * 권한 정보 포함 (TbStmsRole 조회)
     */
    private LoginResponse convertToLoginResponse(TbLmsMember member, String skid) {
        // TbStmsRole 조회하여 권한 확인
        String role = "담당자"; // 기본값
        java.util.Optional<TbStmsRole> stmsRole = roleRepository.findBySkid(skid);
        if (stmsRole.isPresent()) {
            String roleValue = stmsRole.get().getRole();
            if ("관리자".equals(roleValue)) {
                role = "관리자";
            } else {
                role = "담당자";
            }
        }
        // TbStmsRole에 조회한 계정이 없으면 기본값 "담당자" 유지

        return LoginResponse.builder()
                .skid(member.getSkid())
                .userName(member.getMbName())
                .role(role) // TbStmsRole에서 조회한 권한 사용
                .comCode(parseComCode(member.getComCode()))
                .deptIdx(member.getDeptIdx())
                .deptName(member.getDeptName())
                .mbPosition(member.getMbPosition())
                .build();
    }

    /**
     * comCode String -> Integer 변환
     */
    private Integer parseComCode(String comCode) {
        try {
            return comCode != null ? Integer.parseInt(comCode) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
