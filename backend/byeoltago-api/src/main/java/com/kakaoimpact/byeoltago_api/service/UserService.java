package com.kakaoimpact.byeoltago_api.service;

import com.kakaoimpact.byeoltago_api.dto.req.PasswordResetRequestDto;
import com.kakaoimpact.byeoltago_api.dto.req.RegistrationDto;
import com.kakaoimpact.byeoltago_api.model.User;
import com.kakaoimpact.byeoltago_api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // PasswordEncoder 주입 확인


    /**
     * 사용자 회원가입 처리 메서드
     * @param request 회원가입 요청 데이터 (이메일, 비밀번호, 비밀번호 확인)
     * @return 생성된 User 객체
     */
    @Transactional
    public User registerUser(RegistrationDto request) {

        validateRegistrationRequest(request);
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .points(0)
                .build();

        return userRepository.save(newUser);
    }

    /**
     * 핸드폰 번호로 사용자의 이메일을 조회합니다.
     * @param phoneNumber 조회할 핸드폰 번호
     * @return 사용자의 이메일
     * @throws EntityNotFoundException 해당 핸드폰 번호의 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public String findEmailByPhoneNumber(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new EntityNotFoundException("해당 핸드폰 번호로 가입된 사용자를 찾을 수 없습니다: " + phoneNumber));
        return user.getEmail();
    }


    /**
     * 사용자의 비밀번호를 재설정합니다.
     * 휴대폰 번호와 이메일이 모두 일치하는 사용자를 대상으로 합니다.
     *
     * @param requestDto 비밀번호 재설정 요청 데이터 (휴대폰 번호, 이메일, 새 비밀번호, 새 비밀번호 확인)
     * @throws EntityNotFoundException 해당 휴대폰 번호의 사용자를 찾을 수 없는 경우
     * @throws IllegalArgumentException 입력 값 유효성 검사 실패 시 (이메일 불일치, 새 비밀번호 불일치 등)
     */
    @Transactional
    public void resetPassword(PasswordResetRequestDto requestDto) {
        // 1. 휴대폰 번호로 사용자 조회
        User user = userRepository.findByPhoneNumber(requestDto.getPhoneNumber())
                .orElseThrow(() -> new EntityNotFoundException("입력하신 휴대폰 번호로 가입된 사용자를 찾을 수 없습니다."));

        // 2. 조회된 사용자의 이메일과 요청된 이메일이 일치하는지 확인
        if (!user.getEmail().equalsIgnoreCase(requestDto.getEmail())) {
            throw new IllegalArgumentException("입력하신 휴대폰 번호와 이메일 정보가 일치하지 않습니다.");
        }

        // 3. 새 비밀번호와 새 비밀번호 확인이 일치하는지 검사
        if (!requestDto.getNewPassword().equals(requestDto.getNewPasswordConfirmation())) {
            throw new IllegalArgumentException("새 비밀번호와 새 비밀번호 확인이 일치하지 않습니다.");
        }

        // 4. 새 비밀번호 암호화
        String encodedNewPassword = passwordEncoder.encode(requestDto.getNewPassword());

        // 5. 사용자 비밀번호 업데이트
        user.setPassword(encodedNewPassword);

        userRepository.save(user);
    }

    private void validateRegistrationRequest(RegistrationDto request) {
        // 1. 이메일 중복 확인
        if (userRepository.findUserByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        // 2. 닉네임 중복 확인 (추가된 부분)
        if (userRepository.findByNickname(request.getNickname()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        // 3. 비밀번호와 비밀번호 확인 일치 여부 검사
        if (!request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }


    /**
     * Spring Security UserDetailsService 구현 메서드.
     * 사용자 이름(여기서는 이메일)으로 사용자 정보를 로드합니다.
     * @param email 사용자의 이메일 (로그인 시 사용되는 username)
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 해당 이메일의 사용자를 찾을 수 없는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> {
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email);
                });

        // 사용자의 권한 설정 (User 모델에 roles 필드가 있다면 해당 정보를 사용)
        // 여기서는 간단히 "ROLE_USER" 권한을 부여합니다.
        // 실제 애플리케이션에서는 User 엔티티에 Role 정보를 관리하고 가져와야 합니다.
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        // 예시: user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                authorities // 활성화 여부, 계정 만료 여부 등 추가 설정 가능
        );
    }
}
